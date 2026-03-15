package ak.dev.khi_backend.khi_app.dto.about;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * AboutDTOs — All request / response DTOs for the About module.
 *
 * Bilingual content mirrors the Video module pattern:
 *   slugCkb    → Sorani   (CKB) URL slug  — required, unique
 *   slugKmr    → Kurmanji (KMR) URL slug  — optional, unique
 *   ckbContent → Sorani   (CKB) language fields
 *   kmrContent → Kurmanji (KMR) language fields
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

        /** Sorani (CKB) page-level text content. */
        private AboutContentRequest ckbContent;

        /** Kurmanji (KMR) page-level text content. */
        private AboutContentRequest kmrContent;

        /** Ordered list of rich-content blocks. */
        private List<AboutBlockRequest> blocks;
        private String heroImageUrl;
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
    }

    // =========================================================================
    // ABOUT BLOCK — REQUEST
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutBlockRequest {

        /**
         * Block type: TEXT | IMAGE | VIDEO | AUDIO | GALLERY | QUOTE | STATS
         * Matched case-insensitively in the service.
         */
        private String contentType;

        /** Sorani (CKB) block text content. */
        private AboutBlockContentRequest ckbContent;

        /** Kurmanji (KMR) block text content. */
        private AboutBlockContentRequest kmrContent;

        /** S3 / CDN media URL (language-agnostic — same file for both locales). */
        private String mediaUrl;

        /** Thumbnail URL for VIDEO / IMAGE blocks. */
        private String thumbnailUrl;

        /**
         * Flexible metadata bag:
         *   IMAGE/GALLERY → width, height
         *   VIDEO/AUDIO   → duration, format, fileSizeMb
         *   STATS         → statItems array
         */
        private Map<String, Object> metadata;
    }

    // ─── Bilingual block text ─────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutBlockContentRequest {
        private String contentText;
        private String title;
        private String altText;
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

        /** Sorani (CKB) URL slug. */
        private String slugCkb;

        /** Kurmanji (KMR) URL slug — may be null. */
        private String slugKmr;

        /** Sorani (CKB) page-level text content. */
        private AboutContentResponse ckbContent;

        /** Kurmanji (KMR) page-level text content. */
        private AboutContentResponse kmrContent;

        private String heroImageUrl;

        private boolean active;
        private List<AboutBlockResponse> blocks;
        private String createdAt;
        private String updatedAt;
    }

    // ─── Bilingual page-level text ────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutContentResponse {
        private String title;
        private String subtitle;
        private String metaDescription;
    }

    // =========================================================================
    // ABOUT BLOCK — RESPONSE
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutBlockResponse {
        private Long id;
        private String contentType;
        private Integer sequence;

        /** Sorani (CKB) block text content. */
        private AboutBlockContentResponse ckbContent;

        /** Kurmanji (KMR) block text content. */
        private AboutBlockContentResponse kmrContent;

        private String mediaUrl;
        private String thumbnailUrl;
        private Map<String, Object> metadata;
    }

    // ─── Bilingual block text ─────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AboutBlockContentResponse {
        private String contentText;
        private String title;
        private String altText;
    }

    // =========================================================================
    // MEDIA UPLOAD — RESPONSE
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