package ak.dev.khi_backend.khi_app.repository.news;

import ak.dev.khi_backend.khi_app.model.news.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {

    /**
     * ✅ Simple query - tags and keywords are EAGER, media is LAZY
     */
    @Query("SELECT n FROM News n ORDER BY n.datePublished DESC, n.id DESC")
    List<News> findAllOrderedByDate();

    /**
     * ✅ Find by ID with explicit ordering
     */
    @Query("SELECT n FROM News n WHERE n.id = :id")
    Optional<News> findByIdWithOrdering(@Param("id") Long id);

    /**
     * ✅ Search by keyword in content or keywords collection
     */
    @Query("""
        SELECT DISTINCT n FROM News n
        WHERE LOWER(n.ckbContent.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(n.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(n.kmrContent.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(n.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR EXISTS (SELECT 1 FROM n.keywords k WHERE LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY n.datePublished DESC, n.id DESC
    """)
    List<News> searchByKeyword(@Param("keyword") String keyword);

    /**
     * ✅ Find by single tag
     */
    @Query("""
        SELECT DISTINCT n FROM News n
        WHERE EXISTS (SELECT 1 FROM n.tags t WHERE LOWER(t) = LOWER(:tag))
        ORDER BY n.datePublished DESC, n.id DESC
    """)
    List<News> findByTag(@Param("tag") String tag);

    /**
     * ✅ Find by multiple tags (OR logic)
     */
    @Query("""
        SELECT DISTINCT n FROM News n
        WHERE EXISTS (SELECT 1 FROM n.tags t WHERE LOWER(t) IN :tags)
        ORDER BY n.datePublished DESC, n.id DESC
    """)
    List<News> findByTags(@Param("tags") List<String> tags);
}