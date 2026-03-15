package ak.dev.khi_backend.khi_app.api.news;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.service.news.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService  newsService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE — multipart /with-files  (accepts both field name variants)
    // ============================================================

    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> createNewsWithFiles(
            @RequestPart(value = "news",        required = false) String newsJson,
            @RequestPart(value = "data",        required = false) String dataJson,
            @RequestPart(value = "coverImage",  required = false) MultipartFile coverImage,
            @RequestPart(value = "cover",       required = false) MultipartFile cover,
            @RequestPart(value = "mediaFiles",  required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "media",       required = false) List<MultipartFile> media
    ) throws Exception {

        String json = newsJson != null ? newsJson : dataJson;
        if (json == null) {
            throw new BadRequestException("error.validation", Map.of("field", "news or data"));
        }

        NewsDto dto       = objectMapper.readValue(json, NewsDto.class);
        MultipartFile    coverFile = coverImage != null ? coverImage : cover;
        List<MultipartFile> files = (mediaFiles != null && !mediaFiles.isEmpty()) ? mediaFiles : media;

        NewsDto created = newsService.addNews(dto, coverFile, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully"));
    }

    // ============================================================
    // CREATE — simple multipart
    // ============================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> createNews(
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles
    ) throws Exception {

        NewsDto dto     = objectMapper.readValue(newsJson, NewsDto.class);
        NewsDto created = newsService.addNews(dto, coverImage, mediaFiles);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully"));
    }

    // ============================================================
    // CREATE BULK — JSON only
    // ============================================================

    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<NewsDto>>> createNewsBulk(
            @RequestBody List<NewsDto> list
    ) {
        List<NewsDto> created = newsService.addNewsBulk(list);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully (bulk)"));
    }

    // ============================================================
    // GET ALL — paginated
    //
    // GET /api/v1/news?page=0&size=20
    // GET /api/v1/news/all?page=0&size=20
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

    // ============================================================
    // GET BY ID
    // ============================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsDto>> getNewsById(@PathVariable Long id) {
        log.info("GET /api/v1/news/{}", id);
        NewsDto dto = newsService.getNewsById(id);
        return ResponseEntity.ok(ApiResponse.success(dto, "News fetched successfully"));
    }

    // ============================================================
    // GLOBAL SEARCH — searches title + description + tags + keywords
    //
    // GET /api/v1/news/search?q=کوردستان&page=0&size=20
    // ============================================================

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

    // ============================================================
    // SEARCH BY KEYWORD — language-aware, paginated
    //
    // GET /api/v1/news/search/keyword?keyword=کوردستان&language=ckb&page=0&size=20
    // language: ckb | kmr | both (default)
    // ============================================================

    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size
    ) {
        log.info("GET /api/v1/news/search/keyword | keyword={} lang={} | page={} size={}",
                keyword, language, page, size);
        Page<NewsDto> result = newsService.searchByKeyword(keyword, language, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by keyword completed"));
    }

    // ============================================================
    // SEARCH BY TAG — language-aware, paginated
    //
    // GET /api/v1/news/search/tag?tag=کوردستان&language=kmr&page=0&size=20
    // language: ckb | kmr | both (default)
    // ============================================================

    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size
    ) {
        log.info("GET /api/v1/news/search/tag | tag={} lang={} | page={} size={}",
                tag, language, page, size);
        Page<NewsDto> result = newsService.searchByTag(tag, language, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by tag completed"));
    }

    // ============================================================
    // SEARCH BY CATEGORY — paginated
    //
    // GET /api/v1/news/search/category?name=سیاسی&page=0&size=20
    // ============================================================

    @GetMapping("/search/category")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchByCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news/search/category | name={} | page={} size={}", name, page, size);
        Page<NewsDto> result = newsService.searchByCategory(name, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by category completed"));
    }

    // ============================================================
    // SEARCH BY SUBCATEGORY — paginated
    //
    // GET /api/v1/news/search/subcategory?name=ئابووری&page=0&size=20
    // ============================================================

    @GetMapping("/search/subcategory")
    public ResponseEntity<ApiResponse<Page<NewsDto>>> searchBySubCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/news/search/subcategory | name={} | page={} size={}", name, page, size);
        Page<NewsDto> result = newsService.searchBySubCategory(name, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Search by subcategory completed"));
    }

    // ============================================================
    // UPDATE — /update/{id}/with-files  (accepts both field name variants)
    // ============================================================

    @PutMapping(value = "/update/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> updateNewsWithFilesAlt(
            @PathVariable Long id,
            @RequestPart(value = "news",       required = false) String newsJson,
            @RequestPart(value = "data",       required = false) String dataJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "cover",      required = false) MultipartFile cover,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "media",      required = false) List<MultipartFile> media
    ) throws Exception {

        String json = newsJson != null ? newsJson : dataJson;
        if (json == null) {
            throw new BadRequestException("error.validation", Map.of("field", "news or data"));
        }

        NewsDto dto       = objectMapper.readValue(json, NewsDto.class);
        MultipartFile    coverFile = coverImage != null ? coverImage : cover;
        List<MultipartFile> files = (mediaFiles != null && !mediaFiles.isEmpty()) ? mediaFiles : media;

        NewsDto updated = newsService.updateNews(id, dto, coverFile, files);
        return ResponseEntity.ok(ApiResponse.success(updated, "News updated successfully"));
    }

    // ============================================================
    // UPDATE — /{id}/with-files
    // ============================================================

    @PutMapping(value = "/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> updateNewsWithFiles(
            @PathVariable Long id,
            @RequestPart(value = "news",       required = false) String newsJson,
            @RequestPart(value = "data",       required = false) String dataJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "cover",      required = false) MultipartFile cover,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "media",      required = false) List<MultipartFile> media
    ) throws Exception {

        String json = newsJson != null ? newsJson : dataJson;
        if (json == null) {
            throw new BadRequestException("error.validation", Map.of("field", "news or data"));
        }

        NewsDto dto       = objectMapper.readValue(json, NewsDto.class);
        MultipartFile    coverFile = coverImage != null ? coverImage : cover;
        List<MultipartFile> files = (mediaFiles != null && !mediaFiles.isEmpty()) ? mediaFiles : media;

        NewsDto updated = newsService.updateNews(id, dto, coverFile, files);
        return ResponseEntity.ok(ApiResponse.success(updated, "News updated successfully"));
    }

    // ============================================================
    // UPDATE — simple multipart /{id}
    // ============================================================

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> updateNews(
            @PathVariable Long id,
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles
    ) throws Exception {

        NewsDto dto     = objectMapper.readValue(newsJson, NewsDto.class);
        NewsDto updated = newsService.updateNews(id, dto, coverImage, mediaFiles);
        return ResponseEntity.ok(ApiResponse.success(updated, "News updated successfully"));
    }

    // ============================================================
    // DELETE — /delete/{id}  and  /{id}
    // ============================================================

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNewsAlt(@PathVariable Long id) {
        log.info("DELETE /api/v1/news/delete/{}", id);
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable Long id) {
        log.info("DELETE /api/v1/news/{}", id);
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully"));
    }

    // ============================================================
    // BULK DELETE
    // ============================================================

    @DeleteMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteNewsBulk(@RequestBody List<Long> newsIds) {
        log.info("DELETE /api/v1/news/bulk | count={}", newsIds != null ? newsIds.size() : 0);
        newsService.deleteNewsBulk(newsIds);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully (bulk)"));
    }
}