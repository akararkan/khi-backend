package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.ProjectKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectKeywordRepository extends JpaRepository<ProjectKeyword, Long> {

    Optional<ProjectKeyword> findByNameIgnoreCase(String name);

    List<ProjectKeyword> findAllByNameIgnoreCaseIn(Collection<String> names);
}