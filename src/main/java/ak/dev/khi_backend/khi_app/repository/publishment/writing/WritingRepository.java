package ak.dev.khi_backend.khi_app.repository.publishment.writing;

import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Writing/Book entities with optimized query methods
 */
@Repository
public interface WritingRepository extends JpaRepository<Writing, Long> {

    // ============================================================
    // SEARCH BY TAG (CKB)
    // ============================================================

    @Query("SELECT DISTINCT w FROM Writing w JOIN w.tagsCkb t WHERE LOWER(t) = LOWER(:tag)")
    List<Writing> findByTagCkb(@Param("tag") String tag);

    // ============================================================
    // SEARCH BY TAG (KMR)
    // ============================================================

    @Query("SELECT DISTINCT w FROM Writing w JOIN w.tagsKmr t WHERE LOWER(t) = LOWER(:tag)")
    List<Writing> findByTagKmr(@Param("tag") String tag);

    // ============================================================
    // SEARCH BY KEYWORD (CKB)
    // ============================================================

    @Query("SELECT DISTINCT w FROM Writing w JOIN w.keywordsCkb k WHERE LOWER(k) = LOWER(:keyword)")
    List<Writing> findByKeywordCkb(@Param("keyword") String keyword);

    // ============================================================
    // SEARCH BY KEYWORD (KMR)
    // ============================================================

    @Query("SELECT DISTINCT w FROM Writing w JOIN w.keywordsKmr k WHERE LOWER(k) = LOWER(:keyword)")
    List<Writing> findByKeywordKmr(@Param("keyword") String keyword);
}