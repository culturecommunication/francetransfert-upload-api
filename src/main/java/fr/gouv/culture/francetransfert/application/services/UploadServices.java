package fr.gouv.culture.francetransfert.application.services;

import fr.gouv.culture.francetransfert.application.resources.upload.FileInfo;
import fr.gouv.culture.francetransfert.configuration.ConfigProperties;
import fr.gouv.culture.francetransfert.configuration.ExtensionProperties;
import fr.gouv.culture.francetransfert.domain.exceptions.ExtensionNotFoundException;
import fr.gouv.culture.francetransfert.domain.exceptions.FlowChunkNotExistException;
import fr.gouv.culture.francetransfert.domain.utils.ExtensionFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UploadServices {
        private static final Logger LOGGER = LoggerFactory.getLogger(UploadServices.class);

        private final Map<String, FileInfo> fileInfos = new ConcurrentHashMap<>();

        private final String uploadDirectory = "uploadDirectory";

        @Autowired
        ConfigProperties configProp;

        @Autowired
        ExtensionProperties extensionProp;

        public Boolean processUpload(int flowChunkNumber, int flowTotalChunks, long flowChunkSize, String flowIdentifier, String flowFilename, MultipartFile file) throws IOException, ExtensionNotFoundException {
            //TODO: Upload in local to delete after connexion with OSU
            Path dataDir = Paths.get(uploadDirectory);
            try {
                Files.createDirectories(dataDir);
            }
            catch (IOException e) {
                LOGGER.error("ERROR -> file reading error : ", e);
            }
            //Check if chunk exist
            chunkExists(flowChunkNumber, flowIdentifier);

            Boolean isUploaded = false;
                if (!ExtensionFileUtils.isAuthorisedToUpload(extensionProp.getExtensionValue(), file, flowFilename)) { // Test authorized file to upload.
                    LOGGER.debug("extension file no authorised");
//                    return isUploaded;
                    throw new ExtensionNotFoundException("extension file no authorised");
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
                    isUploaded = true;
                }
            return isUploaded;
        }

    public void chunkExists(int flowChunkNumber, String flowIdentifier) {
        FileInfo fi = this.fileInfos.get(flowIdentifier);
        if (fi != null && fi.containsChunk(flowChunkNumber)) {
            throw new FlowChunkNotExistException("Chunk exist");
        }
    }

}
