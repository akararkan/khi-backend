package ak.dev.khi_backend.khi_app.dto.publishment.image;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ImageCollectionDTO {

    private ImageCollectionDTO() {}

    // =========================
    // LANGUAGE CONTENT DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {
        @Size(max = 300)
        private String title;

        @Size(max = 8000)
        private String description;

        @Size(max = 300)
        private String topic;

        @Size(max = 250)
        private String location;

        @Size(max = 250)
        private String collectedBy;
    }

    // =========================
    // BILINGUAL SET (tags/keywords)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BilingualSet {
        private Set<String> ckb;
        private Set<String> kmr;
    }

    // =========================
    // IMAGE ITEM DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ImageItemDto {
        private Long id;

        /**
         * One of these is required:
         * - imageUrl (S3)
         * - externalUrl
         * - embedUrl
         */
        private String imageUrl;
        private String externalUrl;
        private String embedUrl;

        // Caption in CKB (short title for image)
        @Size(max = 500)
        private String captionCkb;

        // Caption in KMR (short title for image)
        @Size(max = 500)
        private String captionKmr;

        // Description in CKB (detailed description)
        private String descriptionCkb;

        // Description in KMR (detailed description)
        private String descriptionKmr;

        // Sort order
        private Integer sortOrder;
    }

    // =========================
    // REQUEST (CREATE)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        /**
         * Collection type - REQUIRED
         * SINGLE, GALLERY, or PHOTO_STORY
         */
        @NotNull
        private ImageCollectionType collectionType;

        /**
         * coverUrl is used if no cover file uploaded
         */
        private String coverUrl;

        @NotNull
        @NotEmpty
        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private LocalDate publishmentDate;

        private BilingualSet tags;
        private BilingualSet keywords;

        /**
         * Image album items:
         * - SINGLE: Must have exactly 1 item
         * - GALLERY: Can have multiple items
         * - PHOTO_STORY: Must have multiple items (2+)
         * Each item can be file OR imageUrl OR externalUrl OR embedUrl
         */
        private List<ImageItemDto> imageAlbum;
    }

    // =========================
    // REQUEST (UPDATE)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        /**
         * Collection type (can be changed during update)
         */
        private ImageCollectionType collectionType;

        private String coverUrl;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private LocalDate publishmentDate;

        private BilingualSet tags;
        private BilingualSet keywords;

        /**
         * If provided => replace all items (service will clear old + rebuild)
         * If null => keep old items
         */
        private List<ImageItemDto> imageAlbum;
    }

    // =========================
    // RESPONSE
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {

        private Long id;

        /**
         * Collection type: SINGLE, GALLERY, or PHOTO_STORY
         */
        private ImageCollectionType collectionType;

        private String coverUrl;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private LocalDate publishmentDate;

        private BilingualSet tags;
        private BilingualSet keywords;

        private List<ImageItemDto> imageAlbum;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // helper for empty sets
    public static Set<String> emptySet() {
        return new LinkedHashSet<>();
    }
}