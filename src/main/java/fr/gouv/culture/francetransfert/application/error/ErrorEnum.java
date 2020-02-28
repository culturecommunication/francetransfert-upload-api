package fr.gouv.culture.francetransfert.application.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorEnum {
    TECHNICAL_ERROR("TECHNICAL_ERROR"),
    FUNCTIONAL_ERROR("FUNCTIONAL_ERROR"),
    LIMT_SIZE_ERROR("LIMT_SIZE_ERROR"),
    INVALID_TOKEN("INVALID_TOKEN"),
    CONFIRMATION_CODE_ERROR("CONFIRMATION_CODE_ERROR");

    private String value;
}
