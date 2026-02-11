package ak.dev.khi_backend.khi_app.service.publishment.film;


import ak.dev.khi_backend.khi_app.dto.publishment.film.FilmDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.film.FilmLogDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.film.FilmMapper;
import ak.dev.khi_backend.khi_app.model.publishment.film.Film;
import ak.dev.khi_backend.khi_app.model.publishment.film.FilmLog;
import ak.dev.khi_backend.khi_app.repository.publishment.film.FilmLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.film.FilmRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilmService {

    private final FilmRepository filmRepository;
    private final FilmLogRepository filmLogRepository;
    private final S3Service s3Service;

    private static final String ACTION_CREATED = "CREATED";
    private static final String ACTION_UPDATED = "UPDATED";
    private static final String ACTION_DELETED = "DELETED";

    // ═══════════════════════════════════════════════════════════════════
    //  1. ADD
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public FilmDTO addFilm(FilmDTO dto, MultipartFile coverFile, MultipartFile filmFile) {
        Film film = FilmMapper.toEntity(dto);

        if (coverFile != null && !coverFile.isEmpty()) {
            film.setCoverUrl(uploadToS3(coverFile));
        }
        if (filmFile != null && !filmFile.isEmpty()) {
            film.setSourceUrl(uploadToS3(filmFile));
        }

        Film saved = filmRepository.save(film);
        logAction(saved.getId(), getFilmTitle(saved), ACTION_CREATED,
                "Film created with type: " + saved.getFilmType().name());

        return FilmMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. GET ALL
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<FilmDTO> getAllFilms(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return filmRepository.findAll(pageable).map(FilmMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. SEARCH BY KEYWORDS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<FilmDTO> searchByKeyword(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return filmRepository.searchByKeyword(keyword.trim(), pageable).map(FilmMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. SEARCH BY TAGS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<FilmDTO> searchByTag(String tag, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return filmRepository.searchByTag(tag.trim(), pageable).map(FilmMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. UPDATE
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public FilmDTO updateFilm(Long id, FilmDTO dto, MultipartFile coverFile, MultipartFile filmFile) {
        Film film = findFilmOrThrow(id);
        FilmMapper.updateEntity(film, dto);

        if (coverFile != null && !coverFile.isEmpty()) {
            film.setCoverUrl(uploadToS3(coverFile));
        }
        if (filmFile != null && !filmFile.isEmpty()) {
            film.setSourceUrl(uploadToS3(filmFile));
        }

        Film updated = filmRepository.save(film);
        logAction(updated.getId(), getFilmTitle(updated), ACTION_UPDATED, "Film updated");

        return FilmMapper.toDTO(updated);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. DELETE
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public void deleteFilm(Long id) {
        Film film = findFilmOrThrow(id);
        String title = getFilmTitle(film);
        filmRepository.delete(film);
        logAction(id, title, ACTION_DELETED, "Film deleted permanently");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LOGS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<FilmLogDTO> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return filmLogRepository.findAllByOrderByTimestampDesc(pageable).map(FilmMapper::toLogDTO);
    }

    @Transactional(readOnly = true)
    public Page<FilmLogDTO> getLogsByFilmId(Long filmId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return filmLogRepository.findByFilmIdOrderByTimestampDesc(filmId, pageable).map(FilmMapper::toLogDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private Film findFilmOrThrow(Long id) {
        return filmRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Film not found with id: " + id));
    }

    private String uploadToS3(MultipartFile file) {
        try {
            return s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            log.error("Failed to upload file to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    private void logAction(Long filmId, String filmTitle, String action, String details) {
        filmLogRepository.save(FilmLog.builder()
                .filmId(filmId)
                .filmTitle(filmTitle)
                .action(action)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private String getFilmTitle(Film film) {
        if (film.getCkbContent() != null && film.getCkbContent().getTitle() != null) {
            return film.getCkbContent().getTitle();
        }
        if (film.getKmrContent() != null && film.getKmrContent().getTitle() != null) {
            return film.getKmrContent().getTitle();
        }
        return "Untitled Film";
    }
}