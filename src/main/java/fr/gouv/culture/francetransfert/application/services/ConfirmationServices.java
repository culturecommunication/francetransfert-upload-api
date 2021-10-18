package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.domain.exceptions.ConfirmationCodeException;
import fr.gouv.culture.francetransfert.domain.exceptions.DomainNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.MaxTryException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;

@Service
public class ConfirmationServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmationServices.class);

	private final static int lengthCode = 8;

	@Value("${expire.confirmation.code}")
	private int secondsToExpireConfirmationCode;

	@Value("${expire.token.sender}")
	private int daysToExpiretokenSender;

	@Value("${application.cookies.domain}")
	private String applicationCookiesDomain;

	@Value("${enclosure.max.password.try}")
	private int maxTryCodeCount;

	@Autowired
	private RedisManager redisManager;

	public void generateCodeConfirmation(String senderMail) throws Exception {
//generate confirmation code
		// verify code exist in REDIS for this mail : if not exist -> generate
		// confirmation code and insert in queue redis (send mail to the sender
		// enclosure with code)
//        RedisManager redisManager = RedisManager.getInstance();
		if (null == redisManager
				.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(RedisUtils.generateHashsha1(senderMail)))) {
			String confirmationCode = RandomStringUtils.randomNumeric(lengthCode);
			// insert confirmation code in REDIS
			redisManager.setNxString(RedisKeysEnum.FT_CODE_SENDER.getKey(RedisUtils.generateHashsha1(senderMail)),
					confirmationCode, secondsToExpireConfirmationCode);
			redisManager.deleteKey(RedisKeysEnum.FT_CODE_SENDER.getKey(senderMail));
			redisManager.setString(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail)), "0");
			LOGGER.info("sender: {} generated confirmation code in redis", senderMail);
			// insert in queue of REDIS: confirmation-code-mail" => SenderMail":"code" (
			// insert in queue to: send mail to sender in worker module)
			redisManager.publishFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue(),
					senderMail + ":" + confirmationCode);
			LOGGER.info("sender: {} insert in queue rdis to send mail with confirmation code", senderMail);
		}

	}

	public String validateCodeConfirmationAndGenerateToken(String senderMail, String code) throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
		// validate confirmation code
		validateCodeConfirmation(redisManager, senderMail, code);
		try {
			/*
			 * genarate and insert in REDIS :(GUID && timpStamp cokies) per sender by
			 * internet browser add token validity sender to Redis. Token form :
			 * "sender:senderMail:token" => SET ["GUID:time-stamp"] exemple :
			 * "sender:test@gouv.fr:token" => SET
			 * [e4cce869-6f3d-4e10-900a-74299602f460:2018-01-21T12:01:34.519, ..]
			 */
			String token = RedisUtils.generateGUID() + ":" + LocalDateTime.now().toString();
//			redisManager.deleteKey(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail));
			redisManager.deleteKey(RedisKeysEnum.FT_CODE_SENDER.getKey(RedisUtils.generateHashsha1(senderMail)));
			redisManager.deleteKey(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail)));
			redisManager.saddString(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail), token);
			int dayInSecond = daysToExpiretokenSender * 86400;
			redisManager.expire(RedisKeysEnum.FT_TOKEN_SENDER.getKey(senderMail), dayInSecond);
			LOGGER.info("sender: {} generated token: {} ", senderMail, token);
			return token;
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			LOGGER.error("Type: {} -- id: {} -- message : {}", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid,
					e.getMessage(), e);
			LOGGER.error("Erreur Code validation : " + e.getMessage(), e);
			throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
		}
	}

	public void validateCodeConfirmation(RedisManager redisManager, String senderMail, String code) throws Exception {
		LOGGER.info("verify validy confirmation code");
		String redisCode = redisManager
				.getString(RedisKeysEnum.FT_CODE_SENDER.getKey(RedisUtils.generateHashsha1(senderMail)));
		int tryCount = 0;
		try {
			tryCount = Integer.parseInt(
					redisManager.getString(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail))));
		} catch (Exception e) {
			throw new DomainNotFoundException(senderMail.getClass(), null);
		}
		if (tryCount < maxTryCodeCount) {
			if (null == redisCode || !(redisCode != null && code.equals(redisCode))) {
				tryCount++;
				LOGGER.error("error code sender: this code: {} is not validated for this sender mail {}", code,
						senderMail);
				redisManager.setString(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail)),
						Integer.toString(tryCount));
				throw new ConfirmationCodeException(ErrorEnum.CONFIRMATION_CODE_ERROR.getValue(), null, tryCount);
			}
			redisManager.setString(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail)), "0");
			LOGGER.info("sender: {} valid code: {} ", senderMail, code);
		} else {
			redisManager.deleteKey(RedisKeysEnum.FT_CODE_SENDER.getKey(RedisUtils.generateHashsha1(senderMail)));
			redisManager.deleteKey(RedisKeysEnum.FT_CODE_TRY.getKey(RedisUtils.generateHashsha1(senderMail)));
			throw new MaxTryException("Unauthorized");
		}
	}

}
