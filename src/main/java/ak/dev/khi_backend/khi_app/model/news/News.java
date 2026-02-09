package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "news")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String coverUrl;

    private LocalDate datePublished;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ✅ CKB (Sorani) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 250)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT"))
    })
    private NewsContent ckbContent;

    // ✅ KMR (Kurmanji) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 250)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT"))
    })
    private NewsContent kmrContent;

    // ✅ EAGER FETCH - tags as collection (stored in table news_tags)
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "news_tags",
            joinColumns = @JoinColumn(name = "news_id")
    )
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> tags = new LinkedHashSet<>();

    // ✅ EAGER FETCH - keywords as collection (stored in table news_keywords)
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "news_keywords",
            joinColumns = @JoinColumn(name = "news_id")
    )
    @Column(name = "keyword", nullable = false, length = 120)
    private Set<String> keywords = new LinkedHashSet<>();

    // ✅ Both category and subcategory
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NewsCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private NewsSubCategory subCategory;

    // ✅ LAZY FETCH - One news contains many media (we'll handle this separately)
    @Builder.Default
    @OneToMany(mappedBy = "news", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<NewsMedia> media = new java.util.ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.datePublished == null) {
            this.datePublished = LocalDate.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}