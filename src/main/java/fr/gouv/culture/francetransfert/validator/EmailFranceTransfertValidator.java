package fr.gouv.culture.francetransfert.validator;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class EmailFranceTransfertValidator implements ConstraintValidator<EmailsFranceTransfert, FranceTransfertDataRepresentation> {

    @Value("${regex.gouv.mail}")
    private String regexGouvMail;

    public EmailFranceTransfertValidator() {
    }

    public void initialize(EmailsFranceTransfert constraint) {
    }

    public boolean isValid(FranceTransfertDataRepresentation metadata, ConstraintValidatorContext constraintValidatorContext) {
        boolean isValid = false;
        if (metadata != null && !CollectionUtils.isEmpty(metadata.getRecipientEmails()) && metadata.getSenderEmail() != null) {
            if (StringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
                if (StringUploadUtils.isGouvEmail(metadata.getSenderEmail(), regexGouvMail)) {
                    // sender Gouv Mail
                    isValid = true;
                } else {// sender Public Mail
                    //   All the Receivers Email must be Gouv Mail else request rejected.
                    isValid = metadata.getRecipientEmails().size() == metadata.getRecipientEmails().stream().filter(receiverEmail ->
                            StringUploadUtils.isValidEmail(receiverEmail) &&
                                    StringUploadUtils.isGouvEmail(receiverEmail, regexGouvMail)).count();
                }
            }
        }
        return isValid;
    }
}
