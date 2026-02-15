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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_collection_id", nullable = false)
    private ImageCollection imageCollection;
}