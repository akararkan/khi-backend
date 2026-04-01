package ak.dev.khi_backend.khi_app.model.about;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * AboutBlockContent — Bilingual embeddable for the AboutBlock entity.
 * <p>
 * Holds all language-specific text fields for a single language version
 * (CKB = Sorani or KMR = Kurmanji) of a content block.
 * <p>
 * Follows the same pattern as {@link ak.dev.khi_backend.khi_app.model.publishment.video.VideoContent}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AboutBlockContent {

    /**
     * Main text body — used for TEXT blocks as full content, others as caption/description.
     */
    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    /**
     * Display title for the block (e.g. image caption header, quote attribution).
     */
    @Column(name = "title", length = 300)
    private String title;

    /**
     * Accessibility alt text for IMAGE/VIDEO/GALLERY blocks.
     */
    @Column(name = "alt_text", length = 500)
    private String altText;
}