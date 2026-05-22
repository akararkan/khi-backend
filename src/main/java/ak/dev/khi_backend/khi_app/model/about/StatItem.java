package ak.dev.khi_backend.khi_app.model.about;

import lombok.*;

import java.io.Serializable;

/**
 * StatItem — a single entry inside the About page's stats array.
 *
 * Persisted as a JSONB element of {@link About#getStats()}, so it is a
 * plain POJO with no JPA annotations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatItem implements Serializable {

    /** Sorani (CKB) label, e.g. "کتێب" */
    private String labelCkb;

    /** Kurmanji (KMR) label, e.g. "Pirtûk" */
    private String labelKmr;

    /** Display value, e.g. "5,000+" */
    private String value;
}
