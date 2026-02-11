package ak.dev.khi_backend.khi_app.dto.publishment.image;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageContentDTO {

    private String title;
    private String description;
    private String topic;
    private String location;
    private String collectedBy;
}