package ak.dev.khi_backend.khi_app.repository.publishment.image;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollectionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageCollectionLogRepository extends JpaRepository<ImageCollectionLog, Long> {

    Page<ImageCollectionLog> findByImageCollectionIdOrderByTimestampDesc(Long imageCollectionId, Pageable pageable);

    Page<ImageCollectionLog> findAllByOrderByTimestampDesc(Pageable pageable);
}