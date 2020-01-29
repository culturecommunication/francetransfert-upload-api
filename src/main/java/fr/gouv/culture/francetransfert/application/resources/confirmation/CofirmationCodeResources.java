package fr.gouv.culture.francetransfert.application.resources.confirmation;


import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@CrossOrigin
@RestController
@RequestMapping("/api-private/confirmation-module")
@Api(value = "Confirmation code resources")
@Validated
public class CofirmationCodeResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(CofirmationCodeResources.class);

    @Autowired
    private ConfirmationServices confirmationServices;

    @GetMapping("/generate-code")
    @ApiOperation(httpMethod = "GET", value = "Generate code  ")
    public void generateCode(HttpServletResponse response,
                            @RequestParam("senderMail") String senderMail) throws UploadExcption {
        try {
            confirmationServices.generateCodeConfirmation(senderMail);
            response.setStatus(HttpStatus.OK.value());
        } catch (Exception e) {
            LOGGER.error("generate confirmation code error ");
            throw new UploadExcption("generate confirmation code error ");
        }
    }

    @GetMapping("/validate-code")
    @ApiOperation(httpMethod = "GET", value = "Validate code  ")
    public void validateCode(HttpServletResponse response,
                                       @RequestParam("senderMail") String senderMail,
                                       @RequestParam("code") String code) throws UploadExcption {
        try {
            String token = confirmationServices.validateCodeConfirmation(senderMail, code);
            Cookie cookie = new Cookie("sender-token", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(31 * 24 * 60 * 60);
            response.addCookie(cookie);
            response.setStatus(HttpStatus.OK.value());
//            HttpHeaders headers = new HttpHeaders();
//            headers.setExpires(69874445);
//            headers.add("Set-Cookie","platform=mobile; Max-Age=604800; Path=/; Secure; HttpOnly; Expires=Fri, 28-Mar-2020 14:35:06 GMT");
//            return new ResponseEntity(token, headers, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("validate confirmation code error ");
            throw new UploadExcption("validate confirmation code error ");
        }
    }
}

