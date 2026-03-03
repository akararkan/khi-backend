package ak.dev.khi_backend.khi_app.model.publishment.writing;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Writing/Book entity — bilingual (CKB & KMR) with Series support.
 *
 * ─── Cover Images (3 slots — entity level, mirrors ImageCollection) ──────────
 *
 *  ckbCoverUrl   → Sorani   cover  (column: ckb_cover_url)
 *  kmrCoverUrl   → Kurmanji cover  (column: kmr_cover_url)
 *  hoverCoverUrl → hover overlay   (column: hover_cover_url)
 *
 *  All three are nullable — a writing may carry only one or two covers.
 *  Moved out of WritingContent so the embeddable stays focused on text/file data.
 *
 * ─── Topic (ManyToOne → PublishmentTopic, entityType = "WRITING") ────────────
 *
 *  Dynamic bilingual topic managed in the shared publishment_topics table.
 *  Nullable — a writing may have no topic assigned yet.
 *
 * ─── DB Migration ─────────────────────────────────────────────────────────────
 *
 *  -- Rename old per-language cover columns → new 3-slot names
 *  ALTER TABLE writings RENAME COLUMN cover_url_ckb TO ckb_cover_url;
 *  ALTER TABLE writings RENAME COLUMN cover_url_kmr TO kmr_cover_url;
 *  ALTER TABLE writings ALTER COLUMN ckb_cover_url TYPE TEXT;
 *  ALTER TABLE writings ALTER COLUMN kmr_cover_url TYPE TEXT;
 *  ALTER TABLE writings ALTER COLUMN ckb_cover_url DROP NOT NULL;
 *  ALTER TABLE writings ALTER COLUMN kmr_cover_url DROP NOT NULL;
 *
 *  -- Add hover cover column
 *  ALTER TABLE writings ADD COLUMN IF NOT EXISTS hover_cover_url TEXT;
 *
 *  -- Add topic FK column + constraint
 *  ALTER TABLE writings ADD COLUMN IF NOT EXISTS topic_id BIGINT;
 *  ALTER TABLE writings
 *      ADD CONSTRAINT fk_writing_topic
 *      FOREIGN KEY (topic_id) REFERENCES publishment_topics(id) ON DELETE SET NULL;
 */
@Entity
@Table(
        name = "writings",
        indexes = {
                @Index(name = "idx_writing_topic",      columnList = "writing_topic"),
                @Index(name = "idx_writing_topic_id",   columnList = "topic_id"),
                @Index(name = "idx_writing_institute",  columnList = "published_by_institute"),
                @Index(name = "idx_writing_created_at", columnList = "created_at"),
                @Index(name = "idx_writing_updated_at", columnList = "updated_at"),
                @Index(name = "idx_writer_ckb",         columnList = "writer_ckb"),
                @Index(name = "idx_writer_kmr",         columnList = "writer_kmr"),
                @Index(name = "idx_series_id",          columnList = "series_id"),
                @Index(name = "idx_series_composite",   columnList = "series_id, series_order"),
                @Index(name = "idx_parent_book",        columnList = "parent_book_id")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Writing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover Images (3 slots) ───────────────────────────────────────────────

    /**
     * Sorani (CKB) cover image — S3 URL or external URL.
     * Displayed when active language is CKB, or as the primary cover fallback.
     */
    @Column(name = "ckb_cover_url", columnDefinition = "TEXT")
    private String ckbCoverUrl;

    /**
     * Kurmanji (KMR) cover image — S3 URL or external URL.
     * Displayed when active language is KMR.
     */
    @Column(name = "kmr_cover_url", columnDefinition = "TEXT")
    private String kmrCoverUrl;

    /**
     * Hover overlay image — shown on mouse-over in list / card views.
     * Optional; if absent, ckbCoverUrl is used as fallback hover target.
     */
    @Column(name = "hover_cover_url", columnDefinition = "TEXT")
    private String hoverCoverUrl;

    // ─── Topic ────────────────────────────────────────────────────────────────

    /**
     * Dynamic bilingual topic (entityType = "WRITING").
     * Nullable — assign at create time or update later via topicId / newTopic.
     * ON DELETE SET NULL keeps the writing intact if the topic is removed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private PublishmentTopic topic;

    // ─── Series / Edition Support ─────────────────────────────────────────────

    /**
     * Series identifier — all books sharing the same seriesId form one series.
     * Auto-generated on persist if not explicitly provided.
     */
    @Column(name = "series_id", length = 100)
    private String seriesId;

    /** Display name of the series (optional; falls back to book title). */
    @Column(name = "series_name", length = 300)
    private String seriesName;

    /** Order within the series. Allows fractional insertion (e.g. 1.5 between 1 and 2). */
    @Column(name = "series_order")
    private Double seriesOrder;

    /** Reference to the parent / first book in this series (null for standalone or series root). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_book_id")
    private Writing parentBook;

    /** Sibling books ordered by seriesOrder ASC (populated when this is the series parent). */
    @OneToMany(mappedBy = "parentBook", fetch = FetchType.LAZY)
    @OrderBy("seriesOrder ASC")
    @Builder.Default
    private List<Writing> seriesBooks = new ArrayList<>();

    /** Cached total book count for the series — kept in sync by the service. */
    @Column(name = "series_total_books")
    private Integer seriesTotalBooks;

    // ─── Bilingual Support ────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "writing_content_languages",
            joinColumns = @JoinColumn(name = "writing_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    /**
     * Sorani (CKB) content — title, description, writer, file data, genre.
     * Cover URL lives in {@link #ckbCoverUrl}; it is intentionally absent here.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",         column = @Column(name = "title_ckb",           length = 300)),
            @AttributeOverride(name = "description",   column = @Column(name = "description_ckb",     columnDefinition = "TEXT")),
            @AttributeOverride(name = "writer",        column = @Column(name = "writer_ckb",           length = 200)),
            @AttributeOverride(name = "fileUrl",       column = @Column(name = "file_url_ckb",         length = 1000)),
            @AttributeOverride(name = "fileFormat",    column = @Column(name = "file_format_ckb",      length = 20)),
            @AttributeOverride(name = "fileSizeBytes", column = @Column(name = "file_size_bytes_ckb")),
            @AttributeOverride(name = "pageCount",     column = @Column(name = "page_count_ckb")),
            @AttributeOverride(name = "genre",         column = @Column(name = "genre_ckb",            length = 150))
    })
    private WritingContent ckbContent;

    /**
     * Kurmanji (KMR) content — title, description, writer, file data, genre.
     * Cover URL lives in {@link #kmrCoverUrl}; it is intentionally absent here.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",         column = @Column(name = "title_kmr",           length = 300)),
            @AttributeOverride(name = "description",   column = @Column(name = "description_kmr",     columnDefinition = "TEXT")),
            @AttributeOverride(name = "writer",        column = @Column(name = "writer_kmr",           length = 200)),
            @AttributeOverride(name = "fileUrl",       column = @Column(name = "file_url_kmr",         length = 1000)),
            @AttributeOverride(name = "fileFormat",    column = @Column(name = "file_format_kmr",      length = 20)),
            @AttributeOverride(name = "fileSizeBytes", column = @Column(name = "file_size_bytes_kmr")),
            @AttributeOverride(name = "pageCount",     column = @Column(name = "page_count_kmr")),
            @AttributeOverride(name = "genre",         column = @Column(name = "genre_kmr",            length = 150))
    })
    private WritingContent kmrContent;

    // ─── Shared Fields ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_topic", nullable = false, length = 30)
    private WritingTopic writingTopic;

    @Column(name = "published_by_institute", nullable = false)
    private boolean publishedByInstitute;

    // ─── Bilingual Tags & Keywords ────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_keywords_ckb", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 120)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_keywords_kmr", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 120)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_tags_ckb", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "tag_ckb", nullable = false, length = 80)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_tags_kmr", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "tag_kmr", nullable = false, length = 80)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── Timestamps ───────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (seriesId == null)    seriesId    = "series-" + System.currentTimeMillis();
        if (seriesOrder == null) seriesOrder = 1.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public boolean isPartOfSeries() {
        return seriesId != null && (seriesTotalBooks == null || seriesTotalBooks > 1);
    }

    public boolean isSeriesParent() {
        return parentBook == null && isPartOfSeries();
    }

    public String getEffectiveSeriesName() {
        if (seriesName != null && !seriesName.isBlank())         return seriesName;
        if (ckbContent != null && ckbContent.getTitle() != null) return ckbContent.getTitle();
        if (kmrContent != null && kmrContent.getTitle() != null) return kmrContent.getTitle();
        return "Unknown Series";
    }

    /**
     * Returns the first non-blank cover URL across all three slots (ckb → kmr → hover).
     * Useful as a fallback for logs, notifications, and thumbnail generation.
     */
    public String getAnyCoverUrl() {
        if (ckbCoverUrl   != null && !ckbCoverUrl.isBlank())   return ckbCoverUrl;
        if (kmrCoverUrl   != null && !kmrCoverUrl.isBlank())   return kmrCoverUrl;
        if (hoverCoverUrl != null && !hoverCoverUrl.isBlank()) return hoverCoverUrl;
        return null;
    }
}