package fr.gouv.culture.francetransfert.domain.enums;

public enum CookiesEnum {
    SENDER_ID("sender-id"),
    SENDER_TOKEN("sender-token"),
    IS_CONSENTED("IS_CONSENTED");

    private String value;

    CookiesEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
