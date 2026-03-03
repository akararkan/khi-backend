package ak.dev.khi_backend.khi_app.repository.news;

import ak.dev.khi_backend.khi_app.model.news.NewsAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsAuditLogRepository extends JpaRepository<NewsAuditLog , Long> {
}
