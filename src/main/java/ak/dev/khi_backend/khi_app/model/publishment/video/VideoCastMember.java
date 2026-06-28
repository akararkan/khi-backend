package ak.dev.khi_backend.khi_app.model.publishment.video;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VideoCastMember {
    @Column(name = "name_ckb", length = 300) private String nameCkb;
    @Column(name = "name_kmr", length = 300) private String nameKmr;
    @Column(name = "role_ckb", length = 300) private String roleCkb;
    @Column(name = "role_kmr", length = 300) private String roleKmr;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
}
