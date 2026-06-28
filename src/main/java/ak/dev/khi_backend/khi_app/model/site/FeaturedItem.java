package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "featured_items", indexes = {
        @Index(name = "idx_featured_locale_active_order", columnList = "locale,active,display_order")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeaturedItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30) private String type;
    @Column(nullable = false, length = 300) private String slug;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT") private String imageUrl;
    @Column(name = "image_alt", length = 500) private String imageAlt;
    @Column(length = 10) private String locale;
    @Builder.Default private boolean active = true;
    @Column(name = "display_order") @Builder.Default private Integer displayOrder = 0;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
