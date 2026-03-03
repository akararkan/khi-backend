package ak.dev.khi_backend.khi_app.model.news;

import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsMediaType type;

    /**
     * Direct URL to a hosted file (e.g., S3 mp4/mp3/pdf).
     * Optional for AUDIO/VIDEO because they can be provided via externalUrl/embedUrl.
     */
    @Column(columnDefinition = "TEXT")
    private String url;

    /**
     * A normal link to a third-party page (e.g., https://youtube.com/watch?v=...).
     * Optional.
     */
    @Column(columnDefinition = "TEXT")
    private String externalUrl;

    /**
     * An embeddable link for iframe (e.g., https://www.youtube.com/embed/...).
     * Optional.
     */
    @Column(columnDefinition = "TEXT")
    private String embedUrl;

    // optional: for ordering in UI
    private Integer sortOrder;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.sortOrder == null) this.sortOrder = 0;
    }
}
