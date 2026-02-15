package ak.dev.khi_backend.khi_app.api.publishment.writing;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.publishment.writing.WritingDtos.*;
import ak.dev.khi_backend.khi_app.service.publishment.writing.WritingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ✅ ENHANCED WritingController with:
 * - Book Series/Edition endpoints
 * - Writer search endpoints
 * - All existing functionality preserved
 */
@RestController
@RequestMapping("/api/v1/writings")
@RequiredArgsConstructor
@Slf4j
public class WritingController {

    private final WritingService writingService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE
    // ============================================================

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Response>> create(
            @Valid @RequestBody CreateRequest request) {

        log.info("POST /api/v1/writings/create | langs={}", request.getContentLanguages());

        Response response = writingService.addWriting(request, null, null, null, null);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Writing created successfully"));
    }

    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Response>> createWithFiles(
            @RequestPart(value = "data") String dataJson,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "ckbBookFile", required = false) MultipartFile ckbBookFile,
            @RequestPart(value = "kmrBookFile", required = false) MultipartFile kmrBookFile) throws IOException {

        // Parse JSON string to CreateRequest object
        CreateRequest request;
        try {
            request = objectMapper.readValue(dataJson, CreateRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse data JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON format in 'data' field: " + e.getMessage());
        }

        int fileCount = 0;
        if (ckbCoverImage != null) fileCount++;
        if (kmrCoverImage != null) fileCount++;
        if (ckbBookFile != null) fileCount++;
        if (kmrBookFile != null) fileCount++;

        log.info("POST /api/v1/writings/with-files | langs={} | files={}",
                request.getContentLanguages(), fileCount);

        Response response = writingService.addWriting(request, ckbCoverImage, kmrCoverImage, ckbBookFile, kmrBookFile);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Writing created successfully"));
    }

    // ============================================================
    // ✅ NEW: SERIES MANAGEMENT
    // ============================================================

    /**
     * Link an existing book to a series
     * Example: You created "Eslam Part 1" last week, now you want to link "Eslam Part 2" to it
     */
    @PostMapping("/series/link")
    public ResponseEntity<ApiResponse<Response>> linkToSeries(
            @Valid @RequestBody LinkToSeriesRequest request) {

        log.info("POST /api/v1/writings/series/link | book={} parent={}",
                request.getBookId(), request.getParentBookId());

        Response response = writingService.linkBookToSeries(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response, "Book linked to series successfully"));
    }

    /**
     * Get all books in a series
     * Example: GET /api/v1/writings/series/eslam-series
     */
    @GetMapping("/series/{seriesId}")
    public ResponseEntity<ApiResponse<SeriesResponse>> getSeriesBooks(
            @PathVariable("seriesId") String seriesId) {

        log.info("GET /api/v1/writings/series/{}", seriesId);

        SeriesResponse response = writingService.getSeriesBooks(seriesId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Series books fetched successfully")
        );
    }

    /**
     * Get all series (parent books only)
     * Useful for showing a list of all book series
     */
    @GetMapping("/series/all")
    public ResponseEntity<ApiResponse<Page<Response>>> getAllSeries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/writings/series/all | page={} size={}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Response> result = writingService.getAllSeriesParents(pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Series list fetched successfully")
        );
    }

    // ============================================================
    // ✅ NEW: WRITER SEARCH
    // ============================================================

    /**
     * Search books by writer name
     * Example: GET /api/v1/writings/search/writer?name=أحمد&language=ckb
     *
     * Performance: O(log n) with database indexes
     */
    @GetMapping("/search/writer")
    public ResponseEntity<ApiResponse<Page<Response>>> searchByWriter(
            @RequestParam("name") String writerName,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/writings/search/writer | name={} | language={}", writerName, language);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Response> result = writingService.searchByWriter(writerName, language, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by writer completed")
        );
    }

    /**
     * Get ALL books by a writer (no pagination)
     * Example: GET /api/v1/writings/writer/all?name=أحمد
     *
     * Use this when you need the complete list (e.g., for author profile page)
     */
    @GetMapping("/writer/all")
    public ResponseEntity<ApiResponse<List<Response>>> getAllBooksByWriter(
            @RequestParam("name") String writerName,
            @RequestParam(defaultValue = "both") String language) {

        log.info("GET /api/v1/writings/writer/all | name={} | language={}", writerName, language);

        List<Response> result = writingService.getAllBooksByWriter(writerName, language);

        return ResponseEntity.ok(
                ApiResponse.success(result, String.format("Found %d books by writer", result.size()))
        );
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<Response>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateRequest request) {

        log.info("PUT /api/v1/writings/update/{} | langs={}", id, request.getContentLanguages());

        Response response = writingService.updateWriting(id, request, null, null, null, null);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Writing updated successfully")
        );
    }

    @PutMapping(value = "/update/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Response>> updateWithFiles(
            @PathVariable("id") Long id,
            @RequestPart(value = "data") String dataJson,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "ckbBookFile", required = false) MultipartFile ckbBookFile,
            @RequestPart(value = "kmrBookFile", required = false) MultipartFile kmrBookFile) throws IOException {

        // Parse JSON string to UpdateRequest object
        UpdateRequest request;
        try {
            request = objectMapper.readValue(dataJson, UpdateRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse data JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON format in 'data' field: " + e.getMessage());
        }

        int fileCount = 0;
        if (ckbCoverImage != null) fileCount++;
        if (kmrCoverImage != null) fileCount++;
        if (ckbBookFile != null) fileCount++;
        if (kmrBookFile != null) fileCount++;

        log.info("PUT /api/v1/writings/update/{}/with-files | files={} | langs={}",
                id, fileCount, request.getContentLanguages());

        Response response = writingService.updateWriting(id, request, ckbCoverImage, kmrCoverImage, ckbBookFile, kmrBookFile);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Writing updated successfully")
        );
    }

    // ============================================================
    // DELETE
    // ============================================================

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("id") Long id) {

        log.info("DELETE /api/v1/writings/delete/{}", id);

        writingService.deleteWriting(id);

        return ResponseEntity.ok(
                ApiResponse.success(null, "Writing deleted successfully")
        );
    }

    // ============================================================
    // GET ALL
    // ============================================================

    @GetMapping("/getAll")
    public ResponseEntity<ApiResponse<Page<Response>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {

        log.info("GET /api/v1/writings/getAll | page={} size={} sort={} dir={}",
                page, size, sort, dir);

        Sort.Direction direction = "asc".equalsIgnoreCase(dir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<Response> result = writingService.getAllWritings(pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Writings fetched successfully")
        );
    }

    // ============================================================
    // SEARCH BY TAG
    // ============================================================

    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<Page<Response>>> searchByTag(
            @RequestParam("tag") String tag,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/writings/search/tag | tag={} | language={}", tag, language);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Response> result = writingService.searchByTag(tag, language, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by tag completed")
        );
    }

    // ============================================================
    // SEARCH BY KEYWORD
    // ============================================================

    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<Page<Response>>> searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "both") String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/writings/search/keyword | keyword={} | language={}", keyword, language);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Response> result = writingService.searchByKeyword(keyword, language, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by keyword completed")
        );
    }
}