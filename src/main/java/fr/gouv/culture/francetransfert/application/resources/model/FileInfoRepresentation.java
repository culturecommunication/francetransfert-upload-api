package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileInfoRepresentation {
    private LocalDate validUntilDate;
    private String senderEmail;
    private List<String> recipientsMails;
    private List<String> deletedRecipients;
    private String message;
    private String timestamp;
    private List<FileRepresentation> rootFiles;
    private List<DirectoryRepresentation> rootDirs;
    private boolean withPassword;
    private int downloadCount;
    
    //added by abir
    private String token;
    private String subject;
    private String enclosureId;
}
