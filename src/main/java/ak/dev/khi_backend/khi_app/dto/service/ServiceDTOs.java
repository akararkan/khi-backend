package ak.dev.khi_backend.khi_app.dto.service;

import lombok.*;

import java.util.List;

/**
 * ServiceDTOs — Request / response DTOs for the Service module.
 *
 * Service-level text is a list of {@link ServiceContentRequest} each carrying
 * a languageCode and a Tiptap HTML {@code description}.  All visual media
 * (image, video, voice, document, or any other file) is embedded inline in
 * that description as {@code <img>}, {@code <video>}, {@code <audio>}, or
 * {@code <a href>} tags whose URLs already point at S3.  The
 * {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor}
 * intercepts every save and hoists any inline base64 payloads to S3.
 *
 * Service no longer carries a separate cover, hero, gallery, or per-file
 * media metadata model.
 */
public class ServiceDTOs {

    // =========================================================================
    // SERVICE — REQUEST
    // =========================================================================

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceRequest {
        /** Dynamic service type. Examples: "Training", "Event", "Program", "Workshop" */
        private String serviceType;
        /** Physical or virtual location. Null when not applicable. */
        private String location;
        /**
         * Explicit publish timestamp.
         * Format: "yyyy-MM-dd HH:mm:ss"   Null = draft / unpublished.
         */
        private String publishedAt;
        /**
         * Bilingual content list.
         * Each entry must have a languageCode ("CKB" | "KMR") and a title.
         */
        private List<ServiceContentRequest> contents;
    }

    // ─── Bilingual Service Content ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceContentRequest {
        /** "CKB" (Sorani) or "KMR" (Kurmanji). */
        private String languageCode;
        private String title;
        /**
         * Tiptap HTML description — all media (image / video / voice / file)
         * is embedded inline here and rewritten to S3 URLs on save.
         */
        private String description;
    }

    // =========================================================================
    // SERVICE — RESPONSE
    // =========================================================================

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceResponse {
        private Long id;
        private String serviceType;
        private String location;
        private boolean active;
        private String publishedAt;
        private List<ServiceContentResponse> contents;
        private String createdAt;
        private String updatedAt;
    }

    // ─── Bilingual Service Content ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceContentResponse {
        private Long id;
        private String languageCode;
        private String title;
        /** Tiptap HTML description. */
        private String description;
    }
}
