package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.ProjectTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectTagRepository extends JpaRepository<ProjectTag, Long> {

    Optional<ProjectTag> findByNameIgnoreCase(String name);

    List<ProjectTag> findAllByNameIgnoreCaseIn(Collection<String> names);
}