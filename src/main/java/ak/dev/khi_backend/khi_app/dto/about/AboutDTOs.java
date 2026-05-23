package ak.dev.khi_backend.khi_app.dto.about;

import lombok.*;

import java.util.List;

/**
 * AboutDTOs — Request / response DTOs for the About module.
 *
 * About no longer carries any standalone hero or gallery field.  All
 * visual media (image, video, voice, document, or any other file) lives
 * inside the bilingual Tiptap {@code body} HTML and is hoisted to S3 by
 * {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor} at
 * save time.
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

        /** Sorani (CKB) URL slug — required, unique. */
        private String slugCkb;

        /** Kurmanji (KMR) URL slug — optional, unique. */
        private String slugKmr;

        /** Sorani (CKB) page-level content (title / subtitle / meta / Tiptap body). */
        private AboutContentRequest ckbContent;

        /** Kurmanji (KMR) page-level content (title / subtitle / meta / Tiptap body). */
        private AboutContentRequest kmrContent;

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
        /**
         * Tiptap HTML body — all media (image / video / voice / file) is
         * embedded inline here and rewritten to S3 URLs on save.
         */
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
        private String labelCkb;
        private String labelKmr;
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
}
