package ak.dev.khi_backend.khi_app.model.publishment.writing;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Writing/Book entity with bilingual support (CKB & KMR)
 * Represents books, documents, and publications with SERIES SUPPORT
 *
 * ✅ ENHANCED FEATURES:
 * - Book Series/Editions support (e.g., "Eslam Part 1", "Eslam Part 2")
 * - Optimized indexing for fast writer search O(log n)
 * - Two separate cover pages (CKB & KMR) already supported
 */
@Entity
@Table(
        name = "writings",
        indexes = {
                @Index(name = "idx_writing_topic", columnList = "writing_topic"),
                @Index(name = "idx_writing_institute", columnList = "published_by_institute"),
                @Index(name = "idx_writing_created_at", columnList = "created_at"),
                @Index(name = "idx_writing_updated_at", columnList = "updated_at"),

                // ✅ NEW: Optimized indexes for writer search O(log n)
                @Index(name = "idx_writer_ckb", columnList = "writer_ckb"),
                @Index(name = "idx_writer_kmr", columnList = "writer_kmr"),

                // ✅ NEW: Optimized indexes for series queries O(log n)
                @Index(name = "idx_series_id", columnList = "series_id"),
                @Index(name = "idx_series_composite", columnList = "series_id, series_order"),
                @Index(name = "idx_parent_book", columnList = "parent_book_id")
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

    // ============================================================
    // ✅ NEW: BOOK SERIES / EDITION SUPPORT
    // ============================================================

    /**
     * Series identifier - books with same seriesId belong to same series
     * Example: "eslam-series", "quran-commentary-series"
     * Generated automatically for first book, can be shared by related books
     */
    @Column(name = "series_id", length = 100)
    private String seriesId;

    /**
     * Series name (displayed to users)
     * Example: "Eslam Series", "Introduction to Islam"
     * Optional - if null, individual book titles are used
     */
    @Column(name = "series_name", length = 300)
    private String seriesName;

    /**
     * Order within the series
     * Example: 1 for "Part 1", 2 for "Part 2"
     * Allows flexible ordering: 1, 2, 3... or 1, 1.5, 2 (for insertions)
     */
    @Column(name = "series_order")
    private Double seriesOrder;

    /**
     * Reference to the parent/first book in series (optional)
     * Useful for quick navigation to series root
     * NULL for standalone books or series parent
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_book_id")
    private Writing parentBook;

    /**
     * Books in this series (if this is a parent)
     * Bi-directional relationship for easy series navigation
     */
    @OneToMany(mappedBy = "parentBook", fetch = FetchType.LAZY)
    @OrderBy("seriesOrder ASC")
    @Builder.Default
    private List<Writing> seriesBooks = new ArrayList<>();

    /**
     * Total books in this series (cached for performance)
     * Updated when books are added/removed from series
     */
    @Column(name = "series_total_books")
    private Integer seriesTotalBooks;

    // ============================================================
    // BILINGUAL SUPPORT (like News and SoundTrack)
    // ============================================================

    /**
     * Which languages are active for this writing
     * Can be: [CKB], [KMR], or [CKB, KMR]
     * ✅ Supports TWO SEPARATE COVER PAGES (one for each language)
     */
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
     * ✅ Sorani (CKB) Content
     * Contains: title, description, writer, coverUrl (COVER PAGE 1),
     * fileUrl, fileFormat, fileSizeBytes, pageCount, genre
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "writer", column = @Column(name = "writer_ckb", length = 200)),
            @AttributeOverride(name = "coverUrl", column = @Column(name = "cover_url_ckb", length = 1000)),
            @AttributeOverride(name = "fileUrl", column = @Column(name = "file_url_ckb", length = 1000)),
            @AttributeOverride(name = "fileFormat", column = @Column(name = "file_format_ckb", length = 20)),
            @AttributeOverride(name = "fileSizeBytes", column = @Column(name = "file_size_bytes_ckb")),
            @AttributeOverride(name = "pageCount", column = @Column(name = "page_count_ckb")),
            @AttributeOverride(name = "genre", column = @Column(name = "genre_ckb", length = 150))
    })
    private WritingContent ckbContent;

    /**
     * ✅ Kurmanji (KMR) Content
     * Contains: title, description, writer, coverUrl (COVER PAGE 2),
     * fileUrl, fileFormat, fileSizeBytes, pageCount, genre
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "writer", column = @Column(name = "writer_kmr", length = 200)),
            @AttributeOverride(name = "coverUrl", column = @Column(name = "cover_url_kmr", length = 1000)),
            @AttributeOverride(name = "fileUrl", column = @Column(name = "file_url_kmr", length = 1000)),
            @AttributeOverride(name = "fileFormat", column = @Column(name = "file_format_kmr", length = 20)),
            @AttributeOverride(name = "fileSizeBytes", column = @Column(name = "file_size_bytes_kmr")),
            @AttributeOverride(name = "pageCount", column = @Column(name = "page_count_kmr")),
            @AttributeOverride(name = "genre", column = @Column(name = "genre_kmr", length = 150))
    })
    private WritingContent kmrContent;

    // ============================================================
    // SHARED FIELDS (not language-specific)
    // ============================================================

    /**
     * Main topic/category of the writing
     * Examples: HISTORICAL, FOLKLORE, RELIGIOUS, POLITICAL, POETRY, etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "writing_topic", nullable = false, length = 30)
    private WritingTopic writingTopic;

    /**
     * Is this publication by the institute itself?
     * true = Published by the institute
     * false = Written for others / external publication
     */
    @Column(name = "published_by_institute", nullable = false)
    private boolean publishedByInstitute;

    // ============================================================
    // BILINGUAL TAGS & KEYWORDS
    // ============================================================

    /**
     * ✅ Sorani (CKB) Keywords
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_keywords_ckb", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 120)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    /**
     * ✅ Kurmanji (KMR) Keywords
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_keywords_kmr", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 120)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    /**
     * ✅ Sorani (CKB) Tags
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_tags_ckb", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "tag_ckb", nullable = false, length = 80)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    /**
     * ✅ Kurmanji (KMR) Tags
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "writing_tags_kmr", joinColumns = @JoinColumn(name = "writing_id"))
    @Column(name = "tag_kmr", nullable = false, length = 80)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ============================================================
    // TIMESTAMPS
    // ============================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // Auto-generate series ID if not provided
        if (seriesId == null) {
            seriesId = "series-" + System.currentTimeMillis();
        }

        // Default series order to 1 if not set
        if (seriesOrder == null) {
            seriesOrder = 1.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============================================================
    // HELPER METHODS FOR SERIES MANAGEMENT
    // ============================================================

    /**
     * Check if this book is part of a series
     */
    public boolean isPartOfSeries() {
        return seriesId != null && (seriesTotalBooks == null || seriesTotalBooks > 1);
    }

    /**
     * Check if this is the parent/first book in a series
     */
    public boolean isSeriesParent() {
        return parentBook == null && isPartOfSeries();
    }

    /**
     * Get the effective series name (falls back to book title if not set)
     */
    public String getEffectiveSeriesName() {
        if (seriesName != null && !seriesName.isBlank()) {
            return seriesName;
        }
        if (ckbContent != null && ckbContent.getTitle() != null) {
            return ckbContent.getTitle();
        }
        if (kmrContent != null && kmrContent.getTitle() != null) {
            return kmrContent.getTitle();
        }
        return "Unknown Series";
    }
}