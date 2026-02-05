package ak.dev.khi_backend.dto.project;

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
    private String caption;
    private Integer sortOrder;
    private String textBody;
}
