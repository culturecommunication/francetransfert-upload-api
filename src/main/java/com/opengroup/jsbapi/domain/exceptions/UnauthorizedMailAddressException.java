package com.opengroup.jsbapi.domain.exceptions;

public class UnauthorizedMailAddressException extends RuntimeException {
    /**
     * Unauthorized Access Exception
     * @param msg
     */
    public UnauthorizedMailAddressException(String msg){
        super(msg);
    }
}
