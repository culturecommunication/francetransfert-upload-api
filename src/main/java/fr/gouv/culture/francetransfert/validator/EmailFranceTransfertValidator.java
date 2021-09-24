package fr.gouv.culture.francetransfert.validator;

import java.util.Iterator;
import java.util.Objects;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;

public class EmailFranceTransfertValidator
		implements ConstraintValidator<EmailsFranceTransfert, FranceTransfertDataRepresentation> {

	@Autowired
	private RedisManager redisManager;

	@Autowired
	StringUploadUtils stringUploadUtils;

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
	public boolean isValid(FranceTransfertDataRepresentation metadata,
			ConstraintValidatorContext constraintValidatorContext) {
		boolean isValid = false;
		if (metadata.getPublicLink()) {
			if (Objects.nonNull(metadata) && Objects.nonNull(metadata.getSenderEmail())) {
				if (stringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
					isValid = stringUploadUtils.isValidEmailIgni(metadata.getSenderEmail());
				}
			}
		} else {
			if (Objects.nonNull(metadata) && !CollectionUtils.isEmpty(metadata.getRecipientEmails())
					&& Objects.nonNull(metadata.getSenderEmail())) {
				if (stringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
					isValid = stringUploadUtils.isValidEmailIgni(metadata.getSenderEmail());
					if (!isValid && !CollectionUtils.isEmpty(metadata.getRecipientEmails())) {
						// sender Public Mail
						// All the Receivers Email must be Gouv Mail else request rejected.
						boolean canUpload = true;
						Iterator<String> domainIter = metadata.getRecipientEmails().iterator();
						while (domainIter.hasNext() && canUpload) {
							canUpload = stringUploadUtils.isValidEmailIgni(domainIter.next());
						}
						isValid = canUpload;
					}
				}
			}
		}
		return isValid;
	}
}
