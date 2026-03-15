package ak.dev.khi_backend.khi_app.dto.publishment.writing;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.BookGenre;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingFileFormat;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

public final class WritingDtos {

    private WritingDtos() {}

    // =========================================================================
    // LANGUAGE CONTENT DTO
    // =========================================================================

    /**
     * Language-specific fields for one bilingual block (CKB or KMR).
     * Cover URLs are NOT here — they live as top-level fields on
     * {@link CreateRequest} / {@link UpdateRequest} / {@link Response}
     * mirroring the 3-slot entity design (ckbCoverUrl, kmrCoverUrl, hoverCoverUrl).
     */
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

        /** Book file URL (PDF, DOCX, etc.) for this language. */
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

    // =========================================================================
    // BILINGUAL SET DTO
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BilingualSet {
        private Set<String> ckb;
        private Set<String> kmr;
    }

    // =========================================================================
    // TOPIC DTOS  (inline create / response projection)
    // =========================================================================

    /**
     * Minimal topic payload used when creating a brand-new topic inline
     * during writing create / update.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TopicPayload {
        @Size(max = 300)
        private String nameCkb;

        @Size(max = 300)
        private String nameKmr;
    }

    /**
     * Topic projection returned inside {@link Response}.
     * Carries enough data to display the topic without a separate API call.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TopicInfo {
        private Long   id;
        private String nameCkb;
        private String nameKmr;
    }

    // =========================================================================
    // SERIES DTOS
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesInfoDto {
        private String  seriesId;
        private String  seriesName;
        private Double  seriesOrder;
        private Long    parentBookId;
        private Integer totalBooks;
        private boolean isParent;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesBookSummary {
        private Long          id;
        private String        titleCkb;
        private String        titleKmr;
        private Double        seriesOrder;
        private LocalDateTime createdAt;
    }

    // =========================================================================
    // CREATE REQUEST
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        /** At least one language is required. */
        @NotNull
        @NotEmpty(message = "At least one content language is required")
        private Set<Language> contentLanguages;

        // ─── Cover Images (3 slots) ───────────────────────────────────────────

        /**
         * Sorani (CKB) cover URL.
         * If a file is uploaded via multipart, the service will override this.
         */
        @Size(max = 2000)
        private String ckbCoverUrl;

        /**
         * Kurmanji (KMR) cover URL.
         * Overridden by multipart upload when provided.
         */
        @Size(max = 2000)
        private String kmrCoverUrl;

        /**
         * Hover overlay URL.
         * Overridden by multipart upload when provided.
         */
        @Size(max = 2000)
        private String hoverCoverUrl;

        // ─── Language Content ─────────────────────────────────────────────────

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        // ─── Topic ────────────────────────────────────────────────────────────

        /**
         * Link to an existing PublishmentTopic by ID.
         * Mutually exclusive with {@link #newTopic}.
         */
        private Long topicId;

        /**
         * Create a brand-new topic inline and link it to this writing.
         * If both {@code topicId} and {@code newTopic} are provided,
         * {@code topicId} takes precedence.
         */
        private TopicPayload newTopic;

        // ─── Book Genre ───────────────────────────────────────────────────────

        /**
         * Primary genre / category of this book (e.g. NOVEL, POETRY, HISTORY).
         * Replaces the old {@code writingTopic} field — maps to column {@code book_genre}.
         */
        @NotNull(message = "Book genre is required")
        private BookGenre bookGenre;

        // ─── Shared ───────────────────────────────────────────────────────────

        private boolean publishedByInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        // ─── Series ───────────────────────────────────────────────────────────

        @Size(max = 100)
        private String seriesId;

        @Size(max = 300)
        private String seriesName;

        @Min(0)
        private Double seriesOrder;

        private Long parentBookId;
    }

    // =========================================================================
    // UPDATE REQUEST
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        private Set<Language> contentLanguages;

        // ─── Cover Images (3 slots) ───────────────────────────────────────────

        /**
         * Pass a new URL to replace the current CKB cover without uploading a file.
         * Overridden by multipart upload when provided.
         * Pass an empty string {@code ""} to explicitly clear the cover.
         */
        private String ckbCoverUrl;

        /** Same as {@link #ckbCoverUrl} but for the KMR slot. */
        private String kmrCoverUrl;

        /** Same as {@link #ckbCoverUrl} but for the hover slot. */
        private String hoverCoverUrl;

        // ─── Language Content ─────────────────────────────────────────────────

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        // ─── Topic ────────────────────────────────────────────────────────────

        /**
         * Replace the current topic with an existing one by ID.
         * Use {@link #clearTopic} to detach the topic entirely.
         */
        private Long topicId;

        /**
         * Create a brand-new topic inline and assign it to this writing.
         * Ignored when {@link #topicId} is also provided.
         */
        private TopicPayload newTopic;

        /**
         * When {@code true}, detaches the current topic without assigning a new one.
         * Takes precedence over {@link #topicId} and {@link #newTopic}.
         */
        private Boolean clearTopic;

        // ─── Book Genre ───────────────────────────────────────────────────────

        /**
         * Update the book's genre/category.
         * Nullable — omit to leave the current value unchanged.
         * Replaces the old {@code writingTopic} field.
         */
        private BookGenre bookGenre;

        // ─── Shared ───────────────────────────────────────────────────────────

        private Boolean publishedByInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        // ─── Series ───────────────────────────────────────────────────────────

        @Size(max = 300)
        private String seriesName;

        @Min(0)
        private Double seriesOrder;

        private Long parentBookId;
    }

    // =========================================================================
    // RESPONSE
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {

        private Long id;

        private Set<Language> contentLanguages;

        // ─── Cover Images (3 slots) ───────────────────────────────────────────

        /** Sorani cover URL — null if not set. */
        private String ckbCoverUrl;

        /** Kurmanji cover URL — null if not set. */
        private String kmrCoverUrl;

        /** Hover overlay URL — null if not set. */
        private String hoverCoverUrl;

        // ─── Language Content ─────────────────────────────────────────────────

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        // ─── Topic ────────────────────────────────────────────────────────────

        /**
         * The assigned topic (id + bilingual names).
         * Null when no topic is linked.
         */
        private TopicInfo topic;

        // ─── Book Genre ───────────────────────────────────────────────────────

        /**
         * Genre / category of this book.
         * Replaces the old {@code writingTopic} field.
         */
        private BookGenre bookGenre;

        // ─── Shared ───────────────────────────────────────────────────────────

        private boolean publishedByInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        // ─── Series ───────────────────────────────────────────────────────────

        private SeriesInfoDto seriesInfo;

        // ─── Timestamps ───────────────────────────────────────────────────────

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // =========================================================================
    // SERIES RESPONSE
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SeriesResponse {
        private String              seriesId;
        private String              seriesName;
        private Integer             totalBooks;
        private List<SeriesBookSummary> books;
    }

    // =========================================================================
    // LINK TO SERIES REQUEST
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LinkToSeriesRequest {

        @NotNull(message = "Book ID is required")
        private Long bookId;

        @NotNull(message = "Parent book ID is required")
        private Long parentBookId;

        @NotNull(message = "Series order is required")
        @Min(1)
        private Double seriesOrder;

        @Size(max = 300)
        private String seriesName;
    }

    // =========================================================================
    // SEARCH / FILTER REQUEST
    // =========================================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SearchRequest {
        /** Filter by book genre (was: topic). */
        private BookGenre bookGenre;
        private Boolean   instituteOnly;
        private String    writer;
        private String    language;
        private String    seriesId;
        private Boolean   seriesParentsOnly;
    }
}