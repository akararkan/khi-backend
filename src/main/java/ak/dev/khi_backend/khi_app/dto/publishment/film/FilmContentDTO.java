package ak.dev.khi_backend.khi_app.dto.publishment.film;


import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FilmContentDTO {

    private String title;
    private String description;
    private String topic;
    private String location;
    private String director;
    private String producer;
}