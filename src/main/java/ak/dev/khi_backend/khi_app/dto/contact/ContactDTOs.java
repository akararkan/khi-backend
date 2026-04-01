package ak.dev.khi_backend.khi_app.dto.contact;

import lombok.*;

/**
 * ContactDTOs — All request / response DTOs for the Contact module.
 *
 * Bilingual content mirrors the About module pattern:
 *   slugCkb    → Sorani   (CKB) URL slug  — required, unique
 *   slugKmr    → Kurmanji (KMR) URL slug  — optional, unique
 *   ckbContent → Sorani   (CKB) language fields (title, subtitle, address, workingHours)
 *   kmrContent → Kurmanji (KMR) language fields (title, subtitle, address, workingHours)
 *
 * Contact-specific fields (language-agnostic):
 *   phone, secondaryPhone, email, mapEmbedUrl, latitude, longitude
 */
public class ContactDTOs {

    // =========================================================================
    // CONTACT PAGE — REQUEST
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContactRequest {

        /** Sorani (CKB) URL slug — required, unique. */
        private String slugCkb;

        /** Kurmanji (KMR) URL slug — optional, unique. */
        private String slugKmr;

        /** Full-bleed hero / banner image URL. */
        private String heroImageUrl;

        /** Sorani (CKB) page-level text content. */
        private ContactContentRequest ckbContent;

        /** Kurmanji (KMR) page-level text content. */
        private ContactContentRequest kmrContent;

        // ─── Contact Details ─────────────────────────────────────────────────

        /** Primary phone number — e.g. "+964 770 123 4567" */
        private String phone;

        /** Secondary / additional phone number */
        private String secondaryPhone;

        /** Primary contact email */
        private String email;

        /**
         * Google Maps embed URL or any iframe-compatible map URL.
         */
        private String mapEmbedUrl;

        /** Latitude for map marker */
        private Double latitude;

        /** Longitude for map marker */
        private Double longitude;
    }

    // ─── Bilingual page-level text ────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContactContentRequest {
        private String title;
        private String subtitle;
        private String address;
        private String workingHours;
    }

    // =========================================================================
    // CONTACT PAGE — RESPONSE
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContactResponse {
        private Long   id;
        private String slugCkb;
        private String slugKmr;
        private String heroImageUrl;

        private ContactContentResponse ckbContent;
        private ContactContentResponse kmrContent;

        private String phone;
        private String secondaryPhone;
        private String email;
        private String mapEmbedUrl;
        private Double latitude;
        private Double longitude;

        private boolean active;
        private String  createdAt;
        private String  updatedAt;
    }

    // ─── Bilingual page-level text ────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContactContentResponse {
        private String title;
        private String subtitle;
        private String address;
        private String workingHours;
    }

    // =========================================================================
    // MEDIA UPLOAD — RESPONSE  (reused from About pattern)
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadResponse {
        private String fileUrl;
        private String fileName;
        private Long   fileSize;
        private String contentType;
    }
}