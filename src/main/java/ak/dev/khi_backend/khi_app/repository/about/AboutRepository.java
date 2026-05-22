package ak.dev.khi_backend.khi_app.repository.about;

import ak.dev.khi_backend.khi_app.model.about.About;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AboutRepository extends JpaRepository<About, Long> {

    // ─── Single slug lookups (for uniqueness validation) ──────────────────────

    Optional<About> findBySlugCkb(String slugCkb);

    Optional<About> findBySlugKmr(String slugKmr);

    // ─── Public lookup: match either slug ─────────────────────────────────────

    /**
     * Find a page by its CKB slug OR its KMR slug.
     * Used by the public-facing {@code getBySlug(slug)} endpoint so callers
     * can pass whichever language slug they have.
     */
    Optional<About> findBySlugCkbOrSlugKmr(String slugCkb, String slugKmr);

    // ─── Existence checks ─────────────────────────────────────────────────────

    boolean existsBySlugCkb(String slugCkb);

    boolean existsBySlugKmr(String slugKmr);
}
