package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.configuration.ExtensionProperties;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.domain.utils.UploadUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.FileKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RootDirKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RootFileKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.DateUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.utils.Base64CryptoService;

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

	@Autowired
	private ExtensionProperties extensionProp;

	@Autowired
	private ConfirmationServices confirmationServices;

	@Autowired
	private StorageManager storageManager;

	@Autowired
	private Base64CryptoService base64CryptoService;

	public DeleteRepresentation deleteFile(String enclosureId, String token) {
		DeleteRepresentation deleteRepresentation = new DeleteRepresentation();
		try {
			String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (token.equals(tokenMap.get(EnclosureKeysEnum.TOKEN.getKey()))) {
				String fileToDelete = storageManager.getZippedEnclosureName(enclosureId) + ".zip";
				storageManager.deleteObject(bucketName, fileToDelete);
				LOGGER.debug("Fichier supprimé, suppresson du token sur redis");
				deleteRepresentation
						.setSuccess(redisManager.deleteKey(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId)));
				deleteRepresentation.setMessage("Fichier supprimé");
				deleteRepresentation.setStatus(HttpStatus.OK.value());
				return deleteRepresentation;
			} else {
				String uuid = UUID.randomUUID().toString();
				LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
						"Invalid Token");
				deleteRepresentation.setMessage("Invalid Token");
				deleteRepresentation.setSuccess(false);
				deleteRepresentation.setStatus(HttpStatus.NOT_FOUND.value());
				return deleteRepresentation;
			}
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			deleteRepresentation.setMessage("Internal error, uuid: " + uuid);
			deleteRepresentation.setSuccess(false);
			deleteRepresentation.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			return deleteRepresentation;
		}

	}

	public EnclosureRepresentation updateExpiredTimeStamp(String enclosureId, String token, LocalDate newDate)
			throws Exception {
		try {
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (tokenMap != null) {
				Map<String, String> enclosureMap = redisManager
						.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
				enclosureMap.replace(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey(), newDate.atStartOfDay().toString());
				redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
				return EnclosureRepresentation.builder().enclosureId(enclosureId).expireDate(newDate.toString())
						.build();
			} else {
				throw new Exception("tokenMap from Redis is null");
			}
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw e;
		}
	}

	public EnclosureRepresentation updateExpiredTimeStamp(String enclosureId, LocalDate newDate) throws Exception {
		try {
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (tokenMap != null) {
				Map<String, String> enclosureMap = redisManager
						.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
				enclosureMap.replace(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey(), newDate.atStartOfDay().toString());
				redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
				return EnclosureRepresentation.builder().enclosureId(enclosureId).expireDate(newDate.toString())
						.build();
			} else {
				throw new Exception("tokenMap from Redis is null");
			}
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw e;
		}
	}

	public String insertExpiredTimeStamp(String enclosureId, int expireDelay) throws Exception {
		try {
			LocalDate date = LocalDate.now();
			LocalDate dateInsert = date.plusDays(expireDelay);
			LocalDate maxDate = date.plusDays(maxUpdateDate);
			if (dateInsert.isAfter(maxDate)) {
				throw new Exception("Date invalide, veuillez sélectionner une date inférieure à " + maxUpdateDate
						+ " jours depuis la création du pli");
			}
			return dateInsert.atStartOfDay().toString();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw e;
		}
	}

	public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, long flowTotalSize,
			String flowIdentifier, String flowFilename, MultipartFile multipartFile, String enclosureId)
			throws Exception {

		try {
			if (ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), multipartFile,
					flowFilename)) { // Test authorized file to upload.
				LOGGER.error("Extension file no authorised");
				throw new ExtensionNotFoundException("Extension file no authorised");
			}
			LOGGER.info("Extension file authorised");

			String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
			if (chunkExists(redisManager, flowChunkNumber, hashFid)) {
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

			LOGGER.info("Osu bucket name: {}", bucketName);
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
					LOGGER.info("Finish upload File ==> {} ", fileNameWithPath);
					long uploadFilesCounter = RedisUtils.incrementCounterOfUploadFilesEnclosure(redisManager,
							enclosureId);
					LOGGER.info("Counter of successful upload files : {} ", uploadFilesCounter);
					if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == uploadFilesCounter) {
						redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
						LOGGER.info("Finish upload enclosure ==> {} ",
								redisManager.lrange(RedisQueueEnum.ZIP_QUEUE.getValue(), 0, -1));
					}
				}
			}
			return isUploaded;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- error : {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid, e.getMessage(),
					e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public boolean chunkExists(RedisManager redisManager, int flowChunkNumber, String hashFid) throws Exception {
		try {
			return RedisUtils.getNumberOfPartEtags(redisManager, hashFid).contains(flowChunkNumber);
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	/**
	 *
	 * @param metadata
	 * @param token
	 * @return
	 * @throws Exception
	 */
	public EnclosureRepresentation senderInfoWithTockenValidation(FranceTransfertDataRepresentation metadata,
			String token) throws Exception {
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
			// TODO uncomment contrôle mail sender-info
			boolean validSender = redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""),
					StringUploadUtils.getEmailDomain(metadata.getSenderEmail()));
			boolean validRecipients = true;
			if (!metadata.getPublicLink()) {
				if (!CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
					Iterator<String> domainIter = metadata.getRecipientEmails().iterator();
					while (domainIter.hasNext() && validRecipients) {
						validRecipients = redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""),
								StringUploadUtils.getEmailDomain(domainIter.next()));
					}
				}
			}

			LOGGER.debug("Can Upload ==> sender {} / recipients {}  ", validSender, validRecipients);
			if (validSender || validRecipients) {
				boolean isRequiredToGeneratedCode = generateCode(redisManager, metadata.getSenderEmail(), token);
				if (!isRequiredToGeneratedCode) {
					return createMetaDataEnclosureInRedis(metadata, redisManager);
				}
			} else {
				return EnclosureRepresentation.builder().canUpload(false).build();
			}
			return null;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("CODE Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			LOGGER.error("Erreur Code validation : " + e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public FileInfoRepresentation getInfoPlis(String enclosureId) throws Exception {
		// RedisManager redisManager = RedisManager.getInstance();
		// validate Enclosure download right
		LocalDate expirationDate = validateDownloadAuthorizationPublic(redisManager, enclosureId);
		try {
			String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosureId,
					EnclosureKeysEnum.PASSWORD.getKey());
			String message = RedisUtils.getEnclosureValue(redisManager, enclosureId,
					EnclosureKeysEnum.MESSAGE.getKey());
			String senderMail = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
			List<FileRepresentation> rootFiles = getRootFiles(redisManager, enclosureId);
			List<DirectoryRepresentation> rootDirs = getRootDirs(redisManager, enclosureId);
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey((enclosureId)));
			String timestamp = enclosureMap.get(EnclosureKeysEnum.TIMESTAMP.getKey());
			int downloadCount = 0;
			String downString = getNumberOfDownloadPublic(enclosureId);
			if (StringUtils.isNotBlank(downString)) {
				downloadCount = Integer.parseInt(getNumberOfDownloadPublic(enclosureId));
			}
			return FileInfoRepresentation.builder().validUntilDate(expirationDate).senderEmail(senderMail)
					.message(message).rootFiles(rootFiles).rootDirs(rootDirs).timestamp(timestamp)
					.downloadCount(downloadCount).withPassword(!StringUtils.isEmpty(passwordRedis)).build();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public EnclosureRepresentation senderInfoWithCodeValidation(FranceTransfertDataRepresentation metadata, String code)
			throws Exception {
		LOGGER.info("create metadata in redis with code validation");
		confirmationServices.validateCodeConfirmation(redisManager, metadata.getSenderEmail(), code);
		return createMetaDataEnclosureInRedis(metadata, redisManager);
	}

	private EnclosureRepresentation createMetaDataEnclosureInRedis(FranceTransfertDataRepresentation metadata,
			RedisManager redisManager) throws Exception {
		if (FileUtils.getEnclosureTotalSize(metadata) > uploadLimitSize
				|| FileUtils.getSizeFileOver(metadata, uploadFileLimitSize)) {
			LOGGER.error("enclosure size > upload limit size: {}", uploadLimitSize);
			throw new UploadExcption(ErrorEnum.LIMT_SIZE_ERROR.getValue(), null);
		}
		try {
			LOGGER.info("limit enclosure size is < upload limit size: {}", uploadLimitSize);
			// generate password if provided one not valid
			if (base64CryptoService.validatePassword(metadata.getPassword())) {
				LOGGER.info("Hashing password");
				String passwordHashed = base64CryptoService.aesEncrypt(metadata.getPassword());
				metadata.setPassword(passwordHashed);
				LOGGER.info("calculate pasword hashed ******");
			} else {
				LOGGER.info("No password generating new one");
				String generatedPassword = base64CryptoService.generatePassword();
				LOGGER.info("Hashing generated password");
				String passwordHashed = base64CryptoService.aesEncrypt(generatedPassword);
				metadata.setPassword(passwordHashed);
			}
			LOGGER.info("create enclosure metadata in redis ");
			HashMap<String, String> hashEnclosureInfo = RedisForUploadUtils.createHashEnclosure(redisManager, metadata,
					expiredays);
			LOGGER.info("get expiration date and enclosure id back ");
			String enclosureId = hashEnclosureInfo.get(RedisForUploadUtils.EnclosureHashGUIDKey);
			String expireDate = hashEnclosureInfo.get(RedisForUploadUtils.EnclosureHashExpirationDateKey);
			LOGGER.info("update list date-enclosure in redis ");
			RedisUtils.updateListOfDatesEnclosure(redisManager, enclosureId);
			LOGGER.info("create sender metadata in redis");
			String senderId = RedisForUploadUtils.createHashSender(redisManager, metadata, enclosureId);
			LOGGER.info("create all recipients metadata in redis ");
			RedisForUploadUtils.createAllRecipient(redisManager, metadata, enclosureId);
			LOGGER.info("create root-files metadata in redis ");
			RedisForUploadUtils.createRootFiles(redisManager, metadata, enclosureId);
			LOGGER.info("create root-dirs metadata in redis ");
			RedisForUploadUtils.createRootDirs(redisManager, metadata, enclosureId);
			LOGGER.info("create contents-files-ids metadata in redis ");
			RedisForUploadUtils.createContentFilesIds(storageManager, redisManager, metadata, enclosureId,
					bucketPrefix);
			LOGGER.info("enclosure id : {} and the sender id : {} ", enclosureId, senderId);
			RedisForUploadUtils.createDeleteToken(redisManager, enclosureId);

			return EnclosureRepresentation.builder().enclosureId(enclosureId).senderId(senderId)
					.expireDate(hashEnclosureInfo.get(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()))
					.canUpload(Boolean.TRUE).build();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			LOGGER.error("Error Generate metadata: " + e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	private void validateToken(RedisManager redisManager, String senderMail, String token) throws Exception {
		// verify token in redis
		if (token != null && !token.equalsIgnoreCase("unknown")) {
			boolean tokenExistInRedis;
			try {
				Set<String> setTokenInRedis = redisManager
						.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
				tokenExistInRedis = setTokenInRedis.stream().anyMatch(tokenRedis -> tokenRedis.equals(token));
			} catch (Exception e) {
				String uuid = UUID.randomUUID().toString();
				LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
						e.getMessage(), e);
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			}
			if (!tokenExistInRedis) {
				LOGGER.error("invalid token: token does not exist in redis ");
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), null);
			}
		} else {
			LOGGER.error("invalid token ");
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), null);
		}
		LOGGER.info("valid token for sender mail {}", senderMail);
	}

	private boolean generateCode(RedisManager redisManager, String senderMail, String token) throws Exception {
		try {
			boolean result = false;
			// verify token in redis
			if (!StringUtils.isEmpty(token)) {
				LOGGER.info("verify token in redis");
				Set<String> setTokenInRedis = redisManager
						.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
				LOGGER.info("extract all Token sender from redis");
				boolean tokenExistInRedis = setTokenInRedis.stream()
						.anyMatch(tokenRedis -> LocalDate.now().minusDays(1).isBefore(
								UploadUtils.extractStartDateSenderToken(tokenRedis).plusDays(daysToExpiretokenSender))
								&& tokenRedis.equals(token));
				if (!tokenExistInRedis) {
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
			LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public void validateToken(String enclosureId, String token) {
		Map<String, String> tokenMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
		if (tokenMap != null) {
			if (!token.equals(tokenMap.get(EnclosureKeysEnum.TOKEN.getKey()))) {
				String uuid = UUID.randomUUID().toString();
				LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
						"Invalid Token");
				throw new UnauthorizedAccessException("Invalid Token");
			}
		} else {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					"Invalid Token");
			throw new UnauthorizedAccessException("Invalid Token");
		}
	}

	private List<FileRepresentation> getRootFiles(RedisManager redisManager, String enclosureId) throws Exception {
		List<FileRepresentation> rootFiles = new ArrayList<>();
		List<DirectoryRepresentation> rootDirs = new ArrayList<>();
		redisManager.lrange(RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId), 0, -1).forEach(rootFileName -> {
			String size = "";
			String hashRootFile = RedisUtils.generateHashsha1(enclosureId + ":" + rootFileName);
			try {
				size = redisManager.getHgetString(RedisKeysEnum.FT_ROOT_FILE.getKey(hashRootFile),
						RootFileKeysEnum.SIZE.getKey());
			} catch (Exception e) {
				String uuid = UUID.randomUUID().toString();
				LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
						e.getMessage(), e);
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			}
			FileRepresentation rootFile = new FileRepresentation();
			rootFile.setName(rootFileName);
			rootFile.setSize(Long.valueOf(size));
			rootFiles.add(rootFile);
			LOGGER.debug("root file: {}", rootFileName);
		});
		return rootFiles;
	}

	private List<DirectoryRepresentation> getRootDirs(RedisManager redisManager, String enclosureId) throws Exception {
		List<DirectoryRepresentation> rootDirs = new ArrayList<>();
		redisManager.lrange(RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId), 0, -1).forEach(rootDirName -> {
			String size = "";
			String hashRootDir = RedisUtils.generateHashsha1(enclosureId + ":" + rootDirName);
			try {
				size = redisManager.getHgetString(RedisKeysEnum.FT_ROOT_DIR.getKey(hashRootDir),
						RootDirKeysEnum.TOTAL_SIZE.getKey());
			} catch (Exception e) {
				String uuid = UUID.randomUUID().toString();
				LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
						e.getMessage(), e);
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			}
			DirectoryRepresentation rootDir = new DirectoryRepresentation();
			rootDir.setName(rootDirName);
			rootDir.setTotalSize(Long.valueOf(size));
			rootDirs.add(rootDir);
			LOGGER.debug("root Dir: {}", rootDirName);
		});
		return rootDirs;
	}

	private LocalDate validateDownloadAuthorizationPublic(RedisManager redisManager, String enclosureId)
			throws Exception {
		LocalDate expirationDate = validateExpirationDate(redisManager, enclosureId);
		return expirationDate;
	}

	private LocalDate validateExpirationDate(RedisManager redisManager, String enclosureId) throws Exception {
		LocalDate expirationDate = DateUtils.convertStringToLocalDate(
				RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
		if (LocalDate.now().isAfter(expirationDate)) {
			throw new Exception("Vous ne pouvez plus accéder à ces fichiers");
		}
		return expirationDate;
	}

	private String getNumberOfDownloadPublic(String enclosureId) throws Exception {
		Map<String, String> enclosureMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
		if (enclosureMap != null) {
			return enclosureMap.get(EnclosureKeysEnum.PUBLIC_DOWNLOAD_COUNT.getKey());
		} else {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- Message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					"Invalid Token");
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), enclosureId);
		}
	}
}
