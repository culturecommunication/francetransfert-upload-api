package fr.gouv.culture.francetransfert.validator;


import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.xml.validation.Validator;
import java.util.ArrayList;
import java.util.List;

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
//        Metadata metadata = new Metadata();
//        metadata.setReceiversEmailAddress(receiversEmail);
//        metadata.setSenderEmailAddress(senderEmail);
//        Set<ConstraintViolation<Metadata>> violations = validator.validate(metadata);
        // then
//        assertEquals(1, violations.size());
    }
}
