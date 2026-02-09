package ak.dev.khi_backend.khi_app.dto.news;

import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsDto {

    // Response (null on create)
    private Long id;

    private String coverUrl;

    private LocalDate datePublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // collections ✅
    private Set<String> tags;
    private Set<String> keywords;

    // ✅ Category and SubCategory Names (auto-create if not exist)
    private String categoryName;
    private String subCategoryName;

    // ✅ Bilingual content
    private LanguageContentDto ckbContent;  // Sorani
    private LanguageContentDto kmrContent;  // Kurmanji

    // News has many media ✅
    private List<MediaDto> media;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LanguageContentDto {
        private String title;
        private String description;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MediaDto {
        private Long id;              // response
        private NewsMediaType type;   // request/response
        private String url;           // request/response
        private Integer sortOrder;    // request/response
        private LocalDateTime createdAt; // response
    }
}