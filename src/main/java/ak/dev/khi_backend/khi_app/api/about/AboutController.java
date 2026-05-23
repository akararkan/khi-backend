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
