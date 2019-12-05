package com.opengroup.jsbapi.domain.exceptions;

public class ExtensionNotFoundException extends RuntimeException {

    public ExtensionNotFoundException(String extension) {
        super(extension);
    }

}
