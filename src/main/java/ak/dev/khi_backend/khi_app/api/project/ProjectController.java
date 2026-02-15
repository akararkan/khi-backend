package ak.dev.khi_backend.khi_app.api.project;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.project.ProjectCreateRequest;
import ak.dev.khi_backend.khi_app.dto.project.ProjectResponse;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.project.Project;
import ak.dev.khi_backend.khi_app.service.project.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ============================================================
    // CREATE (JSON)
    // ============================================================
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Project>> create(
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("POST /api/v1/projects/create | langs={} | mediaDtoCount={}",
                request.getContentLanguages(),
                request.getMedia() != null ? request.getMedia().size() : 0
        );

        Project project = projectService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    /**
     * Multipart:
     * - data: JSON ProjectCreateRequest
     * - cover: optional file BUT if cover is missing then coverUrl in JSON must exist
     * - media: optional files (can repeat)
     */
    @PostMapping(
            value = "/with-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Project>> createWithFiles(
            @RequestPart(value = "data") @Valid ProjectCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) throws IOException {

        // ✅ EASIEST GUARANTEE:
        // coverUrl must come either from uploaded cover file OR from request.coverUrl
        if ((cover == null || cover.isEmpty()) && isBlank(request.getCoverUrl())) {
            throw new BadRequestException("project.cover_required", Map.of());
        }

        int mediaFilesCount = mediaFiles != null ? mediaFiles.size() : 0;
        int coverCount = (cover != null && !cover.isEmpty()) ? 1 : 0;

        log.info("POST /api/v1/projects/with-files | langs={} | cover={} | mediaFiles={} | mediaDtoCount={}",
                request.getContentLanguages(),
                coverCount,
                mediaFilesCount,
                request.getMedia() != null ? request.getMedia().size() : 0
        );

        Project project = projectService.create(request, cover, mediaFiles);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Project created successfully"));
    }

    // ============================================================
    // UPDATE (JSON)
    // ============================================================
    @PutMapping(value = "/update/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Project>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        log.info("PUT /api/v1/projects/update/{} | langs={} | mediaDtoCount={}",
                id,
                request.getContentLanguages(),
                request.getMedia() != null ? request.getMedia().size() : 0
        );

        Project project = projectService.update(id, request);

        return ResponseEntity.ok(
                ApiResponse.success(project, "Project updated successfully")
        );
    }

    @PutMapping(
            value = "/update/{id}/with-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Project>> updateWithFiles(
            @PathVariable("id") Long id,
            @RequestPart(value = "data") @Valid ProjectCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) throws IOException {

        // ✅ EASIEST GUARANTEE:
        // If client doesn't upload new cover, they must send existing coverUrl in JSON.
        if ((cover == null || cover.isEmpty()) && isBlank(request.getCoverUrl())) {
            throw new BadRequestException("project.cover_required", Map.of());
        }

        int mediaFilesCount = mediaFiles != null ? mediaFiles.size() : 0;
        int coverCount = (cover != null && !cover.isEmpty()) ? 1 : 0;

        log.info("PUT /api/v1/projects/update/{}/with-files | langs={} | cover={} | mediaFiles={} | mediaDtoCount={}",
                id,
                request.getContentLanguages(),
                coverCount,
                mediaFilesCount,
                request.getMedia() != null ? request.getMedia().size() : 0
        );

        Project updated = projectService.updateWithFiles(id, request, cover, mediaFiles);

        return ResponseEntity.ok(ApiResponse.success(updated, "Project updated successfully"));
    }

    // ============================================================
    // DELETE
    // ============================================================
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/projects/delete/{}", id);

        projectService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.success(null, "Project deleted successfully")
        );
    }

    // ============================================================
    // GET ALL
    // ============================================================
    @GetMapping(value = "/getAll", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/getAll | page={} size={}", page, size);

        Page<ProjectResponse> result = projectService.getAllResponse(page, size);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Projects fetched successfully")
        );
    }

    // ============================================================
    // SEARCH BY TAG
    // ============================================================
    @GetMapping(value = "/search/tag", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByTag(
            @RequestParam("tag") String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/tag | tag={} | page={} size={}", tag, page, size);

        Page<ProjectResponse> result = projectService.searchByTagResponse(tag, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by tag completed")
        );
    }

    // ============================================================
    // SEARCH BY KEYWORD
    // ============================================================
    @GetMapping(value = "/search/keyword", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/projects/search/keyword | keyword={} | page={} size={}", keyword, page, size);

        Page<ProjectResponse> result = projectService.searchByKeywordResponse(keyword, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Search by keyword completed")
        );
    }

    // ============================================================
    // UTIL
    // ============================================================
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
