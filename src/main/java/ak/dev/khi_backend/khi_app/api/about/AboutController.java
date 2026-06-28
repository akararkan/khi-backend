package ak.dev.khi_backend.khi_app.api.about;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs;
import ak.dev.khi_backend.khi_app.service.about.AboutService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AboutController — Tiptap-aware About endpoints.
 *
 * About carries no standalone media field — all visual media (image, video,
 * voice, document, or any other file) lives inside {@code ckbContent.body}
 * and {@code kmrContent.body} as Tiptap HTML. The frontend uploads each
 * file once via the shared {@code POST /api/v1/media/upload}, bakes the
 * returned URL into the editor, then submits the JSON body to this
 * controller. {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor}
 * also acts as a safety net that rewrites any inline base64 payloads on save.
 */
@RestController
@RequestMapping("/api/v1/about")
@RequiredArgsConstructor
@Tag(name = "About", description = "Bilingual About pages with Tiptap bodies and structured stats")
public class AboutController {

    private final AboutService aboutService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AboutDTOs.AboutResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                aboutService.getAllActive(page, size), "About pages fetched"));
    }

    /**
     * Backward-compatible detail lookup. Numeric values resolve by ID; all
     * other values resolve against both localized slugs.
     */
    @GetMapping("/{identifier}")
    public ResponseEntity<ApiResponse<AboutDTOs.AboutResponse>> getByIdentifier(
            @PathVariable String identifier) {
        return ResponseEntity.ok(ApiResponse.success(
                aboutService.getByIdentifier(identifier), "About page fetched"));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<AboutDTOs.AboutResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                aboutService.getBySlug(slug), "About page fetched"));
    }

    @PostMapping
    public ResponseEntity<AboutDTOs.AboutResponse> create(
            @RequestBody AboutDTOs.AboutRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aboutService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AboutDTOs.AboutResponse> update(
            @PathVariable Long id,
            @RequestBody AboutDTOs.AboutRequest request) {

        return ResponseEntity.ok(aboutService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aboutService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
