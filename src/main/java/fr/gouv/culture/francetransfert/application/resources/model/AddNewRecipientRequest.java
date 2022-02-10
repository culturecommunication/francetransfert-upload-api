package fr.gouv.culture.francetransfert.application.resources.model;

import fr.gouv.culture.francetransfert.validator.DateUpdateConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
//@Builder
@NoArgsConstructor

public class AddNewRecipientRequest {
    private String token;

    private String enclosureId;

    private String newRecipient;
}
