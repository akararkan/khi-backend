package ak.dev.khi_backend.khi_app.enums.publishment;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * File format for writing/book files
 */
public enum WritingFileFormat {
    PDF,      // PDF documents
    DOCX,     // Microsoft Word (modern)
    DOC,      // Microsoft Word (legacy)
    TXT,      // Plain text
    EPUB,     // E-book format
    ODT,      // OpenDocument Text
    RTF,      // Rich Text Format
    HTML,     // HTML document
    OTHER;    // Other formats

    @JsonCreator
    public static WritingFileFormat from(String value) {
        if (value == null) return null;
        return WritingFileFormat.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}