package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
//@Builder
@NoArgsConstructor
public class FileRepresentation extends DataRepresentation {

    @NotBlank
    private String fid;

    @Min(1)
    private int size;
}
