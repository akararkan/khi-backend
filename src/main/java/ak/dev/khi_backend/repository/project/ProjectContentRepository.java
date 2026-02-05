package ak.dev.khi_backend.repository.project;

import ak.dev.khi_backend.model.project.ProjectContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectContentRepository extends JpaRepository<ProjectContent, Long> {

    Optional<ProjectContent> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
    // ✅ ADD THIS METHOD
    List<ProjectContent> findByNameInIgnoreCase(List<String> names);
}
