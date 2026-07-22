package ak.dev.khi_backend.khi_app.service.publishment.video;

import ak.dev.khi_backend.khi_app.dto.publishment.video.VideoDTO;
import ak.dev.khi_backend.khi_app.model.publishment.video.Video;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoType;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the FILM multi-source fix: every uploaded video file must
 * be kept (not just the first), with exactly one flagged as the main source.
 */
@ExtendWith(MockitoExtension.class)
class VideoServiceFilmSourceTests {

    @Mock private VideoRepository videoRepository;
    @Mock private VideoLogRepository videoLogRepository;
    @Mock private PublishmentTopicRepository topicRepository;
    @Mock private S3Service s3Service;
    @Mock private TiptapHtmlProcessor tiptapHtmlProcessor;

    @InjectMocks
    private VideoService videoService;

    @Test
    void filmKeepsAllUploadedFilesAndMarksFirstAsMain() {
        when(s3Service.upload(any(byte[].class), any(), any()))
                .thenReturn("https://cdn/one.mp4", "https://cdn/two.mp4", "https://cdn/three.mp4");
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoDTO request = VideoDTO.builder()
                .videoType(VideoType.FILM)
                .build();

        VideoDTO response = videoService.addVideo(
                request, null, null, null, threeFilmFiles());

        // All three files are preserved (previously only the first survived).
        assertThat(response.getVideoSources()).hasSize(3);
        assertThat(response.getVideoSources()).extracting(VideoDTO.VideoSourceDTO::getUrl)
                .containsExactly("https://cdn/one.mp4", "https://cdn/two.mp4", "https://cdn/three.mp4");

        // Exactly one main — the first added.
        assertThat(response.getVideoSources()).filteredOn(s -> Boolean.TRUE.equals(s.getMain()))
                .singleElement()
                .satisfies(s -> assertThat(s.getUrl()).isEqualTo("https://cdn/one.mp4"));

        // Legacy mirror points at the main source (backward compatibility).
        assertThat(response.getSourceUrl()).isEqualTo("https://cdn/one.mp4");
    }

    @Test
    void filmHonoursExplicitMainFlagFromJson() {
        when(s3Service.upload(any(byte[].class), any(), any()))
                .thenReturn("https://cdn/one.mp4", "https://cdn/two.mp4", "https://cdn/three.mp4");
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoDTO request = VideoDTO.builder()
                .videoType(VideoType.FILM)
                .videoSources(List.of(
                        VideoDTO.VideoSourceDTO.builder().label("Part 1").build(),
                        VideoDTO.VideoSourceDTO.builder().label("Part 2").main(true).build(),
                        VideoDTO.VideoSourceDTO.builder().label("Part 3").build()))
                .build();

        VideoDTO response = videoService.addVideo(
                request, null, null, null, threeFilmFiles());

        assertThat(response.getVideoSources()).hasSize(3);
        assertThat(response.getVideoSources()).filteredOn(s -> Boolean.TRUE.equals(s.getMain()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getLabel()).isEqualTo("Part 2");
                    assertThat(s.getUrl()).isEqualTo("https://cdn/two.mp4");
                });
        assertThat(response.getSourceUrl()).isEqualTo("https://cdn/two.mp4");
    }

    private List<MultipartFile> threeFilmFiles() {
        return List.of(
                new MockMultipartFile("videoFiles", "one.mp4",   "video/mp4", new byte[]{1}),
                new MockMultipartFile("videoFiles", "two.mp4",   "video/mp4", new byte[]{2}),
                new MockMultipartFile("videoFiles", "three.mp4", "video/mp4", new byte[]{3}));
    }
}
