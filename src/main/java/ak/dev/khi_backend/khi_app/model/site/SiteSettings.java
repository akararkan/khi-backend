package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "site_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettings {

    public static final int DEFAULT_MAX_FEATURED_SLIDES = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "max_featured_slides", nullable = false)
    private Integer maxFeaturedSlides = DEFAULT_MAX_FEATURED_SLIDES;
}
