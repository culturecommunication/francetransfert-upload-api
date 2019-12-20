package fr.gouv.culture.francetransfert.application.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum FileKeysEnum {
    REL_OBJ_KEY("rel-obj-key"),
    SIZE("size"),
    MUL_ID("mul-id");

    private String key;

    FileKeysEnum(String key) {
        this.key = key;
    }

    public static List<String> keys() {
        return Stream.of(FileKeysEnum.values())
                .map(e -> e.key)
                .collect(Collectors.toList());
    }

    public String getKey() {
        return key;
    }
}
