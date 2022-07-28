/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PartETag;
import com.google.gson.Gson;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.application.resources.model.RecipientInfo;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateCodeResponse;
import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.FileKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.RootDirKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RootFileKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.StatutEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
import fr.gouv.culture.francetransfert.core.model.FormulaireContactData;
import fr.gouv.culture.francetransfert.core.model.NewRecipient;
import fr.gouv.culture.francetransfert.core.services.MimeService;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.services.StorageManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.DateUtils;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.core.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.InvalidCaptchaException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;

@Service
public class UploadServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadServices.class);

	@Value("${enclosure.expire.days}")
	private int expiredays;

	@Value("${bucket.prefix}")
	private String bucketPrefix;

	@Value("${upload.limit}")
	private long uploadLimitSize;

	@Value("${upload.file.limit}")
	private long uploadFileLimitSize;

	@Value("${expire.token.sender}")
	private int daysToExpiretokenSender;

	@Autowired
	private RedisManager redisManager;

	@Value("${upload.expired.limit}")
	private int maxUpdateDate;

	@Value("${upload.limit.senderMail}")
	private Long maxUpload;

	@Value("${upload.token.chunkModulo:20}")
	private int chunkModulo;

	@Value("#{${api.key}}")
	Map<String, Map<String, String[]>> apiKey;

	@Autowired
	private ConfirmationServices confirmationServices;

	@Autowired
	private StorageManager storageManager;

	@Autowired
	private Base64CryptoService base64CryptoService;

	@Autowired
	private StringUploadUtils stringUploadUtils;

	@Autowired
	private MimeService mimeService;

	@Autowired
	private CaptchaService captchaService;

	private LocalDate validateDownloadArchiveAuthorizationPublic(String enclosureId)
			throws MetaloadException, UploadException {

		LocalDate archiveDate;
		String dateString = RedisUtils.getEnclosureValue(redisManager, enclosureId,
				EnclosureKeysEnum.EXPIRED_TIMESTAMP_ARCHIVE.getKey());

		archiveDate = DateUtils.convertStringToLocalDate(dateString);
		if (LocalDate.now().isAfter(archiveDate) && StringUtils.isNotBlank(dateString)) {
			throw new UploadException("Vous ne pouvez plus modifier ces fichiers archivés", enclosureId);
		}

		boolean deleted = Boolean.parseBoolean(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.DELETED.getKey()));
		if (deleted) {
			throw new UploadException("Vous ne pouvez plus accéder à ces fichiers", enclosureId);
		}
		return archiveDate;
	}

	private LocalDate validateDownloadExpiredAuthorizationPublic(String enclosureId)
			throws MetaloadException, UploadException {
		LocalDate expirationDate = DateUtils.convertStringToLocalDate(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
		boolean deleted = Boolean.parseBoolean(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.DELETED.getKey()));
		if (deleted) {
			throw new UploadException("Vous ne pouvez plus accéder à ces fichiers", enclosureId);
		}
		return expirationDate;
	}

	public DeleteRepresentation deleteFile(String enclosureId) {
		DeleteRepresentation deleteRepresentation = new DeleteRepresentation();
		try {
			String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
			String fileToDelete = storageManager.getZippedEnclosureName(enclosureId);
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
			enclosureMap.put(EnclosureKeysEnum.DELETED.getKey(), "true");
			redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
			storageManager.deleteObject(bucketName, fileToDelete);
			redisManager.publishFT(RedisQueueEnum.DELETE_ENCLOSURE_QUEUE.getValue(), enclosureId);
			LOGGER.info("Fichier {} supprime", enclosureId);
			deleteRepresentation.setSuccess(redisManager.deleteKey(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId)));
			deleteRepresentation.setMessage("Fichier supprimé");
			deleteRepresentation.setStatus(HttpStatus.OK.value());
			return deleteRepresentation;
		} catch (Exception e) {
			LOGGER.error("Type: {} -- enclosureId: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(),
					enclosureId, e.getMessage(), e);
			deleteRepresentation.setMessage("Internal error, uuid: " + enclosureId);
			deleteRepresentation.setSuccess(false);
			deleteRepresentation.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			return deleteRepresentation;
		}
	}

	public EnclosureRepresentation updateExpiredTimeStamp(String enclosureId, LocalDate newDate) {
		try {
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (tokenMap != null) {
				Map<String, String> enclosureMap = redisManager
						.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
				enclosureMap.put(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey(), newDate.atStartOfDay().toString());
				redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
				return EnclosureRepresentation.builder().enclosureId(enclosureId).expireDate(newDate.toString())
						.build();
			} else {
				throw new UploadException("tokenMap from Redis is null", enclosureId);
			}
		} catch (Exception e) {
			throw new UploadException("Unable to update timestamp", enclosureId, e);
		}
	}

	public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, String flowIdentifier,
			MultipartFile multipartFile, String enclosureId, String senderId, String senderToken)
			throws MetaloadException, StorageException {

		try {
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
			redisManager.validateToken(senderId, senderToken);
			if ((flowChunkNumber % chunkModulo) == 0) {
				redisManager.extendTokenValidity(senderId, senderToken);
			}

			if (!mimeService.isAuthorisedMimeTypeFromFileName(multipartFile.getOriginalFilename())) {
				LOGGER.error("Extension file no authorised for file {}", multipartFile.getOriginalFilename());
				cleanEnclosure(enclosureId);
				throw new ExtensionNotFoundException(
						"Extension file no authorised for file " + multipartFile.getOriginalFilename());
			}
			LOGGER.debug("Extension file authorised");

			String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
			if (chunkExists(flowChunkNumber, hashFid)) {
				return true; // multipart is uploaded
			}
			String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
			Map<String, String> redisFileInfo = RedisUtils.getFileInfo(redisManager, hashFid);
			String fileNameWithPath = redisFileInfo.get(FileKeysEnum.REL_OBJ_KEY.getKey());
			if (RedisUtils.incrementCounterOfChunkIteration(redisManager, hashFid) == 1) {
				String uploadID = storageManager.generateUploadIdOsu(bucketName, fileNameWithPath);
				RedisForUploadUtils.AddToFileMultipartUploadIdContainer(redisManager, uploadID, hashFid);
				enclosureMap.put(EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.ECC.getCode());
				enclosureMap.put(EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.ECC.getWord());

			}

			String uploadOsuId = RedisForUploadUtils.getUploadIdBlocking(redisManager, hashFid);

			Boolean isUploaded = false;

			LOGGER.debug("Osu bucket name: {}", bucketName);
			PartETag partETag = storageManager.uploadMultiPartFileToOsuBucket(bucketName, flowChunkNumber,
					fileNameWithPath, multipartFile.getInputStream(), multipartFile.getSize(), uploadOsuId);
			String partETagToString = RedisForUploadUtils.addToPartEtags(redisManager, partETag, hashFid);
			LOGGER.debug("PartETag added {} for: {}", partETagToString, hashFid);
			long flowChuncksCounter = RedisUtils.incrementCounterOfUploadChunksPerFile(redisManager, hashFid);
			isUploaded = true;
			LOGGER.debug("FlowChuncksCounter in redis {}", flowChuncksCounter);
			if (flowTotalChunks == flowChuncksCounter) {
				List<PartETag> partETags = RedisForUploadUtils.getPartEtags(redisManager, hashFid);
				String succesUpload = storageManager.completeMultipartUpload(bucketName, fileNameWithPath, uploadOsuId,
						partETags);
				if (succesUpload != null) {
					enclosureMap.put(EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.CHT.getCode());
					enclosureMap.put(EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.CHT.getWord());

					LOGGER.info("Finish upload File for enclosure {} ==> {} ", enclosureId, fileNameWithPath);
					long uploadFilesCounter = RedisUtils.incrementCounterOfUploadFilesEnclosure(redisManager,
							enclosureId);
					if ((uploadFilesCounter % chunkModulo) == 0) {
						redisManager.extendTokenValidity(senderId, senderToken);
					}
					LOGGER.info("Counter of successful upload files for enclosure {} : {} ", enclosureId,
							uploadFilesCounter);
					if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == uploadFilesCounter) {
						redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
						RedisUtils.addPliToDay(redisManager, senderId, enclosureId);
						LOGGER.info("Finish upload enclosure ==> {} ", enclosureId);
					}
				} else {
					enclosureMap.put(EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.ECH.getCode());
					enclosureMap.put(EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.ECH.getWord());
				}
			}
			return isUploaded;
		} catch (ExtensionNotFoundException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.error("Error while uploading enclosure " + enclosureId + " for chunk " + flowChunkNumber
					+ " and flowidentifier " + flowIdentifier + " : " + e.getMessage(), e);
//			try {
//				cleanEnclosure(enclosureId);
//			} catch (Exception e1) {
//				LOGGER.error("Error while cleanin after upload error : " + e.getMessage(), e);
//			}
			throw new UploadException(ErrorEnum.TECHNICAL_ERROR.getValue() + " during file upload : " + e.getMessage(),
					enclosureId, e);
		}
	}

	public boolean chunkExists(int flowChunkNumber, String hashFid) {
		try {
			return RedisUtils.getNumberOfPartEtags(redisManager, hashFid).contains(flowChunkNumber);
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException("Chunk doest not exist : " + e.getMessage(), uuid, e);
		}
	}

	/**
	 *
	 * @param metadata
	 * @param token
	 * @return
	 */
	public EnclosureRepresentation senderInfoWithTockenValidation(FranceTransfertDataRepresentation metadata,
			String token) {
		try {
			LOGGER.info("create metadata in redis with token validation {} / {} ", metadata.getSenderEmail(), token);
			/**
			 * Si l’expéditeur communique une adresse existante dans ignimission, l’envoi
			 * peut se faire sur une adresse externe ou en @email_valide_ignimission (Pas de
			 * règle nécessaire) Si l’expéditeur communique une adresse inexistante dans
			 * ignimission, l’envoi doit se faire exclusivement sur une adresse
			 * en @email_valide_ignimission. Si ce n’est pas le cas, un message d'erreur
			 * s’affiche.
			 **/
			boolean validSender = stringUploadUtils.isValidEmailIgni(metadata.getSenderEmail().toLowerCase());
			boolean validRecipients = false;
			if (!metadata.getPublicLink() && !CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
				validRecipients = metadata.getRecipientEmails().stream().noneMatch(x -> {

					return !stringUploadUtils.isValidEmailIgni(x);

				});
			}

			LOGGER.debug("Can Upload ==> sender {} / recipients {}  ", validSender, validRecipients);
			if (validSender || validRecipients) {
				String language = Locale.FRANCE.toString();
				// added
				if (metadata.getLanguage() != null) {
					language = metadata.getLanguage().toString();
				}

				boolean isRequiredToGeneratedCode = generateCode(metadata.getSenderEmail(), token, language);
				if (!isRequiredToGeneratedCode) {
					return createMetaDataEnclosureInRedis(metadata);
				}
			} else {
				return EnclosureRepresentation.builder().canUpload(false).build();
			}
			return null;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while checking mail creating meta : " + e.getMessage(),
					uuid, e);
		}
	}

	public FileInfoRepresentation getInfoPlis(String enclosureId) throws MetaloadException, UploadException {
		// validate Enclosure download right
		LocalDate expirationDate = validateDownloadExpiredAuthorizationPublic(enclosureId);
		LocalDate expirationArchiveDate = validateDownloadArchiveAuthorizationPublic(enclosureId);

		try {

			String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosureId,
					EnclosureKeysEnum.PASSWORD.getKey());

			boolean withPassword = !StringUtils.isEmpty(passwordRedis);
			passwordRedis = "";

			boolean publicLink = Boolean.parseBoolean(
					RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.PUBLIC_LINK.getKey()));

			List<RecipientInfo> recipientsMails = new ArrayList<>();
			List<RecipientInfo> deletedRecipients = new ArrayList<>();

			int downloadCount = getSenderInfoPlis(enclosureId, recipientsMails, deletedRecipients);

			FileInfoRepresentation fileInfoRepresentation = infoPlis(enclosureId, expirationDate);
			fileInfoRepresentation.setDeletedRecipients(deletedRecipients);
			fileInfoRepresentation.setRecipientsMails(recipientsMails);
			fileInfoRepresentation.setPublicLink(publicLink);
			fileInfoRepresentation.setWithPassword(withPassword);
			fileInfoRepresentation.setDownloadCount(downloadCount);

			boolean archive = false;
			boolean expired = false;

			if (LocalDate.now().isAfter(expirationDate)) {
				expired = true;
				archive = true;
				fileInfoRepresentation.setArchiveUntilDate(expirationArchiveDate);
			}

			fileInfoRepresentation.setArchive(archive);
			fileInfoRepresentation.setExpired(expired);

			return fileInfoRepresentation;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while getting plisInfo : " + e.getMessage(), enclosureId,
					e);
		}
	}

	private int getSenderInfoPlis(String enclosureId, List<RecipientInfo> recipientsMails,
			List<RecipientInfo> deletedRecipients) throws MetaloadException {
		for (Map.Entry<String, String> recipient : RedisUtils.getRecipientsEnclosure(redisManager, enclosureId)
				.entrySet()) {
			RecipientInfo recinfo = buildRecipient(recipient.getKey(), enclosureId);
			if (recinfo.isDeleted()) {
				deletedRecipients.add(recinfo);
			} else {
				recipientsMails.add(recinfo);
			}
		}
		int downloadCount = 0;
		String downString = getNumberOfDownloadPublic(enclosureId);
		if (StringUtils.isNotBlank(downString)) {
			downloadCount = Integer.parseInt(getNumberOfDownloadPublic(enclosureId));
		}
		return downloadCount;
	}

	public FileInfoRepresentation getInfoPlisForReciever(String enclosureId) throws MetaloadException, UploadException {
		// validate Enclosure download right
		LocalDate expirationDate = validateDownloadAuthorizationPublic(enclosureId);
		try {
			FileInfoRepresentation fileInfoRepresentation = infoPlis(enclosureId, expirationDate);
			return fileInfoRepresentation;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while getting plisInfo : " + e.getMessage(), enclosureId,
					e);
		}
	}

	private FileInfoRepresentation infoPlis(String enclosureId, LocalDate expirationDate) throws MetaloadException {
		String message = RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.MESSAGE.getKey());

		String subject = RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.SUBJECT.getKey());

		boolean deleted = Boolean.parseBoolean(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.DELETED.getKey()));

		String senderMail = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
		List<FileRepresentation> rootFiles = getRootFiles(enclosureId);
		List<DirectoryRepresentation> rootDirs = getRootDirs(enclosureId);
		Map<String, String> enclosureMap = redisManager
				.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey((enclosureId)));
		String timestamp = enclosureMap.get(EnclosureKeysEnum.TIMESTAMP.getKey());

		long tailleStr = (long) RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
		String taille = org.apache.commons.io.FileUtils.byteCountToDisplaySize(tailleStr);

		FileInfoRepresentation fileInfoRepresentation = FileInfoRepresentation.builder().validUntilDate(expirationDate)
				.senderEmail(senderMail).message(message).rootFiles(rootFiles).rootDirs(rootDirs).timestamp(timestamp)
				.subject(subject).deleted(deleted).enclosureId(enclosureId).totalSize(taille).build();
		return fileInfoRepresentation;
	}

	public RecipientInfo buildRecipient(String email, String enclosureId) throws MetaloadException {
		String recipientId = RedisUtils.getRecipientId(redisManager, enclosureId, email);
		Map<String, String> recipientMap = redisManager.hmgetAllString(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));
		Set<String> downloadDate = redisManager.smembersString(RedisKeysEnum.FT_Download_Date.getKey(recipientId));
		ArrayList<String> downloadDates = new ArrayList<String>(downloadDate);
		int nbDownload = RedisUtils.getNumberOfDownloadsPerRecipient(redisManager, recipientId);
		boolean deleted = Integer.parseInt(recipientMap.get(RecipientKeysEnum.LOGIC_DELETE.getKey())) == 1;
		RecipientInfo recipient = new RecipientInfo(email, nbDownload, deleted, downloadDates);
		return recipient;
	}

	public boolean resendDonwloadLink(String enclosureId, String email) {
		try {
			LOGGER.debug("create new recipient ");
			Map<String, String> recipientMap = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);
			boolean emailExist = recipientMap.containsKey(email);
			if (!emailExist) {
				LOGGER.error("Recipient doesn't exist");
				throw new UploadException(ErrorEnum.RECIPIENT_DOESNT_EXIST.getValue());
			} else {
				NewRecipient rec = new NewRecipient();
				String idRecipient = RedisUtils.getRecipientId(redisManager, enclosureId, email);
				rec.setMail(email);
				rec.setId(idRecipient);
				rec.setIdEnclosure(enclosureId);
				String recJsonInString = new Gson().toJson(rec);
				redisManager.publishFT(RedisQueueEnum.MAIL_NEW_RECIPIENT_QUEUE.getValue(), recJsonInString);
			}
			return true;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while resending download link : " + e.getMessage(),
					enclosureId, e);
		}
	}

	public boolean addNewRecipientToMetaDataInRedis(String enclosureId, String email) {
		try {
			LOGGER.debug("create new recipient ");
			Map<String, String> recipientMap = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);
			boolean emailExist = recipientMap.containsKey(email);
			if (emailExist) {
				String recipientId = RedisUtils.getRecipientId(redisManager, enclosureId, email);
				String id = recipientMap.get(email);
				Map<String, String> recipient = redisManager
						.hmgetAllString(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));

				recipient.put(RecipientKeysEnum.LOGIC_DELETE.getKey(), "0");
				redisManager.insertHASH(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId), recipient);
			} else {
				NewRecipient rec = new NewRecipient();
				String idRecipient = RedisForUploadUtils.createNewRecipient(redisManager, email, enclosureId);
				rec.setMail(email);
				rec.setId(idRecipient);
				rec.setIdEnclosure(enclosureId);
				String recJsonInString = new Gson().toJson(rec);
				redisManager.publishFT(RedisQueueEnum.MAIL_NEW_RECIPIENT_QUEUE.getValue(), recJsonInString);
			}
			return true;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while adding new recipient : " + e.getMessage(),
					enclosureId, e);
		}
	}

	public boolean logicDeleteRecipient(String enclosureId, String email) throws MetaloadException {
		try {
			LOGGER.debug("delete recipient");
			String recipientId = RedisUtils.getRecipientId(redisManager, enclosureId, email);
			Map<String, String> recipientMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));

			recipientMap.put(RecipientKeysEnum.LOGIC_DELETE.getKey(), "1");
			redisManager.insertHASH(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId), recipientMap);
			return true;

		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while deleting recipient : " + e.getMessage(), email, e);
		}

	}

	public EnclosureRepresentation createMetaDataEnclosureInRedis(FranceTransfertDataRepresentation metadata) {
		if (FileUtils.getEnclosureTotalSize(metadata.getRootFiles(), metadata.getRootDirs()) > uploadLimitSize
				|| FileUtils.getSizeFileOver(metadata.getRootFiles(), uploadFileLimitSize)) {
			LOGGER.error("enclosure size > upload limit size: {}", uploadLimitSize);
			throw new UploadException(ErrorEnum.LIMT_SIZE_ERROR.getValue());
		}
		try {
			LOGGER.debug("limit enclosure size is < upload limit size: {}", uploadLimitSize);
			// generate password if provided one not valid
			if (metadata.getPassword() == null) {
				LOGGER.info("password is null");
			}
			if (base64CryptoService.validatePassword(metadata.getPassword().trim())) {
				LOGGER.info("Hashing password");
				String passwordHashed = base64CryptoService.aesEncrypt(metadata.getPassword().trim());
				metadata.setPassword(passwordHashed);
				metadata.setPasswordGenerated(false);
				LOGGER.debug("calculate pasword hashed ******");
				passwordHashed = "";
			} else {
				LOGGER.info("No password generating new one");
				String generatedPassword = base64CryptoService.generatePassword(0);
				LOGGER.debug("Hashing generated password");
				String passwordHashed = base64CryptoService.aesEncrypt(generatedPassword.trim());
				metadata.setPassword(passwordHashed);
				metadata.setPasswordGenerated(true);
				passwordHashed = "";
			}
			LOGGER.debug("create enclosure metadata in redis ");
			HashMap<String, String> hashEnclosureInfo = RedisForUploadUtils.createHashEnclosure(redisManager, metadata);

			LOGGER.debug("get expiration date and enclosure id back ");
			String enclosureId = hashEnclosureInfo.get(RedisForUploadUtils.ENCLOSURE_HASH_GUID_KEY);
			String expireDate = hashEnclosureInfo.get(RedisForUploadUtils.ENCLOSURE_HASH_EXPIRATION_DATE_KEY);

			LOGGER.debug("update list date-enclosure in redis ");
			RedisUtils.updateListOfDatesEnclosure(redisManager, enclosureId);
			LOGGER.debug("create sender metadata in redis");
			String senderId = RedisForUploadUtils.createHashSender(redisManager, metadata, enclosureId);
			LOGGER.debug("create all recipients metadata in redis ");
			RedisForUploadUtils.createAllRecipient(redisManager, metadata, enclosureId);
			LOGGER.debug("create root-files metadata in redis ");
			RedisForUploadUtils.createRootFiles(redisManager, metadata, enclosureId);
			LOGGER.debug("create root-dirs metadata in redis ");
			RedisForUploadUtils.createRootDirs(redisManager, metadata, enclosureId);
			LOGGER.debug("create contents-files-ids metadata in redis ");
			RedisForUploadUtils.createContentFilesIds(redisManager, metadata, enclosureId);
			LOGGER.info("enclosure id : {} and the sender id : {} and senderMail : {}", enclosureId, senderId,
					metadata.getSenderEmail());
			RedisForUploadUtils.createDeleteToken(redisManager, enclosureId);

			return EnclosureRepresentation.builder().enclosureId(enclosureId).senderId(senderId).expireDate(expireDate)
					.canUpload(Boolean.TRUE).build();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException("Error generating Metadata", uuid, e);
		}
	}

	private boolean generateCode(String senderMail, String token, String currentLanguage) {
		try {
			senderMail = senderMail.toLowerCase();
			boolean result = false;
			// verify token in redis
			if (!StringUtils.isEmpty(token)) {
				try {
					LOGGER.debug("verify token in redis");
					redisManager.validateToken(senderMail, token);
					redisManager.extendTokenValidity(senderMail, token);
				} catch (UploadException e) {
					confirmationServices.generateCodeConfirmation(senderMail, currentLanguage);
					result = true;
					LOGGER.info("generate confirmation code for sender mail {}", senderMail);
				}
			} else {
				LOGGER.debug("token does not exist");
				confirmationServices.generateCodeConfirmation(senderMail, currentLanguage);
				result = true;
				LOGGER.info("generate confirmation code for sender mail {}", senderMail);
			}
			return result;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException(ErrorEnum.TECHNICAL_ERROR.getValue() + " generating code : " + e.getMessage(),
					uuid, e);
		}
	}

	public Boolean validateMailDomain(List<String> mails) {
		Boolean isValid = false;
		isValid = mails.stream().allMatch(mail -> {
			if (stringUploadUtils.isValidEmail(mail)) {
				return stringUploadUtils.isValidEmailIgni(mail);
			}
			return false;
		});

		return isValid;
	}

	public Boolean allowedSendermail(String senderMail) {
		if (!stringUploadUtils.isValidEmailIgni(senderMail)) {
			Long nbUpload = numberTokensOfTheDay(senderMail);
			LOGGER.debug("Upload for user {} = {}", senderMail, nbUpload);
			if (nbUpload >= maxUpload) {
				return false;
			}
			return true;
		}
		return true;
	}

	public List<FileInfoRepresentation> getSenderPlisList(ValidateCodeResponse metadata) throws MetaloadException {
		List<FileInfoRepresentation> listPlis = new ArrayList<FileInfoRepresentation>();
		List<String> result = RedisUtils.getSentPli(redisManager, metadata.getSenderMail());

		if (!CollectionUtils.isEmpty(result)) {
			for (String enclosureId : result) {
				try {
					FileInfoRepresentation enclosureInfo = getInfoPlis(enclosureId);
					if (!enclosureInfo.isDeleted()) {
						listPlis.add(enclosureInfo);
					}
				} catch (Exception e) {
					LOGGER.error("Cannot get plis {} for list ", enclosureId, e);
				}
			}
		}
		return listPlis;
	}

	public List<FileInfoRepresentation> getReceivedPlisList(ValidateCodeResponse metadata) throws MetaloadException {
		List<FileInfoRepresentation> listPlis = new ArrayList<FileInfoRepresentation>();
		List<String> result = RedisUtils.getReceivedPli(redisManager, metadata.getSenderMail());

		if (!CollectionUtils.isEmpty(result)) {
			for (String enclosureId : result) {
				try {
					FileInfoRepresentation enclosureInfo = getInfoPlisForReciever(enclosureId);
					if (!enclosureInfo.isDeleted()) {
						listPlis.add(enclosureInfo);
					}
				} catch (Exception e) {
					LOGGER.error("Cannot get plis {} for list ", enclosureId, e);
				}
			}
		}
		return listPlis;
	}

	private List<FileRepresentation> getRootFiles(String enclosureId) {
		List<FileRepresentation> rootFiles = new ArrayList<>();
		redisManager.lrange(RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId), 0, -1).forEach(rootFileName -> {
			String size = "";
			String hashRootFile = RedisUtils.generateHashsha1(enclosureId + ":" + rootFileName);
			try {
				size = redisManager.getHgetString(RedisKeysEnum.FT_ROOT_FILE.getKey(hashRootFile),
						RootFileKeysEnum.SIZE.getKey());
			} catch (Exception e) {
				throw new UploadException("Cannot get RootFiles : " + e.getMessage(), enclosureId, e);
			}
			FileRepresentation rootFile = new FileRepresentation();
			rootFile.setName(rootFileName);
			rootFile.setSize(Long.valueOf(size));
			rootFiles.add(rootFile);
			LOGGER.debug("root file: {}", rootFileName);
		});
		return rootFiles;
	}

	private List<DirectoryRepresentation> getRootDirs(String enclosureId) {
		List<DirectoryRepresentation> rootDirs = new ArrayList<>();
		redisManager.lrange(RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId), 0, -1).forEach(rootDirName -> {
			String size = "";
			String hashRootDir = RedisUtils.generateHashsha1(enclosureId + ":" + rootDirName);
			try {
				size = redisManager.getHgetString(RedisKeysEnum.FT_ROOT_DIR.getKey(hashRootDir),
						RootDirKeysEnum.TOTAL_SIZE.getKey());
			} catch (Exception e) {
				throw new UploadException("Cannot get RootDirs : " + e.getMessage(), enclosureId, e);
			}
			DirectoryRepresentation rootDir = new DirectoryRepresentation();
			rootDir.setName(rootDirName);
			rootDir.setTotalSize(Long.valueOf(size));
			rootDirs.add(rootDir);
			LOGGER.debug("root Dir: {}", rootDirName);
		});
		return rootDirs;
	}

	private LocalDate validateDownloadAuthorizationPublic(String enclosureId)
			throws MetaloadException, UploadException {
		LocalDate expirationDate = validateExpirationDate(enclosureId);
		boolean deleted = Boolean.parseBoolean(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.DELETED.getKey()));
		if (deleted) {
			throw new UploadException("Vous ne pouvez plus accéder à ces fichiers", enclosureId);
		}
		return expirationDate;
	}

	public LocalDate validateExpirationDate(String enclosureId) throws MetaloadException {
		LocalDate expirationDate = DateUtils.convertStringToLocalDate(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
		if (LocalDate.now().isAfter(expirationDate)) {
			throw new UploadException("Vous ne pouvez plus accéder à ces fichiers", enclosureId);
		}
		return expirationDate;
	}

	private String getNumberOfDownloadPublic(String enclosureId) {
		Map<String, String> enclosureMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
		if (enclosureMap != null) {
			return enclosureMap.get(EnclosureKeysEnum.PUBLIC_DOWNLOAD_COUNT.getKey());
		} else {
			throw new UploadException("Error getting public donwload count", enclosureId);
		}
	}

	private void cleanEnclosure(String prefix) throws MetaloadException, StorageException {
		String bucketName = RedisUtils.getBucketName(redisManager, prefix, bucketPrefix);
		storageManager.deleteFilesWithPrefix(bucketName, prefix);
	}

	private Long numberTokensOfTheDay(String senderMail) {
		Set<String> setTokenInRedis = redisManager.smembersString(RedisKeysEnum.FT_SENDER_PLIS.getKey(senderMail));
		if (CollectionUtils.isNotEmpty(setTokenInRedis)) {
			return setTokenInRedis.stream().count();
		}
		return 0L;
	}

	/**
	 *
	 * @param metadata
	 * @return
	 */
	public boolean senderContact(FormulaireContactData metadata) {

		if (!captchaService.checkCaptcha(metadata.getChallengeId(), metadata.getUserResponse(),
				metadata.getCaptchaType())) {
			throw new InvalidCaptchaException("Captcha incorrect");
		}
		checkNull(metadata);
		String jsonInString = new Gson().toJson(metadata);
		redisManager.publishFT(RedisQueueEnum.FORMULE_CONTACT_QUEUE.getValue(), jsonInString);
		return true;
	}

	public void checkNull(FormulaireContactData metadat) {
		if (StringUtils.isBlank(metadat.getNom())) {
			metadat.setNom("");
		}
		if (StringUtils.isBlank(metadat.getPrenom())) {
			metadat.setPrenom("");
		}
		if (StringUtils.isBlank(metadat.getAdministration())) {
			metadat.setAdministration("");
		}
		if (StringUtils.isBlank(metadat.getSubject())) {
			metadat.setSubject("");
		}
	}

	public boolean isBoolean(String value) {
		return value != null
				&& Arrays.stream(new String[] { "true", "false", "1", "0" }).anyMatch(b -> b.equalsIgnoreCase(value));
	}

	public InitialisationInfo validPassword(String password) {
		InitialisationInfo passwordInfo = null;
		if (!base64CryptoService.validatePassword(password.trim())) {
			passwordInfo = new InitialisationInfo();
			passwordInfo.setCodeChamp("preferences.motDePasse");
			passwordInfo.setNumErreur("ERR_FT01_012");
			passwordInfo.setLibelleErreur(
					"Le mot de passe doit respecter les critères de robustesse et caractères autorisés : 12 caractères minimum - 20 caractères maximum - Au moins 3 lettres minuscules (a-z non accentué) - Au moins 3 lettres majuscules (A-Z non accentué) - Au moins 3 chiffres - Au moins 3 caractères spéciaux (!@#$%^&*()_-:+) - aucun caractère spécial non supporté\r\n");
		}
		return passwordInfo;
	}

	public InitialisationInfo validType(String typePli) {

		InitialisationInfo typeInfo = null;
		String[] typeArray = new String[] { "mail", "link" };
		List<String> typeList = new ArrayList<>(Arrays.asList(typeArray));
		if (typePli != null && !typePli.isEmpty()) {
			if (!typeList.contains(typePli)) {
				typeInfo = new InitialisationInfo();
				typeInfo.setCodeChamp("typePli");
				typeInfo.setNumErreur("ERR_FT01_002");
				typeInfo.setLibelleErreur(
						"La valeur fournie pour le champ typePli doit appartenir à la liste de valeur « Liste des types de pli »");
			}
		} else {
			typeInfo = new InitialisationInfo();
			typeInfo.setCodeChamp("typePli");
			typeInfo.setNumErreur("ERR_FT01_001");
			typeInfo.setLibelleErreur("Le type de pli est obligatoire");
		}

		return typeInfo;
	}

	public InitialisationInfo validRecipientSender(String senderEmail, List<String> recipientEmails, String typePli) {

		InitialisationInfo recipientSenderInfo = null;

		if (StringUtils.isNotEmpty(senderEmail)) {
			if (stringUploadUtils.isValidEmail(senderEmail)) {
				boolean validSender = stringUploadUtils.isValidEmailIgni(senderEmail.toLowerCase());
				if (validSender) {
					if (typePli.equals("mail")) {
						if (CollectionUtils.isNotEmpty(recipientEmails)) {
							boolean validFormatRecipients = false;

							validFormatRecipients = recipientEmails.stream().noneMatch(x -> {
								return !stringUploadUtils.isValidEmail(x);
							});
							if (!validFormatRecipients) {
								recipientSenderInfo = new InitialisationInfo();
								recipientSenderInfo.setCodeChamp("ERR_FT01_009");
								recipientSenderInfo.setNumErreur("destinataires.courrielDestinataire");
								recipientSenderInfo.setLibelleErreur(
										"Le courriel destinataire doit respecter le format d’un courriel");
							}
						} else {
							recipientSenderInfo = new InitialisationInfo();
							recipientSenderInfo.setCodeChamp("destinataires");
							recipientSenderInfo.setNumErreur("ERR_FT01_007");
							recipientSenderInfo.setLibelleErreur(
									"Une liste de destinataires est obligatoire si le type de Pli est « courriel »");
						}
					}
				} else {
					recipientSenderInfo = new InitialisationInfo();
					recipientSenderInfo.setCodeChamp("courrielExpediteur");
					recipientSenderInfo.setNumErreur("ERR_FT01_004");
					recipientSenderInfo.setLibelleErreur(
							"Le domaine de messagerie du courriel expéditeur doit être celui d’un agent de l’Etat");
				}
			} else {
				recipientSenderInfo = new InitialisationInfo();
				recipientSenderInfo.setCodeChamp("courrielExpediteur");
				recipientSenderInfo.setNumErreur("ERR_FT01_006");
				recipientSenderInfo.setLibelleErreur("Le courriel expéditeur doit respecter le format d’un courriel");
			}
		} else {
			recipientSenderInfo = new InitialisationInfo();
			recipientSenderInfo.setCodeChamp("courrielExpediteur");
			recipientSenderInfo.setNumErreur("ERR_FT01_004");
			recipientSenderInfo.setLibelleErreur("Le courriel expéditeur est obligatoire");
		}

		return recipientSenderInfo;

	}

	public InitialisationInfo validLangueCourriel(Locale langue) {

		InitialisationInfo langueCourrielInfo = null;
		String[] langueArray = new String[] { "fr_FR", "en_GB" };
		List<String> langueList = new ArrayList<>(Arrays.asList(langueArray));
		if (!langueList.contains(langue.toString())) {
			langueCourrielInfo = new InitialisationInfo();
			langueCourrielInfo.setCodeChamp("preferences.langueCourriel");
			langueCourrielInfo.setNumErreur("ERR_FT01_014");
			langueCourrielInfo.setLibelleErreur(
					"La valeur fournie pour le champ langueCourriel doit appartenir à la liste de valeur « Liste des langues de courriel »");
		}
		return langueCourrielInfo;
	}

	public InitialisationInfo validDateFormat(LocalDate expireDelay) {

		InitialisationInfo dateFormatInfo = null;
		if (!expireDelay.getClass().getSimpleName().equals("LocalDate")) {
			dateFormatInfo = new InitialisationInfo();
			dateFormatInfo.setCodeChamp("preferences.dateValidite");
			dateFormatInfo.setNumErreur("ERR_FT01_011");
			dateFormatInfo.setLibelleErreur("La date de fin de validité doit respecter le format d’une date");
		}

		return dateFormatInfo;
	}

	public InitialisationInfo validPeriodFormat(LocalDate expireDelay) {

		InitialisationInfo periodFormatInfo = null;
		LocalDate now = LocalDate.now();

		long daysBetween = ChronoUnit.DAYS.between(now, expireDelay);
		if (daysBetween > 90 || daysBetween <= 0 || LocalDate.now().isAfter(expireDelay)) {
			periodFormatInfo = new InitialisationInfo();
			periodFormatInfo.setCodeChamp("preferences.dateValidite");
			periodFormatInfo.setNumErreur("ERR_FT01_010");
			periodFormatInfo
					.setLibelleErreur("La date de fin de validité du pli doit être comprise entre J+1 et J+90 jours");
		}

		return periodFormatInfo;
	}

	public InitialisationInfo validProtectionArchive(Boolean protectionArchive) {

		InitialisationInfo protectionArchiveInfo = null;
		if (!protectionArchive.getClass().getSimpleName().equals("Boolean")) {
			protectionArchiveInfo = new InitialisationInfo();
			protectionArchiveInfo.setCodeChamp("preferences.protectionArchive");
			protectionArchiveInfo.setNumErreur("ERR_FT01_016");
			protectionArchiveInfo.setLibelleErreur("Le champ protectionArchive doit respecter le format d’un booléen");
		}

		return protectionArchiveInfo;
	}

	public InitialisationInfo validSizePackage(List<FileRepresentation> rootFiles,
			List<DirectoryRepresentation> rootDirs) {

		InitialisationInfo SizePackageInfo = null;

		if (CollectionUtils.isNotEmpty(rootFiles) || CollectionUtils.isNotEmpty(rootDirs)) {
			if (FileUtils.getEnclosureTotalSize(rootFiles, rootDirs) == 0) {
				SizePackageInfo = new InitialisationInfo();
				SizePackageInfo.setCodeChamp("fichiers.tailleFichier");
				SizePackageInfo.setNumErreur("ERR_FT01_020");
				SizePackageInfo.setLibelleErreur("La taille de fichier est obligatoire");
			} else {
				if (FileUtils.getEnclosureTotalSize(rootFiles, rootDirs) > uploadLimitSize) {
					SizePackageInfo = new InitialisationInfo();
					SizePackageInfo.setCodeChamp("fichiers.tailleFichier");
					SizePackageInfo.setNumErreur("ERR_FT01_022");
					SizePackageInfo
							.setLibelleErreur("La taille totale du pli ne peut pas dépasser 21 474 836 480 (20 Go)");
				} else {
					if (FileUtils.getSizeFileOver(rootFiles, uploadFileLimitSize)
							|| FileUtils.getSizeDirFileOver(rootDirs, uploadFileLimitSize)) {
						SizePackageInfo = new InitialisationInfo();
						SizePackageInfo.setCodeChamp("fichiers.tailleFichier");
						SizePackageInfo.setNumErreur("RG_FT01_021");
						SizePackageInfo.setLibelleErreur(
								"La taille de chaque fichier ne peut pas dépasser 2 147 483 648 octets (2 Go)");
					}

				}
			}
		} else {
			SizePackageInfo = new InitialisationInfo();
			SizePackageInfo.setCodeChamp("fichiers");
			SizePackageInfo.setNumErreur("ERR_FT01_018");
			SizePackageInfo.setLibelleErreur("Au moins un fichier doit être fourni");
		}
		return SizePackageInfo;
	}

	public InitialisationInfo validPathFiles(List<FileRepresentation> rootFiles) {

		InitialisationInfo validFiles = null;
		boolean pathCheck = false;
		for (FileRepresentation rootFile : rootFiles) {
			pathCheck = StringUtils.isNotEmpty(rootFile.getPath());
			if (!pathCheck) {
				validFiles = new InitialisationInfo();
				validFiles.setCodeChamp("fichiers.cheminRelatif");
				validFiles.setNumErreur("ERR_FT01_024");
				validFiles.setLibelleErreur("Le chemin relatif d’accès au fichier est obligatoire");
				return validFiles;
			}
		}

		return validFiles;
	}

	public InitialisationInfo validPathDirs(List<DirectoryRepresentation> rootDirs) {

		InitialisationInfo validDirs = null;

		for (DirectoryRepresentation rootDir : rootDirs) {
			validDirs = validPathFiles(rootDir.getFiles());
		}
		return validDirs;
	}

	public InitialisationInfo validIdNameFiles(List<FileRepresentation> rootFiles) {

		InitialisationInfo validFiles = null;
		Map<String, String> filesMap = FileUtils.RootFilesValidation(rootFiles);
		boolean idCheck = false;
		boolean nameCheck = false;

		for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
			idCheck = StringUtils.isNotEmpty(currentFile.getValue());
			nameCheck = StringUtils.isNotEmpty(currentFile.getKey());
			if (!idCheck) {
				validFiles = new InitialisationInfo();
				validFiles.setCodeChamp("fichiers.idFichier");
				validFiles.setNumErreur("ERR_FT01_023");
				validFiles.setLibelleErreur("L’identifiant de fichier est obligatoire");
				return validFiles;
			} else {
				if (!nameCheck) {
					validFiles = new InitialisationInfo();
					validFiles.setCodeChamp("fichiers.nomFichier");
					validFiles.setNumErreur("RG_FT01_019");
					validFiles.setLibelleErreur("Le nom de fichier est obligatoire");
					return validFiles;
				}
			}
		}

		return validFiles;
	}

	public InitialisationInfo validIdNameDirs(List<DirectoryRepresentation> rootDirs) {

		InitialisationInfo validDirs = null;

		for (DirectoryRepresentation rootDir : rootDirs) {
			validDirs = validIdNameFiles(rootDir.getFiles());
		}
		return validDirs;
	}

	public InitialisationInfo validDomainHeader(String headerAddr, String sender) {

		InitialisationInfo validDomainHeader = null;

		String[] domaine = apiKey.get(headerAddr).get("domaine");// get domain from properties
		String senderDomaine = sender.substring(sender.indexOf("@") + 1);

		if (!StringUtils.contains(domaine[0], senderDomaine)) {
			validDomainHeader = new InitialisationInfo();
			validDomainHeader.setCodeChamp("courrielExpediteur");
			validDomainHeader.setNumErreur("RG_FT01_003");
			validDomainHeader.setLibelleErreur(
					"Le courriel expéditeur doit être du même domaine de messagerie que celui fourni dans le Header");
		}

		return validDomainHeader;
	}

	public InitialisationInfo validIpAddress(String headerAddr, String remoteAddr) {

		InitialisationInfo validIpAddress = null;

		String[] ips = apiKey.get(headerAddr).get("ips");
		boolean ipMatch = Arrays.stream(ips).anyMatch(ip -> {
			IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ip);
			return ipAddressMatcher.matches(remoteAddr);
		});

		if (!ipMatch) {
			validIpAddress = new InitialisationInfo();
			validIpAddress.setCodeChamp("courrielExpediteur");
			validIpAddress.setNumErreur("RG_FT01_003");
			validIpAddress.setLibelleErreur("L'adresse ip de l'expéditeur doit être valider");
		}

		return validIpAddress;
	}

}
