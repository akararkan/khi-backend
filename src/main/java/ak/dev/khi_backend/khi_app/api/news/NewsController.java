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
     *
     * Expected JSON format:
     * {
     *   "category": {"ckbName": "ئابووری", "kmrName": "Aborî"},
     *   "subCategory": {"ckbName": "بازاڕ", "kmrName": "Bazar"},
     *   "ckbContent": {"title": "...", "description": "..."},
     *   "kmrContent": {"title": "...", "description": "..."},
     *   "tags": {"ckb": ["..."], "kmr": ["..."]},
     *   "keywords": {"ckb": ["..."], "kmr": ["..."]}
     * }
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsDto> createNews(
            @RequestPart("news") String newsJson,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles) {

        log.info("📰 Received request to create bilingual news");

        try {
            NewsDto newsDto = objectMapper.readValue(newsJson, NewsDto.class);
            NewsDto created = newsService.addNews(newsDto, coverImage, mediaFiles);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("❌ Failed to create news", e);
            throw new RuntimeException("Failed to create news: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ CREATE NEWS IN BULK (without files - URLs already in S3)
     *
     * Expected JSON format: Array of news objects with bilingual structure
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<NewsDto>> createNewsBulk(@RequestBody List<NewsDto> newsDtoList) {
        log.info("📰 Received bulk news creation request - Count: {}", newsDtoList.size());
        List<NewsDto> created = newsService.addNewsBulk(newsDtoList);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * ✅ GET ALL NEWS
     * Returns all news with bilingual content
     */
    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<List<NewsDto>> getAllNews() {
        log.info("📋 Fetching all news");
        List<NewsDto> newsList = newsService.getAllNews();
        return ResponseEntity.ok(newsList);
    }

    /**
     * ✅ SEARCH BY KEYWORD (Language-Specific)
     *
     * @param keyword - Search term
     * @param language - "ckb" (Sorani), "kmr" (Kurmanji), or "both" (default: both)
     *
     * Examples:
     * - /api/v1/news/search/keyword?keyword=ئابووری&language=ckb
     * - /api/v1/news/search/keyword?keyword=Aborî&language=kmr
     * - /api/v1/news/search/keyword?keyword=economy (searches both languages)
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<List<NewsDto>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "both") String language) {

        log.info("🔍 Searching news by keyword: '{}' in language: {}", keyword, language);
        List<NewsDto> results = newsService.searchByKeyword(keyword, language);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ SEARCH BY TAG (Language-Specific)
     *
     * @param tag - Tag to search for
     * @param language - "ckb" (Sorani), "kmr" (Kurmanji), or "both" (default: both)
     *
     * Examples:
     * - /api/v1/news/search/tag?tag=بازاڕ&language=ckb
     * - /api/v1/news/search/tag?tag=Bazar&language=kmr
     * - /api/v1/news/search/tag?tag=market (searches both languages)
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<NewsDto>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "both") String language) {

        log.info("🏷️ Searching news by tag: '{}' in language: {}", tag, language);
        List<NewsDto> results = newsService.searchByTag(tag, language);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ SEARCH BY MULTIPLE TAGS (Language-Specific)
     *
     * @param tags - Set of tags to search for
     * @param language - "ckb" (Sorani), "kmr" (Kurmanji), or "both" (default: both)
     *
     * Request Body: ["tag1", "tag2", "tag3"]
     *
     * Examples:
     * - POST /api/v1/news/search/tags?language=ckb
     *   Body: ["ئابووری", "بازاڕ"]
     * - POST /api/v1/news/search/tags?language=kmr
     *   Body: ["Aborî", "Bazar"]
     * - POST /api/v1/news/search/tags (searches both languages)
     *   Body: ["economy", "market"]
     */
    @PostMapping("/search/tags")
    public ResponseEntity<List<NewsDto>> searchByTags(
            @RequestBody Set<String> tags,
            @RequestParam(defaultValue = "both") String language) {

        log.info("🏷️ Searching news by {} tags in language: {}", tags.size(), language);
        List<NewsDto> results = newsService.searchByTags(tags, language);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ UPDATE NEWS with form data
     * Files: coverImage, mediaFiles (optional, form-data)
     * Data: news (JSON string in form-data)
     *
     * All fields are optional - only provided fields will be updated
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsDto> updateNews(
            @PathVariable Long id,
            @RequestPart("news") String newsJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles) {

        log.info("✏️ Updating news - ID: {}", id);

        try {
            NewsDto newsDto = objectMapper.readValue(newsJson, NewsDto.class);
            NewsDto updated = newsService.updateNews(id, newsDto, coverImage, mediaFiles);
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
    public ResponseEntity<Void> deleteNews(@PathVariable Long id) {
        log.info("🗑️ Deleting news - ID: {}", id);
        newsService.deleteNews(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * ✅ BULK DELETE
     *
     * Request Body: [1, 2, 3, 4, 5]
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteNewsBulk(@RequestBody List<Long> newsIds) {
        log.info("🗑️ Bulk deleting {} news items", newsIds.size());
        newsService.deleteNewsBulk(newsIds);
        return ResponseEntity.noContent().build();
    }
}