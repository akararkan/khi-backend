package ak.dev.khi_backend.khi_app.model.publishment.image;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "image_album_items")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageAlbumItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ✅ Uploaded S3 URL (optional now)
     * Because you may use externalUrl / embedUrl instead.
     */
    @Column(name = "image_url", nullable = true, columnDefinition = "TEXT")
    private String imageUrl;

    /**
     * ✅ NEW: External page link
     * Example: https://example.com/image.jpg
     */
    @Column(name = "external_url", columnDefinition = "TEXT")
    private String externalUrl;

    /**
     * ✅ NEW: Embed link (iframe-ready)
     * Example: https://some-cdn.com/embed/xxxx
     */
    @Column(name = "embed_url", columnDefinition = "TEXT")
    private String embedUrl;

    // Optional description in CKB
    @Column(name = "description_ckb", columnDefinition = "TEXT")
    private String descriptionCkb;

    // Optional description in KMR
    @Column(name = "description_kmr", columnDefinition = "TEXT")
    private String descriptionKmr;

    // Display order
    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_collection_id", nullable = false)
    private ImageCollection imageCollection;
}
