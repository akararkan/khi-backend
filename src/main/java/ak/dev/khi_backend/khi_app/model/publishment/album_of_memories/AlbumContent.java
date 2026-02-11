package ak.dev.khi_backend.khi_app.model.publishment.album_of_memories;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlbumContent {

    @Column(name = "title", length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 200)
    private String location;
}