package fr.gouv.culture.francetransfert.validator;

import fr.gouv.culture.francetransfert.domain.utils.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EmailFranceTransfertValidator implements ConstraintValidator<EmailsFranceTransfert, GroupEmails> {

    public EmailFranceTransfertValidator() {
    }

    public void initialize(EmailsFranceTransfert constraint) {
    }

    public boolean isValid(GroupEmails groupEmails, ConstraintValidatorContext constraintValidatorContext) {
        boolean isValid = false;
        if (groupEmails != null && !CollectionUtils.isEmpty(groupEmails.getReceiversEmailAddress()) && groupEmails.getSenderEmailAddress() != null) {
            if (StringUtils.isValidEmail(groupEmails.getSenderEmailAddress())) {
                if (StringUtils.isGouvEmail(groupEmails.getSenderEmailAddress())) {
                    // sender Gouv Mail
                    isValid = true;
                } else {// sender Public Mail
                    //   All the Receivers Email must be Gouv Mail else request rejected.
                    isValid = groupEmails.getReceiversEmailAddress().size() == groupEmails.getReceiversEmailAddress().stream().filter(receiverEmail ->
                            StringUtils.isValidEmail(receiverEmail) &&
                                    StringUtils.isGouvEmail(receiverEmail)).count();
                }
            }
        }
        return isValid;
    }
}