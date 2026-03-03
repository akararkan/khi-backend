package ak.dev.khi_backend.khi_app.repository.publishment.video;

import ak.dev.khi_backend.khi_app.model.publishment.video.Video;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    // ─── Filter by video type ─────────────────────────────────────────────────

    Page<Video> findByVideoType(VideoType videoType, Pageable pageable);

    // ─── Album of Memories filters ────────────────────────────────────────────

    /**
     * All VIDEO_CLIP videos flagged as album of memories.
     */
    @Query("SELECT v FROM Video v " +
           "WHERE v.videoType = 'VIDEO_CLIP' AND v.albumOfMemories = true " +
           "ORDER BY v.publishmentDate DESC, v.createdAt DESC")
    List<Video> findVideoClipAlbumsOfMemories();

    /**
     * All VIDEO_CLIP videos that are regular clip collections (not albums).
     */
    @Query("SELECT v FROM Video v " +
           "WHERE v.videoType = 'VIDEO_CLIP' AND v.albumOfMemories = false " +
           "ORDER BY v.publishmentDate DESC, v.createdAt DESC")
    List<Video> findRegularVideoClips();

    // ─── Filter by topic ──────────────────────────────────────────────────────

    @Query("SELECT v FROM Video v WHERE v.topic.id = :topicId ORDER BY v.createdAt DESC")
    Page<Video> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    // ─── Keyword search ───────────────────────────────────────────────────────

    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN v.keywordsCkb kc " +
           "LEFT JOIN v.keywordsKmr km " +
           "WHERE LOWER(kc) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(km) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.ckbContent.title)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.kmrContent.title)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.ckbContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.kmrContent.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.ckbContent.director)    LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(v.kmrContent.director)    LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Video> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ─── Tag search ───────────────────────────────────────────────────────────

    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN v.tagsCkb tc " +
           "LEFT JOIN v.tagsKmr tm " +
           "WHERE LOWER(tc) LIKE LOWER(CONCAT('%', :tag, '%')) " +
           "   OR LOWER(tm) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<Video> searchByTag(@Param("tag") String tag, Pageable pageable);

    List<Video> findByTopicId(Long topicId);
}
