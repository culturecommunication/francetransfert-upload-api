package fr.gouv.culture.francetransfert.application.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum RootDirKeysEnum {
    TOTAL_SIZE("total-size");

    private String key;

    RootDirKeysEnum(String key) {
        this.key = key;
    }

    public static List<String> keys() {
        return Stream.of(RootDirKeysEnum.values())
                .map(e -> e.key)
                .collect(Collectors.toList());
    }

    public String getKey() {
        return key;
    }
}
