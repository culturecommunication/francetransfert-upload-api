package fr.gouv.culture.francetransfert.domain.exceptions;

public class InvalidCaptchaException extends RuntimeException {
    /**
     * Unauthorized Access Exception
     * @param msg
     */
	public InvalidCaptchaException(String msg){
        super(msg);
    }
}
