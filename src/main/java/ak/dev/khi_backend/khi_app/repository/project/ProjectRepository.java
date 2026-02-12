package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // ============================================================
    // SEARCH BY TAG (CKB or KMR)
    // ============================================================

    @Query(
            value = """
            select distinct p from Project p
            left join fetch p.tagsCkb tckb
            left join fetch p.tagsKmr tkmr
            left join fetch p.contentsCkb
            left join fetch p.contentsKmr
            left join fetch p.keywordsCkb
            left join fetch p.keywordsKmr
            left join fetch p.media
            left join fetch p.contentLanguages
            where
                (tckb is not null and lower(tckb.name) = lower(:tag))
                or
                (tkmr is not null and lower(tkmr.name) = lower(:tag))
            """,
            countQuery = """
            select count(distinct p) from Project p
            left join p.tagsCkb tckb
            left join p.tagsKmr tkmr
            where
                (tckb is not null and lower(tckb.name) = lower(:tag))
                or
                (tkmr is not null and lower(tkmr.name) = lower(:tag))
            """
    )
    Page<Project> searchByTag(@Param("tag") String tag, Pageable pageable);

    // ============================================================
    // SEARCH BY KEYWORD (CKB or KMR)
    // ============================================================

    @Query(
            value = """
            select distinct p from Project p
            left join fetch p.keywordsCkb kckb
            left join fetch p.keywordsKmr kkmr
            left join fetch p.tagsCkb
            left join fetch p.tagsKmr
            left join fetch p.contentsCkb
            left join fetch p.contentsKmr
            left join fetch p.media
            left join fetch p.contentLanguages
            where
                (kckb is not null and lower(kckb.name) = lower(:keyword))
                or
                (kkmr is not null and lower(kkmr.name) = lower(:keyword))
            """,
            countQuery = """
            select count(distinct p) from Project p
            left join p.keywordsCkb kckb
            left join p.keywordsKmr kkmr
            where
                (kckb is not null and lower(kckb.name) = lower(:keyword))
                or
                (kkmr is not null and lower(kkmr.name) = lower(:keyword))
            """
    )
    Page<Project> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ============================================================
    // GET ALL with eager fetch (avoids N+1)
    // ============================================================

    @Override
    @EntityGraph(attributePaths = {
            "contentsCkb", "contentsKmr",
            "tagsCkb", "tagsKmr",
            "keywordsCkb", "keywordsKmr",
            "media",
            "contentLanguages"
    })
    Page<Project> findAll(Pageable pageable);
}