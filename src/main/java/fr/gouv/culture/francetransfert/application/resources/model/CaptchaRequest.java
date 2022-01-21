package fr.gouv.culture.francetransfert.application.resources.model;

import fr.gouv.culture.francetransfert.domain.soap.CaptchaTypeEnum;
import lombok.Data;

@Data
public class CaptchaRequest {

	private String challengeId;
	private String userResponse;
	private CaptchaTypeEnum captchaType;

}
