package ak.dev.khi_backend.khi_app.dto.project;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMediaResponse {

    private Long id;

    private String mediaType;

    private String url;

    private String externalUrl;

    private String embedUrl;

    private String caption;

    private Integer sortOrder;

    private String textBody;
}
