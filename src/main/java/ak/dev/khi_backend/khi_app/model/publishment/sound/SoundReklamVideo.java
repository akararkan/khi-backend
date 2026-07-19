package ak.dev.khi_backend.khi_app.model.publishment.sound;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sound_reklam_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundReklamVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_url", nullable = false, length = 1200)
    private String videoUrl;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
