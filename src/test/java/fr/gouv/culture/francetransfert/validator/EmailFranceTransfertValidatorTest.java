package fr.gouv.culture.francetransfert.validator;



import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.validation.ConstraintViolation;
import javax.xml.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EmailFranceTransfertValidatorTest {

//    @Autowired
    private Validator validator;

    @Ignore
    @Test
    public void isValidTest() throws Exception {
        // given
        String senderEmail = "daniel@gouv.fr";
        List<String> receiversEmail = new ArrayList<>();
        receiversEmail.add("");
        receiversEmail.add("");

        // when
        GroupEmails groupEmails = new GroupEmails();
//        groupEmails.setReceiversEmailAddress(receiversEmail);
//        groupEmails.setSenderEmailAddress(senderEmail);
//        Set<ConstraintViolation<GroupEmails>> violations = validator.validate(groupEmails);
        // then
//        assertEquals(1, violations.size());
    }
}
