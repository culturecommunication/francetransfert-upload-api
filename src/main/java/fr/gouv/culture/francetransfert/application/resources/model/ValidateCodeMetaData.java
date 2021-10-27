package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidateCodeMetaData {
    @NotBlank(message = "EnclosureId obligatoire")
    private String enclosureId;
    @NotBlank(message = "Code obligatoire")
    private String code;
}
