package ak.dev.khi_backend.khi_app.service.news;

import ak.dev.khi_backend.khi_app.repository.news.NewsAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsSubCategoryRepository;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceDeleteTests {

    @Mock private NewsRepository newsRepository;
    @Mock private NewsCategoryRepository newsCategoryRepository;
    @Mock private NewsSubCategoryRepository newsSubCategoryRepository;
    @Mock private NewsAuditLogRepository newsAuditLogRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TiptapHtmlProcessor tiptapHtmlProcessor;

    @InjectMocks
    private NewsService newsService;

    @BeforeEach
    void executeTransactionsInline() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void deleteIgnoresMissingNews() {
        when(newsRepository.findByIdWithGraph(999L)).thenReturn(Optional.empty());

        newsService.deleteNews(999L);

        verify(newsRepository, never()).delete(any());
    }
}
