package ak.dev.khi_backend.khi_app.dto.project;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMediaCreateRequest {

    // IMAGE | VIDEO | AUDIO | TEXT
    private String mediaType;

    // S3 URL (stored in DB)
    private String url;

    // optional caption
    private String caption;

    // ordering inside project
    private Integer sortOrder;

    // only used when mediaType = TEXT
    private String textBody;
}
