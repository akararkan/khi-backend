package ak.dev.khi_backend.khi_app.repository.publishment.album_of_memories;


import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlbumAuditLogRepository extends JpaRepository<AlbumAuditLog, Long> {
}