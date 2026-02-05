package ak.dev.khi_backend.repository.project;

import ak.dev.khi_backend.enums.project.ProjectMediaType;
import ak.dev.khi_backend.model.project.Project;
import ak.dev.khi_backend.model.project.ProjectMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, Long> {

    // Get all media for one project (ordered)
    List<ProjectMedia> findByProjectOrderBySortOrderAsc(Project project);

    // Get media by project id
    List<ProjectMedia> findByProjectIdOrderBySortOrderAsc(Long projectId);

    // Filter media by type (IMAGE / VIDEO / AUDIO / TEXT)
    List<ProjectMedia> findByProjectAndMediaType(Project project, ProjectMediaType mediaType);

    // Delete all media for a project
    void deleteByProject(Project project);
}
