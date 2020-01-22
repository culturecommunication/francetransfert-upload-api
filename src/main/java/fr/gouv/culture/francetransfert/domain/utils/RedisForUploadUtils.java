package fr.gouv.culture.francetransfert.domain.utils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.redisson.client.RedisTryAgainException;
import org.springframework.util.CollectionUtils;

import com.amazonaws.services.s3.model.PartETag;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisForUploadUtils {

    public static String createHashEnclosure(RedisManager redisManager, FranceTransfertDataRepresentation metadata, int expiredays) {
        //  ================ set enclosure info in redis ================
        String guidEnclosure = RedisUtils.generateGUID();
        Map<String, String> map = RedisUtils.generateMapRedis(
                EnclosureKeysEnum.keys(),
                Arrays.asList(
                        LocalDateTime.now().toString(),
                        LocalDateTime.now().plusDays(expiredays).toString(),
                        metadata.getPassword(),
                        metadata.getMessage()
                )
        );
        redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(guidEnclosure), map);
        return guidEnclosure;
    }

    public static String createHashSender(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        //  ================ set sender info in redis ================
        if (null == metadata.getSenderEmail()) {
            throw new Exception();
        }
        boolean isNewSender = (null == metadata.getConfirmedSenderId() || "".equals(metadata.getConfirmedSenderId()));
        if (isNewSender) {
            metadata.setConfirmedSenderId(RedisUtils.generateGUID());
        }
        Map<String, String> map = RedisUtils.generateMapRedis(
                SenderKeysEnum.keys(),
                Arrays.asList(
                        metadata.getSenderEmail(),
                        isNewSender ? "0" : "1",
                        metadata.getConfirmedSenderId()
                )
        );
        redisManager.insertHASH(RedisKeysEnum.FT_SENDER.getKey(enclosureId), map);
        return metadata.getConfirmedSenderId();
    }

    public static void createAllRecipient(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        if (CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
            throw new Exception();
        }
        List<String> listRecipientId = new ArrayList<>();
        metadata.getRecipientEmails().forEach(r -> {
            String recipientId = RedisKeysEnum.FT_RECIPIENT.getKey(RedisUtils.generateGUID());
            listRecipientId.add(recipientId);

            // idRecepient => HASH { nbDl: "0" }
            redisManager.insertHASH(
                    recipientId,
                    RedisUtils.generateMapRedis(RecipientKeysEnum.keys(), Arrays.asList("0"))
            );
        });
        // enclosure:enclosureId:recipients:emails-ids  => HASH <mail_recepient, idRecepient>
        redisManager.insertHASH(
                RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId),
                RedisUtils.generateMapRedis(metadata.getRecipientEmails(), listRecipientId));
    }

    public static void createRootFiles(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        Map<String, String> filesMap = FileUtils.searchRootFiles(metadata);
        //  ================ set List root-files info in redis================
        redisManager.insertList(                                          // idRootFilesNames => LIST [file1, file2, ...]
                RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId),
                new ArrayList(filesMap.keySet())
        );

        for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
            //  ================ set HASH root-file info in redis================
            redisManager.insertHASH(
                    RedisKeysEnum.FT_ROOT_FILE.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + currentFile.getKey())),
                    RedisUtils.generateMapRedis(
                            RootFileKeysEnum.keys(),
                            Arrays.asList(currentFile.getValue())
                    )
            );
        }
    }

    public static void createRootDirs(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        Map<String, String> dirsMap = FileUtils.searchRootDirs(metadata);
        //  ================ set List root-dirs info in redis================
        redisManager.insertList(                                          // idRootDirsNames => LIST [dir1, dir2, ...]
                RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId),
                new ArrayList(dirsMap.keySet())
        );

        for (Map.Entry<String, String> currentDir : dirsMap.entrySet()) {
            //  ================ set HASH root-dir info in redis================
            redisManager.insertHASH(
                    RedisKeysEnum.FT_ROOT_DIR.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + currentDir.getKey())),
                    RedisUtils.generateMapRedis(
                            RootDirKeysEnum.keys(),
                            Arrays.asList(currentDir.getValue())
                    )
            );
        }
    }

    public static void createContentFilesIds(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        List<FileDomain> files = FileUtils.searchFiles(metadata, enclosureId);
        //  ================ set List files info in redis================
        redisManager.insertList(  // FILES_IDS => list [ SHA1(enclosureId":"fid1), SHA1(enclosureId":"fid2), ...]
                RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId),
                files.stream().map(file -> RedisUtils.generateHashsha1(enclosureId + ":" + file.getFid())).collect(Collectors.toList())
        );
        StorageManager storageManager = new StorageManager();
        String bucketName = RedisUtils.getBucketName(redisManager, null);
        for (FileDomain currentfile : files) {
            String shaFid = RedisUtils.generateHashsha1(enclosureId + ":" + currentfile.getFid());
            String uploadID = storageManager.generateUploadIdOsu(bucketName, currentfile.getPath());

            //create list part-etags for each file in Redis =  file:SHA1(GUID_pli:fid):mul:part-etags =>List [etag1.getPartNumber()+":"+etag1.getETag(), etag2.getPartNumber()+":"+etag2.getETag(), ...]
            RedisUtils.createListPartEtags(redisManager, shaFid);

            //  ================ set HASH file info in redis================
            redisManager.insertHASH(          //file:SHA1(GUID_pli:fid) => HASH { rel-obj-key: "Façade.jpg", size: "2", mul-id: "..." }
                    RedisKeysEnum.FT_FILE.getKey(shaFid),
                    RedisUtils.generateMapRedis(
                            FileKeysEnum.keys(),
                            Arrays.asList(currentfile.getPath(), currentfile.getSize(), uploadID)
                    )
            );
        }
    }

    public static List<PartETag> getPartEtags(RedisManager redisManager, String hashFid) throws Exception {
        Pattern pattern = Pattern.compile(":");
        List<PartETag> partETags = new ArrayList<>();
        RedisUtils.getPartEtagsString(redisManager, hashFid).stream().forEach(k-> {
            String[] items = pattern.split(k, 2);
            if (2 == items.length) {
                PartETag partETag = new PartETag(Integer.parseInt(items[0]), items[1]);
                partETags.add(partETag);
            } else {
                throw new RedisTryAgainException("");
            }

        });
        return partETags;
    }

    public static String addToPartEtags(RedisManager redisManager, PartETag partETag, String hashFid) throws Exception {
        String key = RedisKeysEnum.FT_PART_ETAGS.getKey(hashFid);
        String partEtagRedisForm = partETag.getPartNumber()+":"+partETag.getETag();
        redisManager.rpush(key, partEtagRedisForm);
        return partEtagRedisForm;
    }
}
