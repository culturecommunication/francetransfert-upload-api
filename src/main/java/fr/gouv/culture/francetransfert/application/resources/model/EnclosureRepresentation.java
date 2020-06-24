package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnclosureRepresentation {
    private String enclosureId;
    private String senderId;
    private String expireDate;
}
