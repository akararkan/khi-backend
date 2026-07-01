package ak.dev.khi_backend.khi_app.service.project;

import ak.dev.khi_backend.khi_app.repository.project.ProjectKeywordRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectLogRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectTagRepository;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceDeleteTests {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTagRepository projectTagRepository;
    @Mock private ProjectKeywordRepository projectKeywordRepository;
    @Mock private ProjectLogRepository projectLogRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TiptapHtmlProcessor tiptapHtmlProcessor;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void configureTransactionManager() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void deleteIgnoresMissingProject() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        projectService.delete(999L);

        verify(projectRepository, never()).delete(any());
    }
}
