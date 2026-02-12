package ak.dev.khi_backend.khi_app.dto.publishment.writing;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingFileFormat;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
public final class WritingDtos {

    private WritingDtos() {}

    // =========================
    // LANGUAGE CONTENT DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {

        @Size(max = 300)
        private String title;

        @Size(max = 10000)
        private String description;

        @Size(max = 200)
        private String writer;

        /**
         * Cover page URL (optional)
         */
        @Size(max = 1000)
        private String coverUrl;

        /**
         * Book file URL (PDF, DOCX, etc.)
         */
        @Size(max = 1000)
        private String fileUrl;

        private WritingFileFormat fileFormat;

        @Min(0)
        private Long fileSizeBytes;

        @Min(1)
        private Integer pageCount;

        @Size(max = 150)
        private String genre;
    }

    // =========================
    // BILINGUAL SET DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BilingualSet {
        private Set<String> ckb;
        private Set<String> kmr;
    }

    // =========================
    // CREATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        /**
         * Which languages are active
         * Must have at least one language
         */
        @NotNull
        @NotEmpty(message = "At least one content language is required")
        private Set<Language> contentLanguages;

        /**
         * ✅ Sorani (CKB) Content
         */
        private LanguageContentDto ckbContent;

        /**
         * ✅ Kurmanji (KMR) Content
         */
        private LanguageContentDto kmrContent;

        /**
         * Writing topic/category
         */
        @NotNull(message = "Writing topic is required")
        private WritingTopic writingTopic;

        /**
         * Is this published by the institute?
         */
        private boolean publishedByInstitute;

        /**
         * ✅ Bilingual tags
         */
        private BilingualSet tags;

        /**
         * ✅ Bilingual keywords
         */
        private BilingualSet keywords;
    }

    // =========================
    // UPDATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        /**
         * Which languages are active (optional for update)
         */
        private Set<Language> contentLanguages;

        /**
         * ✅ Sorani (CKB) Content
         */
        private LanguageContentDto ckbContent;

        /**
         * ✅ Kurmanji (KMR) Content
         */
        private LanguageContentDto kmrContent;

        /**
         * Writing topic/category
         */
        private WritingTopic writingTopic;

        /**
         * Is this published by the institute?
         */
        private Boolean publishedByInstitute;

        /**
         * ✅ Bilingual tags
         */
        private BilingualSet tags;

        /**
         * ✅ Bilingual keywords
         */
        private BilingualSet keywords;
    }

    // =========================
    // RESPONSE DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {

        private Long id;

        /**
         * Active languages for this writing
         */
        private Set<Language> contentLanguages;

        /**
         * ✅ Sorani (CKB) Content
         */
        private LanguageContentDto ckbContent;

        /**
         * ✅ Kurmanji (KMR) Content
         */
        private LanguageContentDto kmrContent;

        /**
         * Writing topic/category
         */
        private WritingTopic writingTopic;

        /**
         * Is this published by the institute?
         */
        private boolean publishedByInstitute;

        /**
         * ✅ Bilingual tags
         */
        private BilingualSet tags;

        /**
         * ✅ Bilingual keywords
         */
        private BilingualSet keywords;

        /**
         * Timestamps
         */
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // =========================
    // SEARCH/FILTER REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SearchRequest {

        /**
         * Search by writing topic
         */
        private WritingTopic topic;

        /**
         * Filter by institute publications only
         */
        private Boolean instituteOnly;

        /**
         * Search by writer name (searches both languages)
         */
        private String writer;

        /**
         * Search language (ckb, kmr, or null for both)
         */
        private String language;
    }
}