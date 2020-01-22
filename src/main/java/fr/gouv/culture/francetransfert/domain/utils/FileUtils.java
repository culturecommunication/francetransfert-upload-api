package fr.gouv.culture.francetransfert.domain.utils;

import fr.gouv.culture.francetransfert.application.resources.model.DataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.domain.redis.entity.FileDomain;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileUtils {
	
	
	private FileUtils() {
		// private Constructor
	}
	
	
    //return Map<name-file, size>
    public static Map<String, String> searchRootFiles(FranceTransfertDataRepresentation metadata) {
        Map<String, String> files = new HashMap<>();
        if (!CollectionUtils.isEmpty(metadata.getRootFiles())) {
            files =  metadata.getRootFiles().stream().collect(
                    Collectors.toMap(file ->
                                    file.getName(),
                            file -> String.valueOf(file.getSize())
                    )
            );
        }
        return files;
    }

    //return Map<name-dir, total-size>
    public static Map<String, String> searchRootDirs(FranceTransfertDataRepresentation metadata) {
        Map<String, String> dirs = new HashMap<>();
        if (!CollectionUtils.isEmpty(metadata.getRootDirs())) {
            dirs =  metadata.getRootDirs().stream().collect(
                    Collectors.toMap(dir -> dir.getName(), dir -> String.valueOf(dir.getTotalSize()))
            );
        }
        return dirs;
    }
    //return Map<path/name-file, fileDomain {path, size, fid}>
    public static List<FileDomain> searchFiles(FranceTransfertDataRepresentation metadata, String enclosureId) {
        List<FileDomain> files = metadata.getRootFiles().stream().map(file ->
                FileDomain.builder()
                        .path(enclosureId +"/" + file.getName())
                        .size(String.valueOf(file.getSize()))
                        .fid(file.getFid())
                        .build()
        ).collect(Collectors.toList());

        for (DirectoryRepresentation rootDir : metadata.getRootDirs()) {
            searchFilesInDirectory((DataRepresentation) rootDir, enclosureId, files);
        }
        return files;
    }

    private static  void searchFilesInDirectory(DataRepresentation data, String path, List<FileDomain> files) {
        if (data instanceof DirectoryRepresentation){
            path = path + "/" + data.getName();
            for (FileRepresentation file : ((DirectoryRepresentation) data).getFiles()) {
                files.add(FileDomain.builder()
                        .path(path + "/" + file.getName())
                        .size(String.valueOf(file.getSize()))
                        .fid(file.getFid())
                        .build()
                );
            }
            for (DataRepresentation temp : ((DirectoryRepresentation) data).getDirs()) {
                searchFilesInDirectory(temp, path, files);
            }
        }
    }
}
