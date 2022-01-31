package fr.gouv.culture.francetransfert.application.resources.captcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.gouv.culture.francetransfert.application.resources.model.CaptchaRequest;
import fr.gouv.culture.francetransfert.application.services.CaptchaService;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin
@RestController
@RequestMapping("/api-private/captcha")
@Tag(name = "Captcha")
public class CaptchaResources {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaResources.class);

	@Autowired
	private CaptchaService captchaService;

	@PostMapping("/validate-captcha")
	@Operation(method = "POST", description = "Validate Captcha")
	public boolean validateCaptcha(@RequestBody CaptchaRequest captchaBody) throws UploadException {
		return captchaService.checkCaptcha(captchaBody.getChallengeId(), captchaBody.getUserResponse(),
				captchaBody.getCaptchaType());
	}

}
