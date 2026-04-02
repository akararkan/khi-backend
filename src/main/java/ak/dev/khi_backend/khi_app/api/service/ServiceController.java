package ak.dev.khi_backend.khi_app.api.service;

import ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs.*;
import ak.dev.khi_backend.khi_app.service.service.ServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *  /api/services
 *
 * ─── Endpoint Groups ──────────────────────────────────────────────────────────
 *
 *  ① Service CRUD
 *  ─────────────────────────────────────────────────────────────────────────────
 *  GET    /api/services                          → list all active services
 *  GET    /api/services?type={serviceType}       → filter active by type
 *  GET    /api/services/{id}                     → get single service (full)
 *  POST   /api/services                          → create service
 *  PUT    /api/services/{id}                     → full update
 *  PATCH  /api/services/{id}/active?value=false  → soft toggle
 *  DELETE /api/services/{id}                     → delete + S3 cleanup
 *
 *  ② Collection Management
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/services/{serviceId}/collections               → add collection
 *  PUT    /api/services/{serviceId}/collections/{colId}       → update collection
 *  DELETE /api/services/{serviceId}/collections/{colId}       → delete + S3 files
 *
 *  ③ File Management
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/services/collections/{colId}/files             → add pre-uploaded file
 *  DELETE /api/services/files/{fileId}                        → delete file + S3
 *
 *  ④ Media Upload (Multipart)
 *  ─────────────────────────────────────────────────────────────────────────────
 *  POST   /api/services/upload                         → single file → S3 URL
 *  POST   /api/services/upload/multiple                → multi-file → S3 URL list
 *  DELETE /api/services/upload?fileUrl={url}           → delete from S3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    // =========================================================================
    // ① SERVICE CRUD
    // =========================================================================

    /**
     * List all active services.
     * Optional ?type=Training filter narrows results to one service type.
     */
    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAll(
            @RequestParam(required = false) String type) {

        List<ServiceResponse> result = (type != null && !type.isBlank())
                ? serviceService.getAllActiveByType(type)
                : serviceService.getAllActive();

        return ResponseEntity.ok(result);
    }

    /**
     * Fetch a single service with full bilingual content + all media collections.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.getById(id));
    }

    /**
     * Create a new service.
     * Media files referenced in the request body must already be uploaded to S3
     * via {@code POST /api/services/upload} before calling this endpoint.
     */
    @PostMapping
    public ResponseEntity<ServiceResponse> create(@RequestBody ServiceRequest request) {
        ServiceResponse response = serviceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Full update — replaces all content rows and media collections.
     * S3 files that are no longer referenced are automatically deleted.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> update(
            @PathVariable Long id,
            @RequestBody ServiceRequest request) {
        return ResponseEntity.ok(serviceService.update(id, request));
    }

    /**
     * Soft-toggle the active flag without touching content or media.
     * Example: PATCH /api/services/5/active?value=false
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<ServiceResponse> setActive(
            @PathVariable Long id,
            @RequestParam boolean value) {
        return ResponseEntity.ok(serviceService.setActive(id, value));
    }

    /**
     * Permanently delete the service, all its content rows, all media collections,
     * all media files, and all referenced S3 objects.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ② COLLECTION MANAGEMENT
    // =========================================================================

    /**
     * Add a new media collection to an existing service.
     * No files are included — use ③ File Management to add files afterwards.
     */
    @PostMapping("/{serviceId}/collections")
    public ResponseEntity<ServiceMediaCollectionResponse> addCollection(
            @PathVariable Long serviceId,
            @RequestBody CollectionUpsertRequest request) {

        ServiceMediaCollectionResponse response =
                serviceService.addCollection(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update a collection's name, type, or sort order.
     * Files inside the collection are not affected.
     */
    @PutMapping("/{serviceId}/collections/{collectionId}")
    public ResponseEntity<ServiceMediaCollectionResponse> updateCollection(
            @PathVariable Long serviceId,
            @PathVariable Long collectionId,
            @RequestBody CollectionUpsertRequest request) {

        return ResponseEntity.ok(
                serviceService.updateCollection(serviceId, collectionId, request));
    }

    /**
     * Delete a collection and all its files from the DB and S3.
     */
    @DeleteMapping("/{serviceId}/collections/{collectionId}")
    public ResponseEntity<Void> deleteCollection(
            @PathVariable Long serviceId,
            @PathVariable Long collectionId) {

        serviceService.deleteCollection(serviceId, collectionId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ③ FILE MANAGEMENT
    // =========================================================================

    /**
     * Add a single pre-uploaded file (already on S3) to an existing collection.
     * Upload the file first via {@code POST /api/services/upload},
     * then pass the returned URL in the {@link FileAddRequest}.
     */
    @PostMapping("/collections/{collectionId}/files")
    public ResponseEntity<ServiceMediaFileResponse> addFile(
            @PathVariable Long collectionId,
            @RequestBody FileAddRequest request) {

        ServiceMediaFileResponse response =
                serviceService.addFileToCollection(collectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Remove a single file from its collection and delete it from S3.
     */
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId) {
        serviceService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ④ MEDIA UPLOAD — MULTIPART
    // =========================================================================

    /**
     * Upload a single file to S3 and receive the public URL back.
     *
     * Form fields:
     *   file  — the binary file (required)
     *   type  — hint: "image" | "video" | "audio" | "gallery"  (optional)
     *
     * The returned {@link UploadResponse#getFileUrl()} can then be embedded in
     * {@link ServiceRequest}, {@link FileAddRequest}, or used as coverMediaUrl.
     *
     * Example (curl):
     *   curl -X POST /api/services/upload \
     *        -F "file=@photo.jpg" \
     *        -F "type=image"
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadSingle(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) throws IOException {

        log.info("Single upload request: name={}, type={}", file.getOriginalFilename(), type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceService.uploadMedia(file, type));
    }

    /**
     * Upload multiple files in one multipart request (bulk gallery upload).
     * All files in the batch share the same optional type hint.
     *
     * Form fields:
     *   files — one or more binary files (required)
     *   type  — hint shared for all files (optional)
     *
     * Returns a list of {@link UploadResponse} in the same order as the input.
     *
     * Example (curl):
     *   curl -X POST /api/services/upload/multiple \
     *        -F "files=@img1.jpg" \
     *        -F "files=@img2.jpg" \
     *        -F "type=image"
     */
    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadResponse>> uploadMultiple(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "type", required = false) String type) {

        log.info("Bulk upload request: count={}, type={}", files.size(), type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceService.uploadMultipleMedia(files, type));
    }

    /**
     * Delete a file from S3 by its URL.
     * Use this when the admin discards an uploaded file before saving the service.
     *
     * Example: DELETE /api/services/upload?fileUrl=https://s3.../file.jpg
     */
    @DeleteMapping("/upload")
    public ResponseEntity<Void> deleteUploadedFile(
            @RequestParam("fileUrl") String fileUrl) {

        serviceService.deleteMedia(fileUrl);
        return ResponseEntity.noContent().build();
    }
}