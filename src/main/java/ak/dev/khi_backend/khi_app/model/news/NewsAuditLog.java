package ak.dev.khi_backend.khi_app.model.news;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which news was affected
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    // CREATE / UPDATE / DELETE stored as string
    @Column(nullable = false, length = 20)
    private String action;

    // Who did the action
    @Column(length = 150)
    private String performedBy;

    // Optional description
    @Column(columnDefinition = "TEXT")
    private String note;

    // When action happened (optional; same as createdAt)
    private LocalDateTime actionTime;

    // ✅ Standard timestamps
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        // keep compatibility
        if (this.actionTime == null) {
            this.actionTime = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
