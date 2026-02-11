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
     * ADD - Create new SoundTrack with file upload
     * POST /api/v1/soundtracks
     * Content-Type: multipart/form-data
     *
     * Form Fields:
     * - title (required)
     * - soundType (required): LAWK or HAIRAN
     * - language (required): CKB or KMR
     * - trackState (required): SINGLE or MULTI
     * - description (optional)
     * - reading (optional)
     * - locations (optional): comma-separated, e.g., "Sulaymaniyah,Erbil,Duhok"
     * - director (optional)
     * - isThisProjectOfInstitute (optional): true/false
     * - keywords (optional): comma-separated, e.g., "story,folklore,kurdish"
     * - tags (optional): comma-separated, e.g., "audio,culture"
     * - readerNames (optional): For MULTI tracks, comma-separated reader names
     * - coverImage (optional): Image file for cover
     * - audioFiles (required): One or more audio files (MP3, WAV, OGG, etc.)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> addSoundTrack(
            @Valid @ModelAttribute CreateRequest request,
            @RequestPart(value = "audioFiles", required = true) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) {

        Response response = soundTrackService.addSoundTrack(request, audioFiles, coverImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET ALL - Retrieve all SoundTracks
     * GET /api/v1/soundtracks
     */
    @GetMapping
    public ResponseEntity<List<Response>> getAllSoundTracks() {
        List<Response> response = soundTrackService.getAllSoundTracks();
        return ResponseEntity.ok(response);
    }

    /**
     * UPDATE - Update existing SoundTrack with optional file upload
     * PUT /api/v1/soundtracks/{id}
     * Content-Type: multipart/form-data
     *
     * All fields are optional. Only send what you want to update.
     * If audioFiles are sent, they will REPLACE all existing audio files.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> updateSoundTrack(
            @PathVariable Long id,
            @Valid @ModelAttribute UpdateRequest request,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) {

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
     * SEARCH BY TAG
     * GET /api/v1/soundtracks/search/tag?value=folklore
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<Response>> searchByTag(@RequestParam String value) {
        List<Response> response = soundTrackService.searchByTag(value);
        return ResponseEntity.ok(response);
    }

    /**
     * SEARCH BY KEYWORD
     * GET /api/v1/soundtracks/search/keyword?value=story
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<List<Response>> searchByKeyword(@RequestParam String value) {
        List<Response> response = soundTrackService.searchByKeyword(value);
        return ResponseEntity.ok(response);
    }

    /**
     * SEARCH BY LOCATION (NEW)
     * GET /api/v1/soundtracks/search/location?value=Sulaymaniyah
     */
    @GetMapping("/search/location")
    public ResponseEntity<List<Response>> searchByLocation(@RequestParam String value) {
        List<Response> response = soundTrackService.searchByLocation(value);
        return ResponseEntity.ok(response);
    }
}