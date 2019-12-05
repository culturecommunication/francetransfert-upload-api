package fr.gouv.culture.francetransfert.domain.exceptions;

public class FlowChunkNotExistException extends RuntimeException {
    public FlowChunkNotExistException(String flowIdentifier) {
        super(flowIdentifier);
    }
}
