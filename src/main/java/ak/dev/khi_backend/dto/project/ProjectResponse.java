package ak.dev.khi_backend.dto.project;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private Long id;

    private String cover;
    private String title;
    private String description;
    private String projectType;
    private String content;

    private List<String> tags;
    private List<String> keywords;

    private String date;
    private String location;
    private String language;
    private String result;

    private Instant createdAt;

    private List<ProjectMediaResponse> media;
}
