package ak.dev.khi_backend.khi_app.repository.publishment.sound;

import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundReklamVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SoundReklamVideoRepository extends JpaRepository<SoundReklamVideo, Long> {
    Optional<SoundReklamVideo> findTopByOrderByIdAsc();
}
