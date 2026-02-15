package ak.dev.khi_backend.khi_app.api.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.service.publishment.sound.SoundTrackService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    /**
     * ADD - Create new SoundTrack with BILINGUAL content
     * POST /api/v1/soundtracks
     * Content-Type: multipart/form-data
     *
     * Parts:
     * - data (TEXT/JSON string): CreateRequest JSON
     * - coverImage (optional): image file
     * - audioFiles (optional): list of audio files
     *
     * NOTE (best behavior):
     * - audioFiles is OPTIONAL.
     * - If audioFiles are provided, you SHOULD NOT send data.files (links).
     * - If audioFiles are not provided, you can send link-based files in data.files.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> addSoundTrack(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage
    ) throws Exception {

        // Keep String parsing to be compatible with Postman "text" part (same as News)
        CreateRequest request = objectMapper.readValue(dataJson, CreateRequest.class);

        // Service will enforce business validation (single vs multi + either/or sources).
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
     * - data (TEXT/JSON string): UpdateRequest JSON
     * - coverImage (optional): image file
     * - audioFiles (optional): list of audio files
     *
     * NOTE (best behavior):
     * - If audioFiles are provided, they REPLACE existing files and data.files is ignored.
     * - If audioFiles are not provided and data.files is present (even empty list), it REPLACES existing files.
     * - If neither provided, files remain unchanged.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> updateSoundTrack(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage
    ) throws Exception {

        UpdateRequest request = objectMapper.readValue(dataJson, UpdateRequest.class);

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
