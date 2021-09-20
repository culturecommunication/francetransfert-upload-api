package fr.gouv.culture.francetransfert.domain.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ConfirmationCodeException extends RuntimeException {
    private String type;
    private String id;
    private int count;
}
