package fr.gouv.culture.francetransfert.domain.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class UploadExcption extends RuntimeException {
    private String type;
    private String id;
}
