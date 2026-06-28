package ak.dev.khi_backend.khi_app.model.publishment.video;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VideoHighlightClip {
    @Column(name = "title_ckb", length = 300) private String titleCkb;
    @Column(name = "title_kmr", length = 300) private String titleKmr;
    @Column(name = "clip_url", columnDefinition = "TEXT") private String url;
    @Column(name = "embed_url", columnDefinition = "TEXT") private String embedUrl;
    @Column(name = "duration_seconds") private Integer durationSeconds;
}
