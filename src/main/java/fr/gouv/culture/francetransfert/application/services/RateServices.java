package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.TypeStat;
import fr.gouv.culture.francetransfert.core.model.RateRepresentation;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;

@Service
public class RateServices {
	private static final Logger LOGGER = LoggerFactory.getLogger(RateServices.class);

	@Autowired
	RedisManager redisManager;

	public boolean createSatisfactionFT(RateRepresentation rateRepresentation) throws UploadException {
		try {

			String domain = "";

			if (StringUtils.isNotBlank(rateRepresentation.getMailAdress())) {
				domain = rateRepresentation.getMailAdress().split("@")[1];
			}

			rateRepresentation.setDate(LocalDate.now());
			rateRepresentation.setDomain(domain);
			rateRepresentation.setHashMail(null);
			rateRepresentation.setMailAdress(null);
			rateRepresentation.setType(TypeStat.UPLOAD_SATISFACTION);
			String jsonInString = new Gson().toJson(rateRepresentation);
			redisManager.publishFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue(), jsonInString);
			return true;
		} catch (Exception e) {
			throw new UploadException(
					ErrorEnum.TECHNICAL_ERROR.getValue() + " while create satisfaction : " + e.getMessage(),
					rateRepresentation.getPlis(), e);
		}
	}
}
