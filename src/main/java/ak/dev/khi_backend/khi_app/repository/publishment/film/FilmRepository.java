package ak.dev.khi_backend.khi_app.repository.publishment.film;


import ak.dev.khi_backend.khi_app.model.publishment.film.Film;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmRepository extends JpaRepository<Film, Long> {

    // ─── Search by keywords (CKB + KMR, case-insensitive, partial match) ──
    @Query("SELECT DISTINCT f FROM Film f " +
            "LEFT JOIN f.keywordsCkb kc " +
            "LEFT JOIN f.keywordsKmr km " +
            "WHERE LOWER(kc) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(km) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.ckbContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.kmrContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.ckbContent.topic) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.kmrContent.topic) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.ckbContent.director) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(f.kmrContent.director) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Film> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ─── Search by tags (CKB + KMR, case-insensitive, partial match) ──
    @Query("SELECT DISTINCT f FROM Film f " +
            "LEFT JOIN f.tagsCkb tc " +
            "LEFT JOIN f.tagsKmr tm " +
            "WHERE LOWER(tc) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "   OR LOWER(tm) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<Film> searchByTag(@Param("tag") String tag, Pageable pageable);
}