package ak.dev.khi_backend.khi_app.model.publishment.writing;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
/**
 * Audit log for Writing/Book operations
 * Tracks CREATE, UPDATE, DELETE actions
 */
@Entity
@Table(
        name = "writing_logs",
        indexes = {
                @Index(name = "idx_wlog_writing", columnList = "writing_id"),
                @Index(name = "idx_wlog_action", columnList = "action"),
                @Index(name = "idx_wlog_created_at", columnList = "created_at")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WritingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which writing was affected
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "writing_id", nullable = false)
    private Writing writing;

    /**
     * Action performed: "CREATED", "UPDATED", "DELETED", "FILE_UPLOADED", etc.
     */
    @Column(nullable = false, length = 40)
    private String action;

    /**
     * Who performed the action (user ID)
     */
    @Column(name = "actor_id", length = 120)
    private String actorId;

    /**
     * Actor's name
     */
    @Column(name = "actor_name", length = 200)
    private String actorName;

    /**
     * Request/Trace ID for debugging
     */
    @Column(name = "request_id", length = 120)
    private String requestId;

    /**
     * Additional metadata (IP, device, etc.)
     */
    @Column(name = "meta", length = 1000)
    private String meta;

    /**
     * Details of what changed (JSON format)
     */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}