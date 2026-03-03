package ak.dev.khi_backend.khi_app.repository.publishment.writing;


import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for WritingLog (audit logs)
 */
@Repository
public interface WritingLogRepository extends JpaRepository<WritingLog, Long> {
    // Additional custom queries can be added here if needed
}