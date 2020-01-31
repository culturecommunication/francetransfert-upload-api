package fr.gouv.culture.francetransfert.application.resources.upload;


import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.rate.RateRepresentation;
import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.application.services.CookiesServices;
import fr.gouv.culture.francetransfert.application.services.RateServices;
import fr.gouv.culture.francetransfert.application.services.UploadServices;
import fr.gouv.culture.francetransfert.domain.enums.CookiesEnum;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;


@CrossOrigin
@RestController
@RequestMapping("/api-private/upload-module")
@Api(value = "Upload resources")
@Validated
public class UploadResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResources.class);

    @Autowired
    private UploadServices uploadServices;
    
    @Autowired
    private RateServices rateServices;

    @Autowired
    private ConfirmationServices confirmationServices;

    @Autowired
    private CookiesServices cookiesServices;

    @GetMapping("/upload")
    @ApiOperation(httpMethod = "GET", value = "Upload  ")
    public void chunkExists(HttpServletResponse response,
                            @RequestParam("flowChunkNumber") int flowChunkNumber,
                            @RequestParam("flowChunkSize") int flowChunkSize,
                            @RequestParam("flowCurrentChunkSize") int flowCurrentChunkSize,
                            @RequestParam("flowTotalSize") int flowTotalSize,
                            @RequestParam("flowIdentifier") String flowIdentifier,
                            @RequestParam("flowFilename") String flowFilename,
                            @RequestParam("flowRelativePath") String flowRelativePath,
                            @RequestParam("flowTotalChunks") int flowTotalChunks,
                            @RequestParam("enclosureId") String enclosureId) throws Exception {
        String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
        uploadServices.chunkExists(RedisManager.getInstance(), flowChunkNumber, hashFid);
        response.setStatus(HttpStatus.EXPECTATION_FAILED.value());
    }

    @PostMapping("/upload")
    @ApiOperation(httpMethod = "POST", value = "Upload  ")
    public void processUpload(HttpServletResponse response,
                              @RequestParam("flowChunkNumber") int flowChunkNumber,
                              @RequestParam("flowTotalChunks") int flowTotalChunks,
                              @RequestParam("flowChunkSize") long flowChunkSize,
                              @RequestParam("flowTotalSize") long flowTotalSize,
                              @RequestParam("flowIdentifier") String flowIdentifier,
                              @RequestParam("flowFilename") String flowFilename,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam("enclosureId") String enclosureId) throws Exception {
        uploadServices.processUpload(flowChunkNumber, flowTotalChunks, flowChunkSize, flowTotalSize, flowIdentifier, flowFilename, file, enclosureId, "token");
        response.setStatus(HttpStatus.OK.value());
    }

    @PostMapping("/sender-info")
    @ApiOperation(httpMethod = "POST", value = "sender Info  ")
    public EnclosureRepresentation senderInfo(HttpServletRequest request, HttpServletResponse response,
                                              @Valid @EmailsFranceTransfert @RequestBody FranceTransfertDataRepresentation metadata) throws Exception {
        String token = cookiesServices.getToken(request);
        EnclosureRepresentation enclosureRepresentation = uploadServices.senderInfoWithTockenValidation(metadata, token);
        if (enclosureRepresentation != null) {
            response.addCookie(cookiesServices.createCookie(CookiesEnum.SENDER_ID.getValue(), enclosureRepresentation.getSenderId(), false, "/", "localhost", 396 * 24 * 60 * 600));
        }
        response.setStatus(HttpStatus.OK.value());
        return enclosureRepresentation;
    }

    @PostMapping("/validate-code")
    @ApiOperation(httpMethod = "POST", value = "Validate code  ")
    public EnclosureRepresentation validateCode(HttpServletRequest request, HttpServletResponse response,
                             @RequestParam("senderMail") String senderMail,
                             @RequestParam("code") String code,
                            @Valid @EmailsFranceTransfert @RequestBody FranceTransfertDataRepresentation metadata) throws UploadExcption {
        try {
            EnclosureRepresentation enclosureRepresentation = null;
            if (cookiesServices.isConsented(request.getCookies())) {
                LOGGER.info("===========================> with IS-CONSENTED");
                Cookie cookie = confirmationServices.validateCodeConfirmationAndGenerateToken(metadata.getSenderEmail(), code);
                enclosureRepresentation = uploadServices.senderInfoWithTockenValidation(metadata, cookie.getValue());
                response.addCookie(cookie);
            } else {
                LOGGER.info("===========================> without IS-CONSENTED");
                enclosureRepresentation = uploadServices.senderInfoWithCodeValidation(metadata, code);
            }
            response.setStatus(HttpStatus.OK.value());
            return enclosureRepresentation;
        } catch (Exception e) {
            LOGGER.error("validate confirmation code error ");
            throw new UploadExcption("validate confirmation code error ");
        }
    }
    

    @RequestMapping(value = "/satisfaction", method = RequestMethod.POST)
    @ApiOperation(httpMethod = "POST", value = "Rates the app on a scvale of 1 to 4")
    public void createSatisfactionFT(HttpServletResponse response,
                             @Valid @RequestBody RateRepresentation rateRepresentation) throws UploadExcption {
        rateServices.createSatisfactionFT(rateRepresentation);
    }
}

