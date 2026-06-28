package ak.dev.khi_backend.khi_app.repository.site;

import ak.dev.khi_backend.khi_app.model.site.ArchiveDonation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveDonationRepository extends JpaRepository<ArchiveDonation, Long> {
}
