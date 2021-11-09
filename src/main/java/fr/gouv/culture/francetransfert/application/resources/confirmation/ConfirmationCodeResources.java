package fr.gouv.culture.francetransfert.application.resources.confirmation;

import java.util.UUID;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@CrossOrigin
@RestController
@RequestMapping("/api-private/confirmation-module")
@Api(value = "Confirmation code resources")
@Validated
public class ConfirmationCodeResources {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmationCodeResources.class);

	@Autowired
	private ConfirmationServices confirmationServices;

	@GetMapping("/generate-code")
	@ApiOperation(httpMethod = "GET", value = "Generate code  ")
	public void generateCode(HttpServletResponse response, @RequestParam("senderMail") String senderMail)
			throws UploadException {
		try {
			confirmationServices.generateCodeConfirmation(senderMail);
			response.setStatus(HttpStatus.OK.value());
		} catch (Exception e) {
			String uuid = UUID.randomUUID().toString();
			throw new UploadException(ErrorEnum.TECHNICAL_ERROR.getValue() + " generating code : " + e.getMessage(),
					uuid, e);
		}
	}
}
