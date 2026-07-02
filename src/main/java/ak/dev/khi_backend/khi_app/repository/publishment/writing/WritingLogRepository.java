package ak.dev.khi_backend.khi_app.repository.publishment.writing;

import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for WritingLog (audit logs)
 */
@Repository
public interface WritingLogRepository extends JpaRepository<WritingLog, Long> {

    /**
     * Removes the live foreign-key reference before a writing is hard-deleted.
     * The audit trail remains associated through writing_id_ref.
     */
    @Modifying
    @Query("UPDATE WritingLog log SET log.writing = null WHERE log.writing.id = :writingId")
    int detachFromWriting(@Param("writingId") Long writingId);
}
