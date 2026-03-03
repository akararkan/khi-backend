package ak.dev.khi_backend.khi_app.model.publishment.video;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * VideoLog — Audit log for Video publishment operations.
 * Tracks CREATED, UPDATED, DELETED actions.
 *
 * The videoId + videoTitle are stored as snapshot fields so that
 * log records survive even after the video itself is hard-deleted.
 */
@Entity
@Table(
        name = "video_logs",
        indexes = {
                @Index(name = "idx_vlog_video_id",   columnList = "video_id"),
                @Index(name = "idx_vlog_action",     columnList = "action"),
                @Index(name = "idx_vlog_timestamp",  columnList = "timestamp")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VideoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the video that was affected (snapshot; nullable after deletion). */
    @Column(name = "video_id")
    private Long videoId;

    /** Title snapshot at the time of the action. */
    @Column(name = "video_title", length = 300)
    private String videoTitle;

    /** Action performed: "CREATED", "UPDATED", "DELETED". */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** Human-readable detail about what changed. */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /** Who performed the action (future auth integration). */
    @Column(name = "performed_by", length = 150)
    private String performedBy;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
