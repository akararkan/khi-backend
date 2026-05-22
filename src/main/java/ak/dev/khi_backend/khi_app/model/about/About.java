package ak.dev.khi_backend.khi_app.model.about;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * About — About-page entity (Tiptap migration).
 *
 * ─── Bilingual Slugs ──────────────────────────────────────────────────────────
 *  Each language has its own unique URL slug:
 *    slugCkb  → Sorani   route identifier
 *    slugKmr  → Kurmanji route identifier (nullable — page may be CKB-only)
 *
 * ─── Hero Image ───────────────────────────────────────────────────────────────
 *  {@link #heroImageUrl} — the full-bleed banner at the top of the page.
 *  Either set or absent — there is no longer a "fallback to first block image"
 *  rule, because blocks have been removed.
 *
 * ─── Bilingual Content ────────────────────────────────────────────────────────
 *  ckbContent  → Sorani  (CKB) title / subtitle / meta / Tiptap body
 *  kmrContent  → Kurmanji (KMR) title / subtitle / meta / Tiptap body
 *
 *  The {@code body} field on {@link AboutContent} replaces the old per-page
 *  block collection. The Tiptap editor produces full HTML for each language,
 *  including inline images / audio / video whose URLs already point at S3
 *  (uploaded via the shared /api/v1/media/upload endpoint).
 *
 * ─── Stats ────────────────────────────────────────────────────────────────────
 *  The STATS section (array of {labelCkb, labelKmr, value}) survives as a
 *  dedicated JSONB column. It cannot be embedded cleanly in HTML and the
 *  frontend renders it from structured JSON.
 */
@Entity
@Table(
        name = "about_pages",
        indexes = {
                @Index(name = "idx_about_slug_ckb", columnList = "slug_ckb"),
                @Index(name = "idx_about_slug_kmr", columnList = "slug_kmr"),
                @Index(name = "idx_about_active",   columnList = "active")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class About {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "slug_ckb", unique = true, nullable = false, length = 200)
    private String slugCkb;

    @Column(name = "slug_kmr", unique = true, nullable = true, length = 200)
    private String slugKmr;

    @Builder.Default
    private boolean active = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "hero_image_url", length = 1000)
    private String heroImageUrl;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",           column = @Column(name = "title_ckb",            length = 300)),
            @AttributeOverride(name = "subtitle",        column = @Column(name = "subtitle_ckb",         length = 500)),
            @AttributeOverride(name = "metaDescription", column = @Column(name = "meta_description_ckb", length = 2500)),
            @AttributeOverride(name = "body",            column = @Column(name = "body_ckb",             columnDefinition = "TEXT"))
    })
    private AboutContent ckbContent;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",           column = @Column(name = "title_kmr",            length = 300)),
            @AttributeOverride(name = "subtitle",        column = @Column(name = "subtitle_kmr",         length = 500)),
            @AttributeOverride(name = "metaDescription", column = @Column(name = "meta_description_kmr", length = 2500)),
            @AttributeOverride(name = "body",            column = @Column(name = "body_kmr",             columnDefinition = "TEXT"))
    })
    private AboutContent kmrContent;

    /**
     * Structured stats — array of {labelCkb, labelKmr, value}.
     * Stored as JSONB so the order and shape are preserved.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats", columnDefinition = "jsonb")
    @Builder.Default
    private List<StatItem> stats = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
