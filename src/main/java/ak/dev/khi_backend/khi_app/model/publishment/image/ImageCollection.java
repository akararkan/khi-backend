package ak.dev.khi_backend.khi_app.model.publishment.image;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ImageCollection — Image / photo publishment entity.
 *
 * ─── Collection Types ─────────────────────────────────────────────────────────
 *  SINGLE      → exactly 1 image
 *  GALLERY     → multiple images (album)
 *  PHOTO_STORY → multiple images (story / process)
 *
 * ─── Cover Images (3 slots — mirrors SoundTrack pattern) ─────────────────────
 *  ckbCoverUrl   → Sorani   cover  (column: ckb_cover_url)
 *  kmrCoverUrl   → Kurmanji cover  (column: kmr_cover_url)
 *  hoverCoverUrl → hover overlay   (column: hover_cover_url)
 *
 *  All three are nullable — a collection may carry only one or two covers.
 *  The service / frontend decides which to display based on the active language.
 *
 * ─── Topic ────────────────────────────────────────────────────────────────────
 *  Topic is a @ManyToOne relation to PublishmentTopic (entityType = "IMAGE").
 *  Mirrors SoundTrack — it is NOT a free-text column. This allows:
 *    - Reuse of the same topic across many image collections
 *    - Bilingual topic names (CKB + KMR) managed in one place
 *    - Frontend autocomplete from the topic table
 *
 * ─── DB Migration (run once when upgrading from old single-cover schema) ──────
 *
 *  -- Step 1: rename old cover_url column to the ckb slot
 *  ALTER TABLE image_collections RENAME COLUMN cover_url TO ckb_cover_url;
 *
 *  -- Step 2: make it nullable (covers are optional per slot now)
 *  ALTER TABLE image_collections ALTER COLUMN ckb_cover_url DROP NOT NULL;
 *
 *  -- Step 3: add the two new cover columns
 *  ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS kmr_cover_url   TEXT;
 *  ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS hover_cover_url TEXT;
 *
 *  -- Step 4: add topic FK column + constraint
 *  ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS topic_id BIGINT;
 *  ALTER TABLE image_collections
 *      ADD CONSTRAINT fk_img_coll_topic
 *      FOREIGN KEY (topic_id) REFERENCES publishment_topics(id) ON DELETE SET NULL;
 */
@Entity
@Table(
        name = "image_collections",
        indexes = {
                @Index(name = "idx_img_collection_type",  columnList = "collection_type"),
                @Index(name = "idx_img_topic",            columnList = "topic_id"),
                @Index(name = "idx_img_title_ckb",        columnList = "title_ckb"),
                @Index(name = "idx_img_title_kmr",        columnList = "title_kmr"),
                @Index(name = "idx_img_publishment_date", columnList = "publishment_date"),
                @Index(name = "idx_img_created_at",       columnList = "created_at"),
                @Index(name = "idx_img_updated_at",       columnList = "updated_at")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Collection Type ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false, length = 20)
    private ImageCollectionType collectionType;

    // ─── Cover Images ─────────────────────────────────────────────────────────

    /** Sorani (CKB) cover — S3 uploaded or external URL. */
    @Column(name = "ckb_cover_url", columnDefinition = "TEXT")
    private String ckbCoverUrl;

    /** Kurmanji (KMR) cover — S3 uploaded or external URL. */
    @Column(name = "kmr_cover_url", columnDefinition = "TEXT")
    private String kmrCoverUrl;

    /** Hover overlay image — shown on mouse-over in list / card views. */
    @Column(name = "hover_cover_url", columnDefinition = "TEXT")
    private String hoverCoverUrl;

    // ─── Topic (ManyToOne → PublishmentTopic, entityType = "IMAGE") ──────────

    /**
     * The topic / subject of this image collection.
     * Points to PublishmentTopic where entityType = "IMAGE".
     * Nullable — a collection may have no topic assigned yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private PublishmentTopic topic;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",        length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb",  columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_ckb",     length = 250)),
            @AttributeOverride(name = "collectedBy", column = @Column(name = "collected_by_ckb", length = 250))
    })
    private ImageContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",        length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr",  columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_kmr",     length = 250)),
            @AttributeOverride(name = "collectedBy", column = @Column(name = "collected_by_kmr", length = 250))
    })
    private ImageContent kmrContent;

    // ─── Image Album ──────────────────────────────────────────────────────────

    @Builder.Default
    @OneToMany(
            mappedBy = "imageCollection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<ImageAlbumItem> imageAlbum = new ArrayList<>();

    // ─── Publishment Date ─────────────────────────────────────────────────────

    @Column(name = "publishment_date")
    private LocalDate publishmentDate;

    // ─── Languages ────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "image_collection_languages",
            joinColumns = @JoinColumn(name = "image_collection_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_tags_ckb", joinColumns = @JoinColumn(name = "image_collection_id"))
    @Column(name = "tag_ckb", nullable = false, length = 100)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_tags_kmr", joinColumns = @JoinColumn(name = "image_collection_id"))
    @Column(name = "tag_kmr", nullable = false, length = 100)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── CKB Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_keywords_ckb", joinColumns = @JoinColumn(name = "image_collection_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 150)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_keywords_kmr", joinColumns = @JoinColumn(name = "image_collection_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 150)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ─── Timestamps ───────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    // ─── Helper Methods ───────────────────────────────────────────────────────

    /**
     * Returns the first non-blank cover URL (ckb → kmr → hover), or null.
     * Useful as a fallback for logs, notifications, thumbnails.
     */
    public String getAnyCoverUrl() {
        if (ckbCoverUrl   != null && !ckbCoverUrl.isBlank())   return ckbCoverUrl;
        if (kmrCoverUrl   != null && !kmrCoverUrl.isBlank())   return kmrCoverUrl;
        if (hoverCoverUrl != null && !hoverCoverUrl.isBlank()) return hoverCoverUrl;
        return null;
    }
}