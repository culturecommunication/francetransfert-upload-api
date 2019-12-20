package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
//@Builder
@NoArgsConstructor
public class DataRepresentation {
    @NotBlank
    private String name;
}
