package ak.dev.khi_backend.khi_app.repository.publishment.video;

import ak.dev.khi_backend.khi_app.model.publishment.video.VideoLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoLogRepository extends JpaRepository<VideoLog, Long> {

    Page<VideoLog> findByVideoIdOrderByTimestampDesc(Long videoId, Pageable pageable);

    Page<VideoLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
