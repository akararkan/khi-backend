package ak.dev.khi_backend.khi_app.model.publishment.film;


import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FilmType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "films", indexes = {
        @Index(name = "idx_film_type", columnList = "film_type"),
        @Index(name = "idx_film_year", columnList = "publishment_date"),
        @Index(name = "idx_film_title_ckb", columnList = "title_ckb"),
        @Index(name = "idx_film_title_kmr", columnList = "title_kmr")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Film {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover / Thumbnail ────────────────────────────────────────────
    @Column(name = "cover_url", nullable = false, columnDefinition = "TEXT")
    private String coverUrl;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "topic",       column = @Column(name = "topic_ckb",       length = 300)),
            @AttributeOverride(name = "location",    column = @Column(name = "location_ckb",    length = 250)),
            @AttributeOverride(name = "director",    column = @Column(name = "director_ckb",    length = 250)),
            @AttributeOverride(name = "producer",    column = @Column(name = "producer_ckb",    length = 250))
    })
    private FilmContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "topic",       column = @Column(name = "topic_kmr",       length = 300)),
            @AttributeOverride(name = "location",    column = @Column(name = "location_kmr",    length = 250)),
            @AttributeOverride(name = "director",    column = @Column(name = "director_kmr",    length = 250)),
            @AttributeOverride(name = "producer",    column = @Column(name = "producer_kmr",    length = 250))
    })
    private FilmContent kmrContent;

    // ─── Film Metadata ────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "film_type", nullable = false, length = 30)
    private FilmType filmType;

    @Column(name = "file_format", length = 20)
    private String fileFormat;     // mp4, mkv, avi, etc.

    @Column(name = "duration_seconds")
    private Integer durationSeconds;  // total duration in seconds

    @Column(name = "publishment_date")
    private LocalDate publishmentDate;

    // ─── Extra Important Fields ───────────────────────────────────────

    @Column(name = "resolution", length = 20)
    private String resolution;     // 480p, 720p, 1080p, 4K

    @Column(name = "file_size_mb")
    private Double fileSizeMb;     // file size in megabytes

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;      // original source or stream URL

    // ─── Languages of the film content ────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "film_content_languages",
            joinColumns = @JoinColumn(name = "film_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "film_tags_ckb",
            joinColumns = @JoinColumn(name = "film_id")
    )
    @Column(name = "tag_ckb", nullable = false, length = 100)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "film_tags_kmr",
            joinColumns = @JoinColumn(name = "film_id")
    )
    @Column(name = "tag_kmr", nullable = false, length = 100)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── CKB Keywords ─────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "film_keywords_ckb",
            joinColumns = @JoinColumn(name = "film_id")
    )
    @Column(name = "keyword_ckb", nullable = false, length = 150)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "film_keywords_kmr",
            joinColumns = @JoinColumn(name = "film_id")
    )
    @Column(name = "keyword_kmr", nullable = false, length = 150)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ─── Timestamps ───────────────────────────────────────────────────

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}