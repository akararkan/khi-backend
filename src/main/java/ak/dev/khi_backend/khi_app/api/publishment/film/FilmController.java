package ak.dev.khi_backend.khi_app.api.publishment.film;

import ak.dev.khi_backend.khi_app.dto.publishment.film.FilmDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.film.FilmLogDTO;
import ak.dev.khi_backend.khi_app.service.publishment.film.FilmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/films")
@RequiredArgsConstructor
public class FilmController {

    private final FilmService filmService;
    private final ObjectMapper objectMapper;

    // ─── 1. ADD ───────────────────────────────────────────────────────
    /**
     * Multipart:
     * - data: JSON string for FilmDTO (can include sourceUrl OR sourceExternalUrl OR sourceEmbedUrl)
     * - cover: optional file (or send coverUrl in data)
     * - film: optional file (or send sourceUrl/external/embed in data)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FilmDTO> addFilm(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cover", required = false) MultipartFile coverFile,
            @RequestPart(value = "film", required = false) MultipartFile filmFile
    ) throws Exception {
        FilmDTO dto = objectMapper.readValue(dataJson, FilmDTO.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(filmService.addFilm(dto, coverFile, filmFile));
    }

    // ─── 2. GET ALL ───────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FilmDTO>> getAllFilms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(filmService.getAllFilms(page, size));
    }

    // ─── 3. SEARCH BY KEYWORDS ────────────────────────────────────────
    @GetMapping(value = "/search/keyword", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FilmDTO>> searchByKeyword(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(filmService.searchByKeyword(q, page, size));
    }

    // ─── 4. SEARCH BY TAGS ───────────────────────────────────────────
    @GetMapping(value = "/search/tag", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FilmDTO>> searchByTag(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(filmService.searchByTag(q, page, size));
    }

    // ─── 5. UPDATE ────────────────────────────────────────────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FilmDTO> updateFilm(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cover", required = false) MultipartFile coverFile,
            @RequestPart(value = "film", required = false) MultipartFile filmFile
    ) throws Exception {
        FilmDTO dto = objectMapper.readValue(dataJson, FilmDTO.class);
        return ResponseEntity.ok(filmService.updateFilm(id, dto, coverFile, filmFile));
    }

    // ─── 6. DELETE ────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFilm(@PathVariable Long id) {
        filmService.deleteFilm(id);
        return ResponseEntity.noContent().build();
    }

    // ─── LOGS ─────────────────────────────────────────────────────────
    @GetMapping(value = "/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FilmLogDTO>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(filmService.getAllLogs(page, size));
    }

    @GetMapping(value = "/{id}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FilmLogDTO>> getLogsByFilmId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(filmService.getLogsByFilmId(id, page, size));
    }
}
