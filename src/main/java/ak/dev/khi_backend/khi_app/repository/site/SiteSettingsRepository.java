package ak.dev.khi_backend.khi_app.repository.site;

import ak.dev.khi_backend.khi_app.model.site.SiteSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {
    Optional<SiteSettings> findFirstByOrderByIdAsc();
}
