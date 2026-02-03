package ak.dev.khi_backend.repository;

import ak.dev.khi_backend.model.Project;
import ak.dev.khi_backend.enums.ProjectLanguage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProjectRepository
        extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    @Query("""
        select distinct p from Project p
        where lower(p.title) like lower(concat('%', :q, '%'))
    """)
    Page<Project> searchByTitle(@Param("q") String q, Pageable pageable);

    @Query("""
        select distinct p from Project p
        join p.tags t
        where lower(t.name) = lower(:tag)
    """)
    Page<Project> searchByTag(@Param("tag") String tag, Pageable pageable);

    @Query("""
        select distinct p from Project p
        join p.contents c
        where lower(c.name) = lower(:content)
    """)
    Page<Project> searchByContent(@Param("content") String content, Pageable pageable);

    @Query("""
        select distinct p from Project p
        join p.keywords k
        where lower(k.name) = lower(:keyword)
    """)
    Page<Project> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
