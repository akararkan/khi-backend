package ak.dev.khi_backend.khi_app.repository.publishment.sound;

import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoundTrackLogRepository extends JpaRepository<SoundTrackLog, Long> {
    // Fetch all logs still linked to a soundtrack (for detaching before hard delete)
    List<SoundTrackLog> findBySoundTrackId(Long soundTrackId);
}
