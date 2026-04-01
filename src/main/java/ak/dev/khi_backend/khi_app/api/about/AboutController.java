package ak.dev.khi_backend.khi_app.api.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs;
import ak.dev.khi_backend.khi_app.service.about.AboutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/about")
@RequiredArgsConstructor
public class AboutController {

    private final AboutService aboutService;

    // GET all active about pages (PUBLIC)
    @GetMapping
    public ResponseEntity<List<AboutDTOs.AboutResponse>> getAll() {
        return ResponseEntity.ok(aboutService.getAllActive());
    }

    /**
     * GET single about page by slug (PUBLIC).
     * Accepts either the CKB slug or the KMR slug —
     * the service resolves whichever matches.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<AboutDTOs.AboutResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(aboutService.getBySlug(slug));
    }

    // CREATE (ADMIN)
    @PostMapping
    public ResponseEntity<AboutDTOs.AboutResponse> create(
            @RequestBody AboutDTOs.AboutRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aboutService.create(request));
    }

    // UPDATE (ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<AboutDTOs.AboutResponse> update(
            @PathVariable Long id,
            @RequestBody AboutDTOs.AboutRequest request) {

        return ResponseEntity.ok(aboutService.update(id, request));
    }

    // DELETE (ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aboutService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Upload single file (ADMIN)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AboutDTOs.UploadResponse> uploadMedia(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "type", required = false) String type
    ) throws IOException {

        String resolvedType = (type != null) ? type : "image";
        return ResponseEntity.ok(aboutService.uploadMedia(file, resolvedType));
    }

    // Upload multiple files (ADMIN)
    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AboutDTOs.UploadResponse>> uploadMultiple(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "type", required = false) String type
    ) {

        String resolvedType = (type != null) ? type : "image";

        List<AboutDTOs.UploadResponse> responses = files.stream()
                .map(file -> {
                    try {
                        return aboutService.uploadMedia(file, resolvedType);
                    } catch (IOException e) {
                        throw new RuntimeException("Upload failed: " + file.getOriginalFilename());
                    }
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}