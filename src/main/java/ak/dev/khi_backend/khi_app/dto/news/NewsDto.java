package ak.dev.khi_backend.khi_app.dto.news;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsDto {

    private Long id;
    private String coverUrl;
    private LocalDate datePublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ✅ Which languages are active (LIKE Project)
    @Builder.Default
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ✅ Bilingual Category
    private CategoryDto category;

    // ✅ Bilingual SubCategory
    private SubCategoryDto subCategory;

    // ✅ Bilingual Content
    private LanguageContentDto ckbContent;
    private LanguageContentDto kmrContent;

    // ✅ Bilingual Tags
    private BilingualSet tags;

    // ✅ Bilingual Keywords
    private BilingualSet keywords;

    // Media
    private List<MediaDto> media;

    // ============================================================
    // NESTED DTOs
    // ============================================================

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CategoryDto {
        private String ckbName;
        private String kmrName;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SubCategoryDto {
        private String ckbName;
        private String kmrName;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {
        private String title;
        private String description;
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
        private NewsMediaType type;

        /** Direct file url (S3/server). Optional for AUDIO/VIDEO if externalUrl/embedUrl is provided. */
        private String url;

        /** Normal link to third-party page (e.g., youtube watch link). Optional. */
        private String externalUrl;

        /** Embeddable link for iframe (e.g., youtube embed link). Optional. */
        private String embedUrl;

        private Integer sortOrder;
        private LocalDateTime createdAt;
    }
}
