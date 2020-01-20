package fr.gouv.culture.francetransfert.domain.exceptions;

public class UploadExcption extends RuntimeException {
    /**
     * UploadExcption Access Exception
     *
     * @param msg
     */
    public UploadExcption(String msg) {
        super(msg);
    }
}