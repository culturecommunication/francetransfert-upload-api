package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DirectoryRepresentation extends DataRepresentation {

    private int totalSize;

    @Valid
    private List<FileRepresentation> files;

    @Valid
    private List<DirectoryRepresentation> dirs;
}
