package ak.dev.khi_backend.khi_app.service.publishment.writing;

import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WritingServiceDeleteIntegrationTests {

    @Autowired private WritingService writingService;
    @Autowired private WritingRepository writingRepository;
    @Autowired private WritingLogRepository writingLogRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void hardDeletePreservesAuditHistoryAndUnlinksChildBooks() {
        Writing parent = writingRepository.saveAndFlush(Writing.builder()
                .seriesId("series-delete-test")
                .seriesOrder(1.0)
                .build());
        Writing child = writingRepository.saveAndFlush(Writing.builder()
                .seriesId(parent.getSeriesId())
                .seriesOrder(2.0)
                .parentBook(parent)
                .build());
        writingLogRepository.saveAndFlush(WritingLog.builder()
                .writing(parent)
                .writingId(parent.getId())
                .action("CREATED")
                .createdAt(LocalDateTime.now())
                .build());
        Long parentId = parent.getId();
        Long childId = child.getId();
        entityManager.clear();

        writingService.deleteWriting(parentId);
        entityManager.clear();

        assertThat(writingRepository.findById(parentId)).isEmpty();
        assertThat(writingRepository.findById(childId))
                .get()
                .extracting(Writing::getParentBook)
                .isNull();

        List<WritingLog> logs = writingLogRepository.findAll().stream()
                .filter(log -> parentId.equals(log.getWritingId()))
                .toList();
        assertThat(logs)
                .hasSize(2)
                .allMatch(log -> log.getWriting() == null);
        assertThat(logs)
                .extracting(WritingLog::getAction)
                .containsExactlyInAnyOrder("CREATED", "DELETED");
    }
}
