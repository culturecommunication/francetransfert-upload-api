package fr.gouv.culture.francetransfert.domain.exceptions;

public class MaxTryException extends RuntimeException {
    /**
     * Unauthorized Access Exception
     * @param msg
     */
    public MaxTryException(String msg){
        super(msg);
    }
}
