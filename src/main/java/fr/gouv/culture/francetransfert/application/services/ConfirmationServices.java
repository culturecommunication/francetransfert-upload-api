package fr.gouv.culture.francetransfert.application.services;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisQueueEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConfirmationServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmationServices.class);

    private final static int lengthCode = 6;
    private final static int secondsToExpireConfirmationCode = 5*60;
    private final static int secondsToExpiretokenSender = 2678400; //31 days to exipre


    public void generateCodeConfirmation(String senderMail) throws Exception {
//generate confirmation code
        //verify code exist in REDIS for this mail : if not exist -> generate confirmation code and insert in queue redis (send mail to the sender enclosure with code)
        RedisManager redisManager = RedisManager.getInstance();
        if (null == redisManager.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail))) {
            String confirmationCode = RandomStringUtils.randomNumeric(lengthCode);
            //insert confirmation code in REDIS
            try {
                redisManager.setNxString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail), confirmationCode, secondsToExpireConfirmationCode);
                LOGGER.info("sender: {} =========> generated confirmation code in redis", senderMail);
            } catch (Exception e) {
                throw new UploadExcption("error generation confirmation code");
            }
            // insert in queue of REDIS: confirmation-code-mail" => SenderMail":"code" ( insert in queue to: send mail to sender in worker module)
            redisManager.publishFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue(), senderMail+":"+confirmationCode);
            LOGGER.info("sender: {} =========> insert in queue rdis to send mail with confirmation code", senderMail);
        }

    }

    public String validateCodeConfirmation(String senderMail, String code) throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
// validate confirmation code
        String redisCode = redisManager.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail));
        if (null == redisCode || !(redisCode != null && code.equals(redisCode))) {
            LOGGER.error("error code sender: =========> this code: {} is not validated for this sender mail {}", code, senderMail);
            throw new UploadExcption("error confirmation code");
        }
        LOGGER.info("sender: {} =========> valid code: {} ", senderMail, code);
// genarate and insert in REDIS :(GUID && timpStamp cokies) per sender by internet browser
        // add token validity sender to Redis. Token form : "sender:senderMail:token" => SET ["GUID:time-stamp"] exemple : "sender:test@gouv.fr:token" => SET [e4cce869-6f3d-4e10-900a-74299602f460:2018-01-21T12:01:34.519, ..]
        String token = RedisUtils.generateGUID() + ":" + LocalDateTime.now().toString();//TODO: timeStamp 64 bits
        redisManager.saddString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail), token);
        LOGGER.info("sender: {} =========> generated token: {} ", senderMail, token);
        // TODO: token 31 days to exipre
        return token;
    }


}
