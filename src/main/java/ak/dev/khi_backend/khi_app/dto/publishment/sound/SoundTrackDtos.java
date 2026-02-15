package ak.dev.khi_backend.khi_app.dto.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.SoundType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class SoundTrackDtos {

    private SoundTrackDtos() {}

    // =========================
    // LANGUAGE CONTENT DTO
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {
        @Size(max = 200)
        private String title;

        @Size(max = 4000)
        private String description;

        @Size(max = 255)
        private String reading;
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
    // FILE CREATE REQUEST (NEW ✅)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FileCreateRequest {
        /**
         * Direct hosted file url (S3)
         * OR externalUrl OR embedUrl (at least one is required)
         */
        private String fileUrl;
        private String externalUrl;
        private String embedUrl;

        private FileType fileType;

        private Long durationSeconds; // optional, set 0 if null
        private Long sizeBytes;       // optional, set 0 if null

        private String readerName;    // optional
        private Integer sortOrder;    // optional (not stored currently, but can be used later)
    }

    // =========================
    // FILE RESPONSE (UPDATED ✅)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FileResponse {
        private Long id;

        private String fileUrl;
        private String externalUrl;
        private String embedUrl;

        private FileType fileType;
        private long durationSeconds;
        private long sizeBytes;
        private String readerName;
    }

    // =========================
    // SOUNDTRACK CREATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull
        private SoundType soundType;

        @NotNull
        private TrackState trackState;

        @NotNull
        @NotEmpty(message = "At least one content language is required")
        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;

        @Size(max = 255)
        private String director;

        private boolean isThisProjectOfInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        /**
         * Optional uploaded files reader names (for multipart upload ordering)
         */
        private List<String> readerNames;

        /**
         * ✅ NEW: optional files as LINKS (no upload needed)
         */
        private List<FileCreateRequest> files;
    }

    // =========================
    // SOUNDTRACK UPDATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        private SoundType soundType;
        private TrackState trackState;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;

        @Size(max = 255)
        private String director;

        private Boolean isThisProjectOfInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        private List<String> readerNames;

        /**
         * ✅ NEW: optional files as LINKS (no upload needed)
         * If provided (not null), it will replace/merge depending on service logic.
         */
        private List<FileCreateRequest> files;
    }

    // =========================
    // SOUNDTRACK RESPONSE
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {

        private Long id;

        private String coverUrl;
        private SoundType soundType;
        private TrackState trackState;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;
        private String director;
        @JsonAlias({"isThisProjectOfInstitute"})
        private Boolean thisProjectOfInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        private List<FileResponse> files;

        private long totalDurationSeconds;
        private long totalSizeBytes;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
