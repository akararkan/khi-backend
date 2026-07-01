package ak.dev.khi_backend.khi_app.service.publishment.video;

import ak.dev.khi_backend.khi_app.dto.publishment.video.VideoDTO;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.publishment.video.Video;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoClipItem;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceUpdateTests {

    @Mock private VideoRepository videoRepository;
    @Mock private VideoLogRepository videoLogRepository;
    @Mock private PublishmentTopicRepository topicRepository;
    @Mock private S3Service s3Service;
    @Mock private TiptapHtmlProcessor tiptapHtmlProcessor;

    @InjectMocks
    private VideoService videoService;

    @Test
    void existingClipKeepsSourceWhenUpdateOnlyChangesMetadata() {
        Video video = videoWithExistingClip();
        when(videoRepository.findById(8L)).thenReturn(Optional.of(video));
        when(videoRepository.save(video)).thenReturn(video);

        VideoDTO request = VideoDTO.builder()
                .videoType(VideoType.VIDEO_CLIP)
                .videoClipItems(List.of(VideoDTO.VideoClipItemDTO.builder()
                        .id(3L)
                        .titleCkb("updated title")
                        .build()))
                .build();

        VideoDTO response = videoService.updateVideo(
                8L, request, null, null, null, null);

        assertThat(response.getVideoClipItems()).singleElement().satisfies(clip -> {
            assertThat(clip.getId()).isEqualTo(3L);
            assertThat(clip.getUrl()).isEqualTo("https://cdn.example.com/original.mp4");
            assertThat(clip.getTitleCkb()).isEqualTo("updated title");
        });
        verify(s3Service, never()).upload(any(byte[].class), any(), any());
    }

    @Test
    void validatesClipBeforeUploadingCover() {
        Video video = videoWithExistingClip();
        when(videoRepository.findById(8L)).thenReturn(Optional.of(video));

        VideoDTO request = VideoDTO.builder()
                .videoType(VideoType.VIDEO_CLIP)
                .videoClipItems(List.of(VideoDTO.VideoClipItemDTO.builder()
                        .titleCkb("new clip without source")
                        .build()))
                .build();
        MockMultipartFile cover = new MockMultipartFile(
                "ckbCoverImage", "cover.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> videoService.updateVideo(
                8L, request, cover, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("video.clip.source.required");

        verify(s3Service, never()).upload(any(byte[].class), any(), any());
        verify(videoRepository, never()).save(any());
    }

    @Test
    void deleteIgnoresMissingVideo() {
        when(videoRepository.findById(999L)).thenReturn(Optional.empty());

        videoService.deleteVideo(999L);

        verify(videoRepository, never()).delete(any());
    }

    private Video videoWithExistingClip() {
        Video video = Video.builder()
                .id(8L)
                .videoType(VideoType.VIDEO_CLIP)
                .build();
        video.addClipItem(VideoClipItem.builder()
                .id(3L)
                .url("https://cdn.example.com/original.mp4")
                .clipNumber(1)
                .titleCkb("old title")
                .build());
        return video;
    }
}
