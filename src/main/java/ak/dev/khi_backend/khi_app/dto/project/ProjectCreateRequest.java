package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import ak.dev.khi_backend.khi_app.model.project.ProjectContentBlock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

    @Size(max = 1024, message = "Cover URL must not exceed 1024 characters")
    private String coverUrl;

    // ✅ Bilingual project type (one label per language)
    @Size(max = 128, message = "CKB project type must not exceed 128 characters")
    private String projectTypeCkb;

    @Size(max = 128, message = "KMR project type must not exceed 128 characters")
    private String projectTypeKmr;

    // ✅ Project status: ONGOING | COMPLETED  (defaults to ONGOING in service if null)
    private ProjectStatus status;

    @NotEmpty(message = "At least one content language is required")
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
    @Valid
    private List<ProjectMediaCreateRequest> media;
}