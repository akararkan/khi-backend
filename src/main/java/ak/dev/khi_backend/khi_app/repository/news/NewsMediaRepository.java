package ak.dev.khi_backend.khi_app.repository.news;

import ak.dev.khi_backend.khi_app.model.news.NewsMedia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsMediaRepository extends JpaRepository<NewsMedia , Long> {
}
