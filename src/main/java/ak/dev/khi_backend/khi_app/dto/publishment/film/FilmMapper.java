package ak.dev.khi_backend.khi_app.dto.publishment.film;
import ak.dev.khi_backend.khi_app.enums.publishment.FilmType;
import ak.dev.khi_backend.khi_app.model.publishment.film.Film;
import ak.dev.khi_backend.khi_app.model.publishment.film.FilmContent;
import ak.dev.khi_backend.khi_app.model.publishment.film.FilmLog;

import java.util.LinkedHashSet;
import java.util.Set;

public class FilmMapper {

    private FilmMapper() {}

    // ─── DTO → Entity (coverUrl & sourceUrl set by service after S3 upload) ──
    public static Film toEntity(FilmDTO dto) {
        return Film.builder()
                .ckbContent(toContentEntity(dto.getCkbContent()))
                .kmrContent(toContentEntity(dto.getKmrContent()))
                .filmType(FilmType.valueOf(dto.getFilmType().toUpperCase()))
                .fileFormat(dto.getFileFormat())
                .durationSeconds(dto.getDurationSeconds())
                .publishmentDate(dto.getPublishmentDate())
                .resolution(dto.getResolution())
                .fileSizeMb(dto.getFileSizeMb())
                .contentLanguages(safeSet(dto.getContentLanguages()))
                .tagsCkb(safeSet(dto.getTagsCkb()))
                .tagsKmr(safeSet(dto.getTagsKmr()))
                .keywordsCkb(safeSet(dto.getKeywordsCkb()))
                .keywordsKmr(safeSet(dto.getKeywordsKmr()))
                .build();
    }

    // ─── Entity → DTO ─────────────────────────────────────────────────
    public static FilmDTO toDTO(Film film) {
        return FilmDTO.builder()
                .id(film.getId())
                .coverUrl(film.getCoverUrl())
                .sourceUrl(film.getSourceUrl())
                .ckbContent(toContentDTO(film.getCkbContent()))
                .kmrContent(toContentDTO(film.getKmrContent()))
                .filmType(film.getFilmType().name())
                .fileFormat(film.getFileFormat())
                .durationSeconds(film.getDurationSeconds())
                .durationFormatted(formatDuration(film.getDurationSeconds()))
                .publishmentDate(film.getPublishmentDate())
                .resolution(film.getResolution())
                .fileSizeMb(film.getFileSizeMb())
                .contentLanguages(film.getContentLanguages())
                .tagsCkb(film.getTagsCkb())
                .tagsKmr(film.getTagsKmr())
                .keywordsCkb(film.getKeywordsCkb())
                .keywordsKmr(film.getKeywordsKmr())
                .createdAt(film.getCreatedAt())
                .updatedAt(film.getUpdatedAt())
                .build();
    }

    // ─── Update entity from DTO (preserves existing S3 URLs if not re-uploaded) ──
    public static void updateEntity(Film film, FilmDTO dto) {
        film.setCkbContent(toContentEntity(dto.getCkbContent()));
        film.setKmrContent(toContentEntity(dto.getKmrContent()));
        film.setFilmType(FilmType.valueOf(dto.getFilmType().toUpperCase()));
        film.setFileFormat(dto.getFileFormat());
        film.setDurationSeconds(dto.getDurationSeconds());
        film.setPublishmentDate(dto.getPublishmentDate());
        film.setResolution(dto.getResolution());
        film.setFileSizeMb(dto.getFileSizeMb());

        updateSet(film.getContentLanguages(), dto.getContentLanguages());
        updateSet(film.getTagsCkb(), dto.getTagsCkb());
        updateSet(film.getTagsKmr(), dto.getTagsKmr());
        updateSet(film.getKeywordsCkb(), dto.getKeywordsCkb());
        updateSet(film.getKeywordsKmr(), dto.getKeywordsKmr());
    }

    // ─── FilmLog → DTO ───────────────────────────────────────────────
    public static FilmLogDTO toLogDTO(FilmLog log) {
        return FilmLogDTO.builder()
                .id(log.getId())
                .filmId(log.getFilmId())
                .filmTitle(log.getFilmTitle())
                .action(log.getAction())
                .details(log.getDetails())
                .performedBy(log.getPerformedBy())
                .timestamp(log.getTimestamp())
                .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private static FilmContent toContentEntity(FilmContentDTO dto) {
        if (dto == null) return null;
        return FilmContent.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .topic(dto.getTopic())
                .location(dto.getLocation())
                .director(dto.getDirector())
                .producer(dto.getProducer())
                .build();
    }

    private static FilmContentDTO toContentDTO(FilmContent content) {
        if (content == null) return null;
        return FilmContentDTO.builder()
                .title(content.getTitle())
                .description(content.getDescription())
                .topic(content.getTopic())
                .location(content.getLocation())
                .director(content.getDirector())
                .producer(content.getProducer())
                .build();
    }

    private static <T> LinkedHashSet<T> safeSet(Set<T> set) {
        return set != null ? new LinkedHashSet<>(set) : new LinkedHashSet<>();
    }

    private static <T> void updateSet(Set<T> target, Set<T> source) {
        if (source != null) {
            target.clear();
            target.addAll(source);
        }
    }

    private static String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return null;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}