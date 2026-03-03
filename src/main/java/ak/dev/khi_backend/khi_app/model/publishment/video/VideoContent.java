package ak.dev.khi_backend.khi_app.model.publishment.video;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * VideoContent — Bilingual embeddable for the Video entity.
 *
 * Holds all language-specific text fields for a single language version
 * (CKB or KMR) of a video publishment.
 *
 * NOTE: `topic` is NOT here. Topic is a shared @ManyToOne relation on the
 * parent Video entity pointing to PublishmentTopic.
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VideoContent {

    @Column(length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 250)
    private String location;

    @Column(length = 250)
    private String director;

    @Column(length = 250)
    private String producer;
}
