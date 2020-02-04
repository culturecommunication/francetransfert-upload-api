package fr.gouv.culture.francetransfert.application.services;

import com.amazonaws.services.s3.model.PartETag;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.configuration.ExtensionProperties;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisForUploadUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.*;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
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

    @Value("${bucket.prefix}")
    private String bucketPrefix;

    @Value("${upload.limit}")
    private long uploadLimitSize;


    @Autowired
    private ExtensionProperties extensionProp;

    @Autowired
    private PasswordHasherServices passwordHasherServices;

    @Autowired
    private ConfirmationServices confirmationServices;

    @Autowired
    private CookiesServices cookiesServices;


    public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, long flowTotalSize, String flowIdentifier, String flowFilename, MultipartFile multipartFile, String enclosureId) throws Exception {
        if (ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), multipartFile, flowFilename)) { // Test authorized file to upload.
            LOGGER.error("================ extension file no authorised");
            throw new ExtensionNotFoundException("================ extension file no authorised");
        }
        LOGGER.info("================ extension file authorised");

        RedisManager redisManager = RedisManager.getInstance();
        //verify token validity
//        String senderMail = RedisUtils.getSenderEnclosure(redisManager, enclosureId);
//        if (fr.gouv.culture.francetransfert.domain.utils.StringUtils.isGouvEmail(senderMail)) {
//            validateToken(redisManager, senderMail, token);
//        }

        String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
        if (chunkExists(redisManager, flowChunkNumber, hashFid)) {
            return true; // multipart is uploaded
        }
        Boolean isUploaded = false;
        StorageManager storageManager = new StorageManager();
        Map<String, String> redisFileInfo = RedisUtils.getFileInfo(redisManager, hashFid);
        String keyUploadOsu = redisFileInfo.get(FileKeysEnum.MUL_ID.getKey());
        String fileNameWithPath = redisFileInfo.get(FileKeysEnum.REL_OBJ_KEY.getKey());
        String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
        LOGGER.info("================ osu bucket name: {}", bucketName);
        PartETag partETag = storageManager.uploadMultiPartFileToOsuBucket(bucketName, flowChunkNumber, fileNameWithPath, multipartFile.getInputStream(), multipartFile.getSize(), keyUploadOsu);
        String partETagToString = RedisForUploadUtils.addToPartEtags(redisManager, partETag, hashFid);
        LOGGER.debug("================ partETag added {} for: {}", partETagToString, hashFid);
        List<String> stringPartETags = RedisUtils.getPartEtagsString(redisManager, hashFid);
        LOGGER.debug("================ partETags  size {} ", stringPartETags.size());
        isUploaded = true;
        if (flowTotalChunks == stringPartETags.size()) {
            List<PartETag> partETags = RedisForUploadUtils.getPartEtags(redisManager, hashFid);
            String succesUpload = storageManager.completeMultipartUpload(bucketName, fileNameWithPath, keyUploadOsu, partETags);
            if (succesUpload != null) {
                LOGGER.info("================ finish upload File ==> {} ", fileNameWithPath);
                redisManager.publishFT(RedisKeysEnum.FT_SUCCESSFUL_UPLOAD.getKey(enclosureId), succesUpload);
                if (RedisUtils.getFilesIds(redisManager, enclosureId).size() == RedisUtils.getListOfSuccessfulUploadFiles(redisManager, enclosureId).size()) {
                    redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosureId);
                    LOGGER.info("================ finish upload enclosure ==> {} ",redisManager.lrange(RedisQueueEnum.ZIP_QUEUE.getValue(), 0, -1));
                }
            }
        }
        return isUploaded;
    }

    public boolean chunkExists(RedisManager redisManager, int flowChunkNumber, String hashFid) throws Exception {
        return RedisUtils.getNumberOfPartEtags(redisManager, hashFid).contains(flowChunkNumber);
    }

    public EnclosureRepresentation senderInfoWithTockenValidation(FranceTransfertDataRepresentation metadata, String token) throws Exception {
        LOGGER.info("==============================> create metadata in redis with token validation");
        RedisManager redisManager = RedisManager.getInstance();
        //verify token validity and generate code if token is not valid
        if (fr.gouv.culture.francetransfert.domain.utils.StringUtils.isGouvEmail(metadata.getSenderEmail())) {
            if (StringUtils.isEmpty(token)) {
                boolean isRequiredToGeneratedCode = generateCode(redisManager, metadata.getSenderEmail(), token);
                if (isRequiredToGeneratedCode) {
                    // return enclosureRepresentation => null : if the confirmation code is generated and it is sent by email
                    return null;
                }
            }
        }
        return createMetaDataEnclosureInRedis(metadata, redisManager);
    }

    public EnclosureRepresentation senderInfoWithCodeValidation(FranceTransfertDataRepresentation metadata, String code) throws Exception {
        LOGGER.info("==============================> create metadata in redis with code validation");
        RedisManager redisManager = RedisManager.getInstance();
        confirmationServices.validateCodeConfirmation(redisManager, metadata.getSenderEmail(), code);
        return createMetaDataEnclosureInRedis(metadata, redisManager);
    }

    private EnclosureRepresentation createMetaDataEnclosureInRedis(FranceTransfertDataRepresentation metadata, RedisManager redisManager) throws Exception {

        if (uploadLimitSize < FileUtils.getEnclosureTotalSize(metadata)) {
            LOGGER.error("================ enclosure size > upload limit size: {}", uploadLimitSize);
            throw new UploadExcption("enclosure size > " + uploadLimitSize);
        }
        LOGGER.info("================ limit enclosure size is < upload limit size: {}", uploadLimitSize);

        if (!StringUtils.isEmpty(metadata.getPassword())) {    // set pasword hashed if password not empty and not-null
            String passwordHashed = passwordHasherServices.calculatePasswordHashed(metadata.getPassword());
            metadata.setPassword(passwordHashed);
            LOGGER.info("================== calculate pasword hashed ******");
        }
        LOGGER.info("================== create enclosure metadata in redis ===================");
        String enclosureId = RedisForUploadUtils.createHashEnclosure(redisManager, metadata, expiredays);
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
        RedisForUploadUtils.createContentFilesIds(redisManager, metadata, enclosureId, bucketPrefix);
        RedisUtils.createListOfSuccessfulUploadFiles(redisManager, enclosureId);
        LOGGER.info("enclosure id : {} and the sender id : {} ", enclosureId, senderId);

        return EnclosureRepresentation.builder()
                .enclosureId(enclosureId)
                .senderId(senderId)
                .build();
    }

    private void validateToken(RedisManager redisManager, String senderMail, String token) throws Exception{
        // verify token in redis
        if (token != null && !token.equalsIgnoreCase("unknown")) {
            Set<String> setTokenInRedis = redisManager.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
            boolean tokenExistInRedis = setTokenInRedis.stream().anyMatch(tokenRedis -> tokenRedis.equals(token));
            if (!tokenExistInRedis) {
                LOGGER.error("==============================> invalid token: token does not exist in redis ");
                throw new UploadExcption("invalid token: token does not exist in redis ");
            }
        } else {
            LOGGER.error("==============================> invalid token ");
            throw new UploadExcption("invalid token");
        }
        LOGGER.info("==============================> valid token for sender mail {}", senderMail);
    }

    private boolean generateCode(RedisManager redisManager, String senderMail, String token) throws Exception{
        boolean result = false;
        // verify token in redis
        if (!StringUtils.isEmpty(token)) {
            LOGGER.info("==============================> verify token in redis");
            Set<String> setTokenInRedis = redisManager.smembersString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
            LOGGER.info("==============================> extract all Token sender from redis");
            boolean tokenExistInRedis = setTokenInRedis.stream().anyMatch(tokenRedis -> tokenRedis.equals(token));
            if (!tokenExistInRedis) {
                confirmationServices.generateCodeConfirmation(senderMail);
                result = true;
                LOGGER.info("==============================> generate confirmation code for sender mail {}", senderMail);
            }
        } else {
            LOGGER.info("==============================> token does not exist");
            confirmationServices.generateCodeConfirmation(senderMail);
            result = true;
            LOGGER.info("==============================> generate confirmation code for sender mail {}", senderMail);
        }
        return result;
    }
}
