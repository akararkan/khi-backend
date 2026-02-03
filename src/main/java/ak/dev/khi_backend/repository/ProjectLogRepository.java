package ak.dev.khi_backend.repository;

import ak.dev.khi_backend.model.ProjectLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectLogRepository extends JpaRepository<ProjectLog, Long> {

    List<ProjectLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
