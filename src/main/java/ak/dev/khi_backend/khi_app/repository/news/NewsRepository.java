package ak.dev.khi_backend.khi_app.repository.news;

import ak.dev.khi_backend.khi_app.model.news.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {

    /**
     * Get all news ordered by date published (newest first)
     */
    @Query("SELECT n FROM News n ORDER BY n.datePublished DESC, n.createdAt DESC")
    List<News> findAllOrderedByDate();

    // ============================================================
    // CKB (SORANI) SEARCH METHODS
    // ============================================================

    /**
     * Search by keyword in CKB content (title, description, tags, keywords)
     */
    @Query("SELECT DISTINCT n FROM News n " +
            "LEFT JOIN n.tagsCkb t " +
            "LEFT JOIN n.keywordsCkb k " +
            "WHERE LOWER(n.ckbContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(n.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY n.datePublished DESC")
    List<News> searchByKeywordCkb(@Param("keyword") String keyword);

    /**
     * Find news by single CKB tag
     */
    @Query("SELECT n FROM News n JOIN n.tagsCkb t WHERE LOWER(t) = LOWER(:tag) ORDER BY n.datePublished DESC")
    List<News> findByTagCkb(@Param("tag") String tag);

    /**
     * Find news by multiple CKB tags (OR logic - any tag matches)
     */
    @Query("SELECT DISTINCT n FROM News n JOIN n.tagsCkb t WHERE LOWER(t) IN :tags ORDER BY n.datePublished DESC")
    List<News> findByTagsCkb(@Param("tags") List<String> tags);

    // ============================================================
    // KMR (KURMANJI) SEARCH METHODS
    // ============================================================

    /**
     * Search by keyword in KMR content (title, description, tags, keywords)
     */
    @Query("SELECT DISTINCT n FROM News n " +
            "LEFT JOIN n.tagsKmr t " +
            "LEFT JOIN n.keywordsKmr k " +
            "WHERE LOWER(n.kmrContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(n.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY n.datePublished DESC")
    List<News> searchByKeywordKmr(@Param("keyword") String keyword);

    /**
     * Find news by single KMR tag
     */
    @Query("SELECT n FROM News n JOIN n.tagsKmr t WHERE LOWER(t) = LOWER(:tag) ORDER BY n.datePublished DESC")
    List<News> findByTagKmr(@Param("tag") String tag);

    /**
     * Find news by multiple KMR tags (OR logic - any tag matches)
     */
    @Query("SELECT DISTINCT n FROM News n JOIN n.tagsKmr t WHERE LOWER(t) IN :tags ORDER BY n.datePublished DESC")
    List<News> findByTagsKmr(@Param("tags") List<String> tags);

    // ============================================================
    // CATEGORY SEARCH METHODS
    // ============================================================

    /**
     * Find news by category CKB name
     */
    @Query("SELECT n FROM News n WHERE LOWER(n.category.nameCkb) = LOWER(:categoryName) ORDER BY n.datePublished DESC")
    List<News> findByCategoryNameCkb(@Param("categoryName") String categoryName);

    /**
     * Find news by category KMR name
     */
    @Query("SELECT n FROM News n WHERE LOWER(n.category.nameKmr) = LOWER(:categoryName) ORDER BY n.datePublished DESC")
    List<News> findByCategoryNameKmr(@Param("categoryName") String categoryName);

    /**
     * Find news by subcategory CKB name
     */
    @Query("SELECT n FROM News n WHERE LOWER(n.subCategory.nameCkb) = LOWER(:subCategoryName) ORDER BY n.datePublished DESC")
    List<News> findBySubCategoryNameCkb(@Param("subCategoryName") String subCategoryName);

    /**
     * Find news by subcategory KMR name
     */
    @Query("SELECT n FROM News n WHERE LOWER(n.subCategory.nameKmr) = LOWER(:subCategoryName) ORDER BY n.datePublished DESC")
    List<News> findBySubCategoryNameKmr(@Param("subCategoryName") String subCategoryName);
}