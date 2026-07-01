package ak.dev.khi_backend.khi_app.api;

import ak.dev.khi_backend.khi_app.api.news.NewsController;
import ak.dev.khi_backend.khi_app.api.project.ProjectController;
import ak.dev.khi_backend.khi_app.api.publishment.image.ImageCollectionController;
import ak.dev.khi_backend.khi_app.api.publishment.sound.SoundTrackController;
import ak.dev.khi_backend.khi_app.api.publishment.video.VideoController;
import ak.dev.khi_backend.khi_app.api.publishment.writing.WritingController;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.service.news.NewsService;
import ak.dev.khi_backend.khi_app.service.project.ProjectService;
import ak.dev.khi_backend.khi_app.service.publishment.image.ImageCollectionService;
import ak.dev.khi_backend.khi_app.service.publishment.sound.SoundTrackService;
import ak.dev.khi_backend.khi_app.service.publishment.video.VideoService;
import ak.dev.khi_backend.khi_app.service.publishment.writing.WritingService;
import ak.dev.khi_backend.khi_app.service.site.SiteContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentDeleteControllerTests {

    @Mock private NewsService newsService;
    @Mock private ProjectService projectService;
    @Mock private ImageCollectionService imageCollectionService;
    @Mock private SoundTrackService soundTrackService;
    @Mock private VideoService videoService;
    @Mock private WritingService writingService;
    @Mock private PublishmentTopicRepository topicRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private SiteContentService siteContentService;

    @InjectMocks private NewsController newsController;
    @InjectMocks private ProjectController projectController;
    @InjectMocks private ImageCollectionController imageCollectionController;
    @InjectMocks private SoundTrackController soundTrackController;
    @InjectMocks private VideoController videoController;
    @InjectMocks private WritingController writingController;

    @Test
    void contentDeleteEndpointsReturnNoContent() {
        assertThat(newsController.deleteNews(999L).getStatusCode().value()).isEqualTo(204);
        assertThat(projectController.delete(999L).getStatusCode().value()).isEqualTo(204);
        assertThat(imageCollectionController.delete(999L).getStatusCode().value()).isEqualTo(204);
        assertThat(soundTrackController.delete(999L).getStatusCode().value()).isEqualTo(204);
        assertThat(videoController.deleteVideo(999L).getStatusCode().value()).isEqualTo(204);
        assertThat(writingController.delete(999L).getStatusCode().value()).isEqualTo(204);

        verify(newsService).deleteNews(999L);
        verify(projectService).delete(999L);
        verify(imageCollectionService).delete(999L);
        verify(soundTrackService).delete(999L);
        verify(videoService).deleteVideo(999L);
        verify(writingService).deleteWriting(999L);
    }
}
