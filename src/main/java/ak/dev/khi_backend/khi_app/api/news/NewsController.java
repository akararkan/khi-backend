package ak.dev.khi_backend.khi_app.api.news;

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

    /**
     * ✅ CREATE SINGLE NEWS with form data
     * Files: coverImage, mediaFiles (form-data)
     * Data: news (JSON string in form-data)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsDto> createNews(
            @RequestPart("news") String newsJson,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestHeader(value = "X-User-ID", defaultValue = "SYSTEM") String userId) {

        log.info("📰 Received request to create news");

        try {
            NewsDto newsDto = objectMapper.readValue(newsJson, NewsDto.class);
            NewsDto created = newsService.addNews(newsDto, coverImage, mediaFiles, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("❌ Failed to create news", e);
            throw new RuntimeException("Failed to create news: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ CREATE NEWS IN BULK (without files - URLs already in S3)
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<NewsDto>> createNewsBulk(
            @RequestBody List<NewsDto> newsDtoList,
            @RequestHeader(value = "X-User-ID", defaultValue = "SYSTEM") String userId) {

        log.info("📰 Received bulk news creation request - Count: {}", newsDtoList.size());
        List<NewsDto> created = newsService.addNewsBulk(newsDtoList, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * ✅ GET ALL NEWS - Fixed endpoint with explicit path
     */
    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<List<NewsDto>> getAllNews() {
        log.info("📋 Fetching all news");
        List<NewsDto> newsList = newsService.getAllNews();
        return ResponseEntity.ok(newsList);
    }

    /**
     * ✅ SEARCH BY KEYWORD
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<List<NewsDto>> searchByKeyword(@RequestParam String keyword) {
        log.info("🔍 Searching news by keyword: {}", keyword);
        List<NewsDto> results = newsService.searchByKeyword(keyword);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ SEARCH BY TAG
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<NewsDto>> searchByTag(@RequestParam String tag) {
        log.info("🏷️ Searching news by tag: {}", tag);
        List<NewsDto> results = newsService.searchByTag(tag);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ SEARCH BY MULTIPLE TAGS
     */
    @PostMapping("/search/tags")
    public ResponseEntity<List<NewsDto>> searchByTags(@RequestBody Set<String> tags) {
        log.info("🏷️ Searching news by tags: {}", tags);
        List<NewsDto> results = newsService.searchByTags(tags);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ UPDATE NEWS with form data
     * Files: coverImage, mediaFiles (optional, form-data)
     * Data: news (JSON string in form-data)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsDto> updateNews(
            @PathVariable Long id,
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestHeader(value = "X-User-ID", defaultValue = "SYSTEM") String userId) {

        log.info("✏️ Updating news - ID: {}", id);

        try {
            NewsDto newsDto = objectMapper.readValue(newsJson, NewsDto.class);
            NewsDto updated = newsService.updateNews(id, newsDto, coverImage, mediaFiles, userId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("❌ Failed to update news", e);
            throw new RuntimeException("Failed to update news: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ DELETE NEWS
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNews(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-ID", defaultValue = "SYSTEM") String userId) {

        log.info("🗑️ Deleting news - ID: {}", id);
        newsService.deleteNews(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * ✅ BULK DELETE
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteNewsBulk(
            @RequestBody List<Long> newsIds,
            @RequestHeader(value = "X-User-ID", defaultValue = "SYSTEM") String userId) {

        log.info("🗑️ Bulk deleting {} news items", newsIds.size());
        newsService.deleteNewsBulk(newsIds, userId);
        return ResponseEntity.noContent().build();
    }
}