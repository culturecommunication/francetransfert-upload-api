package fr.gouv.culture.francetransfert.domain.soap;

public class EnvelopeUtils {

	private static final String checkCaptcha = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"http://fr.mcc.transverse.captcha.service/catpcha/v1.0\"> <soapenv:Header/> <soapenv:Body> <v1:checkCaptcha> <challengeId>{{challengeId}}</challengeId> <userResponse>{{userResponse}}</userResponse> <captchaType>{{captchaType}}</captchaType> </v1:checkCaptcha> </soapenv:Body> </soapenv:Envelope>";

	private static final String uniqueChallengeID = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"http://fr.mcc.transverse.captcha.service/catpcha/v1.0\"> <soapenv:Header/> <soapenv:Body> <v1:generateUniqueChallengeID/> </soapenv:Body> </soapenv:Envelope>";

	public static String generateCheckCaptcha(String challengeId, String userResponse, CaptchaTypeEnum captchaType) {
		return checkCaptcha.replace("{{challengeId}}", challengeId).replace("{{userResponse}}", userResponse)
				.replace("{{captchaType}}", captchaType.getValue());
	}

	public static String generateUniqueChallengeID() {
		return uniqueChallengeID;
	}

}
