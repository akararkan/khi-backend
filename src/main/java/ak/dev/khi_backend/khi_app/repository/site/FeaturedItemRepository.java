package ak.dev.khi_backend.khi_app.repository.site;

import ak.dev.khi_backend.khi_app.model.site.FeaturedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturedItemRepository extends JpaRepository<FeaturedItem, Long> {
    List<FeaturedItem> findAllByActiveTrueAndLocaleIgnoreCaseOrderByDisplayOrderAsc(String locale);
    List<FeaturedItem> findAllByActiveTrueAndLocaleIsNullOrderByDisplayOrderAsc();
    List<FeaturedItem> findAllByActiveTrueOrderByDisplayOrderAsc();
}
