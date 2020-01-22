package fr.gouv.culture.francetransfert.application.resources.upload;


import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.application.services.UploadServices;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

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
    private ConfirmationServices confirmationServices;

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
                              @RequestHeader(value = "sender-token", defaultValue = "unknown") String token,
                              @RequestParam("flowChunkNumber") int flowChunkNumber,
                              @RequestParam("flowTotalChunks") int flowTotalChunks,
                              @RequestParam("flowChunkSize") long flowChunkSize,
                              @RequestParam("flowTotalSize") long flowTotalSize,
                              @RequestParam("flowIdentifier") String flowIdentifier,
                              @RequestParam("flowFilename") String flowFilename,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam("enclosureId") String enclosureId) throws Exception {
        uploadServices.processUpload(flowChunkNumber, flowTotalChunks, flowChunkSize, flowTotalSize, flowIdentifier, flowFilename, file, enclosureId, token);
        response.setStatus(HttpStatus.OK.value());
    }

    @PostMapping("/sender-info")
    @ApiOperation(httpMethod = "POST", value = "sender Info  ")
    public EnclosureRepresentation senderInfo(HttpServletResponse response,
                                              @RequestHeader(value = "sender-token", defaultValue = "unknown") String token,
                                              @Valid @EmailsFranceTransfert @RequestBody FranceTransfertDataRepresentation metadata) throws Exception {

        EnclosureRepresentation enclosureRepresentation = uploadServices.senderInfo(metadata, token);
        response.setStatus(HttpStatus.OK.value());
        return enclosureRepresentation;
    }

    @GetMapping("/validate-code")
    @ApiOperation(httpMethod = "GET", value = "Validate code  ")
    public void validateCode(HttpServletResponse response,
                             @RequestParam("senderMail") String senderMail,
                             @RequestParam("code") String code) throws UploadExcption {
        try {
            String token = confirmationServices.validateCodeConfirmation(senderMail, code);
            Cookie cookie = new Cookie("sender-token", token);
            cookie.isHttpOnly();
            response.addCookie(cookie);
            response.setStatus(HttpStatus.OK.value());
        } catch (Exception e) {
            LOGGER.error("validate confirmation code error ");
            throw new UploadExcption("validate confirmation code error ");
        }
    }

}

