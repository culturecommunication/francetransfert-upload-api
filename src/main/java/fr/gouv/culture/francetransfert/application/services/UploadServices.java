package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PartETag;
import com.google.gson.Gson;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.FileKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.RootDirKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RootFileKeysEnum;
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
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.InvalidCaptchaException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;

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

	public DeleteRepresentation deleteFile(String enclosureId, String token) {
		DeleteRepresentation deleteRepresentation = new DeleteRepresentation();
		try {
			String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (token.equals(tokenMap.get(EnclosureKeysEnum.TOKEN.getKey()))) {
				String fileToDelete = storageManager.getZippedEnclosureName(enclosureId);
				storageManager.deleteObject(bucketName, fileToDelete);
				LOGGER.debug("Fichier supprimé, suppresson du token sur redis");
				deleteRepresentation
						.setSuccess(redisManager.deleteKey(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId)));
				deleteRepresentation.setMessage("Fichier supprimé");
				deleteRepresentation.setStatus(HttpStatus.OK.value());
				return deleteRepresentation;
			} else {
				LOGGER.error("Type: {} -- enclosureId: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(),
						enclosureId, "Invalid Token");
				deleteRepresentation.setMessage("Invalid Token");
				deleteRepresentation.setSuccess(false);
				deleteRepresentation.setStatus(HttpStatus.NOT_FOUND.value());
				return deleteRepresentation;
			}
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

			confirmationServices.validateToken(senderId, senderToken);
			if ((flowChunkNumber % chunkModulo) == 0) {
				confirmationServices.extendTokenValidity(senderId, senderToken);
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
					LOGGER.info("Finish upload File for enclosure {} ==> {} ", enclosureId, fileNameWithPath);
					long uploadFilesCounter = RedisUtils.incrementCounterOfUploadFilesEnclosure(redisManager,
							enclosureId);
					LOGGER.info("Counter of successful upload files for enclosure {} : {} ", enclosureId,
							uploadFilesCounter);
					if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == uploadFilesCounter) {
						redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
						RedisUtils.addPliToDay(redisManager, senderId, enclosureId);
						LOGGER.info("Finish upload enclosure ==> {} ", enclosureId);
					}
				}
			}
			return isUploaded;
		} catch (ExtensionNotFoundException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.error("Error while uploading enclosure" + enclosureId + " : " + e.getMessage(), e);
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
				boolean isRequiredToGeneratedCode = generateCode(metadata.getSenderEmail(), token);
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

	public FileInfoRepresentation getInfoPlis(String enclosureId) throws MetaloadException {
		// validate Enclosure download right
		LocalDate expirationDate = validateDownloadAuthorizationPublic(enclosureId);
		try {
			String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosureId,
					EnclosureKeysEnum.PASSWORD.getKey());
			String message = RedisUtils.getEnclosureValue(redisManager, enclosureId,
					EnclosureKeysEnum.MESSAGE.getKey());
			String senderMail = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
			List<String> recipientsMails = new ArrayList<>();
			List<String> deletedRecipients = new ArrayList<>();
			for (Map.Entry<String, String> recipient : RedisUtils.getRecipientsEnclosure(redisManager, enclosureId)
					.entrySet()) {
				if (buildRecipient(recipient.getKey(), enclosureId)) {
					deletedRecipients.add(recipient.getKey());
				} else {
					recipientsMails.add(recipient.getKey());
				}
			}
			List<FileRepresentation> rootFiles = getRootFiles(enclosureId);
			List<DirectoryRepresentation> rootDirs = getRootDirs(enclosureId);
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey((enclosureId)));
			String timestamp = enclosureMap.get(EnclosureKeysEnum.TIMESTAMP.getKey());
			int downloadCount = 0;
			String downString = getNumberOfDownloadPublic(enclosureId);
			if (StringUtils.isNotBlank(downString)) {
				downloadCount = Integer.parseInt(getNumberOfDownloadPublic(enclosureId));
			}
			FileInfoRepresentation fileInfoRepresentation = FileInfoRepresentation.builder()
					.validUntilDate(expirationDate).senderEmail(senderMail).recipientsMails(recipientsMails)
					.deletedRecipients(deletedRecipients).message(message).rootFiles(rootFiles).rootDirs(rootDirs)
					.timestamp(timestamp).downloadCount(downloadCount).withPassword(!StringUtils.isEmpty(passwordRedis))
					.build();
			passwordRedis = "";
			return fileInfoRepresentation;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while getting plisInfo : " + e.getMessage(), enclosureId,
					e);
		}
	}

	public Boolean buildRecipient(String email, String enclosureId) throws MetaloadException {
		String recipientId = RedisUtils.getRecipientId(redisManager, enclosureId, email);
		Map<String, String> recipientMap = redisManager.hmgetAllString(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));
		if (Integer.parseInt(recipientMap.get(RecipientKeysEnum.LOGIC_DELETE.getKey())) == 1) {
			return true;
		}
		return false;
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

	private EnclosureRepresentation createMetaDataEnclosureInRedis(FranceTransfertDataRepresentation metadata) {
		if (FileUtils.getEnclosureTotalSize(metadata) > uploadLimitSize
				|| FileUtils.getSizeFileOver(metadata, uploadFileLimitSize)) {
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
			LOGGER.info("enclosure id : {} and the sender id : {} ", enclosureId, senderId);
			RedisForUploadUtils.createDeleteToken(redisManager, enclosureId);

			return EnclosureRepresentation.builder().enclosureId(enclosureId).senderId(senderId).expireDate(expireDate)
					.canUpload(Boolean.TRUE).build();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException("Error generating Metadata", uuid, e);
		}
	}

	private boolean generateCode(String senderMail, String token) {
		try {
			senderMail = senderMail.toLowerCase();
			boolean result = false;
			// verify token in redis
			if (!StringUtils.isEmpty(token)) {
				try {
					LOGGER.info("verify token in redis");
					confirmationServices.validateToken(senderMail, token);
					confirmationServices.extendTokenValidity(senderMail, token);
				} catch (UploadException e) {
					confirmationServices.generateCodeConfirmation(senderMail);
					result = true;
					LOGGER.info("generate confirmation code for sender mail {}", senderMail);
				}
			} else {
				LOGGER.info("token does not exist");
				confirmationServices.generateCodeConfirmation(senderMail);
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

	public void validateAdminToken(String enclosureId, String token) {
		Map<String, String> tokenMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
		if (tokenMap != null) {
			if (!token.equals(tokenMap.get(EnclosureKeysEnum.TOKEN.getKey()))) {
				throw new UnauthorizedAccessException("Invalid Token");
			}
		} else {
			throw new UnauthorizedAccessException("Invalid Token");
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

	private LocalDate validateDownloadAuthorizationPublic(String enclosureId) throws MetaloadException {
		LocalDate expirationDate = validateExpirationDate(enclosureId);
		return expirationDate;
	}

	private LocalDate validateExpirationDate(String enclosureId) throws MetaloadException {
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

}
