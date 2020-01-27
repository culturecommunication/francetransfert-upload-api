package fr.gouv.culture.francetransfert.application.services;

import com.google.gson.Gson;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.application.resources.model.rate.RateRepresentation;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateServices.class);

    public void createSatisfactionFT(RateRepresentation rateRepresentation) throws UploadExcption {
        try {
            if (null == rateRepresentation) {
                LOGGER.error("error satisfaction services");
                throw new UploadExcption("error satisfaction services");
            }
            String jsonInString = new Gson().toJson(rateRepresentation);

            RedisManager redisManager = RedisManager.getInstance();
            redisManager.publishFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue(), jsonInString);
        } catch (Exception e) {
            throw new UploadExcption("error satisfaction services");
        }
    }
}
