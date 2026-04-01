package ak.dev.khi_backend.khi_app.dto.search;

import lombok.*;

import java.util.Collections;
import java.util.List;

/**
 * Response envelope for the unified global search endpoint.
 *
 * Each content type has its own paginated section.
 * When a specific type is requested (e.g. type=NEWS), only that
 * section is populated — the rest remain null.
 *
 * Vue usage example:
 *
 *   const res = await api.get('/api/v1/search', { params: { q, page, size } })
 *   const { projects, news, videos, writings, soundTracks, imageCollections } = res.data.data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {

    /** The raw search term sent by the client. */
    private String query;

    /** 0-based page index requested. */
    private int page;

    /** Items per page requested. */
    private int size;

    /**
     * Content scope of this response.
     * Values: ALL | PROJECT | NEWS | VIDEO | WRITING | SOUNDTRACK | IMAGE
     */
    private String type;

    // ─── Sections (null when that type was not searched) ──────────────────────

    private SearchSection projects;
    private SearchSection news;
    private SearchSection videos;
    private SearchSection writings;
    private SearchSection soundTracks;
    private SearchSection imageCollections;

    // ─── Inner — one paginated section for a single content type ──────────────

    /**
     * A single content-type result page.
     *
     * Mirrors Spring's {@code Page} shape so Vue can use
     * the same pagination component for every section.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchSection {

        private List<SearchItem> items;

        /** Total matching records in the database for this type + query. */
        private long totalElements;

        /** Total pages available ( ceil(totalElements / size) ). */
        private int totalPages;

        /** 0-based index of this page. */
        private int currentPage;

        /** Number of items per page. */
        private int size;

        /** Convenience factory for an empty section (no results). */
        public static SearchSection empty(int page, int size) {
            return SearchSection.builder()
                    .items(Collections.emptyList())
                    .totalElements(0)
                    .totalPages(0)
                    .currentPage(page)
                    .size(size)
                    .build();
        }
    }
}