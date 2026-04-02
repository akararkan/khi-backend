package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * ServiceMediaFileContent — Bilingual embeddable for a {@link ServiceMediaFile}.
 *
 * Holds all language-specific text that accompanies a single media asset.
 * Follows the same embeddable pattern used by {@code AboutContent} and
 * {@code AboutBlockContent}, embedded twice per row via
 * {@code @AttributeOverrides}: once as {@code ckbContent} (Sorani) and
 * once as {@code kmrContent} (Kurmanji).
 *
 * ─── Fields ───────────────────────────────────────────────────────────────────
 *  caption     → Short one-liner shown directly beneath the media in the UI.
 *                e.g. "لقطة من يوم التدريب"
 *  title       → Formal title for gallery headers / lightbox overlays.
 *                e.g. "Training Day — Session 3"
 *  description → Longer explanatory text shown in an expanded view or tooltip.
 *                May contain rich / multi-sentence text.
 *
 * All three fields are optional so a file can be inserted with no text at all.
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceMediaFileContent {

    /** Short caption rendered beneath the media asset in the gallery. */
    @Column(length = 500)
    private String caption;

    /** Formal media title — used in lightbox headers and screen-reader alt text. */
    @Column(length = 300)
    private String title;

    /** Extended description shown in an expanded / detail view. */
    @Column(columnDefinition = "TEXT")
    private String description;
}