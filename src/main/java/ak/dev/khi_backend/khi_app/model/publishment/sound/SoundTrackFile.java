package ak.dev.khi_backend.khi_app.model.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "sound_track_files",
        indexes = {
                @Index(name = "idx_sound_file_type", columnList = "file_type")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundTrackFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Where the audio is stored (S3, local, CDN...)
     */
    @Column(name = "file_url", nullable = false, length = 1200)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    /**
     * Duration in seconds (best for querying/sorting)
     */
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    /**
     * File size in bytes
     */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /**
     * Optional: for MULTI tracks, if each file belongs to a person/reader name
     * (you said "belongs to a person")
     */
    @Column(name = "reader_name", length = 255)
    private String readerName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sound_track_id", nullable = false)
    private SoundTrack soundTrack;
}
