package ak.dev.khi_backend.khi_app.dto.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.SoundType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class SoundTrackDtos {

    private SoundTrackDtos() {}

    // =========================
    // FILE RESPONSE
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FileResponse {
        private Long id;
        private String fileUrl;
        private FileType fileType;
        private long durationSeconds;
        private long sizeBytes;
        private String readerName;
    }

    // =========================
    // SOUNDTRACK CREATE REQUEST (For Form Data)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 4000)
        private String description;

        @Size(max = 255)
        private String reading;

        @NotNull
        private SoundType soundType;

        @NotNull
        private Language language;

        @Size(max = 255)
        private String location;

        @Size(max = 255)
        private String director;

        private boolean isThisProjectOfInstitute;

        @NotNull
        private TrackState trackState;

        // For multi-file upload, reader names correspond to each audio file
        private List<String> readerNames;

        // Tags and keywords as comma-separated strings (easier for form-data)
        private String keywords; // e.g., "story,folklore,kurdish"
        private String tags;     // e.g., "audio,culture"

        // Helper methods to convert to Set
        public Set<String> getKeywordsSet() {
            if (keywords == null || keywords.isBlank()) return Set.of();
            return Set.of(keywords.split(","));
        }

        public Set<String> getTagsSet() {
            if (tags == null || tags.isBlank()) return Set.of();
            return Set.of(tags.split(","));
        }

        // Renamed to avoid confusion
        public Set<String> getKeywords() {
            return getKeywordsSet();
        }

        public Set<String> getTags() {
            return getTagsSet();
        }
    }

    // =========================
    // SOUNDTRACK UPDATE REQUEST (For Form Data)
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @Size(max = 200)
        private String title;

        @Size(max = 4000)
        private String description;

        @Size(max = 255)
        private String reading;

        private SoundType soundType;
        private Language language;

        @Size(max = 255)
        private String location;

        @Size(max = 255)
        private String director;

        private Boolean isThisProjectOfInstitute;
        private TrackState trackState;

        private List<String> readerNames;

        private String keywords; // comma-separated
        private String tags;     // comma-separated

        public Set<String> getKeywordsSet() {
            if (keywords == null || keywords.isBlank()) return null;
            return Set.of(keywords.split(","));
        }

        public Set<String> getTagsSet() {
            if (tags == null || tags.isBlank()) return null;
            return Set.of(tags.split(","));
        }

        public Set<String> getKeywords() {
            return getKeywordsSet();
        }

        public Set<String> getTags() {
            return getTagsSet();
        }
    }

    // =========================
    // SOUNDTRACK RESPONSE
    // =========================
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {

        private Long id;

        private String title;
        private String coverUrl;
        private String description;
        private String reading;

        private SoundType soundType;
        private Language language;

        private String location;
        private String director;

        private boolean isThisProjectOfInstitute;

        private TrackState trackState;

        private Set<String> keywords;
        private Set<String> tags;

        private List<FileResponse> files;

        // computed totals
        private long totalDurationSeconds;
        private long totalSizeBytes;

        // timestamps
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}