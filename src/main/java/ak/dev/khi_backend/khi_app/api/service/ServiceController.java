package ak.dev.khi_backend.khi_app.api.service;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs.*;
import ak.dev.khi_backend.khi_app.service.service.ServiceService;
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

/**
 * ServiceController — REST endpoints for the Service module.
 *
 * ─── Base Path ────────────────────────────────────────────────────────────────
 *  /api/v1/services
 *
 * ─── Endpoint Groups ──────────────────────────────────────────────────────────
 *
 *  ① Service CRUD (Paginated + Cached)
 *  ─────────────────────────────────────────────────────────────────────────────
 *  GET    /api/v1/services                                → active services (paginated)
 *  GET    /api/v1/services?type={serviceType}             → filter active by type (paginated)
 *  GET    /api/v1/services/all                            → admin: all incl. inactive (paginated)
 *  GET    /api/v1/services/{id}                           → single service (full detail)
 *  GET    /api/v1/services/types                          → distinct service type names
 *  GET    /api/v1/services/search?q={query}               → global search (active, paginated)
 *  GET    /api/v1/services/search/admin?q={query}         → admin search (all, paginated)
 *  POST   /api/v1/services                                → create service
 *  PUT    /api/v1/services/{id}                           → full update
 *  PATCH  /api/v1/services/{id}/active?value=false        → soft toggle
 *  DELETE /api/v1/services/{id}                           → delete + S3 cleanup
 *  DELETE /api/v1/services/bulk                           → bulk delete
 *
 *  ② Collection Management
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/v1/services/{serviceId}/collections               → add collection
 *  PUT    /api/v1/services/{serviceId}/collections/{colId}       → update collection
 *  DELETE /api/v1/services/{serviceId}/collections/{colId}       → delete + S3 files
 *
 *  ③ File Management
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/v1/services/collections/{colId}/files             → add pre-uploaded file
 *  DELETE /api/v1/services/files/{fileId}                        → delete file + S3
 *
 *  ④ Media Upload (Multipart)
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/v1/services/upload                         → single file → S3 URL
 *  POST   /api/v1/services/upload/multiple                → multi-file → S3 URL list
 *  DELETE /api/v1/services/upload?fileUrl={url}           → delete from S3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    // =========================================================================
    // ① SERVICE CRUD — Paginated + Cached
    // =========================================================================

    /**
     * List all active services — paginated.
     * Optional ?type=Training filter narrows results to one service type.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ServiceResponse> result = (type != null && !type.isBlank())
                ? serviceService.getAllActiveByType(type, page, size)
                : serviceService.getAllActive(page, size);

        return ResponseEntity.ok(
                ApiResponse.success(result, "Services fetched successfully"));
    }

    /**
     * Admin — list ALL services including inactive — paginated.
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getAllAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ServiceResponse> result = serviceService.getAll(page, size);
        return ResponseEntity.ok(
                ApiResponse.success(result, "All services fetched successfully"));
    }

    /**
     * Fetch a single service with full bilingual content + all media collections.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(serviceService.getById(id), "Service fetched successfully"));
    }

    /**
     * List distinct service type names (for filter dropdowns).
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<String>>> getServiceTypes() {
        return ResponseEntity.ok(
                ApiResponse.success(serviceService.getServiceTypes(), "Service types fetched"));
    }

    /**
     * Global search — searches type, location, bilingual title/description.
     * Active services only. Paginated + cached.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                ApiResponse.success(serviceService.globalSearch(q, page, size),
                        "Search results fetched"));
    }

    /**
     * Admin search — includes inactive services. Paginated + cached.
     */
    @GetMapping("/search/admin")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> adminSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                ApiResponse.success(serviceService.adminSearch(q, page, size),
                        "Admin search results fetched"));
    }

    /**
     * Create a new service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceResponse>> create(
            @RequestBody ServiceRequest request) {

        ServiceResponse response = serviceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Service created successfully"));
    }

    /**
     * Full update — replaces all content rows and media collections.
     * S3 files that are no longer referenced are automatically deleted.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> update(
            @PathVariable Long id,
            @RequestBody ServiceRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(serviceService.update(id, request),
                        "Service updated successfully"));
    }

    /**
     * Soft-toggle the active flag without touching content or media.
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<ServiceResponse>> setActive(
            @PathVariable Long id,
            @RequestParam boolean value) {

        return ResponseEntity.ok(
                ApiResponse.success(serviceService.setActive(id, value),
                        value ? "Service activated" : "Service deactivated"));
    }

    /**
     * Permanently delete the service and all its S3 objects.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        serviceService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Service deleted successfully"));
    }

    /**
     * Bulk delete — permanently delete multiple services at once.
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<ApiResponse<Void>> deleteBulk(@RequestBody List<Long> ids) {
        serviceService.deleteBulk(ids);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Services deleted successfully"));
    }

    // =========================================================================
    // ② COLLECTION MANAGEMENT
    // =========================================================================

    @PostMapping("/{serviceId}/collections")
    public ResponseEntity<ApiResponse<ServiceMediaCollectionResponse>> addCollection(
            @PathVariable Long serviceId,
            @RequestBody CollectionUpsertRequest request) {

        ServiceMediaCollectionResponse response =
                serviceService.addCollection(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Collection added successfully"));
    }

    @PutMapping("/{serviceId}/collections/{collectionId}")
    public ResponseEntity<ApiResponse<ServiceMediaCollectionResponse>> updateCollection(
            @PathVariable Long serviceId,
            @PathVariable Long collectionId,
            @RequestBody CollectionUpsertRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        serviceService.updateCollection(serviceId, collectionId, request),
                        "Collection updated successfully"));
    }

    @DeleteMapping("/{serviceId}/collections/{collectionId}")
    public ResponseEntity<ApiResponse<Void>> deleteCollection(
            @PathVariable Long serviceId,
            @PathVariable Long collectionId) {

        serviceService.deleteCollection(serviceId, collectionId);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Collection deleted successfully"));
    }

    // =========================================================================
    // ③ FILE MANAGEMENT
    // =========================================================================

    @PostMapping("/collections/{collectionId}/files")
    public ResponseEntity<ApiResponse<ServiceMediaFileResponse>> addFile(
            @PathVariable Long collectionId,
            @RequestBody FileAddRequest request) {

        ServiceMediaFileResponse response =
                serviceService.addFileToCollection(collectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "File added successfully"));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable Long fileId) {
        serviceService.deleteFile(fileId);
        return ResponseEntity.ok(
                ApiResponse.success(null, "File deleted successfully"));
    }

    // =========================================================================
    // ④ MEDIA UPLOAD — MULTIPART
    // =========================================================================

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> uploadSingle(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) throws IOException {

        log.info("Single upload: name={}, type={}", file.getOriginalFilename(), type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(serviceService.uploadMedia(file, type),
                        "File uploaded successfully"));
    }

    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<UploadResponse>>> uploadMultiple(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "type", required = false) String type) {

        log.info("Bulk upload: count={}, type={}", files.size(), type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(serviceService.uploadMultipleMedia(files, type),
                        "Files uploaded successfully"));
    }

    @DeleteMapping("/upload")
    public ResponseEntity<ApiResponse<Void>> deleteUploadedFile(
            @RequestParam("fileUrl") String fileUrl) {

        serviceService.deleteMedia(fileUrl);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Uploaded file deleted successfully"));
    }
}