package ak.dev.khi_backend.khi_app.api.contact;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.contact.ContactDTOs.*;
import ak.dev.khi_backend.khi_app.service.contact.ContactService;
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
 * REST Controller for Contact page management.
 * Follows the same pattern as {@link ak.dev.khi_backend.khi_app.api.about.AboutController}.
 */
@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactService contactService;

    // ============================================================
    // READ
    // ============================================================

    /** All contact pages (admin — includes inactive). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getAll() {
        log.info("GET /api/v1/contact");
        return ResponseEntity.ok(ApiResponse.success(
                contactService.getAll(), "Contact pages fetched"));
    }

    /** All active contact pages (public). */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getAllActive() {
        return ResponseEntity.ok(ApiResponse.success(
                contactService.getAllActive(), "Active contact pages fetched"));
    }

    /** Get by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactResponse>> getById(@PathVariable Long id) {
        log.info("GET /api/v1/contact/{}", id);
        return ResponseEntity.ok(ApiResponse.success(
                contactService.getById(id), "Contact page fetched"));
    }

    /**
     * Get by slug — works with either CKB or KMR slug.
     * Used by the public frontend for route-based navigation.
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ContactResponse>> getBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/contact/slug/{}", slug);
        return ResponseEntity.ok(ApiResponse.success(
                contactService.getBySlug(slug), "Contact page fetched"));
    }

    // ============================================================
    // CREATE
    // ============================================================

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ContactResponse>> create(
            @RequestBody ContactRequest request) {

        log.info("POST /api/v1/contact | slugCkb={}", request.getSlugCkb());
        ContactResponse response = contactService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Contact page created successfully"));
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ContactResponse>> update(
            @PathVariable Long id,
            @RequestBody ContactRequest request) {

        log.info("PUT /api/v1/contact/{}", id);
        return ResponseEntity.ok(ApiResponse.success(
                contactService.update(id, request), "Contact page updated successfully"));
    }

    // ============================================================
    // DELETE
    // ============================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("DELETE /api/v1/contact/{}", id);
        contactService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact page deleted successfully"));
    }

    // ============================================================
    // MEDIA UPLOAD — S3
    // ============================================================

    /**
     * Upload hero image or any contact-related media to S3.
     * Returns the public URL for use in the request body.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> uploadMedia(
            @RequestPart("file") MultipartFile file) throws IOException {

        log.info("POST /api/v1/contact/upload | name={}, size={}",
                file.getOriginalFilename(), file.getSize());
        return ResponseEntity.ok(ApiResponse.success(
                contactService.uploadMedia(file), "Media uploaded successfully"));
    }

    /**
     * Delete a media file from S3 by URL.
     */
    @DeleteMapping("/media")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(@RequestParam String fileUrl) {
        log.info("DELETE /api/v1/contact/media | url={}", fileUrl);
        contactService.deleteMedia(fileUrl);
        return ResponseEntity.ok(ApiResponse.success(null, "Media deleted successfully"));
    }
}