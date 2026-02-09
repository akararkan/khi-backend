package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.ProjectTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTagRepository extends JpaRepository<ProjectTag, Long> {

    Optional<ProjectTag> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<ProjectTag> findByNameContainingIgnoreCase(String keyword);

    // ✅ ADD THIS METHOD
    List<ProjectTag> findByNameInIgnoreCase(List<String> names);
}
