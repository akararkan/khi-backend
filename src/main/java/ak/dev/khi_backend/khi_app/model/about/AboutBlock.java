package ak.dev.khi_backend.khi_app.model.about;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * AboutBlock — A single rich-content section within an {@link About} page.
 *
 * ─── Bilingual Content ────────────────────────────────────────────────────────
 *
 *  Follows the same pattern as the Video / VideoClipItem entities:
 *    ckbContent  → Sorani  (CKB) text fields (contentText, title, altText)
 *    kmrContent  → Kurmanji (KMR) text fields (contentText, title, altText)
 *
 *  Each is an {@link AboutBlockContent} embeddable mapped to its own columns
 *  via {@code @AttributeOverrides}.
 *
 * ─── Media ────────────────────────────────────────────────────────────────────
 *  mediaUrl / thumbnailUrl are language-agnostic (same file for both locales).
 *  Additional technical metadata lives in the {@code metadata} JSON column.
 */
@Entity
@Table(
        name = "about_blocks",
        indexes = {
                @Index(name = "idx_about_block_about_id",  columnList = "about_id"),
                @Index(name = "idx_about_block_sequence",  columnList = "sequence"),
                @Index(name = "idx_about_block_type",      columnList = "content_type")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AboutBlock {

    public enum ContentType {
        TEXT, IMAGE, VIDEO, AUDIO, GALLERY, QUOTE, STATS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "sequence")
    private Integer sequence = 0;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "contentText", column = @Column(name = "content_text_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",        length = 300)),
            @AttributeOverride(name = "altText",     column = @Column(name = "alt_text_ckb",     length = 500))
    })
    private AboutBlockContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "contentText", column = @Column(name = "content_text_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",        length = 300)),
            @AttributeOverride(name = "altText",     column = @Column(name = "alt_text_kmr",     length = 500))
    })
    private AboutBlockContent kmrContent;

    // ─── Media (language-agnostic) ────────────────────────────────────────────

    /** S3 / CDN hosted file URL. */
    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    /** Thumbnail URL for VIDEO / IMAGE blocks. */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    // ─── Extra Metadata ───────────────────────────────────────────────────────

    /**
     * Flexible JSON bag for technical extras:
     *   IMAGE/GALLERY → width, height
     *   VIDEO/AUDIO   → duration, format, fileSizeMb
     *   STATS         → statItems array
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // ─── Parent ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "about_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private About about;

    // ─── Convenience Checks ───────────────────────────────────────────────────

    public boolean isText()    { return contentType == ContentType.TEXT;    }
    public boolean isImage()   { return contentType == ContentType.IMAGE;   }
    public boolean isVideo()   { return contentType == ContentType.VIDEO;   }
    public boolean isAudio()   { return contentType == ContentType.AUDIO;   }
    public boolean isGallery() { return contentType == ContentType.GALLERY; }
    public boolean isQuote()   { return contentType == ContentType.QUOTE;   }
    public boolean isStats()   { return contentType == ContentType.STATS;   }
}