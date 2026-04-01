package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import ak.dev.khi_backend.khi_app.model.project.ProjectContentBlock;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

    private String coverUrl;

    // ✅ Bilingual project type (one label per language)
    private String projectTypeCkb;
    private String projectTypeKmr;

    // ✅ Project status: ONGOING | COMPLETED  (defaults to ONGOING in service if null)
    private ProjectStatus status;

    private Set<Language> contentLanguages;

    private LocalDate projectDate;

    // Embedded content blocks
    private ProjectContentBlock ckbContent;
    private ProjectContentBlock kmrContent;

    // Per-language tag / keyword / content lists
    private List<String> contentsCkb;
    private List<String> contentsKmr;

    private List<String> tagsCkb;
    private List<String> tagsKmr;

    private List<String> keywordsCkb;
    private List<String> keywordsKmr;

    // Media items sent as JSON (URL-based)
    private List<ProjectMediaCreateRequest> media;
}