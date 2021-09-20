package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FranceTransfertDataRepresentation {
    private String confirmedSenderId;
    private String senderEmail;
    private List<String> recipientEmails;
    private String password;
    private String message;
    private Boolean publicLink;
    private int passwordTryCount;
    private int expireDelay;
    private String senderId;
    private String senderToken;
    @Valid
    private List<FileRepresentation> rootFiles;
    @Valid
    private List<DirectoryRepresentation> rootDirs;
}
