package ak.dev.khi_backend.khi_app.model.about;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * About — About-page entity.
 *
 * ─── Bilingual Slugs ──────────────────────────────────────────────────────────
 *  Each language has its own unique URL slug:
 *    slugCkb  → Sorani   route identifier, e.g. "دەربارەی-ئێمە"  or "about-ckb"
 *    slugKmr  → Kurmanji route identifier, e.g. "derbare-me"      or "about-kmr"
 *
 *  Both are unique across the table.  slugKmr is nullable — a page may be
 *  published in Sorani only.
 *
 * ─── Bilingual Content ────────────────────────────────────────────────────────
 *  Follows the same pattern as the Video entity:
 *    ckbContent  → Sorani  (CKB) version of title / subtitle / meta
 *    kmrContent  → Kurmanji (KMR) version of title / subtitle / meta
 *
 * ─── Blocks ───────────────────────────────────────────────────────────────────
 *  Rich content sections — each {@link AboutBlock} carries its own CKB / KMR
 *  text via {@link AboutBlockContent} embeddables.
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

    // ─── Bilingual Slugs ──────────────────────────────────────────────────────

    /**
     * Sorani (CKB) URL slug — unique, required.
     * e.g. "derbare-ckb", "stanford-derbare"
     */
    @NotBlank
    @Column(name = "slug_ckb", unique = true, nullable = false, length = 200)
    private String slugCkb;

    /**
     * Kurmanji (KMR) URL slug — unique, optional.
     * e.g. "derbare-kmr", "stanford-about"
     * Null when the page has no Kurmanji version yet.
     */
    @Column(name = "slug_kmr", unique = true, nullable = true, length = 200)
    private String slugKmr;

    private boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    // ─── CKB (Sorani) Content ─────────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",           column = @Column(name = "title_ckb",            length = 300)),
            @AttributeOverride(name = "subtitle",        column = @Column(name = "subtitle_ckb",         length = 500)),
            @AttributeOverride(name = "metaDescription", column = @Column(name = "meta_description_ckb", length = 2500))
    })
    private AboutContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",           column = @Column(name = "title_kmr",            length = 300)),
            @AttributeOverride(name = "subtitle",        column = @Column(name = "subtitle_kmr",         length = 500)),
            @AttributeOverride(name = "metaDescription", column = @Column(name = "meta_description_kmr", length = 2500))
    })
    private AboutContent kmrContent;

    // ─── Blocks ───────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "about", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<AboutBlock> blocks = new ArrayList<>();

    // ─── Timestamps ───────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Helper Methods ───────────────────────────────────────────────────────

    public void addBlock(AboutBlock block) {
        blocks.add(block);
        block.setAbout(this);
    }

    public void removeBlock(AboutBlock block) {
        blocks.remove(block);
        block.setAbout(null);
    }
}