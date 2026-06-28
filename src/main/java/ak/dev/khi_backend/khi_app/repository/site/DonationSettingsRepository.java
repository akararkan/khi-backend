package ak.dev.khi_backend.khi_app.repository.site;

import ak.dev.khi_backend.khi_app.model.site.DonationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationSettingsRepository extends JpaRepository<DonationSettings, Long> {
}
