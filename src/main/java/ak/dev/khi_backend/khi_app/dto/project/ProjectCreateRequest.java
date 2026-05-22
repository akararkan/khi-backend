package ak.dev.khi_backend.khi_app.dto.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import ak.dev.khi_backend.khi_app.model.project.ProjectContentBlock;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * ProjectCreateRequest — Tiptap migration.
 *
 * The {@code media[]} array, the {@code contentsCkb / contentsKmr} string
 * lists, and the multipart cover upload have been dropped. Cover image and
 * any inline media are uploaded separately via
 * {@code POST /api/v1/media/upload}, and the returned URLs are sent here
 * inside {@code coverUrl} (top-level) and inside the Tiptap HTML in
 * {@code ckbContent.description} / {@code kmrContent.description}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

    @Size(max = 1024, message = "Cover URL must not exceed 1024 characters")
    private String coverUrl;

    @Size(max = 128, message = "CKB project type must not exceed 128 characters")
    private String projectTypeCkb;

    @Size(max = 128, message = "KMR project type must not exceed 128 characters")
    private String projectTypeKmr;

    private ProjectStatus status;

    @NotEmpty(message = "At least one content language is required")
    private Set<Language> contentLanguages;

    private LocalDate projectDate;

    /**
     * Sorani (CKB) content. The {@code description} field accepts Tiptap HTML.
     */
    private ProjectContentBlock ckbContent;

    /**
     * Kurmanji (KMR) content. The {@code description} field accepts Tiptap HTML.
     */
    private ProjectContentBlock kmrContent;

    private List<String> tagsCkb;
    private List<String> tagsKmr;

    private List<String> keywordsCkb;
    private List<String> keywordsKmr;
}
