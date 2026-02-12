package ak.dev.khi_backend.khi_app.model.publishment.writing;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
/**
 * Writing/Book entity with bilingual support (CKB & KMR)
 * Represents books, documents, and publications
 */
@Entity
@Table(
        name = "writings",
        indexes = {
                @Index(name = "idx_writing_topic", columnList = "writing_topic"),
                @Index(name = "idx_writing_institute", columnList = "published_by_institute"),
                @Index(name = "idx_writing_created_at", columnList = "created_at"),
                @Index(name = "idx_writing_updated_at", columnList = "updated_at")
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
    // BILINGUAL SUPPORT (like News and SoundTrack)
    // ============================================================

    /**
     * Which languages are active for this writing
     * Can be: [CKB], [KMR], or [CKB, KMR]
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
     * Contains: title, description, writer, coverUrl, fileUrl, fileFormat,
     * fileSizeBytes, pageCount, genre
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
     * Contains: title, description, writer, coverUrl, fileUrl, fileFormat,
     * fileSizeBytes, pageCount, genre
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}