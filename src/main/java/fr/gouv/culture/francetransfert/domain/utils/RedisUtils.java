package fr.gouv.culture.francetransfert.domain.utils;

import com.amazonaws.services.s3.model.PartETag;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.application.enums.*;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.redis.entity.FileDomain;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.client.RedisTryAgainException;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RedisUtils {

    public static  Map<String, String> generateMapRedis(List<String> keys, List<String> values) {
        return IntStream.range(0, Math.min(keys.size(), values.size()))
                .boxed()
                .collect(Collectors.toMap(keys::get, values::get));
    }

    public static String generateGUID() {
        return UUID.randomUUID().toString();
    }

    public static String generateHashsha1(String value) {
        return DigestUtils.sha1Hex(value);
    }

    public static String getBucketName() {
        return "fr-gouv-culture-francetransfert-devic1-plis-"+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }




    public static String createHashEnclosure(RedisManager redisManager, FranceTransfertDataRepresentation metadata) {
        //  ================ set enclosure info in redis ================
        String guidEnclosure = generateGUID();
        Map<String, String> map = RedisUtils.generateMapRedis(
                EnclosureKeysEnum.keys(),
                Arrays.asList(
                        LocalDateTime.now().toString(),
                        metadata.getPassword(),
                        metadata.getMessage()
                )
        );
        redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.generateKey(guidEnclosure), map);
        return guidEnclosure;
    }

    public static String createHashSender(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        //  ================ set sender info in redis ================
        if (null == metadata.getSenderEmail()) {
            throw new Exception();
        }
        boolean isNewSender = (null == metadata.getConfirmedSenderId() || "".equals(metadata.getConfirmedSenderId()));
        if (isNewSender) {
            metadata.setConfirmedSenderId(generateGUID());
        }
        Map<String, String> map = RedisUtils.generateMapRedis(
                SenderKeysEnum.keys(),
                Arrays.asList(
                        metadata.getSenderEmail(),
                        isNewSender ? "0" : "1",
                        metadata.getConfirmedSenderId()
                )
        );
        redisManager.insertHASH(RedisKeysEnum.FT_SENDER.generateKey(enclosureId), map);
        return metadata.getConfirmedSenderId();
    }

    public static void createAllRecipient(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        if (CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
            throw new Exception();
        }
        List<String> listRecipientId = new ArrayList<>();
        metadata.getRecipientEmails().forEach(r -> {
            String recipientId = RedisKeysEnum.FT_RECIPIENT.generateKey(generateGUID());
            listRecipientId.add(recipientId);

            // idRecepient => HASH { nbDl: "0" }
            redisManager.insertHASH(
                    recipientId,
                    RedisUtils.generateMapRedis(RecipientKeysEnum.keys(), Arrays.asList("0"))
            );
        });
        // enclosure:enclosureId:recipients:emails-ids  => HASH <mail_recepient, idRecepient>
        redisManager.insertHASH(
                RedisKeysEnum.FT_RECIPIENTS.generateKey(enclosureId),
                RedisUtils.generateMapRedis(metadata.getRecipientEmails(), listRecipientId));
    }

    public static void createRootFiles(RedisManager redisManager, FranceTransfertDataRepresentation metadata, String enclosureId) throws Exception {
        Map<String, String> filesMap = FileUtils.searchRootFiles(metadata);
        //  ================ set List root-files info in redis================
        redisManager.insertList(                                          // idRootFilesNames => LIST [file1, file2, ...]
                RedisKeysEnum.FT_ROOT_FILES.generateKey(enclosureId),
                new ArrayList(filesMap.keySet())
        );

        for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
            //  ================ set HASH root-file info in redis================
            redisManager.insertHASH(
                    RedisKeysEnum.FT_ROOT_FILE.generateKey(generateHashsha1(enclosureId + ":" + currentFile.getKey())),
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
                RedisKeysEnum.FT_ROOT_DIRS.generateKey(enclosureId),
                new ArrayList(dirsMap.keySet())
        );

        for (Map.Entry<String, String> currentDir : dirsMap.entrySet()) {
            //  ================ set HASH root-dir info in redis================
            redisManager.insertHASH(
                    RedisKeysEnum.FT_ROOT_DIR.generateKey(generateHashsha1(enclosureId + ":" + currentDir.getKey())),
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
                RedisKeysEnum.FT_FILES_IDS.generateKey(enclosureId),
                files.stream().map(file -> generateHashsha1(enclosureId + ":" + file.getFid())).collect(Collectors.toList())
        );
        StorageManager storageManager = new StorageManager();
        String bucketName = getBucketName();
        for (FileDomain currentfile : files) {
            String shaFid = generateHashsha1(enclosureId + ":" + currentfile.getFid());
            String uploadID = storageManager.generateUploadIdOsu(bucketName, currentfile.getPath());

            //create list part-etags for each file in Redis =  file:SHA1(GUID_pli:fid):mul:part-etags =>List [etag1.getPartNumber()+":"+etag1.getETag(), etag2.getPartNumber()+":"+etag2.getETag(), ...]
            createListPartEtags(redisManager, shaFid);

            //  ================ set HASH file info in redis================
            redisManager.insertHASH(          //file:SHA1(GUID_pli:fid) => HASH { rel-obj-key: "Façade.jpg", size: "2", mul-id: "..." }
                    RedisKeysEnum.FT_FILE.generateKey(shaFid),
                    RedisUtils.generateMapRedis(
                            FileKeysEnum.keys(),
                            Arrays.asList(currentfile.getPath(), currentfile.getSize(), uploadID)
                    )
            );
        }
    }

    public static void createListPartEtags(RedisManager redisManager, String shaFid) throws Exception {
        redisManager.insertList(
                RedisKeysEnum.FT_PART_ETAGS.generateKey(generateHashsha1(shaFid)),
                new ArrayList<>()
        );
    }

    public static List<PartETag> getPartEtags(RedisManager redisManager, String enclosureId, String fid) throws Exception {
        String key = RedisKeysEnum.FT_PART_ETAGS.generateKey(RedisUtils.generateHashsha1(enclosureId + ":" + fid));
        Pattern pattern = Pattern.compile(":");
        List<PartETag> partETags = new ArrayList<>();
        redisManager.lrange(key, 0, -1).stream().forEach(k-> {
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

    public static String addToPartEtags(RedisManager redisManager, PartETag partETag,  String enclosureId, String fid) throws Exception {
        String key = RedisKeysEnum.FT_PART_ETAGS.generateKey(RedisUtils.generateHashsha1(enclosureId + ":" + fid));
        String partEtagRedisForm = partETag.getPartNumber()+":"+partETag.getETag();
        redisManager.addToList(key, partEtagRedisForm);
        return partEtagRedisForm;
    }

    public static String getUploadId(RedisManager redisManager, String enclosureId, String fid) throws Exception {
        String key = RedisKeysEnum.FT_FILE.generateKey(RedisUtils.generateHashsha1(enclosureId + ":" + fid));
        return redisManager.getHgetString(key, FileKeysEnum.MUL_ID.getKey());
    }

    public static String getFileNameWithPath(RedisManager redisManager, String enclosureId, String fid) throws Exception {
        String key = RedisKeysEnum.FT_FILE.generateKey(RedisUtils.generateHashsha1(enclosureId + ":" + fid));
        return redisManager.getHgetString(key, FileKeysEnum.REL_OBJ_KEY.getKey());
    }

}
