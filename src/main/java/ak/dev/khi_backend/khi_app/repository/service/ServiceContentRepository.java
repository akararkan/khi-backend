package ak.dev.khi_backend.khi_app.repository.service;

import ak.dev.khi_backend.khi_app.model.service.ServiceContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceContentRepository extends JpaRepository<ServiceContent, Long> {

    Optional<ServiceContent> findByServiceIdAndLanguageCode(Long serviceId, String languageCode);

    boolean existsByServiceIdAndLanguageCode(Long serviceId, String languageCode);
}