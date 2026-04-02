package ak.dev.khi_backend.khi_app.repository.service;

import ak.dev.khi_backend.khi_app.model.service.ServiceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceAuditLogRepository extends JpaRepository<ServiceAuditLog, Long> {

    List<ServiceAuditLog> findByServiceIdOrderByTimestampDesc(Long serviceId);
}

