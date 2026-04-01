package ak.dev.khi_backend.khi_app.dto.search;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Lightweight search result — one item from any content type.
 *
 * Designed to be rendered in a Vue search-results list without
 * any further API calls. Contains just enough data for a card:
 * id, type, bilingual titles + description, cover image, date.
 *
 * type values: PROJECT | NEWS | VIDEO | WRITING | SOUNDTRACK | IMAGE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchItem {

    /** Database primary key of the original entity. */
    private Long id;

    /**
     * Content type discriminator — tells Vue which detail route to use.
     * Values: PROJECT | NEWS | VIDEO | WRITING | SOUNDTRACK | IMAGE
     */
    private String type;

    // ─── Bilingual titles ──────────────────────────────────────────────────────
    private String titleCkb;
    private String titleKmr;

    // ─── Short description (snippet) ──────────────────────────────────────────
    private String descriptionCkb;
    private String descriptionKmr;

    /**
     * First available cover image URL.
     * For entities with ckb/kmr split covers, the CKB cover is preferred;
     * falls back to KMR if CKB is absent.
     */
    private String coverUrl;

    /** ISO-8601 creation timestamp — used for "published X days ago" labels. */
    private LocalDateTime createdAt;
}