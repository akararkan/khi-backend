package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "social_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_platform", columnNames = "platform")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SocialLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 60) private String platform;
    @Column(nullable = false, columnDefinition = "TEXT") private String url;
    @Column(name = "label_ckb", length = 200) private String labelCkb;
    @Column(name = "label_kmr", length = 200) private String labelKmr;
    @Builder.Default private boolean active = true;
    @Column(name = "display_order") @Builder.Default private Integer displayOrder = 0;
}
