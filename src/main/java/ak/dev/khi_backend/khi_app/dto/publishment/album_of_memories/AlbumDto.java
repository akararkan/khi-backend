package ak.dev.khi_backend.khi_app.dto.publishment.album_of_memories;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.AlbumType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlbumDto {

    private Long id;
    private String coverUrl;

    // Bilingual Content
    private LanguageContentDto ckbContent;
    private LanguageContentDto kmrContent;

    // Album metadata
    private AlbumType albumType;
    private String fileFormat;
    private Integer cdNumber;
    private Integer numberOfTracks;
    private Integer yearOfPublishment;

    // Languages of album content (CKB, KMR, or both)
    private Set<Language> contentLanguages;

    // Bilingual Tags
    private BilingualSet tags;

    // Bilingual Keywords
    private BilingualSet keywords;

    // Media files (tracks)
    private List<MediaDto> media;

    // Attachment (demonstration/advertisement)
    private AttachmentDto attachment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ============================================================
    // NESTED DTOs
    // ============================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {
        private String title;
        private String description;
        private String location;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BilingualSet {
        @Builder.Default
        private Set<String> ckb = new LinkedHashSet<>();
        @Builder.Default
        private Set<String> kmr = new LinkedHashSet<>();
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class MediaDto {
        private Long id;

        /** hosted file url */
        private String url;

        /** external link */
        private String externalUrl;

        /** embed link */
        private String embedUrl;

        private String trackTitleCkb;
        private String trackTitleKmr;
        private Integer trackNumber;
        private Integer durationSeconds;
        private String fileFormat;
        private Long fileSizeBytes;
        private LocalDateTime createdAt;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class AttachmentDto {
        /** hosted file url (pdf/mp4/etc) */
        private String url;

        /** external link */
        private String externalUrl;

        /** embed link */
        private String embedUrl;

        /** pdf/mp4/... */
        private String type;
    }
}
