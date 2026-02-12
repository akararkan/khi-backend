package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequest {

    private String coverUrl;

    private String projectType;

    private LocalDate projectDate;

    // Which languages exist in this project: CKB, KMR, or both
    @Builder.Default
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // multilingual content blocks
    private ProjectContentBlockDto ckbContent;
    private ProjectContentBlockDto kmrContent;

    // per-language relations (names only)
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

    // media (shared)
    @Builder.Default
    private List<ProjectMediaCreateRequest> media = new ArrayList<>();

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
