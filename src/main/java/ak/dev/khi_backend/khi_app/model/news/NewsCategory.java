package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * @BatchSize on the CLASS tells Hibernate:
 * "when batch-loading NewsCategory for a page of News items,
 * fire 1 IN-query for up to 50 categories at once"
 * instead of N separate queries (one per news row).
 */
@Entity
@BatchSize(size = 50)
@Table(
        name = "news_categories",
        indexes = {
                @Index(name = "idx_news_category_name_ckb", columnList = "name_ckb")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ckb", nullable = false, unique = true, length = 120)
    private String nameCkb;

    @Column(name = "name_kmr", nullable = false, length = 120)
    private String nameKmr;

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NewsSubCategory> subCategories = new ArrayList<>();
}