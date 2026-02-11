package ak.dev.khi_backend.khi_app.repository.publishment.album_of_memories;


import ak.dev.khi_backend.khi_app.enums.publishment.AlbumType;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumOfMemories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumOfMemoriesRepository extends JpaRepository<AlbumOfMemories, Long> {

    /**
     * Get all albums ordered by year (newest first), then by creation date
     */
    @Query("SELECT a FROM AlbumOfMemories a ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findAllOrderedByYear();

    // ============================================================
    // CKB (SORANI) SEARCH METHODS - Optimized for fast search
    // ============================================================

    /**
     * Fast keyword search in CKB content (title, description, location, tags, keywords)
     * Uses indexes on collections for optimal performance
     */
    @Query("SELECT DISTINCT a FROM AlbumOfMemories a " +
            "LEFT JOIN a.tagsCkb t " +
            "LEFT JOIN a.keywordsCkb k " +
            "WHERE LOWER(a.ckbContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.ckbContent.location) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> searchByKeywordCkb(@Param("keyword") String keyword);

    /**
     * Fast search by single CKB tag - Direct collection join for speed
     */
    @Query("SELECT a FROM AlbumOfMemories a JOIN a.tagsCkb t " +
            "WHERE LOWER(t) = LOWER(:tag) " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByTagCkb(@Param("tag") String tag);

    /**
     * Fast search by multiple CKB tags (OR logic - any tag matches)
     */
    @Query("SELECT DISTINCT a FROM AlbumOfMemories a JOIN a.tagsCkb t " +
            "WHERE LOWER(t) IN :tags " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByTagsCkb(@Param("tags") List<String> tags);

    // ============================================================
    // KMR (KURMANJI) SEARCH METHODS - Optimized for fast search
    // ============================================================

    /**
     * Fast keyword search in KMR content (title, description, location, tags, keywords)
     */
    @Query("SELECT DISTINCT a FROM AlbumOfMemories a " +
            "LEFT JOIN a.tagsKmr t " +
            "LEFT JOIN a.keywordsKmr k " +
            "WHERE LOWER(a.kmrContent.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.kmrContent.location) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> searchByKeywordKmr(@Param("keyword") String keyword);

    /**
     * Fast search by single KMR tag
     */
    @Query("SELECT a FROM AlbumOfMemories a JOIN a.tagsKmr t " +
            "WHERE LOWER(t) = LOWER(:tag) " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByTagKmr(@Param("tag") String tag);

    /**
     * Fast search by multiple KMR tags (OR logic - any tag matches)
     */
    @Query("SELECT DISTINCT a FROM AlbumOfMemories a JOIN a.tagsKmr t " +
            "WHERE LOWER(t) IN :tags " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByTagsKmr(@Param("tags") List<String> tags);

    // ============================================================
    // ADDITIONAL OPTIMIZED QUERIES
    // ============================================================

    /**
     * Find albums by type (AUDIO or VIDEO)
     */
    @Query("SELECT a FROM AlbumOfMemories a WHERE a.albumType = :type " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByAlbumType(@Param("type") AlbumType type);

    /**
     * Find albums by year of publishment
     */
    @Query("SELECT a FROM AlbumOfMemories a WHERE a.yearOfPublishment = :year " +
            "ORDER BY a.createdAt DESC")
    List<AlbumOfMemories> findByYear(@Param("year") Integer year);

    /**
     * Find albums by year range
     */
    @Query("SELECT a FROM AlbumOfMemories a " +
            "WHERE a.yearOfPublishment BETWEEN :startYear AND :endYear " +
            "ORDER BY a.yearOfPublishment DESC, a.createdAt DESC")
    List<AlbumOfMemories> findByYearRange(@Param("startYear") Integer startYear,
                                          @Param("endYear") Integer endYear);
}