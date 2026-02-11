package ak.dev.khi_backend.khi_app.repository.publishment.image;


import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageCollectionRepository extends JpaRepository<ImageCollection, Long> {

    // ─── Search by keywords (CKB + KMR + titles, descriptions, topics, collectedBy) ──
    @Query("SELECT DISTINCT ic FROM ImageCollection ic " +
            "LEFT JOIN ic.keywordsCkb kc " +
            "LEFT JOIN ic.keywordsKmr km " +
            "WHERE LOWER(kc) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(km) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.ckbContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.kmrContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.ckbContent.topic) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.kmrContent.topic) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.ckbContent.collectedBy) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(ic.kmrContent.collectedBy) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ImageCollection> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ─── Search by tags (CKB + KMR) ──────────────────────────────────
    @Query("SELECT DISTINCT ic FROM ImageCollection ic " +
            "LEFT JOIN ic.tagsCkb tc " +
            "LEFT JOIN ic.tagsKmr tm " +
            "WHERE LOWER(tc) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "   OR LOWER(tm) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<ImageCollection> searchByTag(@Param("tag") String tag, Pageable pageable);
}