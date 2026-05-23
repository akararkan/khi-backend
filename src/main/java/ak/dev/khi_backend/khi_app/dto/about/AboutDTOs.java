package ak.dev.khi_backend.khi_app.dto.about;

import ak.dev.khi_backend.khi_app.enums.MediaKind;
import ak.dev.khi_backend.khi_app.model.media.MediaItem;
import lombok.*;

import java.util.List;

/**
 * AboutDTOs — All request / response DTOs for the About module (Tiptap migration).
 *
 * Bilingual content mirrors the Video module pattern:
 *   slugCkb    → Sorani   (CKB) URL slug  — required, unique
 *   slugKmr    → Kurmanji (KMR) URL slug  — optional, unique
 *   ckbContent → Sorani   (CKB) language fields (incl. Tiptap body)
 *   kmrContent → Kurmanji (KMR) language fields (incl. Tiptap body)
 *
 * Blocks are gone; the rich content is now a Tiptap HTML string per language.
 * The structured STATS data survives as a top-level array.
 */
public class AboutDTOs {

    // =========================================================================
    // ABOUT PAGE — REQUEST
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutRequest {

        /** Sorani (CKB) URL slug — required, unique. e.g. "derbare-ckb" */
        private String slugCkb;

        /** Kurmanji (KMR) URL slug — optional, unique. e.g. "derbare-kmr" */
        private String slugKmr;

        /** Sorani (CKB) page-level content (title / subtitle / meta / body). */
        private AboutContentRequest ckbContent;

        /** Kurmanji (KMR) page-level content (title / subtitle / meta / body). */
        private AboutContentRequest kmrContent;

        /** Optional full-bleed hero / banner asset URL (image, video, or audio). */
        private String heroImageUrl;

        /** Type of {@link #heroImageUrl} — IMAGE | VIDEO | AUDIO. Defaults to IMAGE. */
        private MediaKind heroMediaType;

        /** Optional poster (VIDEO) or cover art (AUDIO) URL for the hero. */
        private String heroThumbnailUrl;

        /** Mixed-type gallery rendered beside the hero — images, videos, audios. */
        private List<MediaItem> mediaGallery;

        /** Structured stats array. */
        private List<StatItemDto> stats;
    }

    // ─── Bilingual page-level text ────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutContentRequest {
        private String title;
        private String subtitle;
        private String metaDescription;
        /** Tiptap HTML body produced by the editor. */
        private String body;
    }

    // =========================================================================
    // STATS — shared by request and response
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatItemDto {
        /** Sorani (CKB) label, e.g. "کتێب" */
        private String labelCkb;
        /** Kurmanji (KMR) label, e.g. "Pirtûk" */
        private String labelKmr;
        /** Display value, e.g. "5,000+" */
        private String value;
    }

    // =========================================================================
    // ABOUT PAGE — RESPONSE
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutResponse {
        private Long id;

        private String slugCkb;
        private String slugKmr;

        private AboutContentResponse ckbContent;
        private AboutContentResponse kmrContent;

        private String heroImageUrl;
        private MediaKind heroMediaType;
        private String heroThumbnailUrl;
        private List<MediaItem> mediaGallery;

        private boolean active;
        private List<StatItemDto> stats;

        private String createdAt;
        private String updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutContentResponse {
        private String title;
        private String subtitle;
        private String metaDescription;
        /** Tiptap HTML body. */
        private String body;
    }

    // =========================================================================
    // MEDIA UPLOAD — RESPONSE (kept for legacy callers of /about/upload)
    //
    // New code should call the shared /api/v1/media/upload endpoint instead.
    // The about-scoped upload endpoints have been removed in favor of the
    // shared endpoint.
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadResponse {
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String contentType;
    }
}
