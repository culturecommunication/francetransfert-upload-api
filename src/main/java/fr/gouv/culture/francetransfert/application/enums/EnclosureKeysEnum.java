package fr.gouv.culture.francetransfert.application.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum EnclosureKeysEnum {
    TIMESTAMP("timestamp"),
    PASSWORD("password"),
    MESSAGE("message");

    private String key;

    EnclosureKeysEnum(String key) {
        this.key = key;
    }

    public static List<String> keys() {
        return Stream.of(EnclosureKeysEnum.values())
                .map(e -> e.key)
                .collect(Collectors.toList());
    }

    public String getKey() {
        return key;
    }
}