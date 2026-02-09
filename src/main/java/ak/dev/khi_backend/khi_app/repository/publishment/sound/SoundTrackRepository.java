package ak.dev.khi_backend.khi_app.repository.publishment.sound;

import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoundTrackRepository extends JpaRepository<SoundTrack , Long> {
}
