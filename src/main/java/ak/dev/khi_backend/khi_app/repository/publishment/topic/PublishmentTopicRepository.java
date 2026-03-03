package ak.dev.khi_backend.khi_app.repository.publishment.topic;

import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PublishmentTopicRepository
 *
 * ─── Autocomplete ─────────────────────────────────────────────────────────────
 * The frontend sends whatever the admin typed in the topic field.
 * The backend calls searchByNameCkb / searchByNameKmr with that text
 * (partial, case-insensitive LIKE), and returns the matching topics so the
 * admin can pick an existing one instead of creating a near-duplicate.
 *
 * entityType values:  "VIDEO" | "SOUND" | "IMAGE" | "WRITING"
 */
@Repository
public interface PublishmentTopicRepository extends JpaRepository<PublishmentTopic, Long> {
    List<PublishmentTopic> findByEntityType(String entityType);
}
