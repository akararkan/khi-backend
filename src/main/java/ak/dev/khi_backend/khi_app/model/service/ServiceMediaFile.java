package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ServiceMediaFile — A single uploaded media asset inside a
 * {@link ServiceMediaCollection}.
 *
 * ─── Bilingual Text ───────────────────────────────────────────────────────────
 *  Each file carries its own CKB (Sorani) and KMR (Kurmanji) text via
 *  {@link ServiceMediaFileContent} embeddables mapped with @AttributeOverrides.
 *
 *    ckbContent → caption_ckb, title_ckb, description_ckb
 *    kmrContent → caption_kmr, title_kmr, description_kmr
 *
 *  These are entered by the admin in the CMS and rendered in the public UI
 *  under / alongside the media asset.
 *
 * ─── Technical Metadata (Auto-Extracted) ─────────────────────────────────────
 *  These fields are NEVER sent by the client — they are populated automatically
 *  by {@link ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor} during
 *  the upload step and stored transparently.
 *
 *  IMAGE  → fileFormat, widthPx, heightPx
 *  VIDEO  → fileFormat, widthPx, heightPx, durationSeconds, codec, bitrateKbps
 *  AUDIO  → fileFormat, durationSeconds, codec, bitrateKbps
 *
 * ─── File Storage ─────────────────────────────────────────────────────────────
 *  {@link #fileUrl}      — S3 / CDN URL of the actual uploaded file (required).
 *  {@link #thumbnailUrl} — Optional poster frame (VIDEO) or cover art (AUDIO).
 *  {@link #fileSize}     — Raw byte count, also auto-populated from the upload.
 */
@Entity
@Table(
        name = "service_media_files",
        indexes = {
                @Index(name = "idx_smf_collection_id", columnList = "collection_id"),
                @Index(name = "idx_smf_sort_order",    columnList = "sort_order"),
                @Index(name = "idx_smf_file_format",   columnList = "file_format")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── File References ──────────────────────────────────────────────────────

    /**
     * Primary S3 / CDN URL of the uploaded file.
     * Required — every row must point to a real asset.
     */
    @NotBlank
    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    /**
     * Optional thumbnail / poster image URL.
     * Used for VIDEO (poster frame) and AUDIO (cover art / waveform image).
     * May be null for IMAGE files.
     */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    // ─── Bilingual Content — CKB (Sorani) ────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "caption",     column = @Column(name = "caption_ckb",     length = 500)),
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT"))
    })
    private ServiceMediaFileContent ckbContent;

    // ─── Bilingual Content — KMR (Kurmanji) ──────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "caption",     column = @Column(name = "caption_kmr",     length = 500)),
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",       length = 300)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT"))
    })
    private ServiceMediaFileContent kmrContent;

    // ─── Technical Metadata (AUTO-EXTRACTED — never sent by client) ──────────

    /**
     * Normalised file format string, auto-detected from MIME type and file
     * bytes at upload time by {@link ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor}.
     *
     * Examples:
     *   IMAGE  → "JPEG", "PNG", "WEBP", "GIF", "SVG"
     *   VIDEO  → "MP4", "MOV", "MKV", "WEBM", "AVI"
     *   AUDIO  → "MP3", "WAV", "AAC", "FLAC", "OGG", "OPUS"
     */
    @Column(name = "file_format", length = 20)
    private String fileFormat;

    /**
     * Frame width in pixels.
     * Populated for IMAGE and VIDEO files. Null for AUDIO.
     */
    @Column(name = "width_px")
    private Integer widthPx;

    /**
     * Frame height in pixels.
     * Populated for IMAGE and VIDEO files. Null for AUDIO.
     */
    @Column(name = "height_px")
    private Integer heightPx;

    /**
     * Playback duration in whole seconds.
     * Populated for VIDEO and AUDIO files. Null for IMAGE.
     * Examples: 125 → "2:05",  3661 → "1:01:01"
     */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Human-readable codec name, auto-detected by ffprobe.
     * Null for IMAGE files.
     *
     * Examples:
     *   VIDEO → "H.264", "H.265", "VP9", "AV1"
     *   AUDIO → "AAC", "MP3", "FLAC", "Opus", "PCM"
     */
    @Column(name = "codec", length = 50)
    private String codec;

    /**
     * Average bitrate in kilobits per second, auto-detected by ffprobe.
     * Null for IMAGE files or when ffprobe is unavailable.
     * Examples: 128 kbps (audio), 4500 kbps (video)
     */
    @Column(name = "bitrate_kbps")
    private Integer bitrateKbps;

    /**
     * Raw file size in bytes, populated automatically from the multipart upload.
     * Used for admin storage accounting and "Download (12 MB)" labels.
     */
    @Column(name = "file_size")
    private Long fileSize;

    // ─── Display Order ────────────────────────────────────────────────────────

    /** Position of this file within its parent collection. */
    @Builder.Default
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ─── Parent ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ServiceMediaCollection collection;

    // ─── Convenience Computed Properties ─────────────────────────────────────

    /** Returns a formatted resolution string, e.g. "1920 × 1080". Null when not applicable. */
    public String getResolution() {
        if (widthPx != null && heightPx != null) return widthPx + " × " + heightPx;
        return null;
    }

    /** Returns a human-readable duration, e.g. "2:05" or "1:01:01". Null when not applicable. */
    public String getFormattedDuration() {
        if (durationSeconds == null) return null;
        int h = durationSeconds / 3600;
        int m = (durationSeconds % 3600) / 60;
        int s = durationSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }

    /** Returns a human-readable file size, e.g. "4.5 MB". Null when not recorded. */
    public String getFormattedFileSize() {
        if (fileSize == null) return null;
        if (fileSize >= 1_073_741_824L) return String.format("%.1f GB", fileSize / 1_073_741_824.0);
        if (fileSize >= 1_048_576L)     return String.format("%.1f MB", fileSize / 1_048_576.0);
        if (fileSize >= 1_024L)         return String.format("%.1f KB", fileSize / 1_024.0);
        return fileSize + " B";
    }
}