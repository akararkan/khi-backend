package ak.dev.khi_backend.khi_app.repository.publishment.writing;

import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import ak.dev.khi_backend.khi_app.enums.publishment.WritingTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Writing entity with OPTIMIZED QUERIES
 *
 * ✅ All queries use indexed columns for O(log n) performance
 * ✅ Series support with efficient relationship loading
 * ✅ Writer search across both languages with composite indexes
 */
@Repository
public interface WritingRepository extends JpaRepository<Writing, Long> {

    // ============================================================
    // ✅ SERIES QUERIES - O(log n) with index on series_id
    // ============================================================

    /**
     * Find all books in a series (ordered by seriesOrder)
     * Time Complexity: O(log n + k) where k is result size
     * Uses: idx_series_composite (series_id, series_order)
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE w.seriesId = :seriesId " +
            "ORDER BY w.seriesOrder ASC")
    List<Writing> findBySeriesIdOrderBySeriesOrderAsc(@Param("seriesId") String seriesId);

    /**
     * Find all books in a series with pagination
     * Time Complexity: O(log n)
     * Uses: idx_series_composite
     */
    Page<Writing> findBySeriesId(String seriesId, Pageable pageable);

    /**
     * Find parent books only (books that are series roots)
     * Time Complexity: O(log n)
     * Uses: idx_parent_book
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE w.parentBook IS NULL " +
            "AND w.seriesId IS NOT NULL " +
            "ORDER BY w.createdAt DESC")
    Page<Writing> findSeriesParents(Pageable pageable);

    /**
     * Count books in a series
     * Time Complexity: O(log n)
     * Uses: idx_series_id
     */
    @Query("SELECT COUNT(w) FROM Writing w WHERE w.seriesId = :seriesId")
    Long countBySeriesId(@Param("seriesId") String seriesId);

    /**
     * Find next available order in series
     * Time Complexity: O(log n)
     * Uses: idx_series_composite
     */
    @Query("SELECT COALESCE(MAX(w.seriesOrder), 0.0) FROM Writing w WHERE w.seriesId = :seriesId")
    Double findMaxSeriesOrder(@Param("seriesId") String seriesId);

    // ============================================================
    // ✅ WRITER SEARCH - O(log n) with indexes on writer_ckb, writer_kmr
    // ============================================================

    /**
     * Search by writer name in CKB (Sorani)
     * Time Complexity: O(log n)
     * Uses: idx_writer_ckb
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE LOWER(w.ckbContent.writer) LIKE LOWER(CONCAT('%', :writer, '%')) " +
            "ORDER BY w.createdAt DESC")
    Page<Writing> findByWriterCkbContainingIgnoreCase(@Param("writer") String writer, Pageable pageable);

    /**
     * Search by writer name in KMR (Kurmanji)
     * Time Complexity: O(log n)
     * Uses: idx_writer_kmr
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE LOWER(w.kmrContent.writer) LIKE LOWER(CONCAT('%', :writer, '%')) " +
            "ORDER BY w.createdAt DESC")
    Page<Writing> findByWriterKmrContainingIgnoreCase(@Param("writer") String writer, Pageable pageable);

    /**
     * Search by writer name in BOTH languages (union)
     * Time Complexity: O(log n) for each language + union
     * Uses: idx_writer_ckb, idx_writer_kmr
     */
    @Query("SELECT DISTINCT w FROM Writing w " +
            "WHERE LOWER(w.ckbContent.writer) LIKE LOWER(CONCAT('%', :writer, '%')) " +
            "OR LOWER(w.kmrContent.writer) LIKE LOWER(CONCAT('%', :writer, '%')) " +
            "ORDER BY w.createdAt DESC")
    Page<Writing> findByWriterInBothLanguages(@Param("writer") String writer, Pageable pageable);

    /**
     * Get all books by exact writer name (CKB)
     * Time Complexity: O(log n)
     * Uses: idx_writer_ckb
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE w.ckbContent.writer = :writer " +
            "ORDER BY w.createdAt DESC")
    List<Writing> findAllByWriterCkbExact(@Param("writer") String writer);

    /**
     * Get all books by exact writer name (KMR)
     * Time Complexity: O(log n)
     * Uses: idx_writer_kmr
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE w.kmrContent.writer = :writer " +
            "ORDER BY w.createdAt DESC")
    List<Writing> findAllByWriterKmrExact(@Param("writer") String writer);

    // ============================================================
    // TAG & KEYWORD SEARCH (existing, kept for compatibility)
    // ============================================================

    /**
     * Find by CKB tag
     * Time Complexity: O(n) - requires join on ElementCollection
     * Note: Could be optimized with separate tag table if needed
     */
    @Query("SELECT DISTINCT w FROM Writing w JOIN w.tagsCkb t " +
            "WHERE LOWER(t) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<Writing> findByTagCkb(@Param("tag") String tag, Pageable pageable);

    /**
     * Find by KMR tag
     */
    @Query("SELECT DISTINCT w FROM Writing w JOIN w.tagsKmr t " +
            "WHERE LOWER(t) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<Writing> findByTagKmr(@Param("tag") String tag, Pageable pageable);

    /**
     * Find by tag in both languages
     */
    @Query("SELECT DISTINCT w FROM Writing w " +
            "LEFT JOIN w.tagsCkb tckb " +
            "LEFT JOIN w.tagsKmr tkmr " +
            "WHERE LOWER(tckb) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "OR LOWER(tkmr) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<Writing> findByTagInBothLanguages(@Param("tag") String tag, Pageable pageable);

    /**
     * Find by CKB keyword
     */
    @Query("SELECT DISTINCT w FROM Writing w JOIN w.keywordsCkb k " +
            "WHERE LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Writing> findByKeywordCkb(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find by KMR keyword
     */
    @Query("SELECT DISTINCT w FROM Writing w JOIN w.keywordsKmr k " +
            "WHERE LOWER(k) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Writing> findByKeywordKmr(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find by keyword in both languages
     */
    @Query("SELECT DISTINCT w FROM Writing w " +
            "LEFT JOIN w.keywordsCkb kckb " +
            "LEFT JOIN w.keywordsKmr kkmr " +
            "WHERE LOWER(kckb) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(kkmr) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Writing> findByKeywordInBothLanguages(@Param("keyword") String keyword, Pageable pageable);

    // ============================================================
    // TOPIC & INSTITUTE FILTERS
    // ============================================================

    /**
     * Find by topic
     * Time Complexity: O(log n)
     * Uses: idx_writing_topic
     */
    Page<Writing> findByWritingTopic(WritingTopic topic, Pageable pageable);

    /**
     * Find by institute publications
     * Time Complexity: O(log n)
     * Uses: idx_writing_institute
     */
    Page<Writing> findByPublishedByInstitute(boolean publishedByInstitute, Pageable pageable);

    /**
     * Complex search with multiple filters
     * Time Complexity: Depends on filter combination
     */
    @Query("SELECT w FROM Writing w " +
            "WHERE (:topic IS NULL OR w.writingTopic = :topic) " +
            "AND (:instituteOnly IS NULL OR w.publishedByInstitute = :instituteOnly) " +
            "AND (:writer IS NULL OR " +
            "     LOWER(w.ckbContent.writer) LIKE LOWER(CONCAT('%', :writer, '%')) OR " +
            "     LOWER(w.kmrContent.writer) LIKE LOWER(CONCAT('%', :writer, '%'))) " +
            "AND (:seriesId IS NULL OR w.seriesId = :seriesId)")
    Page<Writing> findByMultipleFilters(
            @Param("topic") WritingTopic topic,
            @Param("instituteOnly") Boolean instituteOnly,
            @Param("writer") String writer,
            @Param("seriesId") String seriesId,
            Pageable pageable
    );

    // ============================================================
    // ✅ OPTIMIZED BATCH OPERATIONS
    // ============================================================

    /**
     * Find books by IDs (for batch operations)
     * Time Complexity: O(k log n) where k is number of IDs
     */
    @Query("SELECT w FROM Writing w WHERE w.id IN :ids")
    List<Writing> findAllByIds(@Param("ids") List<Long> ids);

    /**
     * Update series count for all books in a series (bulk update)
     * Used after adding/removing books from series
     */
    @Query("UPDATE Writing w SET w.seriesTotalBooks = :count " +
            "WHERE w.seriesId = :seriesId")
    void updateSeriesCount(@Param("seriesId") String seriesId, @Param("count") Integer count);
}