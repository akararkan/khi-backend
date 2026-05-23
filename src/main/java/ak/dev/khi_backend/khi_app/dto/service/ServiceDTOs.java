package ak.dev.khi_backend.khi_app.dto.service;

import ak.dev.khi_backend.khi_app.enums.MediaKind;
import lombok.*;

import java.util.List;

/**
 * ServiceDTOs — All request / response DTOs for the Service module.
 *
 * ─── Bilingual Design ────────────────────────────────────────────────────────
 *  Service-level text uses a list of {@link ServiceContentRequest} each carrying
 *  a languageCode.  Media file text uses embedded {@link FileContentRequest}
 *  objects (ckbContent / kmrContent), following the same @AttributeOverrides
 *  pattern used by the About module.
 *
 * ─── Technical Metadata — What the CLIENT Sends vs What the System Extracts ──
 *
 *  CLIENT sends (for each file):
 *    ✅ fileUrl       — S3 URL returned by POST /upload
 *    ✅ thumbnailUrl  — optional, admin-chosen poster image
 *    ✅ ckbContent    — bilingual caption / title / description (CKB)
 *    ✅ kmrContent    — bilingual caption / title / description (KMR)
 *    ✅ sortOrder     — display position
 *
 *  SYSTEM auto-extracts and stores (never typed by the user):
 *    🔒 fileSize        — from MultipartFile.getSize()
 *    🔒 fileFormat      — "JPEG" / "MP4" / "MP3" … from ImageIO / ffprobe
 *    🔒 widthPx         — image / video frame width
 *    🔒 heightPx        — image / video frame height
 *    🔒 durationSeconds — video / audio playback length
 *    🔒 codec           — "H.264" / "AAC" / "MP3" … from ffprobe
 *    🔒 bitrateKbps     — from ffprobe
 *
 *  The extracted values are returned in {@link UploadResponse} so the admin
 *  panel can display them as read-only metadata immediately after upload.
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
         * Hero / cover media URL already uploaded to S3.
         * Optional — shown on the listing card.
         */
        private String coverMediaUrl;
        /** Type of {@link #coverMediaUrl} — IMAGE | VIDEO | AUDIO. Defaults to IMAGE. */
        private MediaKind coverMediaType;
        /** Optional poster (VIDEO) or cover art (AUDIO) URL for the cover. */
        private String coverThumbnailUrl;
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
        /**
         * Ordered media collections. Each collection groups files of one type.
         * Files referenced here must already be uploaded to S3.
         */
        private List<ServiceMediaCollectionRequest> mediaCollections;
    }

    // ─── Bilingual Service Content ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceContentRequest {
        /** "CKB" (Sorani) or "KMR" (Kurmanji). */
        private String languageCode;
        private String title;
        private String description;
    }

    // ─── Media Collection ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceMediaCollectionRequest {
        /** e.g. "Event Photos", "Recap Videos", "Interviews" */
        private String collectionName;
        /** "IMAGE" | "VIDEO" | "AUDIO" */
        private String mediaType;
        private Integer sortOrder;
        private List<ServiceMediaFileRequest> files;
    }

    // ─── Media File Request ───────────────────────────────────────────────────

    /**
     * What the admin sends when attaching a file to a collection.
     *
     * Technical metadata (format, resolution, duration, codec…) is NOT included
     * here — the system extracts and stores those automatically at upload time.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceMediaFileRequest {
        /** S3 / CDN URL returned by POST /api/v1/services/upload. Required. */
        private String fileUrl;
        /** Optional poster image / cover art URL. */
        private String thumbnailUrl;
        /** Sorani (CKB) caption, title, description for this file. */
        private FileContentRequest ckbContent;
        /** Kurmanji (KMR) caption, title, description for this file. */
        private FileContentRequest kmrContent;
        /** Display position within the collection. */
        private Integer sortOrder;
    }

    // ─── Bilingual File Text ──────────────────────────────────────────────────

    /**
     * Language-specific text accompanying a single media file.
     * All fields are optional — a file may have no text at all.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FileContentRequest {
        /** Short one-liner shown directly beneath the media in the gallery UI. */
        private String caption;
        /** Formal title used in lightbox headers and screen-reader alt text. */
        private String title;
        /** Extended description shown in an expanded / detail view. */
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
        private String coverMediaUrl;
        private MediaKind coverMediaType;
        private String coverThumbnailUrl;
        private boolean active;
        private String publishedAt;
        private List<ServiceContentResponse> contents;
        private List<ServiceMediaCollectionResponse> mediaCollections;
        private String createdAt;
        private String updatedAt;
    }

    // ─── Bilingual Service Content ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceContentResponse {
        private Long id;
        private String languageCode;
        private String title;
        private String description;
    }

    // ─── Media Collection ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceMediaCollectionResponse {
        private Long id;
        private String collectionName;
        private String mediaType;
        private Integer sortOrder;
        private String createdAt;
        private List<ServiceMediaFileResponse> files;
    }

    // ─── Media File Response ──────────────────────────────────────────────────

    /**
     * Full file response — bilingual text AND all auto-extracted technical
     * metadata so the admin panel can display everything at once.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceMediaFileResponse {

        private Long id;

        // ── Storage ────────────────────────────────────────────────────────────
        private String fileUrl;
        private String thumbnailUrl;

        // ── Bilingual text (user-provided) ────────────────────────────────────
        private FileContentResponse ckbContent;
        private FileContentResponse kmrContent;

        // ── Auto-extracted technical metadata ─────────────────────────────────
        /** "JPEG", "PNG", "MP4", "MOV", "MP3", "WAV", "AAC", "FLAC" … */
        private String fileFormat;
        /** Width in pixels — IMAGE / VIDEO only. */
        private Integer widthPx;
        /** Height in pixels — IMAGE / VIDEO only. */
        private Integer heightPx;
        /** Convenience string: "1920 × 1080". Null when not applicable. */
        private String resolution;
        /** Playback duration in whole seconds — VIDEO / AUDIO only. */
        private Integer durationSeconds;
        /** Human-readable: "2:05" or "1:01:01". */
        private String formattedDuration;
        /** "H.264", "H.265", "AAC", "MP3", "FLAC" … */
        private String codec;
        /** Average bitrate in kbps — VIDEO / AUDIO only. */
        private Integer bitrateKbps;
        /** Raw file size in bytes. */
        private Long fileSize;
        /** Human-readable: "4.5 MB", "12.3 KB". */
        private String formattedFileSize;

        private Integer sortOrder;
        private String createdAt;
    }

    // ─── Bilingual File Text Response ─────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FileContentResponse {
        private String caption;
        private String title;
        private String description;
    }

    // =========================================================================
    // MEDIA UPLOAD — RESPONSE
    // =========================================================================

    /**
     * Returned by POST /api/v1/services/upload.
     *
     * Contains the S3 URL to embed in {@link ServiceMediaFileRequest#getFileUrl()}
     * AND all auto-extracted technical metadata — the admin panel displays these
     * as read-only fields immediately after the upload completes.
     * The user never had to type any of the technical values.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UploadResponse {

        // ── Storage ────────────────────────────────────────────────────────────
        /** Public S3 / CDN URL — use this as fileUrl when creating a service. */
        private String fileUrl;
        private String fileName;

        // ── Auto-extracted ─────────────────────────────────────────────────────
        /** Raw byte count. */
        private Long fileSize;
        /** Human-readable: "4.5 MB". */
        private String formattedFileSize;
        /** Browser-reported MIME type, e.g. "image/jpeg". */
        private String contentType;
        /** Normalised format: "JPEG", "MP4", "MP3" … */
        private String fileFormat;
        /** Width in pixels — IMAGE / VIDEO only. Null otherwise. */
        private Integer widthPx;
        /** Height in pixels — IMAGE / VIDEO only. Null otherwise. */
        private Integer heightPx;
        /** Convenience string: "1920 × 1080". Null when not applicable. */
        private String resolution;
        /** Duration in whole seconds — VIDEO / AUDIO only. */
        private Integer durationSeconds;
        /** Human-readable: "2:05". */
        private String formattedDuration;
        /** Codec name — VIDEO / AUDIO only. */
        private String codec;
        /** Average bitrate in kbps — VIDEO / AUDIO only. */
        private Integer bitrateKbps;
    }

    // =========================================================================
    // COLLECTION / FILE — STANDALONE REQUESTS
    // =========================================================================

    /** Add or update a collection without touching the parent service payload. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CollectionUpsertRequest {
        private String collectionName;
        /** "IMAGE" | "VIDEO" | "AUDIO" */
        private String mediaType;
        private Integer sortOrder;
    }

    /**
     * Add a single pre-uploaded file to an existing collection.
     * Same minimal shape as {@link ServiceMediaFileRequest}.
     * Technical metadata is auto-looked-up from S3 metadata at save time.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FileAddRequest {
        /** S3 URL from the upload response. Required. */
        private String fileUrl;
        /** Optional poster / cover art. */
        private String thumbnailUrl;
        /** Sorani (CKB) caption, title, description. */
        private FileContentRequest ckbContent;
        /** Kurmanji (KMR) caption, title, description. */
        private FileContentRequest kmrContent;
        private Integer sortOrder;
    }
}