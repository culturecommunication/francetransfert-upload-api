package fr.gouv.culture.francetransfert.application.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum RootFileKeysEnum {
    SIZE("size");

    private String key;

    RootFileKeysEnum(String key) {
        this.key = key;
    }

    public static List<String> keys() {
        return Stream.of(RootFileKeysEnum.values())
                .map(e -> e.key)
                .collect(Collectors.toList());
    }

    public String getKey() {
        return key;
    }
}
