package ak.dev.khi_backend.khi_app.enums.publishment;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Book/Writing topic categories
 */
public enum WritingTopic {
    HISTORICAL,      // Historical books
    FOLKLORE,        // Folklore and traditional stories
    RELIGIOUS,       // Religious texts
    POLITICAL,       // Political writings
    POETRY,          // Poetry collections
    LITERATURE,      // Literary works (novels, short stories)
    CULTURAL,        // Cultural studies
    EDUCATIONAL,     // Educational/Academic books
    SCIENTIFIC,      // Scientific publications
    BIOGRAPHICAL,    // Biographies and memoirs
    CHILDREN,        // Children's books
    PHILOSOPHY,      // Philosophical works
    SOCIOLOGY,       // Social studies
    LINGUISTICS,     // Language and linguistics
    ARTS,            // Arts and crafts
    ECONOMICS,       // Economic studies
    MEDICINE,        // Medical books
    LAW,             // Legal texts
    OTHER;           // Other categories

    @JsonCreator
    public static WritingTopic from(String value) {
        if (value == null) return null;
        return WritingTopic.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}