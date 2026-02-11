package ak.dev.khi_backend.khi_app.model.publishment.album_of_memories;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "album_media")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlbumMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    // Track title in both languages
    @Column(name = "track_title_ckb", length = 250)
    private String trackTitleCkb;

    @Column(name = "track_title_kmr", length = 250)
    private String trackTitleKmr;

    // Track number/order
    @Column(name = "track_number")
    private Integer trackNumber;

    // Duration in seconds
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // File format (mp3, mp4, wav, etc.)
    @Column(name = "file_format", length = 20)
    private String fileFormat;

    // File size in bytes
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumOfMemories album;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}