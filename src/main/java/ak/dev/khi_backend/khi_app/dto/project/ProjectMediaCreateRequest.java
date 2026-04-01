package ak.dev.khi_backend.khi_app.dto.project;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMediaCreateRequest {

    // IMAGE | VIDEO | AUDIO | PDF | DOCUMENT
    @NotBlank(message = "Media type is required")
    private String mediaType;

    // S3 url (or already uploaded url). Optional for AUDIO/VIDEO if you provide externalUrl/embedUrl
    @Size(max = 2048, message = "Media URL is too long")
    private String url;

    // Optional external link (e.g., YouTube, SoundCloud, etc.)
    @Size(max = 2048, message = "External URL is too long")
    private String externalUrl;

    // Optional embeddable link (e.g., iframe src)
    @Size(max = 2048, message = "Embed URL is too long")
    private String embedUrl;

    // optional
    @Size(max = 255, message = "Caption must not exceed 255 characters")
    private String caption;

    // ordering
    @Min(value = 0, message = "Sort order must be zero or greater")
    private Integer sortOrder;

    // optional (if you support TEXT media later)
    @Size(max = 10000, message = "Text body is too long")
    private String textBody;
}
