package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_members", indexes = {
        @Index(name = "idx_team_active_order", columnList = "active,display_order")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TeamMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "name_ckb", nullable = false, length = 300) private String nameCkb;
    @Column(name = "name_kmr", length = 300) private String nameKmr;
    @Column(name = "role_ckb", nullable = false, length = 300) private String roleCkb;
    @Column(name = "role_kmr", length = 300) private String roleKmr;
    @Column(name = "bio_ckb", columnDefinition = "TEXT") private String bioCkb;
    @Column(name = "bio_kmr", columnDefinition = "TEXT") private String bioKmr;
    @Column(length = 200) private String office;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
    @Builder.Default private boolean active = true;
    @Column(name = "display_order") @Builder.Default private Integer displayOrder = 0;
}
