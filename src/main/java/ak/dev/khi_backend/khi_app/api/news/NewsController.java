package ak.dev.khi_backend.khi_app.api.news;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.service.news.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NewsController — Tiptap-aware news endpoints.
 *
 * All endpoints are now plain {@code application/json} — multipart upload
 * endpoints have been removed. The frontend uploads the cover image and any
 * inline media first via {@code POST /api/v1/media/upload}, then sends the
 * resulting URLs in the JSON body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    // ============================================================
    // CREATE
    // ============================================================

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> createNews(@RequestBody NewsDto dto) {
        NewsDto created = newsService.addNews(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully"));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<NewsDto>>> createNewsBulk(@RequestBody List<NewsDto> list) {
        List<NewsDto> created = newsService.addNewsBulk(list);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully (bulk)"));
    }

    // ============================================================
    // READ
    // ============================================================

    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<ApiResponse<Page<NewsDto>>> getAllNews(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news | page={} size={}", page, size);
        Page<NewsDto> result = newsService.getAllNews(page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "News fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsDto>> getNewsById(@PathVariable Long id) {
        log.info("GET /api/v1/news/{}", id);
        NewsDto dto = newsService.getNewsById(id);
        return ResponseEntity.ok(ApiResponse.success(dto, "News fetched successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> globalSearch(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news/search | q={} | page={} size={}", q, page, size);
        Page<NewsDto> result = newsService.globalSearch(q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Global search completed"));
    }

    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size
    ) {
        log.info("GET /api/v1/news/search/keyword | keyword={} lang={}", keyword, language);
        Page<NewsDto> result = newsService.searchByKeyword(keyword, language, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by keyword completed"));
    }

    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size
    ) {
        log.info("GET /api/v1/news/search/tag | tag={} lang={}", tag, language);
        Page<NewsDto> result = newsService.searchByTag(tag, language, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by tag completed"));
    }

    @GetMapping("/search/category")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news/search/category | name={}", name);
        Page<NewsDto> result = newsService.searchByCategory(name, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by category completed"));
    }

    @GetMapping("/search/subcategory")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchBySubCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news/search/subcategory | name={}", name);
        Page<NewsDto> result = newsService.searchBySubCategory(name, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by subcategory completed"));
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> updateNews(
            @PathVariable Long id,
            @RequestBody NewsDto dto
    ) {
        NewsDto updated = newsService.updateNews(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "News updated successfully"));
    }

    // ============================================================
    // DELETE
    // ============================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable Long id) {
        log.info("DELETE /api/v1/news/{}", id);
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNewsAlt(@PathVariable Long id) {
        log.info("DELETE /api/v1/news/delete/{}", id);
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully"));
    }

    @DeleteMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteNewsBulk(@RequestBody List<Long> newsIds) {
        log.info("DELETE /api/v1/news/bulk | count={}", newsIds != null ? newsIds.size() : 0);
        newsService.deleteNewsBulk(newsIds);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully (bulk)"));
    }
}
