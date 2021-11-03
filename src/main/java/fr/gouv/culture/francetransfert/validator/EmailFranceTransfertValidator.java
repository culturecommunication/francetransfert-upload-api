package fr.gouv.culture.francetransfert.validator;

import java.util.Iterator;
import java.util.Objects;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;

public class EmailFranceTransfertValidator
		implements ConstraintValidator<EmailsFranceTransfert, FranceTransfertDataRepresentation> {

	@Autowired
	StringUploadUtils stringUploadUtils;

	/**
	 *
	 * @param metadata
	 * @param constraintValidatorContext
	 * @return
	 */
	public boolean isValid(FranceTransfertDataRepresentation metadata,
			ConstraintValidatorContext constraintValidatorContext) {

		boolean isValid = false;

		// Check public link
		if (metadata.getPublicLink()) {
			if (Objects.nonNull(metadata) && Objects.nonNull(metadata.getSenderEmail())) {
				if (stringUploadUtils.isValidEmail(metadata.getSenderEmail())) {
					isValid = stringUploadUtils.isValidEmailIgni(metadata.getSenderEmail());
				}
			}
			return isValid;
		}

		// Empty check recipients
		if (Objects.isNull(metadata) || CollectionUtils.isEmpty(metadata.getRecipientEmails())
				|| Objects.isNull(metadata.getSenderEmail())) {
			return isValid;
		}

		// Check sender/recipient validity
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

		return isValid;
	}
}
