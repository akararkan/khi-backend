package ak.dev.khi_backend.khi_app.repository.project;

import ak.dev.khi_backend.khi_app.model.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // ============================================================
    // PHASE-1: ID-ONLY QUERIES
    // Lightweight, paginated, hits DB index directly.
    // No collection joins = no Cartesian product at this stage.
    // ============================================================

    /**
     * GET ALL — Phase 1
     * Simple ID scan, sorted DESC by id (newest first).
     * With a primary key index this is O(1) — instantaneous.
     */
    @Query("SELECT p.id FROM Project p ORDER BY p.id DESC")
    Page<Long> findAllIds(Pageable pageable);

    /**
     * TAG SEARCH — Phase 1 (partial match, case-insensitive)
     *
     * Uses LIKE for partial matching — modern UX standard.
     * Example: "کوردستان" matches "دێڕی کوردستان"
     *
     * DB index for best performance:
     *   CREATE INDEX idx_project_tag_name ON project_tag(lower(name));
     *
     * Searches BOTH CKB and KMR tags in one query.
     */
    @Query("""
        SELECT DISTINCT p.id FROM Project p
        LEFT JOIN p.tagsCkb tckb
        LEFT JOIN p.tagsKmr tkmr
        WHERE lower(tckb.name) LIKE lower(concat('%', :tag, '%'))
           OR lower(tkmr.name) LIKE lower(concat('%', :tag, '%'))
        ORDER BY p.id DESC
        """)
    Page<Long> findIdsByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * KEYWORD SEARCH — Phase 1 (partial match, case-insensitive)
     *
     * DB index:
     *   CREATE INDEX idx_project_keyword_name ON project_keyword(lower(name));
     */
    @Query("""
        SELECT DISTINCT p.id FROM Project p
        LEFT JOIN p.keywordsCkb kckb
        LEFT JOIN p.keywordsKmr kkmr
        WHERE lower(kckb.name) LIKE lower(concat('%', :keyword, '%'))
           OR lower(kkmr.name) LIKE lower(concat('%', :keyword, '%'))
        ORDER BY p.id DESC
        """)
    Page<Long> findIdsByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * GLOBAL FULL-TEXT SEARCH — Phase 1
     *
     * Searches across: title (CKB + KMR), description, tags, keywords.
     * This is the modern "search everything" endpoint.
     * Single query, returns DISTINCT project IDs sorted by newest.
     *
     * DB indexes needed:
     *   CREATE INDEX idx_project_tag_name     ON project_tag(lower(name));
     *   CREATE INDEX idx_project_keyword_name ON project_keyword(lower(name));
     *   -- Hibernate handles embedded fields inline (no extra index needed for titles)
     */
    @Query("""
        SELECT DISTINCT p.id FROM Project p
        LEFT JOIN p.tagsCkb     tckb
        LEFT JOIN p.tagsKmr     tkmr
        LEFT JOIN p.keywordsCkb kckb
        LEFT JOIN p.keywordsKmr kkmr
        WHERE lower(p.ckbContent.title)       LIKE lower(concat('%', :q, '%'))
           OR lower(p.kmrContent.title)       LIKE lower(concat('%', :q, '%'))
           OR lower(p.ckbContent.description) LIKE lower(concat('%', :q, '%'))
           OR lower(p.kmrContent.description) LIKE lower(concat('%', :q, '%'))
           OR lower(tckb.name)                LIKE lower(concat('%', :q, '%'))
           OR lower(tkmr.name)                LIKE lower(concat('%', :q, '%'))
           OR lower(kckb.name)                LIKE lower(concat('%', :q, '%'))
           OR lower(kkmr.name)                LIKE lower(concat('%', :q, '%'))
        ORDER BY p.id DESC
        """)
    Page<Long> findIdsByGlobalSearch(@Param("q") String q, Pageable pageable);

    // ============================================================
    // PHASE-2: BATCH HYDRATION — NO @EntityGraph here.
    //
    // @BatchSize on entity collections handles everything automatically:
    //
    //   Query 1 : SELECT p   FROM project          WHERE id IN (...)  ← this method
    //   Query 2 : SELECT ... FROM project_tag_ckb   WHERE project_id IN (...)  ← Hibernate auto
    //   Query 3 : SELECT ... FROM project_tag_kmr   WHERE project_id IN (...)  ← Hibernate auto
    //   Query 4 : SELECT ... FROM project_keyword_ckb ...                       ← Hibernate auto
    //   Query 5 : SELECT ... FROM project_keyword_kmr ...                       ← Hibernate auto
    //   Query 6 : SELECT ... FROM project_content_ckb ...                       ← Hibernate auto
    //   Query 7 : SELECT ... FROM project_content_kmr ...                       ← Hibernate auto
    //   Query 8 : SELECT ... FROM project_media      ...                        ← Hibernate auto
    //   Query 9 : SELECT ... FROM project_languages  ...                        ← Hibernate auto
    //
    //   9 fast IN-queries vs 1 massive Cartesian JOIN = 10-20x faster.
    // ============================================================

    @Query("SELECT p FROM Project p WHERE p.id IN :ids")
    List<Project> findAllByIds(@Param("ids") List<Long> ids);

    // ============================================================
    // SINGLE LOOKUP — @EntityGraph is safe here (bounded to 1 project)
    // ============================================================

    @EntityGraph(attributePaths = {
            "contentsCkb", "contentsKmr",
            "tagsCkb",     "tagsKmr",
            "keywordsCkb", "keywordsKmr",
            "media",       "contentLanguages"
    })
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdWithGraph(@Param("id") Long id);
}