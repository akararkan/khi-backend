package ak.dev.khi_backend.khi_app.repository.service;

import ak.dev.khi_backend.khi_app.model.service.ServiceMediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceMediaFileRepository extends JpaRepository<ServiceMediaFile, Long> {

    List<ServiceMediaFile> findByCollectionIdOrderBySortOrderAsc(Long collectionId);
}