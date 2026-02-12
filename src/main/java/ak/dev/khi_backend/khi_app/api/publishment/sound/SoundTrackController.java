package ak.dev.khi_backend.khi_app.api.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.service.publishment.sound.SoundTrackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/soundtracks")
@RequiredArgsConstructor
public class SoundTrackController {

    private final SoundTrackService soundTrackService;

    /**
     * ADD - Create new SoundTrack with BILINGUAL content
     * POST /api/v1/soundtracks
     * Content-Type: multipart/form-data
     *
     * Parts:
     * - data (JSON): CreateRequest
     * - coverImage (optional): image file
     * - audioFiles (optional): list of audio files
     *
     * NOTE:
     * - audioFiles is OPTIONAL now.
     * - You can create SoundTrack using ONLY links inside data.files (externalUrl/embedUrl/fileUrl)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> addSoundTrack(
            @Valid @RequestPart("data") CreateRequest request,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage
    ) {
        Response response = soundTrackService.addSoundTrack(request, audioFiles, coverImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET ALL - Retrieve all SoundTracks with bilingual content
     * GET /api/v1/soundtracks
     */
    @GetMapping
    public ResponseEntity<List<Response>> getAllSoundTracks() {
        List<Response> response = soundTrackService.getAllSoundTracks();
        return ResponseEntity.ok(response);
    }

    /**
     * UPDATE - Update existing SoundTrack with bilingual content
     * PUT /api/v1/soundtracks/{id}
     * Content-Type: multipart/form-data
     *
     * Parts:
     * - data (JSON): UpdateRequest
     * - coverImage (optional): image file
     * - audioFiles (optional): list of audio files
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> updateSoundTrack(
            @PathVariable Long id,
            @Valid @RequestPart("data") UpdateRequest request,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage
    ) {
        Response response = soundTrackService.updateSoundTrack(id, request, audioFiles, coverImage);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE - Remove SoundTrack
     * DELETE /api/v1/soundtracks/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSoundTrack(@PathVariable Long id) {
        soundTrackService.deleteSoundTrack(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * SEARCH BY TAG - Bilingual search
     * GET /api/v1/soundtracks/search/tag?value=folklore&language=ckb
     *
     * language: "ckb" (Sorani only), "kmr" (Kurmanji only), or omit for both
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<Response>> searchByTag(
            @RequestParam String value,
            @RequestParam(required = false) String language
    ) {
        List<Response> response = soundTrackService.searchByTag(value, language);
        return ResponseEntity.ok(response);
    }

    /**
     * SEARCH BY KEYWORD - Bilingual search
     * GET /api/v1/soundtracks/search/keyword?value=story&language=kmr
     *
     * language: "ckb" (Sorani only), "kmr" (Kurmanji only), or omit for both
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<List<Response>> searchByKeyword(
            @RequestParam String value,
            @RequestParam(required = false) String language
    ) {
        List<Response> response = soundTrackService.searchByKeyword(value, language);
        return ResponseEntity.ok(response);
    }

    /**
     * SEARCH BY LOCATION
     * GET /api/v1/soundtracks/search/location?value=Sulaymaniyah
     */
    @GetMapping("/search/location")
    public ResponseEntity<List<Response>> searchByLocation(@RequestParam String value) {
        List<Response> response = soundTrackService.searchByLocation(value);
        return ResponseEntity.ok(response);
    }
}
