package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "news_sub_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "name_ckb"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsSubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ CKB (Sorani) Name
    @Column(name = "name_ckb", nullable = false, length = 120)
    private String nameCkb;

    // ✅ KMR (Kurmanji) Name
    @Column(name = "name_kmr", nullable = false, length = 120)
    private String nameKmr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NewsCategory category;
}