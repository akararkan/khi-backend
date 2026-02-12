package ak.dev.khi_backend.khi_app.dto.publishment.image;

import ak.dev.khi_backend.khi_app.enums.Language;
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
    // IMAGE ITEM DTO (UPDATED ✅)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ImageItemDto {
        private Long id;

        /**
         * ✅ One of these is required:
         * - imageUrl (S3)
         * - externalUrl
         * - embedUrl
         */
        private String imageUrl;
        private String externalUrl;
        private String embedUrl;

        private String descriptionCkb;
        private String descriptionKmr;

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
         * ✅ Optional now:
         * - can be empty
         * - each item can be file OR link OR embed
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

        private String coverUrl;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private LocalDate publishmentDate;

        private BilingualSet tags;
        private BilingualSet keywords;

        /**
         * If provided => replace all items (service will clear old + rebuild)
         * If null => keep old
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
