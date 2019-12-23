package fr.gouv.culture.francetransfert.application.services;

import com.amazonaws.services.s3.model.PartETag;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.application.enums.*;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import fr.gouv.culture.francetransfert.domain.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class UploadServices {
        private static final Logger LOGGER = LoggerFactory.getLogger(UploadServices.class);

    @Value("#{'${extension.name}'.split(',')}")
    private List<String> extension;


        public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, long flowTotalSize, String flowIdentifier, String flowFilename, MultipartFile multipartFile, String enclosureId) throws Exception {
            if (!ExtensionFileUtils.isAuthorisedToUpload(extension, multipartFile, flowFilename)) { // Test authorized file to upload.
                LOGGER.debug("extension file no authorised");
                throw new ExtensionNotFoundException("extension file no authorised");
            }

            StorageManager storageManager = new StorageManager();

            RedisManager redisManager = RedisManager.getInstance();
            String keyUploadOsu = RedisUtils.getUploadId(redisManager, enclosureId, flowIdentifier);
            String fileNameWithPath = RedisUtils.getFileNameWithPath(redisManager, enclosureId, flowIdentifier);
            String bucketName = RedisUtils.getBucketName();
            PartETag partETag = storageManager.uploadMultiPartFileToOsuBucket(bucketName, flowChunkNumber, fileNameWithPath, multipartFile.getInputStream(), multipartFile.getSize(), keyUploadOsu);
            String partETagToString = RedisUtils.addToPartEtags(redisManager, partETag, enclosureId, flowIdentifier);

            LOGGER.debug("=========> {} partETag added", partETagToString);
            List<PartETag> partETags = RedisUtils.getPartEtags(redisManager, enclosureId, flowIdentifier);
            LOGGER.debug("=========> {} partETags  size", partETags.size());
            if (flowTotalChunks == partETags.size()) {
                LOGGER.debug("== finish upload ==> {} ",redisManager.lrange("email-notification-queue", 0, -1));
                String succesUpload = storageManager.completeMultipartUpload(bucketName, fileNameWithPath, keyUploadOsu, partETags);
//                if (succesUpload != null) {
//                    String mailSender = RedisUtils.getSenderMail(redisManager, enclosureId);
//                    redisManager.insertList("email-notification-queue", Arrays.asList(mailSender));
//                }
            }


            //Check if chunk exist
//            chunkExists(flowChunkNumber, flowIdentifier);

            Boolean isUploaded = false;
            return isUploaded;
        }

    public void chunkExists(int flowChunkNumber, String flowIdentifier) {
//        FileInfo fi = this.fileInfos.get(flowIdentifier);
//        if (fi != null && fi.containsChunk(flowChunkNumber)) {
//            throw new FlowChunkNotExistException("Chunk exist");
//        }
    }


//    public void generateKeyOSU(String flowFilename, String flowIdentifier) throws JsonProcessingException {
//        EnclosureUtils.setOsuUploadIdByFlowIdentifier(flowFilename, flowIdentifier);
//        EnclosureUtils.setListPartEtagByFlowIdentifier(flowIdentifier, new ArrayList<>());
//    }

    public static EnclosureRepresentation senderInfo(FranceTransfertDataRepresentation metadata) throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        String enclosureId = RedisUtils.createHashEnclosure(redisManager, metadata);
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.generateKey(enclosureId), EnclosureKeysEnum.TIMESTAMP.getKey()));
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.generateKey(enclosureId),EnclosureKeysEnum.MESSAGE.getKey()));
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.generateKey(enclosureId),EnclosureKeysEnum.PASSWORD.getKey()));
        LOGGER.info("=========================================================================");
        String senderId = RedisUtils.createHashSender(redisManager, metadata, enclosureId);
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.generateKey(enclosureId), SenderKeysEnum.EMAIL.getKey()));
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.generateKey(enclosureId), SenderKeysEnum.ID.getKey()));
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_SENDER.generateKey(enclosureId), SenderKeysEnum.IS_NEW.getKey()));
        LOGGER.info("=========================================================================");



        RedisUtils.createAllRecipient(redisManager, metadata, enclosureId);
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_RECIPIENTS.generateKey(enclosureId), "louay@gouv.fr"));
        LOGGER.info(redisManager.getHgetString(redisManager.getHgetString(RedisKeysEnum.FT_RECIPIENTS.generateKey(enclosureId), "louay@gouv.fr"), RecipientKeysEnum.NB_DL.getKey()));
        LOGGER.info("=========================================================================");

        RedisUtils.createRootFiles(redisManager, metadata, enclosureId);
        redisManager.lrange(RedisKeysEnum.FT_ROOT_FILES.generateKey(enclosureId),0,-1).forEach(f-> {
            LOGGER.info("files:"+f);
        });
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_ROOT_FILE.generateKey(RedisUtils.generateHashsha1(enclosureId+":"+"Docker Desktop Installer.exe")), RootFileKeysEnum.SIZE.getKey() ));
        LOGGER.info("=========================================================================");

        RedisUtils.createRootDirs(redisManager, metadata, enclosureId);
        redisManager.lrange(RedisKeysEnum.FT_ROOT_DIRS.generateKey(enclosureId),0,-1).forEach(f-> {
            LOGGER.info("dirs:"+f);
        });
        LOGGER.info(redisManager.getHgetString(RedisKeysEnum.FT_ROOT_DIR.generateKey(RedisUtils.generateHashsha1(enclosureId+":"+"Projet façade château")), RootDirKeysEnum.TOTAL_SIZE.getKey() ));
        LOGGER.info("=========================================================================");

        RedisUtils.createContentFilesIds(redisManager, metadata, enclosureId);
        redisManager.lrange(RedisKeysEnum.FT_FILES_IDS.generateKey(enclosureId),0,-1).forEach(f-> {
            LOGGER.info("files ids:"+f);
            try {
                LOGGER.info(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.generateKey(f), FileKeysEnum.REL_OBJ_KEY.getKey() ));
                LOGGER.info(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.generateKey(f), FileKeysEnum.SIZE.getKey() ));
                LOGGER.info(f+ "===========>"+redisManager.getHgetString(RedisKeysEnum.FT_FILE.generateKey(f), FileKeysEnum.MUL_ID.getKey() ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        LOGGER.info("=========================================================================");
//        redisManager.getHgetString()

        return EnclosureRepresentation.builder()
                .enclosureId(enclosureId)
                .senderId(senderId)
                .build();
    }
}
