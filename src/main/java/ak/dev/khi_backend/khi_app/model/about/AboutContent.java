package ak.dev.khi_backend.khi_app.model.about;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * AboutContent — Bilingual embeddable for the About entity.
 *
 * Holds all language-specific text fields for a single language version
 * (CKB = Sorani or KMR = Kurmanji) of an about page.
 *
 * The {@code body} field stores Tiptap-rendered HTML — the editor produces
 * a full HTML string (with inline images / video / audio embed URLs already
 * pointing at S3) and the backend stores it verbatim.
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AboutContent {

    @Column(length = 300)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    /**
     * Tiptap HTML body — full editor output for this language.
     * Replaces the old AboutBlock collection.
     */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;
}
