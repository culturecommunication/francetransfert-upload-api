package fr.gouv.culture.francetransfert.validator;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Iterator;
import java.util.Objects;

public class EmailFranceTransfertValidator implements ConstraintValidator<EmailsFranceTransfert, FranceTransfertDataRepresentation> {

    @Autowired
    private RedisManager redisManager;

    public EmailFranceTransfertValidator() {
    }

    public void initialize(EmailsFranceTransfert constraint) {
    }

    /**
     *
     * @param metadata
     * @param constraintValidatorContext
     * @return
     */
    public boolean isValid(FranceTransfertDataRepresentation metadata, ConstraintValidatorContext constraintValidatorContext) {
        boolean isValid = false;
        if(metadata.getPublicLink()){
            if (Objects.nonNull(metadata) && Objects.nonNull(metadata.getSenderEmail())) {
                if (StringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
                    isValid = redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""), StringUploadUtils.getEmailDomain(metadata.getSenderEmail()));
                }
            }
        }else {
            if (Objects.nonNull(metadata) && !CollectionUtils.isEmpty(metadata.getRecipientEmails()) && Objects.nonNull(metadata.getSenderEmail())) {
                if (StringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
                    isValid = redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""), StringUploadUtils.getEmailDomain(metadata.getSenderEmail()));
                    if (!isValid && !CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
                        // sender Public Mail
                        //   All the Receivers Email must be Gouv Mail else request rejected.
                        boolean canUpload = true;
                        Iterator<String> domainIter = metadata.getRecipientEmails().iterator();
                        while (domainIter.hasNext() && canUpload) {
                            canUpload = redisManager.sexists(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""), StringUploadUtils.getEmailDomain(domainIter.next()));
                        }
                        isValid = canUpload;
                    }
                }
            }
        }
        return isValid;
    }
}
