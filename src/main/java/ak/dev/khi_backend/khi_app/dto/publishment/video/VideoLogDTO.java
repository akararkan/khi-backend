package ak.dev.khi_backend.khi_app.dto.publishment.video;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VideoLogDTO {

    private Long id;
    private Long videoId;
    private String videoTitle;
    private String action;
    private String details;
    private String performedBy;
    private LocalDateTime timestamp;
}