package com.opengroup.jsbapi.application.resources.upload;


import com.opengroup.jsbapi.application.services.UploadServices;
import com.opengroup.jsbapi.domain.utils.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

@CrossOrigin
@RestController
@RequestMapping("/api-private/upload-module")
@Api(value = "Upload resources")
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
    @ApiOperation(httpMethod = "GET", value = "Verify Mail  ")
    public void verifyMail(HttpServletResponse response,
                           @RequestParam("receiverEmailAddress") String receiverEmailAddress,
                           @RequestParam("senderEmailAddress") String senderEmailAddress) throws Exception {

        String domainReceiverEmail = StringUtils.extractDomainNameFromEmailAddress(receiverEmailAddress);

        if (domainReceiverEmail.contains("gouv.fr")) {


            if (receiverEmailAddress.contains("gouv.fr")) {

            }
            if (receiverEmailAddress.contains("gouv.fr")) {
                // TODO: authorised
            } else {
                //TODO:  envoyer mail de confirmation code 4 chiffre
            }
        } else {
            if (receiverEmailAddress.contains("gouv.fr")) {
                //TODO: authorised
            } else {
                //TODO: not authorised
            }
        }
    }

}

