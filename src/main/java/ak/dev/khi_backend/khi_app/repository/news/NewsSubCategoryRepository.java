package ak.dev.khi_backend.khi_app.repository.news;

import ak.dev.khi_backend.khi_app.model.news.NewsCategory;
import ak.dev.khi_backend.khi_app.model.news.NewsSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsSubCategoryRepository extends JpaRepository<NewsSubCategory  , Long> {
    Optional<NewsSubCategory> findByCategoryAndName(NewsCategory category, String name);
}
