package com.opengroup.jsbapi.domain.exceptions;

public class FlowChunkNotExistException extends RuntimeException {
    public FlowChunkNotExistException(String flowIdentifier) {
        super(flowIdentifier);
    }
}
