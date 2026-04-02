package ak.dev.khi_backend.khi_app.repository.service;

import ak.dev.khi_backend.khi_app.model.service.ServiceMediaCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceMediaCollectionRepository extends JpaRepository<ServiceMediaCollection, Long> {

    List<ServiceMediaCollection> findByServiceIdOrderBySortOrderAsc(Long serviceId);
}