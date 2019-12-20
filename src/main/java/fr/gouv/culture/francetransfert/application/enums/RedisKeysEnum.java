package fr.gouv.culture.francetransfert.application.enums;

public enum RedisKeysEnum {

    GENERIC("", ""),
    FT_ENCLOSURE("enclosure:", ""),
    FT_SENDER("enclosure:", ":sender"),
    FT_RECIPIENTS("enclosure:", ":recipients:emails-ids"),
    FT_RECIPIENT("recipient:", ""),
    FT_ROOT_FILES("enclosure:", ":contents:root-files:names"),
    FT_ROOT_DIRS("enclosure:", ":contents:root-dirs:names"),
    FT_ROOT_FILE("root-file:", ""),
    FT_ROOT_DIR("root-dir:", ""),
    FT_FILES_IDS("enclosure:", ":contents:files:ids"),
    FT_FILE("file:", ""),
    FT_PART_ETAGS("file:", ":mul:part-etags");

    private String firstKeyPart;
    private String lastKeyPart;

    RedisKeysEnum(String firstKeyPart, String lastKeyPart) {
        this.firstKeyPart = firstKeyPart;
        this.lastKeyPart = lastKeyPart;
    }

    public String generateKey(String key) {
        return firstKeyPart + key + lastKeyPart;
    }
}
