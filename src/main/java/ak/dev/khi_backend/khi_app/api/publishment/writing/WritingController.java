package ak.dev.khi_backend.khi_app.api.publishment.writing;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.publishment.writing.WritingDtos.*;
import ak.dev.khi_backend.khi_app.service.publishment.writing.WritingService;
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

@RestController
@RequestMapping("/api/v1/writings")
@RequiredArgsConstructor
@Slf4j
public class WritingController {

    private final WritingService writingService;

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
            @RequestPart("data") @Valid CreateRequest request,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "ckbBookFile", required = false) MultipartFile ckbBookFile,
            @RequestPart(value = "kmrBookFile", required = false) MultipartFile kmrBookFile) throws IOException {

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
            @RequestPart("data") @Valid UpdateRequest request,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "ckbBookFile", required = false) MultipartFile ckbBookFile,
            @RequestPart(value = "kmrBookFile", required = false) MultipartFile kmrBookFile) throws IOException {

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
