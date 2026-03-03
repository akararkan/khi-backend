package ak.dev.khi_backend.khi_app.repository.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoundTrackRepository extends JpaRepository<SoundTrack, Long> {

    // ─── Filter by track state ────────────────────────────────────────────────

    Page<SoundTrack> findByTrackState(TrackState trackState, Pageable pageable);

    // ─── Album of Memories filters ────────────────────────────────────────────

    /**
     * All MULTI tracks flagged as album of memories.
     */
    @Query("SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = true " +
            "ORDER BY s.createdAt DESC")
    List<SoundTrack> findAlbumsOfMemories();

    /**
     * All MULTI tracks that are regular collections (not albums).
     */
    @Query("SELECT s FROM SoundTrack s " +
            "WHERE s.trackState = 'MULTI' AND s.albumOfMemories = false " +
            "ORDER BY s.createdAt DESC")
    List<SoundTrack> findRegularMultiTracks();

    // ─── Filter by topic ──────────────────────────────────────────────────────

    @Query("SELECT s FROM SoundTrack s WHERE s.topic.id = :topicId ORDER BY s.createdAt DESC")
    Page<SoundTrack> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    // ─── Filter by sound type ─────────────────────────────────────────────────

    @Query("SELECT s FROM SoundTrack s " +
            "WHERE LOWER(s.soundType) = LOWER(:soundType) " +
            "ORDER BY s.createdAt DESC")
    Page<SoundTrack> findBySoundType(@Param("soundType") String soundType, Pageable pageable);

    // ─── Keyword / tag full search ────────────────────────────────────────────

    @Query("SELECT DISTINCT s FROM SoundTrack s " +
            "LEFT JOIN s.keywordsCkb kc LEFT JOIN s.keywordsKmr km " +
            "LEFT JOIN s.tagsCkb tc     LEFT JOIN s.tagsKmr tm " +
            "WHERE LOWER(s.ckbContent.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(s.kmrContent.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(s.soundType)        LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(kc) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(km) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(tc) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(tm) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<SoundTrack> fullSearch(@Param("q") String query, Pageable pageable);

    List<SoundTrack> findByTopicId(Long topicId);
}
