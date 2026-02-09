package ak.dev.khi_backend.khi_app.model.publishment.sound;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "sound_track_logs",
        indexes = {
                @Index(name = "idx_stlog_soundtrack", columnList = "sound_track_id"),
                @Index(name = "idx_stlog_action", columnList = "action"),
                @Index(name = "idx_stlog_created_at", columnList = "created_at")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundTrackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // which soundtrack
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sound_track_id", nullable = false)
    private SoundTrack soundTrack;

    /**
     * NO enum. Service writes: "CREATED", "UPDATED", "DELETED", "FILE_ADDED", ...
     */
    @Column(nullable = false, length = 40)
    private String action;

    // who
    @Column(name = "actor_id", length = 120)
    private String actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    // request/trace
    @Column(name = "request_id", length = 120)
    private String requestId;

    // optional metadata (ip, device, etc.)
    @Column(name = "meta", length = 1000)
    private String meta;

    // changed fields summary / json text
    @Column(name = "details", length = 8000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
