package fr.gouv.culture.francetransfert.application.services;

import com.amazonaws.services.s3.model.PartETag;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.*;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.configuration.ExtensionProperties;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UploadServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadServices.class);

    @Value("${enclosure.expire.days}")
    private int expiredays;

    @Autowired
    private ExtensionProperties extensionProp;

    @Autowired
    private PasswordHasherServices passwordHasherServices;


    public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, long flowTotalSize, String flowIdentifier, String flowFilename, MultipartFile multipartFile, String enclosureId, String token) throws Exception {

        if (!ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), multipartFile, flowFilename)) { // Test authorized file to upload.
            LOGGER.debug("extension file no authorised");
            throw new ExtensionNotFoundException("extension file no authorised");
        }

        RedisManager redisManager = RedisManager.getInstance();
        //verify token validity
        String senderMail = RedisUtils.getSenderEnclosure(redisManager, enclosureId);
        validateToken(redisManager, senderMail, token);

        String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
        if (chunkExists(redisManager, flowChunkNumber, hashFid)) {
            return true; // multipart is uploaded
        }
        Boolean isUploaded = false;
        StorageManager storageManager = new StorageManager();
        Map<String, String> redisFileInfo = RedisUtils.getFileInfo(redisManager, hashFid);
        String keyUploadOsu = redisFileInfo.get(FileKeysEnum.MUL_ID.getKey());
        String fileNameWithPath = redisFileInfo.get(FileKeysEnum.REL_OBJ_KEY.getKey());
        String bucketName = RedisUtils.getBucketName(redisManager, enclosureId);
        PartETag partETag = storageManager.uploadMultiPartFileToOsuBucket(bucketName, flowChunkNumber, fileNameWithPath, multipartFile.getInputStream(), multipartFile.getSize(), keyUploadOsu);
        String partETagToString = RedisForUploadUtils.addToPartEtags(redisManager, partETag, hashFid);
        LOGGER.debug("=========> partETag added {} ", partETagToString);
        List<String> stringPartETags = RedisUtils.getPartEtagsString(redisManager, hashFid);
        LOGGER.debug("=========> partETags  size {} ", stringPartETags.size());
        isUploaded = true;
        if (flowTotalChunks == stringPartETags.size()) {
            List<PartETag> partETags = RedisForUploadUtils.getPartEtags(redisManager, hashFid);
            String succesUpload = storageManager.completeMultipartUpload(bucketName, fileNameWithPath, keyUploadOsu, partETags);
            if (succesUpload != null) {
                LOGGER.info("== finish upload File ==> {} ", fileNameWithPath);
                redisManager.publishFT(RedisKeysEnum.FT_SUCCESSFUL_UPLOAD.getKey(enclosureId), succesUpload);
                if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == RedisUtils.getListOfSuccessfulUploadFiles(redisManager, enclosureId).size()) {
                    redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
                    LOGGER.info("== finish upload enclosure ==> {} ",redisManager.lrange(RedisQueueEnum.ZIP_QUEUE.getValue(), 0, -1));
                }
            }
        }
        return isUploaded;
    }

    public boolean chunkExists(RedisManager redisManager, int flowChunkNumber, String hashFid) throws Exception {
        return RedisUtils.getNumberOfPartEtags(redisManager, hashFid).contains(flowChunkNumber);
    }

    public EnclosureRepresentation senderInfo(FranceTransfertDataRepresentation metadata, String token) throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        //verify token validity
        validateToken(redisManager, metadata.getSenderEmail(), token);
        LOGGER.debug("================== create enclosure metadata in redis ===================");
        if (!StringUtils.isEmpty(metadata.getPassword())) {    // set pasword hashed if password not empty and not-null
            String passwordHashed = passwordHasherServices.calculatePasswordHashed(metadata.getPassword());
            metadata.setPassword(passwordHashed);
            LOGGER.debug("================== calculate pasword hashed ******");
        }
        String enclosureId = RedisForUploadUtils.createHashEnclosure(redisManager, metadata, expiredays);
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), EnclosureKeysEnum.TIMESTAMP.getKey()));
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),EnclosureKeysEnum.MESSAGE.getKey()));
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),EnclosureKeysEnum.PASSWORD.getKey()));
        LOGGER.debug("=========================================================================");
        RedisUtils.updateListOfDatesEnclosure(redisManager, enclosureId);
        /*redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(d-> {
            LOGGER.debug(d);
        });*/
        String senderId = RedisForUploadUtils.createHashSender(redisManager, metadata, enclosureId);
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.getKey(enclosureId), SenderKeysEnum.EMAIL.getKey()));
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.getKey(enclosureId), SenderKeysEnum.ID.getKey()));
        LOGGER.debug(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.getKey(enclosureId), SenderKeysEnum.IS_NEW.getKey()));
        LOGGER.debug("=========================================================================");



        RedisForUploadUtils.createAllRecipient(redisManager, metadata, enclosureId);
        RedisForUploadUtils.createRootFiles(redisManager, metadata, enclosureId);
        redisManager.lrange(RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId),0,-1).forEach(f-> {
            LOGGER.debug("root files to upload:"+f);
        });
        RedisForUploadUtils.createRootDirs(redisManager, metadata, enclosureId);
        redisManager.lrange(RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId),0,-1).forEach(f-> {
            LOGGER.debug("root dirs to upload:"+f);
        });

        RedisForUploadUtils.createContentFilesIds(redisManager, metadata, enclosureId);
        /*redisManager.lrange(RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId),0,-1).forEach(f-> {
            //TODO: delete at the end of dev upload api
            LOGGER.debug("files ids:"+f);
            try {
                LOGGER.debug("=========================================================================");
                LOGGER.debug(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.getKey(f), FileKeysEnum.REL_OBJ_KEY.getKey() ));
                LOGGER.debug(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.getKey(f), FileKeysEnum.SIZE.getKey() ));
                LOGGER.debug(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.getKey(f), FileKeysEnum.MUL_ID.getKey() ));
                LOGGER.debug("=========================================================================");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });*/
        RedisUtils.createListOfSuccessfulUploadFiles(redisManager, enclosureId);
        LOGGER.info("enclosure id : {} and the sender id : {} ", enclosureId, senderId);
        return EnclosureRepresentation.builder()
                .enclosureId(enclosureId)
                .senderId(senderId)
                .build();
    }

    private void validateToken(RedisManager redisManager, String senderMail, String token) throws Exception{
        if (token != null && !token.equalsIgnoreCase("unknown")) {
            // verify token in redis
            Set<String> setTokenInRedis = redisManager.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
            boolean tokenExistInRedis = setTokenInRedis.stream().filter(tokenRedis -> tokenRedis.equals(token)).findFirst().isPresent();
            if (!tokenExistInRedis) {
                LOGGER.error("=============================================> invalid token: token does not exist in redis ");
                throw new UploadExcption("invalid token: token does not exist in redis ");
            }
        } else {
            LOGGER.error("=============================================> invalid token ");
            throw new UploadExcption("invalid token");
        }
        LOGGER.info("=============================================> valid token for sender mail {}", senderMail);
    }
}
