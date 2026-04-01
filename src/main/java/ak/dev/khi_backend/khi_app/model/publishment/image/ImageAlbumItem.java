package ak.dev.khi_backend.khi_app.model.publishment.image;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "image_album_items", indexes = {
        @Index(name = "idx_album_item_collection_id", columnList = "image_collection_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageAlbumItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Uploaded S3 URL (optional)
     * Can use externalUrl or embedUrl instead
     */
    @Column(name = "image_url", nullable = true, columnDefinition = "TEXT")
    private String imageUrl;

    /**
     * External page link
     * Example: https://example.com/image.jpg
     */
    @Column(name = "external_url", columnDefinition = "TEXT")
    private String externalUrl;

    /**
     * Embed link (iframe-ready)
     * Example: https://some-cdn.com/embed/xxxx
     */
    @Column(name = "embed_url", columnDefinition = "TEXT")
    private String embedUrl;

    // ─── Caption in CKB (short title for the image) ───────────────────
    @Column(name = "caption_ckb", length = 500)
    private String captionCkb;

    // ─── Caption in KMR (short title for the image) ───────────────────
    @Column(name = "caption_kmr", length = 500)
    private String captionKmr;

    // ─── Description in CKB (detailed description of the image) ───────
    @Column(name = "description_ckb", columnDefinition = "TEXT")
    private String descriptionCkb;

    // ─── Description in KMR (detailed description of the image) ───────
    @Column(name = "description_kmr", columnDefinition = "TEXT")
    private String descriptionKmr;

    // Display order
    @Column(name = "sort_order")
    private Integer sortOrder;

    // ─── AUTO-EXTRACTED METADATA (New Fields) ─────────────────────────

    /** File size in bytes - auto detected during upload */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Image width in pixels - auto detected during upload */
    @Column(name = "width_px")
    private Integer widthPx;

    /** Image height in pixels - auto detected during upload */
    @Column(name = "height_px")
    private Integer heightPx;

    /** MIME type (image/jpeg, image/png, etc.) - auto detected */
    @Column(name = "mime_type", length = 50)
    private String mimeType;

    // ─── RELATIONSHIPS ─────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_collection_id", nullable = false)
    private ImageCollection imageCollection;

    // ─── TRANSIENT HELPERS (Calculated on the fly) ─────────────────────

    /**
     * Returns aspect ratio (width/height).
     * Useful for frontend to reserve correct space before image loads.
     */
    @Transient
    public Double getAspectRatio() {
        if (widthPx == null || heightPx == null || heightPx == 0) return null;
        return (double) widthPx / heightPx;
    }

    /**
     * Returns true if image is portrait orientation (taller than wide)
     */
    @Transient
    public Boolean isPortrait() {
        if (widthPx == null || heightPx == null) return null;
        return heightPx > widthPx;
    }

    /**
     * Returns true if image is landscape orientation (wider than tall)
     */
    @Transient
    public Boolean isLandscape() {
        if (widthPx == null || heightPx == null) return null;
        return widthPx > heightPx;
    }

    /**
     * Returns human-readable file size like "2.4 MB" or "850 KB"
     */
    @Transient
    public String getHumanReadableSize() {
        if (fileSizeBytes == null) return null;
        if (fileSizeBytes < 1024) return fileSizeBytes + " B";
        if (fileSizeBytes < 1024 * 1024) return String.format("%.1f KB", fileSizeBytes / 1024.0);
        if (fileSizeBytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0));
    }
}