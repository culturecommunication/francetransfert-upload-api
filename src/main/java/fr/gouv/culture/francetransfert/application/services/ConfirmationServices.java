package fr.gouv.culture.francetransfert.application.services;

import fr.gouv.culture.francetransfert.domain.enums.CookiesEnum;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class ConfirmationServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmationServices.class);

    private final static int lengthCode = 6;
    private final static int secondsToExpireConfirmationCode = 5*60;
    private final static int secondsToExpiretokenSender = 2678400; //31 days to exipre

    @Autowired
    private CookiesServices cookiesServices;


    public void generateCodeConfirmation(String senderMail) throws Exception {
//generate confirmation code
        //verify code exist in REDIS for this mail : if not exist -> generate confirmation code and insert in queue redis (send mail to the sender enclosure with code)
        RedisManager redisManager = RedisManager.getInstance();
        if (null == redisManager.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail))) {
            String confirmationCode = RandomStringUtils.randomNumeric(lengthCode);
            //insert confirmation code in REDIS
            try {
                redisManager.setNxString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail), confirmationCode, secondsToExpireConfirmationCode);
                LOGGER.info("================ sender: {} =========> generated confirmation code in redis", senderMail);
            } catch (Exception e) {
                LOGGER.error("================ error generation confirmation code");
                throw new UploadExcption("error generation confirmation code");
            }
            // insert in queue of REDIS: confirmation-code-mail" => SenderMail":"code" ( insert in queue to: send mail to sender in worker module)
            redisManager.deleteKey(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue());
            redisManager.insertList(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue(), Arrays.asList(senderMail+":"+confirmationCode));
            LOGGER.info("================ sender: {} =========> insert in queue rdis to send mail with confirmation code", senderMail);
        }

    }

    public Cookie validateCodeConfirmationAndGenerateToken(String senderMail, String code) throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        // validate confirmation code
        validateCodeConfirmation(redisManager, senderMail, code);
         /*genarate and insert in REDIS :(GUID && timpStamp cokies) per sender by internet browser
         add token validity sender to Redis. Token form : "sender:senderMail:token" => SET ["GUID:time-stamp"] exemple : "sender:test@gouv.fr:token" => SET [e4cce869-6f3d-4e10-900a-74299602f460:2018-01-21T12:01:34.519, ..]*/
        String token = RedisUtils.generateGUID() + ":" + LocalDateTime.now().toString();
        redisManager.saddString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail), token);
        LOGGER.info("================ sender: {} =========> generated token: {} ", senderMail, token);
        return cookiesServices.createCookie(CookiesEnum.SENDER_TOKEN.getValue(), token, true, "/", "localhost", 31 * 24 * 60 * 600);
    }

    public void validateCodeConfirmation(RedisManager redisManager, String senderMail, String code) throws Exception {
        LOGGER.info("================ verify validy confirmation code");
        String redisCode = redisManager.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail));
        if (null == redisCode || !(redisCode != null && code.equals(redisCode))) {
            LOGGER.error("================ error code sender: =========> this code: {} is not validated for this sender mail {}", code, senderMail);
            throw new UploadExcption("error confirmation code");
        }
        LOGGER.info("================ sender: {} =========> valid code: {} ", senderMail, code);
    }

}
