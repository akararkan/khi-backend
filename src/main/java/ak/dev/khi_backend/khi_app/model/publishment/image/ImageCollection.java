package ak.dev.khi_backend.khi_app.model.publishment.image;


import ak.dev.khi_backend.khi_app.enums.Language;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "image_collections", indexes = {
        @Index(name = "idx_img_title_ckb", columnList = "title_ckb"),
        @Index(name = "idx_img_title_kmr", columnList = "title_kmr"),
        @Index(name = "idx_img_publishment_date", columnList = "publishment_date")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover page image URL (S3) ────────────────────────────────────
    @Column(name = "cover_url", nullable = false, columnDefinition = "TEXT")
    private String coverUrl;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",        length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb",  columnDefinition = "TEXT")),
            @AttributeOverride(name = "topic",       column = @Column(name = "topic_ckb",        length = 300)),
            @AttributeOverride(name = "location",    column = @Column(name = "location_ckb",     length = 250)),
            @AttributeOverride(name = "collectedBy", column = @Column(name = "collected_by_ckb", length = 250))
    })
    private ImageContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",        length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr",  columnDefinition = "TEXT")),
            @AttributeOverride(name = "topic",       column = @Column(name = "topic_kmr",        length = 300)),
            @AttributeOverride(name = "location",    column = @Column(name = "location_kmr",     length = 250)),
            @AttributeOverride(name = "collectedBy", column = @Column(name = "collected_by_kmr", length = 250))
    })
    private ImageContent kmrContent;

    // ─── Image Album (list of images with optional descriptions) ──────
    @Builder.Default
    @OneToMany(mappedBy = "imageCollection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ImageAlbumItem> imageAlbum = new ArrayList<>();

    // ─── Publishment date ─────────────────────────────────────────────
    @Column(name = "publishment_date")
    private LocalDate publishmentDate;

    // ─── Languages of the content ─────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_collection_languages",
            joinColumns = @JoinColumn(name = "image_collection_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_tags_ckb",
            joinColumns = @JoinColumn(name = "image_collection_id")
    )
    @Column(name = "tag_ckb", nullable = false, length = 100)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_tags_kmr",
            joinColumns = @JoinColumn(name = "image_collection_id")
    )
    @Column(name = "tag_kmr", nullable = false, length = 100)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── CKB Keywords ─────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_keywords_ckb",
            joinColumns = @JoinColumn(name = "image_collection_id")
    )
    @Column(name = "keyword_ckb", nullable = false, length = 150)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_keywords_kmr",
            joinColumns = @JoinColumn(name = "image_collection_id")
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