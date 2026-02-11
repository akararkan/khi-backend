package ak.dev.khi_backend.khi_app.api.publishment.image;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionLogDTO;
import ak.dev.khi_backend.khi_app.service.publishment.image.ImageCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/image-collections")
@RequiredArgsConstructor
public class ImageCollectionController {

    private final ImageCollectionService imageCollectionService;

    // ─── 1. ADD (cover + multiple album images + JSON data) ───────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageCollectionDTO> addImageCollection(
            @RequestPart("data") ImageCollectionDTO dto,
            @RequestPart(value = "cover", required = false) MultipartFile coverFile,
            @RequestPart(value = "images", required = false) List<MultipartFile> albumFiles) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageCollectionService.addImageCollection(dto, coverFile, albumFiles));
    }

    // ─── 2. GET ALL ───────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Page<ImageCollectionDTO>> getAllImageCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(imageCollectionService.getAllImageCollections(page, size));
    }

    // ─── 3. SEARCH BY KEYWORDS ────────────────────────────────────────
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<ImageCollectionDTO>> searchByKeyword(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(imageCollectionService.searchByKeyword(q, page, size));
    }

    // ─── 4. SEARCH BY TAGS ───────────────────────────────────────────
    @GetMapping("/search/tag")
    public ResponseEntity<Page<ImageCollectionDTO>> searchByTag(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(imageCollectionService.searchByTag(q, page, size));
    }

    // ─── 5. UPDATE (optional new cover + new album images) ────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageCollectionDTO> updateImageCollection(
            @PathVariable Long id,
            @RequestPart("data") ImageCollectionDTO dto,
            @RequestPart(value = "cover", required = false) MultipartFile coverFile,
            @RequestPart(value = "images", required = false) List<MultipartFile> albumFiles) {
        return ResponseEntity.ok(
                imageCollectionService.updateImageCollection(id, dto, coverFile, albumFiles));
    }

    // ─── 6. DELETE ────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImageCollection(@PathVariable Long id) {
        imageCollectionService.deleteImageCollection(id);
        return ResponseEntity.noContent().build();
    }

    // ─── LOGS ─────────────────────────────────────────────────────────
    @GetMapping("/logs")
    public ResponseEntity<Page<ImageCollectionLogDTO>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(imageCollectionService.getAllLogs(page, size));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<Page<ImageCollectionLogDTO>> getLogsByCollectionId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(imageCollectionService.getLogsByCollectionId(id, page, size));
    }
}
