package ak.dev.khi_backend.khi_app.repository.project;


import ak.dev.khi_backend.khi_app.model.project.Project;
import ak.dev.khi_backend.khi_app.model.project.ProjectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectLogRepository extends JpaRepository<ProjectLog, Long> {

    /**
     * Fetch all log entries for a given project (newest first).
     */
    List<ProjectLog> findByProjectOrderByCreatedAtDesc(Project project);

    /**
     * Deletes every log row that belongs to the given project.
     *
     * ✅ @Modifying + @Query is necessary here because Spring Data's
     *    derived "deleteByProject" would load every entity first and
     *    then delete one-by-one.  A bulk JPQL DELETE is far cheaper
     *    when a project has many log entries.
     *
     * Returns the number of rows deleted (useful for debug logging).
     */
    @Modifying
    @Query("DELETE FROM ProjectLog l WHERE l.project = :project")
    int deleteByProject(@Param("project") Project project);

    /**
     * Same as above but by raw project ID — handy if you only have
     * the ID and don't want to load the full Project entity first.
     */
    @Modifying
    @Query("DELETE FROM ProjectLog l WHERE l.project.id = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}