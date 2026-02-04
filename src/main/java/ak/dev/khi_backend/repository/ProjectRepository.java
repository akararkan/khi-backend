package ak.dev.khi_backend.repository;

import ak.dev.khi_backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository
        extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    // ✅ Keep your existing methods (NO change)
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

    /**
     * ✅ Important addition:
     * This makes Specification-based queries fetch relations so JSON mapping won't crash.
     * It does NOT break your custom @Query methods.
     */
    @Override
    @EntityGraph(attributePaths = {"contents", "tags", "keywords", "media"})
    Page<Project> findAll(Specification<Project> spec, Pageable pageable);
}
