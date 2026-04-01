package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

/**
 * @BatchSize on the CLASS: same reasoning as NewsCategory.
 * Batch-loads subcategories for a page of News in 1 IN-query.
 */
@Entity
@BatchSize(size = 50)
@Table(
        name = "news_sub_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "name_ckb"}),
        indexes = {
                @Index(name = "idx_news_sub_category_name_ckb", columnList = "name_ckb")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsSubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ckb", nullable = false, length = 120)
    private String nameCkb;

    @Column(name = "name_kmr", nullable = false, length = 120)
    private String nameKmr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NewsCategory category;
}