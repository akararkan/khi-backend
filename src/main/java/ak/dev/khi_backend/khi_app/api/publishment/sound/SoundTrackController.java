package ak.dev.khi_backend.khi_app.api.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.service.publishment.sound.SoundTrackService;
import ak.dev.khi_backend.khi_app.service.publishment.topic.PublishmentTopicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private final SoundTrackService       soundTrackService;
    private final PublishmentTopicService topicService;
    private final ObjectMapper            objectMapper;

    // ─── Default pagination constants ─────────────────────────────────────────
    private static final int DEFAULT_PAGE      = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE     = 100;

    // ═══════════════════════════════════════════════════════════════════════════
    // دروستکردن
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ADD — Create new SoundTrack with bilingual content.
     * POST /api/v1/soundtracks
     * Content-Type: multipart/form-data
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> addSoundTrack(
            @RequestPart("data")                                             String          dataJson,
            @RequestPart(value = "audioFiles",      required = false) List<MultipartFile>   audioFiles,
            @RequestPart(value = "ckbCoverImage",   required = false)        MultipartFile   ckbCoverImage,
            @RequestPart(value = "kmrCoverImage",   required = false)        MultipartFile   kmrCoverImage,
            @RequestPart(value = "hoverCoverImage", required = false)        MultipartFile   hoverCoverImage
    ) throws Exception {
        CreateRequest request  = objectMapper.readValue(dataJson, CreateRequest.class);
        Response      response = soundTrackService.addSoundTrack(
                request, audioFiles, ckbCoverImage, kmrCoverImage, hoverCoverImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // هێنانەوە - بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET ALL (paginated) — newest first.
     * GET /api/v1/soundtracks?page=0&size=20
     *
     * @param page  zero-based page index  (default 0)
     * @param size  items per page          (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<Page<Response>> getAllSoundTracks(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.getAllSoundTracks(pageable));
    }

    /**
     * GET albums-of-memories (paginated).
     * GET /api/v1/soundtracks/albums-of-memories?page=0&size=20
     */
    @GetMapping("/albums-of-memories")
    public ResponseEntity<Page<Response>> getAlbumsOfMemories(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.getAlbumsOfMemories(pageable));
    }

    /**
     * GET regular multi-tracks (paginated).
     * GET /api/v1/soundtracks/multi?page=0&size=20
     */
    @GetMapping("/multi")
    public ResponseEntity<Page<Response>> getRegularMultiTracks(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.getRegularMultiTracks(pageable));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // بابەتەکان
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET ALL TOPICS for Sound publishments.
     * GET /api/v1/soundtracks/topics
     */
    @GetMapping("/topics")
    public ResponseEntity<List<PublishmentTopic>> getSoundTopics() {
        return ResponseEntity.ok(topicService.getAllByType("SOUND"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // گۆڕین و سڕینەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * UPDATE — Update existing SoundTrack.
     * PUT /api/v1/soundtracks/{id}
     * Content-Type: multipart/form-data
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> updateSoundTrack(
            @PathVariable                                                    Long            id,
            @RequestPart("data")                                             String          dataJson,
            @RequestPart(value = "audioFiles",      required = false) List<MultipartFile>   audioFiles,
            @RequestPart(value = "ckbCoverImage",   required = false)        MultipartFile   ckbCoverImage,
            @RequestPart(value = "kmrCoverImage",   required = false)        MultipartFile   kmrCoverImage,
            @RequestPart(value = "hoverCoverImage", required = false)        MultipartFile   hoverCoverImage
    ) throws Exception {
        UpdateRequest request  = objectMapper.readValue(dataJson, UpdateRequest.class);
        Response      response = soundTrackService.updateSoundTrack(
                id, request, audioFiles, ckbCoverImage, kmrCoverImage, hoverCoverImage);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE — Remove SoundTrack.
     * DELETE /api/v1/soundtracks/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSoundTrack(@PathVariable Long id) {
        soundTrackService.deleteSoundTrack(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕان - هەمووی بە پەیجبەندی
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * SEARCH BY TAG — bilingual.
     * GET /api/v1/soundtracks/search/tag?value=folklore&language=ckb&page=0&size=20
     *
     * @param language  optional: "ckb" | "kmr" — omit for bilingual search
     */
    @GetMapping("/search/tag")
    public ResponseEntity<Page<Response>> searchByTag(
            @RequestParam                      String value,
            @RequestParam(required = false)    String language,
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.searchByTag(value, language, pageable));
    }

    /**
     * SEARCH BY KEYWORD — bilingual.
     * GET /api/v1/soundtracks/search/keyword?value=story&language=kmr&page=0&size=20
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<Response>> searchByKeyword(
            @RequestParam                      String value,
            @RequestParam(required = false)    String language,
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.searchByKeyword(value, language, pageable));
    }

    /**
     * SEARCH BY LOCATION — case-insensitive exact match.
     * GET /api/v1/soundtracks/search/location?value=Sulaymaniyah&page=0&size=20
     */
    @GetMapping("/search/location")
    public ResponseEntity<Page<Response>> searchByLocation(
            @RequestParam                      String value,
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.searchByLocation(value, pageable));
    }

    /**
     * COMBINED SEARCH — searches across titles, descriptions, soundType,
     * director, tags, keywords, and locations in one call.
     *
     * GET /api/v1/soundtracks/search?q=folklore&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Response>> searchCombined(
            @RequestParam                      String q,
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(soundTrackService.searchCombined(q, pageable));
    }
}