package fr.gouv.culture.francetransfert.application.resources.upload;


import fr.gouv.culture.francetransfert.application.services.UploadServices;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import fr.gouv.culture.francetransfert.validator.GroupEmails;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

@CrossOrigin
@RestController
@RequestMapping("/api-private/upload-module")
@Api(value = "Upload resources")
@Validated
public class UploadResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResources.class);

    @Autowired
    private UploadServices uploadServices;

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
                            @RequestParam("flowTotalChunks") int flowTotalChunks) {
        uploadServices.chunkExists(flowChunkNumber, flowIdentifier);
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
                              @RequestParam("file") MultipartFile file) throws Exception {
        uploadServices.processUpload(flowChunkNumber, flowTotalChunks, flowChunkSize, flowIdentifier, flowFilename, file);
        response.setStatus(HttpStatus.OK.value());
    }

    @PostMapping("/verify-mail")
    @ApiOperation(httpMethod = "POST", value = "Verify Mail  ")
    public void verifyMail(HttpServletResponse response, @EmailsFranceTransfert @RequestBody GroupEmails groupEmails) throws Exception {
        response.setStatus(HttpStatus.OK.value());
    }

}

