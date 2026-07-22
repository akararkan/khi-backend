package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * ServiceMedia — one ordered gallery slot on a {@link Service}.
 *
 * ─── Purpose ──────────────────────────────────────────────────────────────────
 *  Backs the {@code galleryMedia[]} array on the public Services page.  Each
 *  record renders one scroll section whose gallery is an ORDERED list of slots.
 *  Every slot is independently an {@code IMAGE} or a {@code VIDEO}, so any
 *  position may be a video and multiple videos are allowed.
 *
 *  This is the RECOMMENDED gallery model.  The legacy {@code featureImageUrls}
 *  / {@code thumbnailUrls} string lists remain on {@link Service} only as a
 *  fallback for records created before this field existed.
 *
 * ─── Persistence ──────────────────────────────────────────────────────────────
 *  Mapped as an {@code @ElementCollection} on {@link Service} with an
 *  {@code @OrderColumn}, so list order is preserved exactly as sent by the CMS.
 *  URLs already point at S3 — the CMS uploads each file once via
 *  {@code POST /api/v1/media/upload} and stores the returned URL here.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMedia {

    /** Slot kind — {@code "IMAGE"} or {@code "VIDEO"}. */
    @Column(name = "media_type", length = 10)
    private String type;

    /** Image URL, or video file URL. Required when a slot is present. */
    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    /** Poster/thumbnail frame — recommended for {@code VIDEO} slots. */
    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    /** Optional alt / accessibility text. */
    @Column(name = "alt", length = 500)
    private String alt;
}
