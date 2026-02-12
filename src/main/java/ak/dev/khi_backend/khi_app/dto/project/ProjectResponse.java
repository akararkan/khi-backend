package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private Long id;

    private String coverUrl;

    private String projectType;

    private LocalDate projectDate;

    private Set<Language> contentLanguages;

    private ProjectContentBlockDto ckbContent;
    private ProjectContentBlockDto kmrContent;

    @Builder.Default
    private List<String> contentsCkb = new ArrayList<>();
    @Builder.Default
    private List<String> contentsKmr = new ArrayList<>();

    @Builder.Default
    private List<String> tagsCkb = new ArrayList<>();
    @Builder.Default
    private List<String> tagsKmr = new ArrayList<>();

    @Builder.Default
    private List<String> keywordsCkb = new ArrayList<>();
    @Builder.Default
    private List<String> keywordsKmr = new ArrayList<>();

    private Instant createdAt;

    @Builder.Default
    private List<ProjectMediaResponse> media = new ArrayList<>();

    // ------------------------------------------------------------
    // Inner DTO: content block
    // ------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectContentBlockDto {
        private String title;
        private String description;
        private String location;
    }
}
