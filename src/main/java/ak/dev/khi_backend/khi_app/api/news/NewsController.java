package ak.dev.khi_backend.khi_app.api.news;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.service.news.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE (multipart: news + coverImage + mediaFiles[])
    // ============================================================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> createNews(
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles
    ) throws Exception {

        // keep String parsing to be compatible with Postman "text" part
        NewsDto newsDto = objectMapper.readValue(newsJson, NewsDto.class);

        NewsDto created = newsService.addNews(newsDto, coverImage, mediaFiles);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully"));
    }

    // ============================================================
    // CREATE BULK (json only)
    // ============================================================
    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<NewsDto>>> createNewsBulk(
            @RequestBody List<NewsDto> newsDtoList
    ) {
        List<NewsDto> created = newsService.addNewsBulk(newsDtoList);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "News created successfully (bulk)"));
    }

    // ============================================================
    // GET ALL
    // ============================================================
    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<ApiResponse<List<NewsDto>>> getAllNews() {
        List<NewsDto> newsList = newsService.getAllNews();
        return ResponseEntity.ok(ApiResponse.success(newsList, "News fetched successfully"));
    }

    // ============================================================
    // SEARCH KEYWORD
    // ============================================================
    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<List<NewsDto>>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "both") String language
    ) {
        List<NewsDto> results = newsService.searchByKeyword(keyword, language);
        return ResponseEntity.ok(ApiResponse.success(results, "Search by keyword completed"));
    }

    // ============================================================
    // SEARCH TAG
    // ============================================================
    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<List<NewsDto>>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "both") String language
    ) {
        List<NewsDto> results = newsService.searchByTag(tag, language);
        return ResponseEntity.ok(ApiResponse.success(results, "Search by tag completed"));
    }

    // ============================================================
    // SEARCH MULTI TAGS
    // ============================================================
    @PostMapping(value = "/search/tags", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<NewsDto>>> searchByTags(
            @RequestBody Set<String> tags,
            @RequestParam(defaultValue = "both") String language
    ) {
        List<NewsDto> results = newsService.searchByTags(tags, language);
        return ResponseEntity.ok(ApiResponse.success(results, "Search by tags completed"));
    }

    // ============================================================
    // UPDATE (multipart)
    // ============================================================
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsDto>> updateNews(
            @PathVariable Long id,
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles
    ) throws Exception {

        NewsDto dto = objectMapper.readValue(newsJson, NewsDto.class);

        NewsDto updated = newsService.updateNews(id, dto, coverImage, mediaFiles);

        return ResponseEntity.ok(ApiResponse.success(updated, "News updated successfully"));
    }

    // ============================================================
    // DELETE
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable Long id) {
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully"));
    }

    // ============================================================
    // BULK DELETE
    // ============================================================
    @DeleteMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteNewsBulk(@RequestBody List<Long> newsIds) {
        newsService.deleteNewsBulk(newsIds);
        return ResponseEntity.ok(ApiResponse.success(null, "News deleted successfully (bulk)"));
    }
}
