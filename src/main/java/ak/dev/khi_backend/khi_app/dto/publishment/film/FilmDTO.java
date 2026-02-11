package ak.dev.khi_backend.khi_app.dto.publishment.film;

import ak.dev.khi_backend.khi_app.enums.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilmDTO {

    // ─── Response only ────────────────────────────────────────────────
    private Long id;

    // ─── Bilingual content ────────────────────────────────────────────
    private FilmContentDTO ckbContent;
    private FilmContentDTO kmrContent;

    // ─── Film metadata ────────────────────────────────────────────────
    private String filmType;          // DOCUMENTARY, EVIDENCE, SHORT_FILM, etc.
    private String fileFormat;        // mp4, mkv, avi
    private Integer durationSeconds;
    private LocalDate publishmentDate;

    // ─── Extra fields ─────────────────────────────────────────────────
    private String resolution;        // 720p, 1080p, 4K
    private Double fileSizeMb;

    // ─── S3 URLs (set by service after upload, returned in response) ──
    private String coverUrl;
    private String sourceUrl;

    // ─── Response only — formatted duration ───────────────────────────
    private String durationFormatted;

    // ─── Languages ────────────────────────────────────────────────────
    private Set<Language> contentLanguages;

    // ─── Tags & Keywords (bilingual) ──────────────────────────────────
    private Set<String> tagsCkb;
    private Set<String> tagsKmr;
    private Set<String> keywordsCkb;
    private Set<String> keywordsKmr;

    // ─── Response only — timestamps ───────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}