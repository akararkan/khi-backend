package ak.dev.khi_backend.khi_app.model.publishment.video;

import ak.dev.khi_backend.khi_app.enums.Language;
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
 * Video — Video publishment entity.
 *
 * ─── Video Types ──────────────────────────────────────────────────────────────
 *
 *  FILM
 *    A traditional film or documentary. Has a single video source
 *    (sourceUrl / sourceExternalUrl / sourceEmbedUrl). No clip list.
 *
 *  VIDEO_CLIP
 *    A collection (set) of short video clips. The clips themselves live in
 *    the `videoClipItems` list, each with its own URL, title, description,
 *    and order number. The top-level sourceUrl fields are unused for this type.
 *
 *    VIDEO_CLIP can optionally be an "Album of Memories":
 *      isAlbumOfMemories = true  → memorial / retrospective clip collection
 *      isAlbumOfMemories = false → regular clip collection
 *
 * ─── Topic ────────────────────────────────────────────────────────────────────
 *  Topic is a @ManyToOne relation to PublishmentTopic (entityType = "VIDEO").
 *  It is NOT a free-text column. This allows:
 *    - Reuse of the same topic across many videos
 *    - Bilingual topic names (CKB + KMR) managed in one place
 *    - Frontend autocomplete from the topic table
 */
@Entity
@Table(
        name = "videos",
        indexes = {
                @Index(name = "idx_video_type",        columnList = "video_type"),
                @Index(name = "idx_video_album",       columnList = "is_album_of_memories"),
                @Index(name = "idx_video_pub_date",    columnList = "publishment_date"),
                @Index(name = "idx_video_topic",       columnList = "topic_id"),
                @Index(name = "idx_video_title_ckb",   columnList = "title_ckb"),
                @Index(name = "idx_video_title_kmr",   columnList = "title_kmr")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover / Thumbnail ────────────────────────────────────────────────────

    @Column(name = "cover_url", length = 1000)      // ← column is "cover_url"
    private String ckbCoverUrl;                       //   but field says "ckb"

    @Column(name = "ckb_cover_url", length = 1000)  // ← column is "ckb_cover_url"
    private String kmrCoverUrl;                       //   but field says "kmr"

    @Column(name = "hover_cover_url", length = 1000)
    private String hoverCoverUrl;

    // ─── Video Type ───────────────────────────────────────────────────────────

    /**
     * FILM       → single traditional film / documentary
     * VIDEO_CLIP → ordered set of short clips (may be album of memories)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "video_type", nullable = false, length = 20)
    private VideoType videoType;

    /**
     * Is this a "Album of Memories" video clip collection?
     *
     * Only meaningful when videoType = VIDEO_CLIP.
     * When true → memorial / retrospective clip set.
     * When false → regular clip collection.
     *
     * Always false for FILM type.
     */
    @Builder.Default
    @Column(name = "is_album_of_memories", nullable = false)
    private boolean albumOfMemories = false;

    // ─── Topic (ManyToOne → PublishmentTopic) ─────────────────────────────────

    /**
     * The topic / subject of this video.
     * Points to PublishmentTopic where entityType = "VIDEO".
     * Nullable — a video may have no topic assigned yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private PublishmentTopic topic;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_ckb",    length = 250)),
            @AttributeOverride(name = "director",    column = @Column(name = "director_ckb",    length = 250)),
            @AttributeOverride(name = "producer",    column = @Column(name = "producer_ckb",    length = 250))
    })
    private VideoContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_kmr",    length = 250)),
            @AttributeOverride(name = "director",    column = @Column(name = "director_kmr",    length = 250)),
            @AttributeOverride(name = "producer",    column = @Column(name = "producer_kmr",    length = 250))
    })
    private VideoContent kmrContent;

    // ─── Single Video Source (FILM type only) ─────────────────────────────────
    // For VIDEO_CLIP type, the source lives on each VideoClipItem instead.

    /** Direct hosted file URL (S3 / CDN) — used for FILM type. */
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /** External page link (YouTube watch, Vimeo …) — used for FILM type. */
    @Column(name = "source_external_url", columnDefinition = "TEXT")
    private String sourceExternalUrl;

    /** Embeddable iframe URL — used for FILM type. */
    @Column(name = "source_embed_url", columnDefinition = "TEXT")
    private String sourceEmbedUrl;

    // ─── Clip Items (VIDEO_CLIP type only) ────────────────────────────────────

    /**
     * Individual clips for VIDEO_CLIP type videos.
     * Empty / unused for FILM type.
     * Ordered by clipNumber ASC.
     */
    @Builder.Default
    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("clipNumber ASC")
    private List<VideoClipItem> videoClipItems = new ArrayList<>();

    // ─── Common Video Metadata ────────────────────────────────────────────────

    @Column(name = "file_format", length = 20)
    private String fileFormat;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "publishment_date")
    private LocalDate publishmentDate;

    @Column(name = "resolution", length = 20)
    private String resolution;

    @Column(name = "file_size_mb")
    private Double fileSizeMb;

    // ─── Languages ────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_content_languages", joinColumns = @JoinColumn(name = "video_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_tags_ckb", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "tag_ckb", nullable = false, length = 100)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_tags_kmr", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "tag_kmr", nullable = false, length = 100)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── CKB Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_keywords_ckb", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 150)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_keywords_kmr", joinColumns = @JoinColumn(name = "video_id"))
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

    public void addClipItem(VideoClipItem item) {
        videoClipItems.add(item);
        item.setVideo(this);
    }

    public void removeClipItem(VideoClipItem item) {
        videoClipItems.remove(item);
        item.setVideo(null);
    }

    /** Convenience check: true only when VIDEO_CLIP and flagged as album of memories. */
    public boolean isVideoClipAlbumOfMemories() {
        return videoType == VideoType.VIDEO_CLIP && albumOfMemories;
    }
}
