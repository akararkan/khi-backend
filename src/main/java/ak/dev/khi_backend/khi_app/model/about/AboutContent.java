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
 * Follows the same pattern as {@link ak.dev.khi_backend.khi_app.model.publishment.video.VideoContent}.
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
}