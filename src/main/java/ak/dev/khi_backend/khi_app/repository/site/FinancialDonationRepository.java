package ak.dev.khi_backend.khi_app.repository.site;

import ak.dev.khi_backend.khi_app.model.site.FinancialDonation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialDonationRepository extends JpaRepository<FinancialDonation, Long> {
}
