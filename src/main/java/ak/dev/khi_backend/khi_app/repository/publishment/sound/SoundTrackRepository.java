package ak.dev.khi_backend.khi_app.repository.publishment.sound;

import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SoundTrackRepository — Optimized for performance.
 *
 * ─── Key design rules ────────────────────────────────────────────────────────
 *
 *  1. NEVER use JOIN FETCH on @ElementCollection inside a paginated query.
 *     Hibernate would silently fall back to in-memory pagination (HHH90003004),
 *     loading the entire table and slicing in Java — catastrophic at scale.
 *
 *  2. Filter on @ElementCollection tables using EXISTS subqueries only.
 *     This keeps the root-entity cardinality at exactly 1 row per SoundTrack,
 *     so the database can apply LIMIT/OFFSET correctly.
 *
 *  3. @BatchSize(size = 50) on every collection (declared on the entity) means
 *     Hibernate fires one IN-query per collection type for the entire page,
 *     never N queries. No @EntityGraph needed.
 *
 *  ─── Query map ──────────────────────────────────────────────────────────────
 *
 *  Page queries  (root entity only, pagination handled by DB):
 *    findAllPaged           → ORDER BY createdAt DESC
 *    findAlbumsOfMemoriesPaged
 *    findRegularMultiTracksPaged
 *    searchByTagPaged       → bilingual / CKB / KMR variants
 *    searchByKeywordPaged   → bilingual / CKB / KMR variants
 *    searchByLocationPaged
 *    searchCombinedPaged    → multi-field full text across all string columns
 *
 *  List queries  (admin / internal / small result sets):
 *    findAlbumsOfMemories
 *    findRegularMultiTracks
 *    findByTopicId
 *    findBySoundType
 *
 *  Single-entity queries:
 *    findWithFiles          → fetches files collection in one query
 */
@Repository
public interface SoundTrackRepository extends JpaRepository<SoundTrack, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // هێنانەوەی هەمووی - بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Paginated fetch of all tracks, newest first.
     * Collections are loaded lazily via @BatchSize — no JOIN FETCH here.
     */
    @Query(value      = "SELECT s FROM SoundTrack s ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s")
    Page<SoundTrack> findAllPaged(Pageable pageable);

    /**
     * Albums of memories — paginated.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = true " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = true")
    Page<SoundTrack> findAlbumsOfMemoriesPaged(Pageable pageable);

    /**
     * Regular multi-track collections — paginated.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = false " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = false")
    Page<SoundTrack> findRegularMultiTracksPaged(Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // لیستی بەڕێوەبەرایەتی - بێ پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /** All MULTI tracks flagged as album of memories — for admin lists. */
    @Query("SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = true " +
            "ORDER BY s.createdAt DESC")
    List<SoundTrack> findAlbumsOfMemories();

    /** All regular MULTI tracks — for admin lists. */
    @Query("SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = false " +
            "ORDER BY s.createdAt DESC")
    List<SoundTrack> findRegularMultiTracks();

    /** Tracks belonging to a specific topic. */
    @Query("SELECT s FROM SoundTrack s WHERE s.topic.id = :topicId ORDER BY s.createdAt DESC")
    List<SoundTrack> findByTopicId(@Param("topicId") Long topicId);

    /** Tracks of a specific sound type (case-insensitive). */
    @Query("SELECT s FROM SoundTrack s WHERE LOWER(s.soundType) = LOWER(:soundType) ORDER BY s.createdAt DESC")
    List<SoundTrack> findBySoundType(@Param("soundType") String soundType);

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕان بە تاگ - بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tag search — CKB only.
     *
     * EXISTS keeps cardinality at 1 per SoundTrack row.
     * The correlated subquery is resolved entirely in the DB.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.tagsCkb t WHERE LOWER(t) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.tagsCkb t WHERE LOWER(t) = LOWER(:value))")
    Page<SoundTrack> searchByTagCkbPaged(@Param("value") String value, Pageable pageable);

    /**
     * Tag search — KMR only.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.tagsKmr t WHERE LOWER(t) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.tagsKmr t WHERE LOWER(t) = LOWER(:value))")
    Page<SoundTrack> searchByTagKmrPaged(@Param("value") String value, Pageable pageable);

    /**
     * Tag search — bilingual (CKB OR KMR).
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.tagsCkb t WHERE LOWER(t) = LOWER(:value)) " +
            "OR EXISTS " +
            "  (SELECT 1 FROM s.tagsKmr t WHERE LOWER(t) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.tagsCkb t WHERE LOWER(t) = LOWER(:value)) " +
                    "OR EXISTS " +
                    "  (SELECT 1 FROM s.tagsKmr t WHERE LOWER(t) = LOWER(:value))")
    Page<SoundTrack> searchByTagBilingualPaged(@Param("value") String value, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕان بە کلیلەووشە - بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Keyword search — CKB only.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.keywordsCkb k WHERE LOWER(k) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.keywordsCkb k WHERE LOWER(k) = LOWER(:value))")
    Page<SoundTrack> searchByKeywordCkbPaged(@Param("value") String value, Pageable pageable);

    /**
     * Keyword search — KMR only.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.keywordsKmr k WHERE LOWER(k) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.keywordsKmr k WHERE LOWER(k) = LOWER(:value))")
    Page<SoundTrack> searchByKeywordKmrPaged(@Param("value") String value, Pageable pageable);

    /**
     * Keyword search — bilingual (CKB OR KMR).
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.keywordsCkb k WHERE LOWER(k) = LOWER(:value)) " +
            "OR EXISTS " +
            "  (SELECT 1 FROM s.keywordsKmr k WHERE LOWER(k) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.keywordsCkb k WHERE LOWER(k) = LOWER(:value)) " +
                    "OR EXISTS " +
                    "  (SELECT 1 FROM s.keywordsKmr k WHERE LOWER(k) = LOWER(:value))")
    Page<SoundTrack> searchByKeywordBilingualPaged(@Param("value") String value, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕان بە شوێن - بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Location search — case-insensitive exact match.
     */
    @Query(value      = "SELECT s FROM SoundTrack s " +
            "WHERE EXISTS " +
            "  (SELECT 1 FROM s.locations l WHERE LOWER(l) = LOWER(:value)) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM SoundTrack s " +
                    "WHERE EXISTS " +
                    "  (SELECT 1 FROM s.locations l WHERE LOWER(l) = LOWER(:value))")
    Page<SoundTrack> searchByLocationPaged(@Param("value") String value, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕانی تێکەڵ - هەمووی لە یەک داواکاری
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Combined full-text search across:
     *   — CKB title / description / reading
     *   — KMR title / description / reading
     *   — soundType
     *   — director
     *   — tagsCkb / tagsKmr
     *   — keywordsCkb / keywordsKmr
     *   — locations
     *
     * Uses LIKE with a single :q parameter (pass already-lowercased + %-wrapped value).
     * All string comparisons are lowercased on both sides so the query is case-insensitive.
     *
     * Caller must pass q as  "%" + value.toLowerCase() + "%"
     */
    @Query(value =
            "SELECT s FROM SoundTrack s WHERE " +
                    "  LOWER(s.ckbContent.title)       LIKE :q OR " +
                    "  LOWER(s.ckbContent.description) LIKE :q OR " +
                    "  LOWER(s.ckbContent.reading)     LIKE :q OR " +
                    "  LOWER(s.kmrContent.title)       LIKE :q OR " +
                    "  LOWER(s.kmrContent.description) LIKE :q OR " +
                    "  LOWER(s.kmrContent.reading)     LIKE :q OR " +
                    "  LOWER(s.soundType)              LIKE :q OR " +
                    "  LOWER(s.director)               LIKE :q OR " +
                    "  EXISTS (SELECT 1 FROM s.tagsCkb      t WHERE LOWER(t) LIKE :q) OR " +
                    "  EXISTS (SELECT 1 FROM s.tagsKmr      t WHERE LOWER(t) LIKE :q) OR " +
                    "  EXISTS (SELECT 1 FROM s.keywordsCkb  k WHERE LOWER(k) LIKE :q) OR " +
                    "  EXISTS (SELECT 1 FROM s.keywordsKmr  k WHERE LOWER(k) LIKE :q) OR " +
                    "  EXISTS (SELECT 1 FROM s.locations    l WHERE LOWER(l) LIKE :q) " +
                    "ORDER BY s.createdAt DESC",
            countQuery =
                    "SELECT COUNT(s) FROM SoundTrack s WHERE " +
                            "  LOWER(s.ckbContent.title)       LIKE :q OR " +
                            "  LOWER(s.ckbContent.description) LIKE :q OR " +
                            "  LOWER(s.ckbContent.reading)     LIKE :q OR " +
                            "  LOWER(s.kmrContent.title)       LIKE :q OR " +
                            "  LOWER(s.kmrContent.description) LIKE :q OR " +
                            "  LOWER(s.kmrContent.reading)     LIKE :q OR " +
                            "  LOWER(s.soundType)              LIKE :q OR " +
                            "  LOWER(s.director)               LIKE :q OR " +
                            "  EXISTS (SELECT 1 FROM s.tagsCkb      t WHERE LOWER(t) LIKE :q) OR " +
                            "  EXISTS (SELECT 1 FROM s.tagsKmr      t WHERE LOWER(t) LIKE :q) OR " +
                            "  EXISTS (SELECT 1 FROM s.keywordsCkb  k WHERE LOWER(k) LIKE :q) OR " +
                            "  EXISTS (SELECT 1 FROM s.keywordsKmr  k WHERE LOWER(k) LIKE :q) OR " +
                            "  EXISTS (SELECT 1 FROM s.locations    l WHERE LOWER(l) LIKE :q)")
    Page<SoundTrack> searchCombinedPaged(@Param("q") String q, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // هێنانەوەی تاک لەگەڵ فایلەکان
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch one track with its files pre-loaded in a single JOIN FETCH.
     * Safe for single-entity (non-paginated) use.
     */
    @Query("SELECT s FROM SoundTrack s LEFT JOIN FETCH s.files WHERE s.id = :id")
    Optional<SoundTrack> findWithFiles(@Param("id") Long id);
}