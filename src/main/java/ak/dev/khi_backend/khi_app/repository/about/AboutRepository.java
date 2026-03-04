package ak.dev.khi_backend.khi_app.repository.about;


import ak.dev.khi_backend.khi_app.model.about.About;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AboutRepository extends JpaRepository<About, Long> {

    Optional<About> findBySlug(String slug);

    Optional<About> findBySlugAndActiveTrue(String slug);

    @Query("SELECT a FROM About a LEFT JOIN FETCH a.blocks WHERE a.slug = :slug")
    Optional<About> findBySlugWithBlocks(@Param("slug") String slug);

    boolean existsBySlug(String slug);
}