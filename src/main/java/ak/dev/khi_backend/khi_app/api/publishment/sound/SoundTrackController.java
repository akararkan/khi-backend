package ak.dev.khi_backend.khi_app.api.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.service.publishment.sound.SoundTrackService;
import ak.dev.khi_backend.khi_app.service.publishment.topic.PublishmentTopicService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final PublishmentTopicService topicService;
    private final ObjectMapper objectMapper;

    /**
     * ADD - Create new SoundTrack with BILINGUAL content
     * POST /api/v1/soundtracks
     * Content-Type: multipart/form-data
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> addSoundTrack(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "hoverCoverImage", required = false) MultipartFile hoverCoverImage


    ) throws Exception {
        CreateRequest request = objectMapper.readValue(dataJson, CreateRequest.class);
        Response response = soundTrackService.addSoundTrack(request, audioFiles, ckbCoverImage , kmrCoverImage , hoverCoverImage);
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
     * GET ALL TOPICS for Sound publishments
     * GET /api/v1/soundtracks/topics
     */
    @GetMapping("/topics")
    public ResponseEntity<List<PublishmentTopic>> getSoundTopics() {
        return ResponseEntity.ok(topicService.getAllByType("SOUND"));
    }

    /**
     * UPDATE - Update existing SoundTrack with bilingual content
     * PUT /api/v1/soundtracks/{id}
     * Content-Type: multipart/form-data
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> updateSoundTrack(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "hoverCoverImage", required = false) MultipartFile hoverCoverImage
    ) throws Exception {
        UpdateRequest request = objectMapper.readValue(dataJson, UpdateRequest.class);
        Response response = soundTrackService.updateSoundTrack(id, request, audioFiles, ckbCoverImage , kmrCoverImage , hoverCoverImage);
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