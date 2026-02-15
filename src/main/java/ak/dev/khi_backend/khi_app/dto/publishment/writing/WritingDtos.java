package ak.dev.khi_backend.khi_app.dto.publishment.writing;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingFileFormat;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

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
         * Cover page URL (SEPARATE for each language)
         * CKB has its own cover, KMR has its own cover
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
    // ✅ NEW: SERIES INFO DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesInfoDto {
        /**
         * Series identifier
         */
        private String seriesId;

        /**
         * Series name (displayed to users)
         */
        private String seriesName;

        /**
         * Order in series
         */
        private Double seriesOrder;

        /**
         * Parent book ID (if this is part of a series)
         */
        private Long parentBookId;

        /**
         * Total books in series
         */
        private Integer totalBooks;

        /**
         * Is this the parent/first book?
         */
        private boolean isParent;
    }

    // =========================
    // ✅ NEW: SERIES BOOK SUMMARY
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesBookSummary {
        private Long id;
        private String titleCkb;
        private String titleKmr;
        private Double seriesOrder;
        private LocalDateTime createdAt;
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
         * ✅ Sorani (CKB) Content (with separate cover page)
         */
        private LanguageContentDto ckbContent;

        /**
         * ✅ Kurmanji (KMR) Content (with separate cover page)
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

        // ============================================================
        // ✅ NEW: SERIES FIELDS
        // ============================================================

        /**
         * Series identifier (optional for standalone books)
         * If null, system will generate one
         */
        @Size(max = 100)
        private String seriesId;

        /**
         * Series name (optional, defaults to book title)
         */
        @Size(max = 300)
        private String seriesName;

        /**
         * Order in series (defaults to 1.0)
         */
        @Min(0)
        private Double seriesOrder;

        /**
         * Parent book ID (if adding to existing series)
         */
        private Long parentBookId;
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

        // ============================================================
        // ✅ NEW: SERIES FIELDS (for updates)
        // ============================================================

        /**
         * Series name update
         */
        @Size(max = 300)
        private String seriesName;

        /**
         * Reorder within series
         */
        @Min(0)
        private Double seriesOrder;

        /**
         * Change parent book (move to different series)
         */
        private Long parentBookId;
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
         * ✅ Sorani (CKB) Content (with coverUrl for CKB cover page)
         */
        private LanguageContentDto ckbContent;

        /**
         * ✅ Kurmanji (KMR) Content (with coverUrl for KMR cover page)
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
         * ✅ NEW: Series information
         */
        private SeriesInfoDto seriesInfo;

        /**
         * Timestamps
         */
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // =========================
    // ✅ NEW: SERIES RESPONSE
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesResponse {
        private String seriesId;
        private String seriesName;
        private Integer totalBooks;
        private List<SeriesBookSummary> books;
    }

    // =========================
    // ✅ NEW: LINK TO SERIES REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LinkToSeriesRequest {
        /**
         * The book ID to link to an existing series
         */
        @NotNull(message = "Book ID is required")
        private Long bookId;

        /**
         * The parent book ID (first book in series)
         */
        @NotNull(message = "Parent book ID is required")
        private Long parentBookId;

        /**
         * Order in the series
         */
        @NotNull(message = "Series order is required")
        @Min(1)
        private Double seriesOrder;

        /**
         * Optional: Override series name
         */
        @Size(max = 300)
        private String seriesName;
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
         * ✅ ENHANCED: Search by writer name (searches both CKB & KMR)
         * Optimized with database indexes for O(log n) performance
         */
        private String writer;

        /**
         * Search language (ckb, kmr, or null for both)
         */
        private String language;

        /**
         * ✅ NEW: Filter by series
         */
        private String seriesId;

        /**
         * ✅ NEW: Show only series parent books
         */
        private Boolean seriesParentsOnly;
    }
}