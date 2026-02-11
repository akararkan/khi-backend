package ak.dev.khi_backend.khi_app.dto.publishment.film;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilmLogDTO {

    private Long id;
    private Long filmId;
    private String filmTitle;
    private String action;
    private String details;
    private String performedBy;
    private LocalDateTime timestamp;
}