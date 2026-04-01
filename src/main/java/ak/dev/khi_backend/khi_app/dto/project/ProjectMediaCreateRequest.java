package ak.dev.khi_backend.khi_app.dto.project;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMediaCreateRequest {

    // IMAGE | VIDEO | AUDIO | PDF | DOCUMENT
    private String mediaType;

    // S3 url (or already uploaded url). Optional for AUDIO/VIDEO if you provide externalUrl/embedUrl
    private String url;

    // Optional external link (e.g., YouTube, SoundCloud, etc.)
    private String externalUrl;

    // Optional embeddable link (e.g., iframe src)
    private String embedUrl;

    // optional
    private String caption;

    // ordering
    private Integer sortOrder;

    // optional (if you support TEXT media later)
    private String textBody;
}
