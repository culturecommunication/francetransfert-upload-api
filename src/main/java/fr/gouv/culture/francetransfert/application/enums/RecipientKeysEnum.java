package fr.gouv.culture.francetransfert.application.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum RecipientKeysEnum {
    NB_DL("nb-dl");

    private String key;

    RecipientKeysEnum(String key) {
        this.key = key;
    }

    public static List<String> keys() {
        return Stream.of(RecipientKeysEnum.values())
                .map(e -> e.key)
                .collect(Collectors.toList());
    }

    public String getKey() {
        return key;
    }
}
