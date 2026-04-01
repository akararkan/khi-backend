package ak.dev.khi_backend.khi_app.api.publishment.video;

import ak.dev.khi_backend.khi_app.dto.publishment.video.VideoDTO;
import ak.dev.khi_backend.khi_app.service.publishment.video.VideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final ObjectMapper objectMapper;

    // 1) ADD
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDTO> addVideo(
            @RequestPart("data") String dtoJson,

            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "hoverImage", required = false) MultipartFile hoverImage,

            @RequestPart(value = "videoFile", required = false) MultipartFile videoFile
    ) throws Exception {
        VideoDTO dto = objectMapper.readValue(dtoJson, VideoDTO.class);
        VideoDTO created = videoService.addVideo(dto, ckbCoverImage, kmrCoverImage, hoverImage, videoFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ✅ Topics (from service directly)
    @GetMapping("/topics")
    public ResponseEntity<List<VideoDTO.TopicView>> getVideoTopics() {
        return ResponseEntity.ok(videoService.getTopics());
    }

    @PostMapping("/topics")
    public ResponseEntity<VideoDTO.TopicView> createVideoTopic(@RequestParam(required = false) String nameCkb,
                                                               @RequestParam(required = false) String nameKmr) {
        return ResponseEntity.status(HttpStatus.CREATED).body(videoService.createTopic(nameCkb, nameKmr));
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> deleteVideoTopic(@PathVariable Long topicId) {
        videoService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    // 2) GET ALL (paged)
    @GetMapping
    public ResponseEntity<Page<VideoDTO>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(videoService.getAllVideos(page, size));
    }

    // 3) GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<VideoDTO> getVideoById(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.getVideoById(id));
    }

    // 4) SEARCH BY TAG (paged)
    @GetMapping("/search/tag")
    public ResponseEntity<Page<VideoDTO>> searchByTag(
            @RequestParam("value") String value,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(videoService.searchByTag(value, page, size));
    }

    // 5) SEARCH BY KEYWORD (paged)
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<VideoDTO>> searchByKeyword(
            @RequestParam("value") String value,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(videoService.searchByKeyword(value, page, size));
    }

    // 6) UPDATE
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDTO> updateVideo(
            @PathVariable Long id,
            @RequestPart("data") String dtoJson,

            @RequestPart(value = "ckbCoverImage", required = false) MultipartFile ckbCoverImage,
            @RequestPart(value = "kmrCoverImage", required = false) MultipartFile kmrCoverImage,
            @RequestPart(value = "hoverImage", required = false) MultipartFile hoverImage,

            @RequestPart(value = "videoFile", required = false) MultipartFile videoFile
    ) throws Exception {
        VideoDTO dto = objectMapper.readValue(dtoJson, VideoDTO.class);
        VideoDTO updated = videoService.updateVideo(id, dto, ckbCoverImage, kmrCoverImage, hoverImage, videoFile);
        return ResponseEntity.ok(updated);
    }

    // 7) DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id) {
        videoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }
}