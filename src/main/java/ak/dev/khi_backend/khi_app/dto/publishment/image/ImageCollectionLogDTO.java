package ak.dev.khi_backend.khi_app.dto.publishment.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageCollectionLogDTO {

    private Long id;
    private Long imageCollectionId;
    private String collectionTitle;
    private String action;
    private String details;
    private String performedBy;
    private LocalDateTime timestamp;
}