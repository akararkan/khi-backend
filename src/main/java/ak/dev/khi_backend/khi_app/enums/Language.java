package ak.dev.khi_backend.khi_app.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Language {
    CKB,  // Kurdish Central (Sorani)
    KMR;  // Kurdish Kurmanji

    @JsonCreator
    public static Language from(String value) {
        if (value == null) return null;
        return Language.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
