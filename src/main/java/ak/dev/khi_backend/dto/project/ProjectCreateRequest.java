package ak.dev.khi_backend.dto.project;

import ak.dev.khi_backend.enums.project.ProjectLanguage;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequest {

    // ==============================
    // Project core fields
    // ==============================

    // S3 image cover URL
    private String coverUrl;

    private String title;

    private String description;

    // example: music, writing, art, film
    private String projectType;

    // ==============================
    // Dynamic relations (M2M)
    // ==============================

    // dynamic content names
    @Builder.Default
    private List<String> contents = new ArrayList<>();

    // tags
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    // keywords for search variations
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    // ==============================
    // Metadata
    // ==============================

    private LocalDate projectDate;

    private String location;

    private ProjectLanguage language;

    // ==============================
    // Media (1 -> many)
    // ==============================

    @Builder.Default
    private List<ProjectMediaCreateRequest> media = new ArrayList<>();
}
