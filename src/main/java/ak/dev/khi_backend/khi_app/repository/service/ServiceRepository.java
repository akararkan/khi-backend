// ─────────────────────────────────────────────────────────────────────────────
// FILE 1 — ServiceRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package ak.dev.khi_backend.khi_app.repository.service;

import ak.dev.khi_backend.khi_app.model.service.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    /** All active services, ordered by publishedAt DESC (latest first). */
    List<Service> findByActiveTrueOrderByPublishedAtDesc();

    /** Filter active services by type (case-insensitive). */
    List<Service> findByActiveTrueAndServiceTypeIgnoreCaseOrderByPublishedAtDesc(String serviceType);

    /**
     * Fetch a service together with its contents and collections
     * in one query to avoid N+1 when building the full response.
     */
    @Query("""
            SELECT DISTINCT s FROM Service s
            LEFT JOIN FETCH s.contents
            LEFT JOIN FETCH s.mediaCollections mc
            LEFT JOIN FETCH mc.files
            WHERE s.id = :id
            """)
    java.util.Optional<Service> findByIdWithAll(@Param("id") Long id);
}


