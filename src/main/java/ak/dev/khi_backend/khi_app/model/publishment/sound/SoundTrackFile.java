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
     * Direct hosted file URL (S3, local, CDN...)
     * OPTIONAL now (because you can provide externalUrl/embedUrl instead)
     */
    @Column(name = "file_url", nullable = true, length = 1200)
    private String fileUrl;

    /**
     * External page link (e.g. YouTube watch, SoundCloud page, etc.)
     */
    @Column(name = "external_url", columnDefinition = "text")
    private String externalUrl;

    /**
     * Embed link (e.g. YouTube embed / iframe url)
     */
    @Column(name = "embed_url", columnDefinition = "text")
    private String embedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    /**
     * Duration in seconds (we set 0 when unknown)
     */
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    /**
     * File size in bytes (we set 0 when unknown)
     */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /**
     * Optional: for MULTI tracks, each file can belong to a person/reader name
     */
    @Column(name = "reader_name", length = 255)
    private String readerName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sound_track_id", nullable = false)
    private SoundTrack soundTrack;
}
