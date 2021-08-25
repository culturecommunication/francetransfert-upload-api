package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PartETag;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.configuration.ExtensionProperties;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;
import fr.gouv.culture.francetransfert.domain.utils.UploadUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.FileKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
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

	@Value("${expire.token.sender}")
	private int daysToExpiretokenSender;

	@Autowired
	RedisManager redisManager;

	@Autowired
	private ExtensionProperties extensionProp;

	@Autowired
	private ConfirmationServices confirmationServices;

	@Autowired
	private CookiesServices cookiesServices;

	@Autowired
	StorageManager storageManager;

	@Autowired
	Base64CryptoService base64CryptoService;

	public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, long flowTotalSize,
			String flowIdentifier, String flowFilename, MultipartFile multipartFile, String enclosureId)
			throws Exception {

		try {
			if (ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), multipartFile,
					flowFilename)) { // Test authorized file to upload.
				LOGGER.error("================ extension file no authorised");
				throw new ExtensionNotFoundException("================ extension file no authorised");
			}
			LOGGER.info("================ extension file authorised");

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

			LOGGER.info("================ osu bucket name: {}", bucketName);
			PartETag partETag = storageManager.uploadMultiPartFileToOsuBucket(bucketName, flowChunkNumber,
					fileNameWithPath, multipartFile.getInputStream(), multipartFile.getSize(), uploadOsuId);
			String partETagToString = RedisForUploadUtils.addToPartEtags(redisManager, partETag, hashFid);
			LOGGER.debug("================ partETag added {} for: {}", partETagToString, hashFid);
			long flowChuncksCounter = RedisUtils.incrementCounterOfUploadChunksPerFile(redisManager, hashFid);
			isUploaded = true;
			LOGGER.debug("================ flowChuncksCounter in redis {}", flowChuncksCounter);
			if (flowTotalChunks == flowChuncksCounter) {
				List<PartETag> partETags = RedisForUploadUtils.getPartEtags(redisManager, hashFid);
				String succesUpload = storageManager.completeMultipartUpload(bucketName, fileNameWithPath, uploadOsuId,
						partETags);
				if (succesUpload != null) {
					LOGGER.info("================ finish upload File ==> {} ", fileNameWithPath);
					long uploadFilesCounter = RedisUtils.incrementCounterOfUploadFilesEnclosure(redisManager,
							enclosureId);
					LOGGER.info("================ counter of successful upload files : {} ", uploadFilesCounter);
					if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == uploadFilesCounter) {
						redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
						LOGGER.info("================ finish upload enclosure ==> {} ",
								redisManager.lrange(RedisQueueEnum.ZIP_QUEUE.getValue(), 0, -1));
					}
				}
			}
			return isUploaded;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public boolean chunkExists(RedisManager redisManager, int flowChunkNumber, String hashFid) throws Exception {
		try {
			return RedisUtils.getNumberOfPartEtags(redisManager, hashFid).contains(flowChunkNumber);
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
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
			LOGGER.info("==============================> create metadata in redis with token validation {} / {} ",
					metadata.getSenderEmail(), token);
			/**
			 * Si l’expéditeur communique une adresse existante dans ignimission, l’envoi
			 * peut se faire sur une adresse externe ou en @email_valide_ignimission (Pas de
			 * règle nécessaire) Si l’expéditeur communique une adresse inexistante dans
			 * ignimission, l’envoi doit se faire exclusivement sur une adresse
			 * en @email_valide_ignimission. Si ce n’est pas le cas, un message d'erreur
			 * s’affiche.
			 **/
			// TODO uncomment contrôle mail sender-info
			// boolean validSender =
			// redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""),
			// StringUploadUtils.getEmailDomain(metadata.getSenderEmail()));
			boolean validSender = true;
			boolean validRecipients = true;
			/*
			 * if(!CollectionUtils.isEmpty(metadata.getRecipientEmails())){ Iterator<String>
			 * domainIter = metadata.getRecipientEmails().iterator(); while
			 * (domainIter.hasNext() && validRecipients){ validRecipients =
			 * redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""),
			 * StringUploadUtils.getEmailDomain(domainIter.next())); } }
			 */

			LOGGER.debug("==============================> Can Upload ==> sender {} / recipients {}  ", validSender,
					validRecipients);
			if (validSender || validRecipients) {
				// TODO UNCOMMENT TO ACTIVATE CODE
				// boolean isRequiredToGeneratedCode = generateCode(redisManager,
				// metadata.getSenderEmail(), token);
				boolean isRequiredToGeneratedCode = false;
				if (!isRequiredToGeneratedCode) {
					return createMetaDataEnclosureInRedis(metadata, redisManager);
				}
			} else {
				return EnclosureRepresentation.builder().canUpload(false).build();
			}

			return null;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public EnclosureRepresentation senderInfoWithCodeValidation(FranceTransfertDataRepresentation metadata, String code)
			throws Exception {
		LOGGER.info("==============================> create metadata in redis with code validation");
		confirmationServices.validateCodeConfirmation(redisManager, metadata.getSenderEmail(), code);
		return createMetaDataEnclosureInRedis(metadata, redisManager);
	}

	private EnclosureRepresentation createMetaDataEnclosureInRedis(FranceTransfertDataRepresentation metadata,
			RedisManager redisManager) throws Exception {
		if (uploadLimitSize < FileUtils.getEnclosureTotalSize(metadata)) {
			LOGGER.error("================ enclosure size > upload limit size: {}", uploadLimitSize);
			throw new UploadExcption(ErrorEnum.LIMT_SIZE_ERROR.getValue(), null);
		}
		try {
			LOGGER.info("================ limit enclosure size is < upload limit size: {}", uploadLimitSize);
			// generate password if provided one not valid
			if (base64CryptoService.validatePassword(metadata.getPassword())) {
				String passwordHashed = base64CryptoService.aesEncrypt(metadata.getPassword());
				metadata.setPassword(passwordHashed);
				LOGGER.info("================== calculate pasword hashed ******");
			} else {
				String generatedPassword = base64CryptoService.generatePassword();
				String passwordHashed = base64CryptoService.aesEncrypt(generatedPassword);
				metadata.setPassword(passwordHashed);
			}
			LOGGER.info("================== create enclosure metadata in redis ===================");
			HashMap<String, String> hashEnclosureInfo = RedisForUploadUtils.createHashEnclosure(redisManager, metadata,
					expiredays);
			LOGGER.info("================== get expiration date and enclosure id back ===================");
			String enclosureId = hashEnclosureInfo.get(RedisForUploadUtils.EnclosureHashGUIDKey);
			String expireDate = hashEnclosureInfo.get(RedisForUploadUtils.EnclosureHashExpirationDateKey);
			LOGGER.info("================== update list date-enclosure in redis ===================");
			RedisUtils.updateListOfDatesEnclosure(redisManager, enclosureId);
			LOGGER.info("===================== create sender metadata in redis ====================");
			String senderId = RedisForUploadUtils.createHashSender(redisManager, metadata, enclosureId);
			LOGGER.info("===================== create all recipients metadata in redis ====================");
			RedisForUploadUtils.createAllRecipient(redisManager, metadata, enclosureId);
			LOGGER.info("===================== create root-files metadata in redis ====================");
			RedisForUploadUtils.createRootFiles(redisManager, metadata, enclosureId);
			LOGGER.info("===================== create root-dirs metadata in redis ====================");
			RedisForUploadUtils.createRootDirs(redisManager, metadata, enclosureId);
			LOGGER.info("===================== create contents-files-ids metadata in redis ====================");
			RedisForUploadUtils.createContentFilesIds(storageManager, redisManager, metadata, enclosureId,
					bucketPrefix);
			LOGGER.info("enclosure id : {} and the sender id : {} ", enclosureId, senderId);

			return EnclosureRepresentation.builder().enclosureId(enclosureId).senderId(senderId).expireDate(expireDate)
					.build();
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid, e);
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
				LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			}
			if (!tokenExistInRedis) {
				LOGGER.error("==============================> invalid token: token does not exist in redis ");
				throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), null);
			}
		} else {
			LOGGER.error("==============================> invalid token ");
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), null);
		}
		LOGGER.info("==============================> valid token for sender mail {}", senderMail);
	}

	private boolean generateCode(RedisManager redisManager, String senderMail, String token) throws Exception {
		try {
			boolean result = false;
			// verify token in redis
			if (!StringUtils.isEmpty(token)) {
				LOGGER.info("==============================> verify token in redis");
				Set<String> setTokenInRedis = redisManager
						.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
				LOGGER.info("==============================> extract all Token sender from redis");
				boolean tokenExistInRedis = setTokenInRedis.stream()
						.anyMatch(tokenRedis -> LocalDate.now().minusDays(1).isBefore(
								UploadUtils.extractStartDateSenderToken(tokenRedis).plusDays(daysToExpiretokenSender))
								&& tokenRedis.equals(token));
				if (!tokenExistInRedis) {
					confirmationServices.generateCodeConfirmation(senderMail);
					result = true;
					LOGGER.info("==============================> generate confirmation code for sender mail {}",
							senderMail);
				}
			} else {
				LOGGER.info("==============================> token does not exist");
				confirmationServices.generateCodeConfirmation(senderMail);
				result = true;
				LOGGER.info("==============================> generate confirmation code for sender mail {}",
						senderMail);
			}
			return result;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}
}
