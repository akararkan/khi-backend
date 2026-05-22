package ak.dev.khi_backend.khi_app.api.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs;
import ak.dev.khi_backend.khi_app.service.about.AboutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AboutController — Tiptap-aware About endpoints.
 *
 * The CRUD shape is unchanged; only the request / response body is different:
 *   - No more {@code blocks[]} — content lives in {@code ckbContent.body}
 *     and {@code kmrContent.body} as Tiptap HTML.
 *   - Structured stats moved to a top-level {@code stats[]} array.
 *
 * Media uploads for inline images / audio / video now go through the shared
 * {@code POST /api/v1/media/upload} endpoint. The frontend uploads first,
 * then bakes the returned {@code fileUrl} into the editor HTML, then submits
 * the JSON body to this controller.
 */
@RestController
@RequestMapping("/api/v1/about")
@RequiredArgsConstructor
public class AboutController {

    private final AboutService aboutService;

    @GetMapping
    public ResponseEntity<List<AboutDTOs.AboutResponse>> getAll() {
        return ResponseEntity.ok(aboutService.getAllActive());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<AboutDTOs.AboutResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(aboutService.getBySlug(slug));
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
