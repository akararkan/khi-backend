package ak.dev.khi_backend.api;

import ak.dev.khi_backend.dto.ApiResponse;
import ak.dev.khi_backend.dto.ProjectCreateRequest;
import ak.dev.khi_backend.model.Project;
import ak.dev.khi_backend.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ======================================================
    // CREATE
    // ======================================================

    /**
     * Create project with JSON body
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Project>> create(
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("POST /api/v1/projects | title={}", request.getTitle());

        Project project = projectService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    /**
     * Create project with files
     */
    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Project>> createWithFiles(
            @RequestPart("data") @Valid ProjectCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) throws IOException {

        log.info("POST /api/v1/projects/with-files | title={} | files={}",
                request.getTitle(), mediaFiles != null ? mediaFiles.size() : 0);

        Project project = projectService.create(request, cover, mediaFiles);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    // ======================================================
    // UPDATE
    // ======================================================

    /**
     * Update project (JSON)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("PUT /api/v1/projects/{} | title={}", id, request.getTitle());

        Project project = projectService.update(id, request);

        return ResponseEntity.ok(ApiResponse.success(project, "Project updated successfully"));
    }

    // ======================================================
    // DELETE
    // ======================================================

    /**
     * Delete project
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/projects/{}", id);

        projectService.delete(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Project deleted successfully"));
    }

    // ======================================================
    // GET ALL (LIST)
    // ======================================================

    /**
     * Get all projects (paginated)
     *
     * Example:
     * GET /api/v1/projects?page=0&size=20&sort=createdAt&dir=desc
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Project>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir
    ) {
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        log.info("GET /api/v1/projects | page={} size={} sort={} dir={}", page, size, sort, dir);

        Page<Project> result = projectService.getAll(pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Projects fetched successfully"));
    }

    // ======================================================
    // SEARCH
    // ======================================================

    /**
     * Search by name/title (contains)
     *
     * Example:
     * GET /api/v1/projects/search/name?q=library&page=0&size=20
     */
    @GetMapping("/search/name")
    public ResponseEntity<ApiResponse<Page<Project>>> searchByName(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.info("GET /api/v1/projects/search/name | q={}", q);

        Page<Project> result = projectService.searchByName(q, pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Search by name completed"));
    }

    /**
     * Search by tag (exact, case-insensitive)
     *
     * Example:
     * GET /api/v1/projects/search/tag?tag=java&page=0&size=20
     */
    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<Page<Project>>> searchByTag(
            @RequestParam("tag") String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.info("GET /api/v1/projects/search/tag | tag={}", tag);

        Page<Project> result = projectService.searchByTag(tag, pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Search by tag completed"));
    }

    /**
     * Search by content (exact, case-insensitive)
     *
     * Example:
     * GET /api/v1/projects/search/content?content=research&page=0&size=20
     */
    @GetMapping("/search/content")
    public ResponseEntity<ApiResponse<Page<Project>>> searchByContent(
            @RequestParam("content") String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.info("GET /api/v1/projects/search/content | content={}", content);

        Page<Project> result = projectService.searchByContent(content, pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Search by content completed"));
    }

    /**
     * Search by keyword (exact, case-insensitive)
     *
     * Example:
     * GET /api/v1/projects/search/keyword?keyword=ai&page=0&size=20
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<Page<Project>>> searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.info("GET /api/v1/projects/search/keyword | keyword={}", keyword);

        Page<Project> result = projectService.searchByKeyword(keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Search by keyword completed"));
    }

    /**
     * Enhanced search (multi filters)
     *
     * Example:
     * GET /api/v1/projects/search?name=lib&tags=java&tags=spring&keywords=ai&contents=research&page=0&size=20
     *
     * Notes:
     * - name: "contains"
     * - tags/keywords/contents: exact match lists (case-insensitive)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Project>>> enhancedSearch(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "contents", required = false) List<String> contents,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.info("GET /api/v1/projects/search | name={} tags={} contents={} keywords={}",
                name, tags != null ? tags.size() : 0, contents != null ? contents.size() : 0, keywords != null ? keywords.size() : 0);

        Page<Project> result = projectService.enhancedSearch(name, tags, contents, keywords, pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Enhanced search completed"));
    }
}
