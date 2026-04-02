package ak.dev.khi_backend.khi_app.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.mp3.Mp3Directory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Locale;

/**
 * MediaMetadataExtractor — Pure-Java metadata extraction for uploaded media.
 *
 * Uses the <a href="https://github.com/drewnoakes/metadata-extractor">metadata-extractor</a>
 * library which works without any external tools (no ffprobe / ffmpeg needed).
 *
 * Supported formats:
 *   IMAGE  → JPEG, PNG, WebP, GIF, BMP, TIFF  (dimensions + format)
 *   VIDEO  → MP4, MOV / QuickTime              (dimensions + duration + codec)
 *   AUDIO  → MP3, M4A (MP4-container audio)    (duration + bitrate + codec)
 *
 * Unsupported formats (MKV, AVI, WEBM, FLAC, OGG …) degrade gracefully —
 * only the MIME-derived format string is returned.
 */
@Slf4j
@Component
public class MediaMetadataExtractor {

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Extract technical metadata from raw file bytes.
     *
     * @param bytes           raw file content
     * @param contentType     MIME type, e.g. "image/jpeg"
     * @param originalFilename original file name with extension
     * @return populated {@link MediaFileMeta} — never null, individual fields may be null
     */
    public MediaFileMeta extract(byte[] bytes, String contentType, String originalFilename) {

        if (bytes == null || bytes.length == 0) {
            log.warn("Empty bytes — skipping metadata extraction");
            return MediaFileMeta.empty();
        }
        if (contentType == null || contentType.isBlank()) {
            log.warn("No content-type provided — skipping metadata extraction");
            return MediaFileMeta.empty();
        }

        String mime = contentType.toLowerCase(Locale.ROOT).trim();

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(
                    new BufferedInputStream(new ByteArrayInputStream(bytes)));

            String fileFormat = detectFileFormat(metadata, mime);

            if (mime.startsWith("image/")) {
                return extractImageMeta(metadata, bytes, fileFormat);
            } else if (mime.startsWith("video/")) {
                return extractVideoMeta(metadata, fileFormat);
            } else if (mime.startsWith("audio/")) {
                return extractAudioMeta(metadata, fileFormat, bytes.length);
            }

            return MediaFileMeta.builder().fileFormat(fileFormat).build();

        } catch (Exception e) {
            log.warn("Metadata extraction failed for '{}': {}", originalFilename, e.getMessage());
            return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
        }
    }

    // =========================================================================
    // IMAGE  (JPEG, PNG, WebP, GIF, BMP, TIFF)
    // =========================================================================

    private MediaFileMeta extractImageMeta(Metadata metadata, byte[] bytes, String format) {
        Integer width  = null;
        Integer height = null;

        // ── Try format-specific directories from metadata-extractor ──────────

        // JPEG
        if (width == null || height == null) {
            for (JpegDirectory dir : metadata.getDirectoriesOfType(JpegDirectory.class)) {
                width  = firstNonNull(width,  dir.getInteger(JpegDirectory.TAG_IMAGE_WIDTH));
                height = firstNonNull(height, dir.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT));
            }
        }

        // PNG
        if (width == null || height == null) {
            for (PngDirectory dir : metadata.getDirectoriesOfType(PngDirectory.class)) {
                width  = firstNonNull(width,  dir.getInteger(PngDirectory.TAG_IMAGE_WIDTH));
                height = firstNonNull(height, dir.getInteger(PngDirectory.TAG_IMAGE_HEIGHT));
            }
        }

        // WebP
        if (width == null || height == null) {
            for (WebpDirectory dir : metadata.getDirectoriesOfType(WebpDirectory.class)) {
                width  = firstNonNull(width,  dir.getInteger(WebpDirectory.TAG_IMAGE_WIDTH));
                height = firstNonNull(height, dir.getInteger(WebpDirectory.TAG_IMAGE_HEIGHT));
            }
        }

        // GIF
        if (width == null || height == null) {
            for (GifHeaderDirectory dir : metadata.getDirectoriesOfType(GifHeaderDirectory.class)) {
                width  = firstNonNull(width,  dir.getInteger(GifHeaderDirectory.TAG_IMAGE_WIDTH));
                height = firstNonNull(height, dir.getInteger(GifHeaderDirectory.TAG_IMAGE_HEIGHT));
            }
        }

        // BMP
        if (width == null || height == null) {
            for (BmpHeaderDirectory dir : metadata.getDirectoriesOfType(BmpHeaderDirectory.class)) {
                width  = firstNonNull(width,  dir.getInteger(BmpHeaderDirectory.TAG_IMAGE_WIDTH));
                height = firstNonNull(height, dir.getInteger(BmpHeaderDirectory.TAG_IMAGE_HEIGHT));
            }
        }

        // ── Fallback: ImageIO (handles standard formats reliably) ────────────
        if (width == null || height == null) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    width  = img.getWidth();
                    height = img.getHeight();
                }
            } catch (Exception e) {
                log.debug("ImageIO fallback failed: {}", e.getMessage());
            }
        }

        log.debug("Image metadata: {}×{}, format={}", width, height, format);

        return MediaFileMeta.builder()
                .widthPx(width)
                .heightPx(height)
                .fileFormat(format)
                .build();
    }

    // =========================================================================
    // VIDEO  (MP4, MOV / QuickTime)
    // =========================================================================

    private MediaFileMeta extractVideoMeta(Metadata metadata, String format) {
        Integer width    = null;
        Integer height   = null;
        Integer duration = null;
        String  codec    = null;
        Integer bitrate  = null;

        // ── Video track: dimensions + codec ──────────────────────────────────
        for (Mp4VideoDirectory dir : metadata.getDirectoriesOfType(Mp4VideoDirectory.class)) {
            width  = firstNonNull(width,  dir.getInteger(Mp4VideoDirectory.TAG_WIDTH));
            height = firstNonNull(height, dir.getInteger(Mp4VideoDirectory.TAG_HEIGHT));
            if (codec == null && dir.containsTag(Mp4VideoDirectory.TAG_COMPRESSION_TYPE)) {
                codec = normaliseCodec(dir.getString(Mp4VideoDirectory.TAG_COMPRESSION_TYPE));
            }
        }

        // ── Container: duration ──────────────────────────────────────────────
        for (Mp4Directory dir : metadata.getDirectoriesOfType(Mp4Directory.class)) {
            // Prefer the pre-calculated duration-in-seconds tag
            if (duration == null && dir.containsTag(Mp4Directory.TAG_DURATION_SECONDS)) {
                Long secs = dir.getLongObject(Mp4Directory.TAG_DURATION_SECONDS);
                if (secs != null) {
                    duration = secs.intValue();
                }
            }
            // Fallback: calculate from raw duration / timescale
            if (duration == null
                    && dir.containsTag(Mp4Directory.TAG_DURATION)
                    && dir.containsTag(Mp4Directory.TAG_TIME_SCALE)) {
                Long dur = dir.getLongObject(Mp4Directory.TAG_DURATION);
                Long ts  = dir.getLongObject(Mp4Directory.TAG_TIME_SCALE);
                if (dur != null && ts != null && ts > 0) {
                    duration = (int) (dur / ts);
                }
            }
        }

        log.debug("Video metadata: {}×{}, duration={}s, codec={}, format={}",
                width, height, duration, codec, format);

        return MediaFileMeta.builder()
                .widthPx(width)
                .heightPx(height)
                .durationSeconds(duration)
                .fileFormat(format)
                .codec(codec)
                .bitrateKbps(bitrate)
                .build();
    }

    // =========================================================================
    // AUDIO  (MP3, M4A / AAC-in-MP4, WAV)
    // =========================================================================

    private MediaFileMeta extractAudioMeta(Metadata metadata, String format, long fileSize) {
        Integer duration = null;
        String  codec    = null;
        Integer bitrate  = null;

        // ── MP3 ──────────────────────────────────────────────────────────────
        for (Mp3Directory dir : metadata.getDirectoriesOfType(Mp3Directory.class)) {
            if (dir.containsTag(Mp3Directory.TAG_BITRATE)) {
                bitrate = dir.getInteger(Mp3Directory.TAG_BITRATE);
                codec   = "MP3";
                // MP3 has no duration tag — calculate from fileSize and bitrate
                if (bitrate != null && bitrate > 0 && fileSize > 0) {
                    duration = (int) (fileSize * 8L / (bitrate * 1000L));
                }
            }
        }

        // ── M4A / AAC in MP4 container ───────────────────────────────────────
        if (codec == null) {
            for (Mp4SoundDirectory dir : metadata.getDirectoriesOfType(Mp4SoundDirectory.class)) {
                if (dir.containsTag(Mp4SoundDirectory.TAG_AUDIO_FORMAT)) {
                    codec = normaliseCodec(dir.getString(Mp4SoundDirectory.TAG_AUDIO_FORMAT));
                }
            }
        }

        // ── Duration from MP4 container (covers M4A / AAC) ──────────────────
        if (duration == null) {
            for (Mp4Directory dir : metadata.getDirectoriesOfType(Mp4Directory.class)) {
                if (dir.containsTag(Mp4Directory.TAG_DURATION_SECONDS)) {
                    Long secs = dir.getLongObject(Mp4Directory.TAG_DURATION_SECONDS);
                    if (secs != null) {
                        duration = secs.intValue();
                    }
                }
                if (duration == null
                        && dir.containsTag(Mp4Directory.TAG_DURATION)
                        && dir.containsTag(Mp4Directory.TAG_TIME_SCALE)) {
                    Long dur = dir.getLongObject(Mp4Directory.TAG_DURATION);
                    Long ts  = dir.getLongObject(Mp4Directory.TAG_TIME_SCALE);
                    if (dur != null && ts != null && ts > 0) {
                        duration = (int) (dur / ts);
                    }
                }
            }
        }

        log.debug("Audio metadata: duration={}s, codec={}, bitrate={}kbps, format={}",
                duration, codec, bitrate, format);

        return MediaFileMeta.builder()
                .durationSeconds(duration)
                .fileFormat(format)
                .codec(codec)
                .bitrateKbps(bitrate)
                .build();
    }

    // =========================================================================
    // FORMAT DETECTION
    // =========================================================================

    /**
     * Use metadata-extractor's {@link FileTypeDirectory} to detect the file type
     * from magic bytes. Falls back to MIME-based mapping if unavailable.
     */
    private String detectFileFormat(Metadata metadata, String mime) {
        Collection<FileTypeDirectory> dirs =
                metadata.getDirectoriesOfType(FileTypeDirectory.class);

        for (FileTypeDirectory dir : dirs) {
            if (dir.containsTag(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME)) {
                String detected = dir.getString(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME);
                if (detected != null && !detected.isBlank()) {
                    return normaliseDetectedFormat(detected);
                }
            }
        }
        return mimeToFormat(mime);
    }

    // =========================================================================
    // NORMALISATION HELPERS
    // =========================================================================

    /**
     * Normalise the format name returned by FileTypeDirectory.
     * metadata-extractor returns names like "JPEG", "PNG", "QuickTime", "MP4", etc.
     */
    private String normaliseDetectedFormat(String detected) {
        if (detected == null) return null;
        String upper = detected.toUpperCase(Locale.ROOT).trim();
        if (upper.contains("QUICKTIME")) return "MOV";
        if (upper.contains("MPEG-4"))    return "MP4";
        return upper;
    }

    /**
     * Normalise codec names to human-readable form.
     */
    private String normaliseCodec(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "h264", "avc1", "avc"            -> "H.264";
            case "hevc", "h265", "hvc1", "hev1"   -> "H.265";
            case "vp8"                             -> "VP8";
            case "vp9"                             -> "VP9";
            case "av1", "av01"                     -> "AV1";
            case "mpeg4", "mp4v"                   -> "MPEG-4";
            case "mpeg2video"                      -> "MPEG-2";
            case "prores"                          -> "ProRes";
            case "aac", "mp4a"                     -> "AAC";
            case "mp3", "mp3float", ".mp3"         -> "MP3";
            case "opus"                            -> "Opus";
            case "vorbis"                          -> "Vorbis";
            case "flac"                            -> "FLAC";
            case "pcm_s16le", "pcm_s24le", "sowt"  -> "PCM";
            case "alac"                            -> "ALAC";
            default                                -> raw.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Derive a format string from a MIME type. Used as fallback.
     */
    private String mimeToFormat(String mime) {
        if (mime == null) return null;
        String sub = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : mime;
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg"      -> "JPEG";
            case "png"              -> "PNG";
            case "gif"              -> "GIF";
            case "webp"             -> "WEBP";
            case "svg+xml"          -> "SVG";
            case "bmp"              -> "BMP";
            case "tiff"             -> "TIFF";
            case "mp4"              -> "MP4";
            case "quicktime"        -> "MOV";
            case "x-msvideo"        -> "AVI";
            case "x-matroska"       -> "MKV";
            case "webm"             -> "WEBM";
            case "mpeg"             -> "MPEG";
            case "ogg"              -> "OGG";
            case "mp3", "mpeg3"     -> "MP3";
            case "wav", "x-wav"     -> "WAV";
            case "aac"              -> "AAC";
            case "flac"             -> "FLAC";
            case "x-ms-wma"         -> "WMA";
            case "opus"             -> "OPUS";
            default                 -> sub.toUpperCase(Locale.ROOT);
        };
    }

    private <T> T firstNonNull(T current, T candidate) {
        return current != null ? current : candidate;
    }

    // =========================================================================
    // RESULT RECORD
    // =========================================================================

    /**
     * Immutable container for all extracted technical metadata.
     * All fields are nullable — only set when extraction succeeds for that field.
     */
    @Getter
    @Builder
    public static class MediaFileMeta {

        /** Width in pixels — IMAGE and VIDEO only. */
        private final Integer widthPx;

        /** Height in pixels — IMAGE and VIDEO only. */
        private final Integer heightPx;

        /** Playback duration in whole seconds — VIDEO and AUDIO only. */
        private final Integer durationSeconds;

        /**
         * Normalised file format string.
         * Examples: "JPEG", "PNG", "MP4", "MOV", "MP3", "WAV", "FLAC", "AAC"
         */
        private final String fileFormat;

        /**
         * Human-readable codec name.
         * Examples: "H.264", "H.265", "AAC", "MP3", "FLAC"
         * Null for IMAGE files.
         */
        private final String codec;

        /**
         * Average bitrate in kilobits per second.
         * Null for IMAGE files or when not detectable.
         */
        private final Integer bitrateKbps;

        /** Convenience factory — returns a fully-null instance. */
        public static MediaFileMeta empty() {
            return MediaFileMeta.builder().build();
        }

        /** Returns true when at least one field was successfully extracted. */
        public boolean hasData() {
            return widthPx != null || heightPx != null || durationSeconds != null
                    || fileFormat != null || codec != null || bitrateKbps != null;
        }
    }
}

