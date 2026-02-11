package ak.dev.khi_backend.khi_app.model.publishment.album_of_memories;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "album_audit_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlbumAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private AlbumOfMemories album;

    // Action: CREATE, UPDATE, DELETE
    @Column(nullable = false, length = 20)
    private String action;

    // Who performed the action
    @Column(name = "performed_by", length = 100)
    private String performedBy;

    // Additional notes
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    public void prePersist() {
        this.performedAt = LocalDateTime.now();
    }
}