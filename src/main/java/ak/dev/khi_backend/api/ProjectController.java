package ak.dev.khi_backend.api;

import ak.dev.khi_backend.dto.ApiResponse;
import ak.dev.khi_backend.dto.ProjectCreateRequest;
import ak.dev.khi_backend.dto.ProjectResponse;
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

/**
 * REST controller for Project management
 *
 * Endpoints:
 * - POST   /api/v1/projects/create          - Create project (JSON)
 * - POST   /api/v1/projects/with-files      - Create project with file uploads
 * - PUT    /api/v1/projects/update/{id}     - Update project
 * - DELETE /api/v1/projects/delete/{id}     - Delete project
 * - GET    /api/v1/projects/getAll          - Get all projects (paginated)
 * - GET    /api/v1/projects/search          - Enhanced multi-filter search
 * - GET    /api/v1/projects/search/name     - Search by name
 * - GET    /api/v1/projects/search/tag      - Search by tag
 * - GET    /api/v1/projects/search/content  - Search by content
 * - GET    /api/v1/projects/search/keyword  - Search by keyword
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ══════════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Create project with JSON body
     *
     * @param request Project data (title, description, tags, etc.)
     * @return Created project
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Project>> create(
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("POST /api/v1/projects/create | title={}", request.getTitle());

        Project project = projectService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    /**
     * Create project with file uploads
     *
     * @param request Project data
     * @param cover Cover image file (optional)
     * @param mediaFiles Media files (optional)
     * @return Created project
     */
    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Project>> createWithFiles(
            @RequestPart("data") @Valid ProjectCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) throws IOException {
        int fileCount = (mediaFiles != null ? mediaFiles.size() : 0) + (cover != null ? 1 : 0);
        log.info("POST /api/v1/projects/with-files | title={} | files={}",
                request.getTitle(), fileCount);

        Project project = projectService.create(request, cover, mediaFiles);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Update existing project
     *
     * @param id Project ID
     * @param request Updated project data
     * @return Updated project
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<Project>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("PUT /api/v1/projects/update/{} | title={}", id, request.getTitle());

        Project project = projectService.update(id, request);

        return ResponseEntity.ok(
                ApiResponse.success(project, "Project updated successfully")
        );
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Delete project by ID
     *
     * @param id Project ID
     * @return Success response
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("id") Long id
    ) {
        log.info("DELETE /api/v1/projects/delete/{}", id);

        projectService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.success(null, "Project deleted successfully")
        );
    }

    // ══════════════════════════════════════════════════════════════
    // READ OPERATIONS - GET ALL
    // ══════════════════════════════════════════════════════════════

    /**
     * Get all projects (paginated & sorted)
     *
     * Example: GET /api/v1/projects/getAll?page=0&size=20&sort=createdAt&dir=desc
     *
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sort Sort field
     * @param dir Sort direction (asc/desc)
     * @return Page of projects
     */
    @GetMapping("/getAll")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir
    ) {
        log.info("GET /api/v1/projects/getAll | page={} size={} sort={} dir={}",
                page, size, sort, dir);

        Sort.Direction direction = "asc".equalsIgnoreCase(dir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<ProjectResponse> result = projectService.getAllResponse(pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Projects fetched successfully")
        );
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS - SINGLE FILTER
    // ══════════════════════════════════════════════════════════════

    /**
     * Search projects by name/title (contains, case-insensitive)
     *
     * Example: GET /api/v1/projects/search/name?q=festival&page=0&size=20
     *
     * @param q Search query
     * @param page Page number
     * @param size Page size
     * @return Page of matching projects
     */
    @GetMapping("/search/name")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByName(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/name | q={}", q);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProjectResponse> result = projectService.searchByNameResponse(q, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by name completed")
        );
    }

    /**
     * Search projects by tag (exact match, case-insensitive)
     *
     * Example: GET /api/v1/projects/search/tag?tag=culture&page=0&size=20
     *
     * @param tag Tag name
     * @param page Page number
     * @param size Page size
     * @return Page of matching projects
     */
    @GetMapping("/search/tag")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByTag(
            @RequestParam("tag") String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/tag | tag={}", tag);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProjectResponse> result = projectService.searchByTagResponse(tag, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by tag completed")
        );
    }

    /**
     * Search projects by content type (exact match, case-insensitive)
     *
     * Example: GET /api/v1/projects/search/content?content=poetry&page=0&size=20
     *
     * @param content Content type name
     * @param page Page number
     * @param size Page size
     * @return Page of matching projects
     */
    @GetMapping("/search/content")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByContent(
            @RequestParam("content") String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/content | content={}", content);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProjectResponse> result = projectService.searchByContentResponse(content, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by content completed")
        );
    }

    /**
     * Search projects by keyword (exact match, case-insensitive)
     *
     * Example: GET /api/v1/projects/search/keyword?keyword=kurdish&page=0&size=20
     *
     * @param keyword Keyword
     * @param page Page number
     * @param size Page size
     * @return Page of matching projects
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/keyword | keyword={}", keyword);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProjectResponse> result = projectService.searchByKeywordResponse(keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by keyword completed")
        );
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS - MULTI FILTER (ENHANCED)
    // ══════════════════════════════════════════════════════════════

    /**
     * Enhanced search with multiple filters
     *
     * Example:
     * GET /api/v1/projects/search?name=festival&tags=culture&tags=arts&keywords=kurdish&page=0&size=20
     *
     * Filters:
     * - name: Title contains (case-insensitive)
     * - tags: Exact match, multiple values (case-insensitive)
     * - contents: Exact match, multiple values (case-insensitive)
     * - keywords: Exact match, multiple values (case-insensitive)
     *
     * @param name Name/title search query (optional)
     * @param tags List of tags to filter by (optional)
     * @param contents List of content types to filter by (optional)
     * @param keywords List of keywords to filter by (optional)
     * @param page Page number
     * @param size Page size
     * @return Page of matching projects
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> enhancedSearch(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "contents", required = false) List<String> contents,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        log.info("GET /api/v1/projects/search | name={} tags={} contents={} keywords={}",
                name,
                tags != null ? tags.size() : 0,
                contents != null ? contents.size() : 0,
                keywords != null ? keywords.size() : 0);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProjectResponse> result = projectService.enhancedSearchResponse(
                name, tags, contents, keywords, pageable
        );

        return ResponseEntity.ok(
                ApiResponse.success(result, "Enhanced search completed")
        );
    }
}