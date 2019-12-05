package fr.gouv.culture.francetransfert.validator;

import lombok.Data;

import javax.validation.constraints.Email;
import java.util.List;

@Data
public class GroupEmails {
    private List<String> receiversEmailAddress;

//    @Email
    private String senderEmailAddress;
}
