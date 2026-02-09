package ak.dev.khi_backend.khi_app.model.news;

import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_media")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsMediaType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

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
