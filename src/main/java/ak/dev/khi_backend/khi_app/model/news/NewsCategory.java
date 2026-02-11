package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news_categories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ CKB (Sorani) Name
    @Column(name = "name_ckb", nullable = false, unique = true, length = 120)
    private String nameCkb;

    // ✅ KMR (Kurmanji) Name
    @Column(name = "name_kmr", nullable = false, length = 120)
    private String nameKmr;

    @Builder.Default
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NewsSubCategory> subCategories = new ArrayList<>();
}