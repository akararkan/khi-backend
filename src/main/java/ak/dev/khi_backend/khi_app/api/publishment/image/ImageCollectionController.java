package ak.dev.khi_backend.khi_app.api.publishment.image;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.*;
import ak.dev.khi_backend.khi_app.service.publishment.image.ImageCollectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/image-collections")
@RequiredArgsConstructor
public class ImageCollectionController {

    private final ImageCollectionService imageCollectionService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE (multipart)
    // - data: JSON string (Postman friendly)
    // - cover: optional (if coverUrl in data)
    // - images: optional (files)
    // ============================================================
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Response>> create(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) throws Exception {

        CreateRequest dto = objectMapper.readValue(dataJson, CreateRequest.class);

        int coverCount = (cover != null && !cover.isEmpty()) ? 1 : 0;
        int imagesCount = (images != null) ? (int) images.stream().filter(f -> f != null && !f.isEmpty()).count() : 0;
        int dtoAlbumCount = (dto.getImageAlbum() != null) ? dto.getImageAlbum().size() : 0;

        log.info("POST /api/v1/image-collections | coverFile={} imagesFiles={} albumDtoCount={} langs={}",
                coverCount, imagesCount, dtoAlbumCount, dto.getContentLanguages());

        Response created = imageCollectionService.create(dto, cover, images);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Image collection created successfully"));
    }

    // ============================================================
    // CREATE (json only)
    // ============================================================
    @PostMapping(
            value = "/json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Response>> createJson(@Valid @RequestBody CreateRequest dto) {

        int dtoAlbumCount = (dto.getImageAlbum() != null) ? dto.getImageAlbum().size() : 0;

        log.info("POST /api/v1/image-collections/json | albumDtoCount={} langs={}",
                dtoAlbumCount, dto.getContentLanguages());

        Response created = imageCollectionService.create(dto, null, null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Image collection created successfully"));
    }

    // ============================================================
    // UPDATE (multipart)
    // ============================================================
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Response>> update(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) throws Exception {

        UpdateRequest dto = objectMapper.readValue(dataJson, UpdateRequest.class);

        int coverCount = (cover != null && !cover.isEmpty()) ? 1 : 0;
        int imagesCount = (images != null) ? (int) images.stream().filter(f -> f != null && !f.isEmpty()).count() : 0;
        int dtoAlbumCount = (dto.getImageAlbum() != null) ? dto.getImageAlbum().size() : -1; // -1 = not provided

        log.info("PUT /api/v1/image-collections/{} | coverFile={} imagesFiles={} albumDtoCount={} langs={}",
                id, coverCount, imagesCount, dtoAlbumCount, dto.getContentLanguages());

        Response updated = imageCollectionService.update(id, dto, cover, images);

        return ResponseEntity.ok(ApiResponse.success(updated, "Image collection updated successfully"));
    }

    // ============================================================
    // GET ALL
    // ============================================================
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<Response>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(
                imageCollectionService.getAll(),
                "Image collections fetched successfully"
        ));
    }

    // ============================================================
    // DELETE
    // ============================================================
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {

        log.info("DELETE /api/v1/image-collections/{}", id);

        imageCollectionService.delete(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Image collection deleted successfully"));
    }
}
