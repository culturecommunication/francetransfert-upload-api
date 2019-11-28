package com.opengroup.jsbapi.application.resources.upload;


import com.opengroup.jsbapi.configuration.ConfigProperties;
import com.opengroup.jsbapi.configuration.ExtensionProperties;
import com.opengroup.jsbapi.domain.utils.ExtensionFileUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin
@RestController
@RequestMapping("/api-private/upload-module")
@Api(value = "Upload resources")
public class UploadResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResources.class);

    private final Map<String, FileInfo> fileInfos = new ConcurrentHashMap<>();

    private final String uploadDirectory = "uploadDirectory";

    @Autowired
    ConfigProperties configProp;

    @Autowired
    ExtensionProperties extensionProp;

    public UploadResources(
            @Value("#{environment.uploadDirectory}") String uploadDirectory) {
        Path dataDir = Paths.get(uploadDirectory);

        try {
            Files.createDirectories(dataDir);
        }
        catch (IOException e) {
            LOGGER.error("ERROR -> file reading error : ", e);
        }
    }

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

        FileInfo fi = this.fileInfos.get(flowIdentifier);

        if (fi != null && fi.containsChunk(flowChunkNumber)) {
            LOGGER.debug("return GET method : {}", HttpStatus.OK.value());
            response.setStatus(HttpStatus.OK.value());
            return;
        }
        LOGGER.debug("return GET method : {}", HttpStatus.EXPECTATION_FAILED.value());
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
        if (!ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), file, flowFilename)) { // Test authorized file to upload.
            LOGGER.debug("return POST method : KO");
            throw new Exception();
        }

        FileInfo fileInfo = this.fileInfos.get(flowIdentifier);
        if (fileInfo == null) {
            fileInfo = new FileInfo();
            this.fileInfos.put(flowIdentifier, fileInfo);
        }

        Path identifierFile = Paths.get(configProp.getConfigValue(uploadDirectory), flowIdentifier);

        try (RandomAccessFile raf = new RandomAccessFile(identifierFile.toString(), "rw");
             InputStream is = file.getInputStream()) {
            raf.seek((flowChunkNumber - 1) * flowChunkSize);

            long readed = 0;
            long content_length = file.getSize();
            byte[] bytes = new byte[1024 * 100];
            while (readed < content_length) {
                int r = is.read(bytes);
                if (r < 0) {
                    break;
                }
                raf.write(bytes, 0, r);
                readed += r;
            }
        }

        fileInfo.addUploadedChunk(flowChunkNumber);

        if (fileInfo.isUploadFinished(flowTotalChunks)) {
            Path uploadedFile = Paths.get(configProp.getConfigValue(uploadDirectory) , flowFilename);
            Files.move(identifierFile, uploadedFile, StandardCopyOption.ATOMIC_MOVE);

            this.fileInfos.remove(flowIdentifier);
        }
        LOGGER.debug("return POST method : {}", HttpStatus.OK.value());
        response.setStatus(HttpStatus.OK.value());
    }

}

