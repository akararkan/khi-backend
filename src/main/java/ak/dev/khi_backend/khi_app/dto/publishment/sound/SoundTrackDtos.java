package ak.dev.khi_backend.khi_app.dto.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
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
    // INLINE TOPIC REQUEST
    // =========================

    /**
     * Embed inside CreateRequest or UpdateRequest to create a new SOUND topic
     * on the fly and assign it to this soundtrack in one request.
     *
     * Resolution order (service):
     *  1. topicId supplied  → use existing topic
     *  2. newTopic supplied → create + assign
     *  3. neither           → no topic
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class InlineTopicRequest {
        private String nameCkb;
        private String nameKmr;
    }

    /**
     * Lightweight topic view — returned from topic-list / create endpoints.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TopicView {
        private Long id;
        private String nameCkb;
        private String nameKmr;
        private LocalDateTime createdAt;
    }

    // =========================
    // FILE CREATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FileCreateRequest {
        private String fileUrl;
        private String externalUrl;
        private String embedUrl;
        private FileType fileType;
        private Long durationSeconds;
        private Long sizeBytes;
        private String readerName;
        private Integer sortOrder;
    }

    // =========================
    // FILE RESPONSE
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

        @NotBlank(message = "soundType is required")
        @Size(max = 50)
        private String soundType;

        @NotNull(message = "trackState is required")
        private TrackState trackState;

        private Boolean albumOfMemories;

        // ── Topic (option A: existing id) ──
        private Long topicId;

        // ── Topic (option B: inline creation) ──
        // If topicId is also set, topicId takes precedence.
        private InlineTopicRequest newTopic;

        @NotNull
        @NotEmpty(message = "At least one content language is required")
        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;

        @Size(max = 255)
        private String director;

        @JsonProperty("thisProjectOfInstitute")
        private boolean thisProjectOfInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        private List<String> readerNames;
        private List<FileCreateRequest> files;
    }

    // =========================
    // SOUNDTRACK UPDATE REQUEST
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @Size(max = 50)
        private String soundType;

        private TrackState trackState;
        private Boolean albumOfMemories;

        // ── Topic (option A: existing id) ──
        private Long topicId;

        // ── Topic (option B: inline creation) ──
        private InlineTopicRequest newTopic;

        // clearTopic = true → remove topic (overrides topicId / newTopic)
        private boolean clearTopic;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;

        @Size(max = 255)
        private String director;

        @JsonProperty("thisProjectOfInstitute")
        private Boolean thisProjectOfInstitute;

        private BilingualSet tags;
        private BilingualSet keywords;

        private List<String> readerNames;
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
        private String ckbCoverUrl;

        private String kmrCoverUrl;

        private String hoverCoverUrl;
        private String soundType;
        private TrackState trackState;
        private Boolean albumOfMemories;

        private Long topicId;
        private String topicNameCkb;
        private String topicNameKmr;

        private Set<Language> contentLanguages;

        private LanguageContentDto ckbContent;
        private LanguageContentDto kmrContent;

        private Set<String> locations;
        private String director;

        @JsonProperty("thisProjectOfInstitute")
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