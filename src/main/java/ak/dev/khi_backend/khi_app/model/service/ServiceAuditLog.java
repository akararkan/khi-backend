package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ServiceAuditLog — Audit trail for Service CRUD operations.
 *
 * Tracks CREATE, UPDATE, DELETE, TOGGLE_ACTIVE actions.
 * The serviceId + serviceType are stored as snapshot fields so that
 * log records survive even after the service itself is hard-deleted.
 */
@Entity
@Table(
        name = "service_audit_logs",
        indexes = {
                @Index(name = "idx_sal_service_id", columnList = "service_id"),
                @Index(name = "idx_sal_action",     columnList = "action"),
                @Index(name = "idx_sal_timestamp",  columnList = "timestamp")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the service this log entry refers to. */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /** Snapshot of the service type at the time of the action. */
    @Column(name = "service_type", length = 100)
    private String serviceType;

    /** CREATE | UPDATE | DELETE | TOGGLE_ACTIVE */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** Human-readable detail about what changed. */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /** Who performed the action (future auth integration). */
    @Column(name = "performed_by", length = 150)
    private String performedBy;

    /** Request trace ID for log correlation. */
    @Column(name = "request_id", length = 120)
    private String requestId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}

