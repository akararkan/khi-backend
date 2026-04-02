package ak.dev.khi_backend.khi_app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * MediaMetadataExtractor — Automatically inspects uploaded file bytes and
 * extracts all technical metadata the system needs to persist on
 * {@link ak.dev.khi_backend.khi_app.model.service.ServiceMediaFile}.
 *
 * ─── What It Extracts ─────────────────────────────────────────────────────────
 *  IMAGE  → widthPx, heightPx, fileFormat ("JPEG" / "PNG" / "WEBP" / "GIF" …)
 *  VIDEO  → widthPx, heightPx, durationSeconds, fileFormat ("MP4" / "MOV" …),
 *            codec ("H.264" / "H.265" / "VP9" …), bitrateKbps
 *  AUDIO  → durationSeconds, fileFormat ("MP3" / "WAV" / "AAC" / "FLAC" …),
 *            codec, bitrateKbps
 *
 * ─── Implementation ───────────────────────────────────────────────────────────
 *  • Images  → {@code javax.imageio.ImageIO} (zero extra dependencies).
 *  • Video / Audio → {@code ffprobe} via {@link ProcessBuilder}.
 *    ffprobe outputs JSON which is parsed with Jackson (already on classpath).
 *    If ffprobe is not installed the extractor logs a warning and falls back
 *    gracefully — only the file format derived from the MIME type is set.
 *
 * ─── Caller Contract ──────────────────────────────────────────────────────────
 *  Call {@link #extract(byte[], String, String)} once per upload.
 *  The returned {@link MediaFileMeta} record is immutable and never null.
 *  All individual fields inside it are nullable — callers must null-check before
 *  persisting to the DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaMetadataExtractor {

    private static final int FFPROBE_TIMEOUT_SECONDS = 15;

    private final ObjectMapper objectMapper;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Entry point — dispatches to the correct extractor based on MIME type.
     *
     * @param bytes           raw file bytes (already read from the multipart upload)
     * @param contentType     MIME type reported by the browser, e.g. "image/jpeg"
     * @param originalFilename original filename including extension, e.g. "photo.jpg"
     * @return populated {@link MediaFileMeta} — never null, individual fields may be null
     */
    public MediaFileMeta extract(byte[] bytes, String contentType, String originalFilename) {

        if (contentType == null || contentType.isBlank()) {
            log.warn("No content-type provided — skipping metadata extraction");
            return MediaFileMeta.empty();
        }

        String mime = contentType.toLowerCase(Locale.ROOT).trim();

        if (mime.startsWith("image/")) {
            return extractImage(bytes, mime);
        } else if (mime.startsWith("video/")) {
            return extractAv(bytes, originalFilename, mime, true);
        } else if (mime.startsWith("audio/")) {
            return extractAv(bytes, originalFilename, mime, false);
        }

        log.debug("Unrecognised MIME type '{}' — returning empty metadata", contentType);
        return MediaFileMeta.empty();
    }

    // =========================================================================
    // IMAGE  —  javax.imageio (no extra deps)
    // =========================================================================

    private MediaFileMeta extractImage(byte[] bytes, String mime) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            if (iis == null) {
                log.warn("ImageIO could not create stream for mime={}", mime);
                return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
            }

            // ── Detect format via registered ImageReader ──────────────────────
            String detectedFormat = null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                detectedFormat = readers.next().getFormatName().toUpperCase(Locale.ROOT);
                // Normalise: "JPEG" and "jpg" both → "JPEG"
                if ("JPG".equals(detectedFormat)) detectedFormat = "JPEG";
            }

            // ── Decode to get dimensions ───────────────────────────────────────
            bais.reset();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            Integer width  = img != null ? img.getWidth()  : null;
            Integer height = img != null ? img.getHeight() : null;

            return MediaFileMeta.builder()
                    .widthPx(width)
                    .heightPx(height)
                    .fileFormat(detectedFormat != null ? detectedFormat : mimeToFormat(mime))
                    .build();

        } catch (IOException e) {
            log.warn("Image metadata extraction failed: {}", e.getMessage());
            return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
        }
    }

    // =========================================================================
    // VIDEO / AUDIO  —  ffprobe JSON
    // =========================================================================

    private MediaFileMeta extractAv(byte[] bytes, String filename,
                                    String mime, boolean isVideo) {

        Path tempFile = null;
        try {
            // ffprobe needs a real file on disk, not a stream
            String ext = extensionFrom(filename);
            tempFile = Files.createTempFile("khi_media_", ext);
            Files.write(tempFile, bytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    "-show_format",
                    tempFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String jsonOutput = new String(process.getInputStream().readAllBytes());
            boolean finished  = process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("ffprobe timed out for file: {}", filename);
                return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
            }

            return parseFfprobeJson(jsonOutput, mime, isVideo);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ffprobe interrupted: {}", e.getMessage());
            return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
        } catch (IOException e) {
            // ffprobe not installed or not on PATH — degrade gracefully
            log.warn("ffprobe not available — technical metadata will be limited. " +
                    "Install ffmpeg to enable full extraction. Error: {}", e.getMessage());
            return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (IOException ignored) { /* best-effort cleanup */ }
            }
        }
    }

    /**
     * Parse the JSON produced by:
     * {@code ffprobe -v quiet -print_format json -show_streams -show_format <file>}
     */
    private MediaFileMeta parseFfprobeJson(String json, String mime, boolean isVideo) {

        try {
            JsonNode root    = objectMapper.readTree(json);
            JsonNode streams = root.path("streams");
            JsonNode format  = root.path("format");

            Integer width     = null;
            Integer height    = null;
            String  codec     = null;
            Integer bitrate   = null;

            // ── Iterate streams: pick the best video or audio stream ──────────
            for (JsonNode stream : streams) {

                String codecType = stream.path("codec_type").asText("");

                if (isVideo && "video".equalsIgnoreCase(codecType)) {
                    width  = nodeIntOrNull(stream, "width");
                    height = nodeIntOrNull(stream, "height");
                    codec  = normaliseCodec(stream.path("codec_name").asText(null));
                    if (bitrate == null) {
                        bitrate = bitsBpsToKbps(stream.path("bit_rate").asText(null));
                    }
                    break; // take the first video stream

                } else if (!isVideo && "audio".equalsIgnoreCase(codecType)) {
                    codec   = normaliseCodec(stream.path("codec_name").asText(null));
                    bitrate = bitsBpsToKbps(stream.path("bit_rate").asText(null));
                    break; // take the first audio stream
                }
            }

            // ── Duration from format section (most reliable source) ───────────
            Integer duration = null;
            String rawDuration = format.path("duration").asText(null);
            if (rawDuration != null && !rawDuration.isBlank()) {
                try {
                    duration = (int) Math.round(Double.parseDouble(rawDuration));
                } catch (NumberFormatException ignored) { /* leave null */ }
            }

            // ── Bitrate from format section if stream didn't have it ──────────
            if (bitrate == null) {
                bitrate = bitsBpsToKbps(format.path("bit_rate").asText(null));
            }

            // ── File format from format_name ──────────────────────────────────
            String ffFormat  = format.path("format_name").asText(null);
            String fileFormat = ffFormat != null
                    ? normaliseFormatName(ffFormat)
                    : mimeToFormat(mime);

            return MediaFileMeta.builder()
                    .widthPx(width)
                    .heightPx(height)
                    .durationSeconds(duration)
                    .fileFormat(fileFormat)
                    .codec(codec)
                    .bitrateKbps(bitrate)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse ffprobe JSON output: {}", e.getMessage());
            return MediaFileMeta.builder().fileFormat(mimeToFormat(mime)).build();
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Convert a raw bits-per-second string (e.g. "128000") to kbps integer. */
    private Integer bitsBpsToKbps(String raw) {
        if (raw == null || raw.isBlank() || "N/A".equalsIgnoreCase(raw)) return null;
        try {
            return (int) (Long.parseLong(raw.trim()) / 1000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Null-safe integer field from a JsonNode. */
    private Integer nodeIntOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asInt();
    }

    /**
     * Derive a clean format string from a MIME type.
     * Used as fallback when ffprobe / ImageIO cannot detect the format.
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
            case "mp3",
                 "mpeg3"            -> "MP3";
            case "wav",
                 "x-wav"            -> "WAV";
            case "aac"              -> "AAC";
            case "flac"             -> "FLAC";
            case "x-ms-wma"         -> "WMA";
            case "opus"             -> "OPUS";
            default                 -> sub.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * ffprobe may return compound format names like "mov,mp4,m4a,3gp,3g2,mj2".
     * This picks the most recognisable token.
     */
    private String normaliseFormatName(String ffFormat) {
        if (ffFormat == null) return null;
        String[] parts = ffFormat.split(",");
        // Prefer well-known names
        for (String p : parts) {
            String t = p.trim().toLowerCase(Locale.ROOT);
            if (t.equals("mp4") || t.equals("mov") || t.equals("mkv")
                    || t.equals("webm") || t.equals("avi") || t.equals("mp3")
                    || t.equals("wav") || t.equals("flac") || t.equals("ogg")
                    || t.equals("aac")) {
                return t.toUpperCase(Locale.ROOT);
            }
        }
        return parts[0].trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalise codec names from ffprobe to human-readable form.
     * Examples: "h264" → "H.264", "hevc" → "H.265", "aac" → "AAC"
     */
    private String normaliseCodec(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "h264", "avc1"   -> "H.264";
            case "hevc", "h265"   -> "H.265";
            case "vp8"            -> "VP8";
            case "vp9"            -> "VP9";
            case "av1"            -> "AV1";
            case "mpeg4"          -> "MPEG-4";
            case "mpeg2video"     -> "MPEG-2";
            case "prores"         -> "ProRes";
            case "aac"            -> "AAC";
            case "mp3"            -> "MP3";
            case "opus"           -> "Opus";
            case "vorbis"         -> "Vorbis";
            case "flac"           -> "FLAC";
            case "pcm_s16le",
                 "pcm_s24le"      -> "PCM";
            default               -> raw.toUpperCase(Locale.ROOT);
        };
    }

    /** Extract a dot-prefixed file extension from a filename, e.g. ".mp4". */
    private String extensionFrom(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        return ".tmp";
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
         * Null for IMAGE files or when ffprobe is unavailable.
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