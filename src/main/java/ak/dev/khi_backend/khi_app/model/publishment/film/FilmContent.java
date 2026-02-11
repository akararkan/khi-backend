package ak.dev.khi_backend.khi_app.model.publishment.film;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FilmContent {

    @Column(length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 300)
    private String topic;

    @Column(length = 250)
    private String location;

    @Column(length = 250)
    private String director;

    @Column(length = 250)
    private String producer;
}