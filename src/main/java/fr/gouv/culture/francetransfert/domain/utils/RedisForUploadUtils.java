package fr.gouv.culture.francetransfert.domain.utils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.redisson.client.RedisTryAgainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.amazonaws.services.s3.model.PartETag;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.domain.redis.entity.FileDomain;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.FileKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RootDirKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RootFileKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.SenderKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;

@Service
public class RedisForUploadUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(RedisForUploadUtils.class);
	public final static String EnclosureHashGUIDKey = "guidEnclosure";
	public final static String EnclosureHashExpirationDateKey = "expirationDate";

	private static int maxUpdateDate;

	@Value("${upload.expired.limit}")
	public void setMaxUpdateDate(int maxUpdateDate) {
		RedisForUploadUtils.maxUpdateDate = maxUpdateDate;
	}

	public static HashMap<String, String> createHashEnclosure(RedisManager redisManager,
			FranceTransfertDataRepresentation metadata, int expiredays) throws Exception {
		// ================ set enclosure info in redis ================
		HashMap<String, String> hashEnclosureInfo = new HashMap<String, String>();
		String guidEnclosure = "";
		try {
			guidEnclosure = RedisUtils.generateGUID();
			LOGGER.debug("enclosure id : {}", guidEnclosure);

			Map<String, String> map = new HashMap<>();
			LocalDateTime startDate = LocalDateTime.now();
			LOGGER.debug("enclosure creation date: {}", startDate);
			map.put(EnclosureKeysEnum.TIMESTAMP.getKey(), startDate.toString());
			LocalDateTime expiredDate = getExpiredTimeStamp(metadata.getExpireDelay());
			LOGGER.debug("enclosure expire date: {}", expiredDate);
			map.put(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey(), expiredDate.toString());
			LOGGER.debug("password: *******");
			map.put(EnclosureKeysEnum.PASSWORD.getKey(), metadata.getPassword());
			if (!StringUtils.isBlank(metadata.getMessage())) {
				LOGGER.debug("message: {}",
						StringUtils.isEmpty(metadata.getMessage()) ? "is empty" : metadata.getMessage());
				map.put(EnclosureKeysEnum.MESSAGE.getKey(), metadata.getMessage());
			} else {
				map.put(EnclosureKeysEnum.MESSAGE.getKey(), "");
			}
			LOGGER.debug("Public Link : {}", metadata.getPublicLink());
			map.put(EnclosureKeysEnum.PUBLIC_LINK.getKey(), metadata.getPublicLink().toString());
			LOGGER.debug("Create Public Link Download Count");
			map.put(EnclosureKeysEnum.PUBLIC_DOWNLOAD_COUNT.getKey(), "0");
			redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(guidEnclosure), map);
			hashEnclosureInfo.put(EnclosureHashGUIDKey, guidEnclosure);
			hashEnclosureInfo.put(EnclosureHashExpirationDateKey, expiredDate.toLocalDate().toString());
			return hashEnclosureInfo;
		} catch (Exception e) {
			LOGGER.error("Error lors de l insertion des metadata : " + e.getMessage(), e);
			throw e;
		}
	}

	public static String createHashSender(RedisManager redisManager, FranceTransfertDataRepresentation metadata,
			String enclosureId) throws Exception {
		// ================ set sender info in redis ================
		try {
			if (null == metadata.getSenderEmail()) {
				throw new Exception();
			}
			boolean isNewSender = StringUtils.isEmpty(metadata.getConfirmedSenderId());
			if (isNewSender) {
				metadata.setConfirmedSenderId(RedisUtils.generateGUID());
			}
			Map<String, String> map = new HashMap<>();
			map.put(SenderKeysEnum.EMAIL.getKey(), metadata.getSenderEmail());
			LOGGER.debug("sender mail: {}", metadata.getSenderEmail());
			map.put(SenderKeysEnum.IS_NEW.getKey(), isNewSender ? "0" : "1");
			LOGGER.debug("is new sender: {}", isNewSender ? "0" : "1");
			map.put(SenderKeysEnum.ID.getKey(), metadata.getConfirmedSenderId());
			LOGGER.debug("sender id: {}", metadata.getConfirmedSenderId());
			redisManager.insertHASH(RedisKeysEnum.FT_SENDER.getKey(enclosureId), map);
			return metadata.getConfirmedSenderId();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return metadata.getConfirmedSenderId();
	}

	public static void createAllRecipient(RedisManager redisManager, FranceTransfertDataRepresentation metadata,
			String enclosureId) throws Exception {
		try {
			if (!metadata.getPublicLink()) {
				if (CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
					throw new Exception("Empty recipient");
				}
				Map<String, String> mapRecipients = new HashMap<>();
				metadata.getRecipientEmails().forEach(recipientMail -> {
					String guidRecipient = RedisUtils.generateGUID();
					mapRecipients.put(recipientMail, guidRecipient);

					// idRecepient => HASH { nbDl: "0" }
					Map<String, String> mapRecipient = new HashMap<>();
					mapRecipient.put(RecipientKeysEnum.NB_DL.getKey(), "0");
					mapRecipient.put(RecipientKeysEnum.PASSWORD_TRY_COUNT.getKey(), "0");
					redisManager.insertHASH(RedisKeysEnum.FT_RECIPIENT.getKey(guidRecipient), mapRecipient);
					LOGGER.debug("mail_recepient : {} => recepient id: {}", recipientMail, guidRecipient);
				});
				// enclosure:enclosureId:recipients:emails-ids => HASH <mail_recepient,
				// idRecepient>
				redisManager.insertHASH(RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId), mapRecipients);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void createRootFiles(RedisManager redisManager, FranceTransfertDataRepresentation metadata,
			String enclosureId) throws Exception {
		try {
			Map<String, String> filesMap = FileUtils.searchRootFiles(metadata);
			// ================ set List root-files info in redis================
			redisManager.insertList( // idRootFilesNames => LIST [file1, file2, ...]
					RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId), new ArrayList(filesMap.keySet()));

			for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
				// ================ set HASH root-file info in redis================
				Map<String, String> map = new HashMap<>();
				map.put(RootFileKeysEnum.SIZE.getKey(), currentFile.getValue());
				LOGGER.debug(" root file: {} => size {}", currentFile.getKey(), currentFile.getValue());
				redisManager.insertHASH(RedisKeysEnum.FT_ROOT_FILE
						.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + currentFile.getKey())), map);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void createDeleteToken(RedisManager redisManager, String enclosureId) {
		try {
			Map<String, String> mapToken = new HashMap<>();
			mapToken.put("token", UUID.randomUUID().toString());
			redisManager.insertHASH(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId), mapToken);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void createRootDirs(RedisManager redisManager, FranceTransfertDataRepresentation metadata,
			String enclosureId) throws Exception {
		try {
			Map<String, String> dirsMap = FileUtils.searchRootDirs(metadata);
			// ================ set List root-dirs info in redis================
			redisManager.insertList( // idRootDirsNames => LIST [dir1, dir2, ...]
					RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId), new ArrayList(dirsMap.keySet()));

			for (Map.Entry<String, String> currentDir : dirsMap.entrySet()) {
				// ================ set HASH root-dir info in redis================
				Map<String, String> map = new HashMap<>();
				map.put(RootDirKeysEnum.TOTAL_SIZE.getKey(), currentDir.getValue());
				LOGGER.debug(" root dir: {} => total size {}", currentDir.getKey(), currentDir.getValue());
				redisManager.insertHASH(RedisKeysEnum.FT_ROOT_DIR
						.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + currentDir.getKey())), map);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void createContentFilesIds(StorageManager storageManager, RedisManager redisManager,
			FranceTransfertDataRepresentation metadata, String enclosureId, String bucketPrefix) throws Exception {
		try {
			List<FileDomain> files = FileUtils.searchFiles(metadata, enclosureId);
			// ================ set List files info in redis================
			redisManager.insertList( // FILES_IDS => list [ SHA1(enclosureId":"fid1), SHA1(enclosureId":"fid2), ...]
					RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId),
					files.stream().map(file -> RedisUtils.generateHashsha1(enclosureId + ":" + file.getFid()))
							.collect(Collectors.toList()));
			String bucketName = RedisUtils.getBucketName(redisManager, null, bucketPrefix);
			for (FileDomain currentfile : files) {
				LOGGER.debug(" current file: {} =>  size {}", currentfile.getFid(), currentfile.getSize());
				String shaFid = RedisUtils.generateHashsha1(enclosureId + ":" + currentfile.getFid());

				// create list part-etags for each file in Redis =
				// file:SHA1(GUID_pli:fid):mul:part-etags =>List
				// [etag1.getPartNumber()+":"+etag1.getETag(),
				// etag2.getPartNumber()+":"+etag2.getETag(), ...]
				LOGGER.debug(" create list part-etags in redis ");
				RedisUtils.createListPartEtags(redisManager, shaFid);
				RedisUtils.createListIdContainer(redisManager, shaFid);
				// ================ set HASH file info in redis================
				Map<String, String> map = new HashMap<>();
				map.put(FileKeysEnum.REL_OBJ_KEY.getKey(), currentfile.getPath());
				LOGGER.debug(" current file path : {} ", currentfile.getPath());
				map.put(FileKeysEnum.SIZE.getKey(), currentfile.getSize());
				LOGGER.debug(" current file size : {} ", currentfile.getSize());
				redisManager.insertHASH( // file:SHA1(GUID_pli:fid) => HASH { rel-obj-key: "Façade.jpg", size: "2",
											// mul-id: "..." }
						RedisKeysEnum.FT_FILE.getKey(shaFid), map);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static List<PartETag> getPartEtags(RedisManager redisManager, String hashFid) throws Exception {
		List<PartETag> partETags = new ArrayList<>();
		try {
			Pattern pattern = Pattern.compile(":");
			RedisUtils.getPartEtagsString(redisManager, hashFid).stream().forEach(k -> {
				String[] items = pattern.split(k, 2);
				if (2 == items.length) {
					PartETag partETag = new PartETag(Integer.parseInt(items[0]), items[1]);
					partETags.add(partETag);
				} else {
					throw new RedisTryAgainException("");
				}

			});
			return partETags;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return partETags;
	}

	public static String addToPartEtags(RedisManager redisManager, PartETag partETag, String hashFid) throws Exception {
		String partEtagRedisForm = "";
		try {
			String key = RedisKeysEnum.FT_PART_ETAGS.getKey(hashFid);
			partEtagRedisForm = partETag.getPartNumber() + ":" + partETag.getETag();
			redisManager.rpush(key, partEtagRedisForm);
			return partEtagRedisForm;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return partEtagRedisForm;
	}

	public static String AddToFileMultipartUploadIdContainer(RedisManager redisManager, String uploadId, String hashFid)
			throws Exception {
		try {
			String key = RedisKeysEnum.FT_ID_CONTAINER.getKey(hashFid);
			redisManager.lpush(key, uploadId);
			return uploadId;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return uploadId;
	}

	public static String getUploadIdBlocking(RedisManager redisManager, String hashFid) throws Exception {
		String keySource = RedisKeysEnum.FT_ID_CONTAINER.getKey(hashFid);
		String uploadOsuId = redisManager.brpoplpush(keySource, keySource, 30);

		if (uploadOsuId == null || uploadOsuId.isBlank() || uploadOsuId.isEmpty()) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
		return uploadOsuId;
	}

	private static LocalDateTime getExpiredTimeStamp(int expireDelay) throws Exception {
		LocalDateTime date = LocalDateTime.now();
		LocalDateTime dateInsert = date.plusDays(expireDelay);
		LocalDateTime maxDate = date.plusDays(maxUpdateDate);
		if (dateInsert.isAfter(maxDate)) {
			throw new Exception("Date invalide, veuillez sélectionner une date inférieure à " + maxUpdateDate
					+ " jours depuis la création du pli");
		}
		return dateInsert;
	}
}
