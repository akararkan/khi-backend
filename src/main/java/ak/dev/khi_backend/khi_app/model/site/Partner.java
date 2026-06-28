package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partners", indexes = {
        @Index(name = "idx_partner_active_order", columnList = "active,display_order")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Partner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "name_ckb", nullable = false, length = 300) private String nameCkb;
    @Column(name = "name_kmr", length = 300) private String nameKmr;
    @Column(name = "description_ckb", columnDefinition = "TEXT") private String descriptionCkb;
    @Column(name = "description_kmr", columnDefinition = "TEXT") private String descriptionKmr;
    @Column(name = "logo_url", columnDefinition = "TEXT") private String logoUrl;
    @Column(name = "website_url", columnDefinition = "TEXT") private String websiteUrl;
    @Builder.Default private boolean active = true;
    @Column(name = "display_order") @Builder.Default private Integer displayOrder = 0;
}
