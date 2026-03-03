package ak.dev.khi_backend.khi_app.model.publishment.writing;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Audit log for Writing/Book operations.
 * Tracks CREATE, UPDATE, DELETE actions.
 *
 * ── Design note ─────────────────────────────────────────────────────────────
 *
 * The {@link #writing} FK is intentionally NULLABLE (optional = true).
 *
 * For DELETE logs the Writing row is removed from the DB, so we cannot keep
 * a live FK. Instead:
 *  - {@link #writing}   → set to null  (no FK)
 *  - {@link #writingId} → stores the original PK as a plain BIGINT tombstone
 *
 * For CREATE / UPDATE logs both fields are populated:
 *  - {@link #writing}   → live FK reference
 *  - {@link #writingId} → same ID, denormalised for cheap queries
 *
 * This avoids the TransientPropertyValueException that occurs when Hibernate
 * auto-flushes a pending WritingLog INSERT that still references a Writing
 * that has already been scheduled for DELETE in the same transaction.
 *
 * ── Migration ────────────────────────────────────────────────────────────────
 *
 *   -- Drop NOT NULL on FK column (if it was previously non-nullable)
 *   ALTER TABLE writing_logs ALTER COLUMN writing_id DROP NOT NULL;
 *
 *   -- Add the tombstone column (idempotent)
 *   ALTER TABLE writing_logs
 *       ADD COLUMN IF NOT EXISTS writing_id_ref BIGINT;
 *
 *   -- Back-fill existing rows
 *   UPDATE writing_logs SET writing_id_ref = writing_id WHERE writing_id_ref IS NULL;
 */
@Entity
@Table(
        name = "writing_logs",
        indexes = {
                @Index(name = "idx_wlog_writing",    columnList = "writing_id"),
                @Index(name = "idx_wlog_writing_ref",columnList = "writing_id_ref"),
                @Index(name = "idx_wlog_action",     columnList = "action"),
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
     * Live FK to the affected Writing.
     * NULL for DELETE logs (the Writing has been removed).
     * Use {@link #writingId} to identify which Writing was deleted.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "writing_id", nullable = true)
    private Writing writing;

    /**
     * Denormalised PK of the affected Writing.
     * Always populated — survives even after the Writing row is deleted.
     * Useful for audit queries: "what happened to writing #42?"
     */
    @Column(name = "writing_id_ref", nullable = false)
    private Long writingId;

    /**
     * Action performed: "CREATED", "UPDATED", "DELETED", "FILE_UPLOADED", etc.
     */
    @Column(nullable = false, length = 40)
    private String action;

    /** Who performed the action (user ID). */
    @Column(name = "actor_id", length = 120)
    private String actorId;

    /** Actor's display name. */
    @Column(name = "actor_name", length = 200)
    private String actorName;

    /** Request / Trace ID for debugging. */
    @Column(name = "request_id", length = 120)
    private String requestId;

    /** Additional metadata (IP, device, etc.) */
    @Column(name = "meta", length = 1000)
    private String meta;

    /** Details of what changed (free-text or JSON). */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}