package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "news_sub_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "name"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsSubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NewsCategory category;
}
