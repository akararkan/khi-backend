package ak.dev.khi_backend.khi_app.repository.publishment.film;


import ak.dev.khi_backend.khi_app.model.publishment.film.FilmLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmLogRepository extends JpaRepository<FilmLog, Long> {

    Page<FilmLog> findByFilmIdOrderByTimestampDesc(Long filmId, Pageable pageable);

    Page<FilmLog> findAllByOrderByTimestampDesc(Pageable pageable);
}