package fr.gouv.culture.francetransfert.validator;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.utils.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class EmailFranceTransfertValidator implements ConstraintValidator<EmailsFranceTransfert, FranceTransfertDataRepresentation> {

    public EmailFranceTransfertValidator() {
    }

    public void initialize(EmailsFranceTransfert constraint) {
    }

    public boolean isValid(FranceTransfertDataRepresentation metadata, ConstraintValidatorContext constraintValidatorContext) {
        boolean isValid = false;
        if (metadata != null && !CollectionUtils.isEmpty(metadata.getRecipientEmails()) && metadata.getSenderEmail() != null) {
            if (StringUtils.isValidEmail(metadata.getSenderEmail())) {
                if (StringUtils.isGouvEmail(metadata.getSenderEmail())) {
                    // sender Gouv Mail
                    isValid = true;
                } else {// sender Public Mail
                    //   All the Receivers Email must be Gouv Mail else request rejected.
                    isValid = metadata.getRecipientEmails().size() == metadata.getRecipientEmails().stream().filter(receiverEmail ->
                            StringUtils.isValidEmail(receiverEmail) &&
                                    StringUtils.isGouvEmail(receiverEmail)).count();
                }
            }
        }
        return isValid;
    }
}