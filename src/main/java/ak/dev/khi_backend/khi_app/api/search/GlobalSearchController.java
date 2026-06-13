package ak.dev.khi_backend.khi_app.api.search;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.search.GlobalSearchResponse;
import ak.dev.khi_backend.khi_app.service.search.GlobalSearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalSearchController
 *
 * ONE endpoint that searches all content types simultaneously.
 *
 * ─── Endpoint ─────────────────────────────────────────────────────────────────
 *
 *  GET /api/v1/search
 *
 * ─── Query parameters ─────────────────────────────────────────────────────────
 *
 *  q       (required)  — the search term
 *                        examples: "کوردستان"  "هاوار"  "music"
 *
 *  type    (optional)  — filter to a single content type (default: ALL)
 *                        values: ALL | PROJECT | NEWS | VIDEO | WRITING | SOUNDTRACK | IMAGE
 *
 *  page    (optional)  — 0-based page index (default: 0)
 *
 *  size    (optional)  — items PER SECTION per page (default: 10)
 *                        with ALL types and size=10 → up to 60 items total
 *
 * ─── Examples ─────────────────────────────────────────────────────────────────
 *
 *  Search everything:
 *    GET /api/v1/search?q=کوردستان
 *
 *  Search everything, page 2:
 *    GET /api/v1/search?q=کوردستان&page=1&size=10
 *
 *  Search only news:
 *    GET /api/v1/search?q=کوردستان&type=NEWS
 *
 *  Search only soundtracks:
 *    GET /api/v1/search?q=هاوار&type=SOUNDTRACK&page=0&size=20
 *
 * ─── Response shape ───────────────────────────────────────────────────────────
 *
 *  {
 *    "success": true,
 *    "message": "Search completed",
 *    "data": {
 *      "query": "کوردستان",
 *      "page": 0,
 *      "size": 10,
 *      "type": "ALL",
 *      "projects": {
 *        "items": [ { "id":1, "type":"PROJECT", "titleCkb":"...", ... } ],
 *        "totalElements": 12,
 *        "totalPages": 2,
 *        "currentPage": 0,
 *        "size": 10
 *      },
 *      "news":            { "items": [...], "totalElements": 45, ... },
 *      "videos":          { "items": [...], "totalElements": 5,  ... },
 *      "writings":        { "items": [...], "totalElements": 8,  ... },
 *      "soundTracks":     { "items": [...], "totalElements": 3,  ... },
 *      "imageCollections":{ "items": [...], "totalElements": 7,  ... }
 *    }
 *  }
 *
 *  When type=NEWS only the "news" section is present; the rest are null.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Global Search", description = "One endpoint searching every content type at once")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "10")  int size
    ) {
        log.info("GET /api/v1/search | q='{}' type={} page={} size={}", q, type, page, size);

        GlobalSearchResponse result = globalSearchService.search(q, type, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search completed")
        );
    }
}