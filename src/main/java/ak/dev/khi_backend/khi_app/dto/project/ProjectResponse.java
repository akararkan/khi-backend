package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long id;
    private String coverUrl;

    // ✅ Bilingual project type
    private String projectTypeCkb;
    private String projectTypeKmr;

    // ✅ Project status
    private ProjectStatus status;

    private LocalDate projectDate;
    private Set<Language> contentLanguages;

    private ProjectContentBlockDto ckbContent;
    private ProjectContentBlockDto kmrContent;

    private List<String> contentsCkb;
    private List<String> contentsKmr;

    private List<String> tagsCkb;
    private List<String> tagsKmr;

    private List<String> keywordsCkb;
    private List<String> keywordsKmr;

    private Instant createdAt;
    private Instant updatedAt;
    private String  createdBy;
    private String  updatedBy;

    private List<ProjectMediaResponse> media;

    // ── Nested DTO ──────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectContentBlockDto {
        private String title;
        private String description;
        private String location;
    }
}