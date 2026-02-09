package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.ProjectLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectLogRepository extends JpaRepository<ProjectLog, Long> {

    List<ProjectLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
