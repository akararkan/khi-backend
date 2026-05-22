package ak.dev.khi_backend.khi_app.service.media;

import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TiptapHtmlProcessor — scans Tiptap HTML for inline base64 data URIs in
 * {@code <img>}, {@code <video>}, {@code <audio>}, and {@code <source>}
 * tags, uploads each one to S3, and rewrites the {@code src} attribute to
 * point at the resulting public URL.
 *
 * Idempotent: HTML that already contains S3 URLs is returned unchanged.
 * Safe on null / blank input. Failures on a single asset are logged and
 * the original src is left in place so the save still succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiptapHtmlProcessor {

    private final S3Service s3Service;

    /**
     * Matches: src="data:<mime>;base64,<payload>" or src='...'.
     * Group 1: quote char.  Group 2: mime type (e.g. image/jpeg).
     * Group 3: base64 payload (may contain whitespace which we strip).
     */
    private static final Pattern DATA_URI_SRC = Pattern.compile(
            "src=([\"'])data:([a-zA-Z0-9.+/-]+);base64,([A-Za-z0-9+/=\\s]+?)\\1",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Process a Tiptap HTML blob. Returns the same string with every inline
     * base64 data URI replaced by an S3 public URL.
     */
    public String process(String html) {
        if (html == null || html.isBlank()) return html;
        if (!html.contains("data:")) return html;

        Matcher m = DATA_URI_SRC.matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        int last = 0;
        int uploaded = 0;
        int failed = 0;

        while (m.find()) {
            out.append(html, last, m.start());

            String quote   = m.group(1);
            String mime    = m.group(2).toLowerCase();
            String payload = m.group(3).replaceAll("\\s+", "");

            String replacement;
            try {
                byte[] bytes = Base64.getDecoder().decode(payload);
                String filename  = "tiptap-" + System.nanoTime() + "." + extensionFor(mime);
                ProjectMediaType type = mediaTypeFor(mime);
                String url = s3Service.upload(bytes, filename, mime, type);
                replacement = "src=" + quote + url + quote;
                uploaded++;
            } catch (IllegalArgumentException e) {
                log.warn("Tiptap: skipping malformed base64 payload (mime={})", mime);
                replacement = m.group(0);
                failed++;
            } catch (Exception e) {
                log.error("Tiptap: failed to upload inline asset (mime={})", mime, e);
                replacement = m.group(0);
                failed++;
            }

            out.append(replacement);
            last = m.end();
        }

        if (last == 0) return html;
        out.append(html, last, html.length());

        if (uploaded > 0 || failed > 0) {
            log.info("Tiptap HTML processed: uploaded={}, failed={}", uploaded, failed);
        }
        return out.toString();
    }

    private static ProjectMediaType mediaTypeFor(String mime) {
        if (mime == null) return ProjectMediaType.IMAGE;
        if (mime.startsWith("image/")) return ProjectMediaType.IMAGE;
        if (mime.startsWith("video/")) return ProjectMediaType.VIDEO;
        if (mime.startsWith("audio/")) return ProjectMediaType.AUDIO;
        return ProjectMediaType.DOCUMENT;
    }

    private static String extensionFor(String mime) {
        if (mime == null) return "bin";
        return switch (mime) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png"               -> "png";
            case "image/gif"               -> "gif";
            case "image/webp"              -> "webp";
            case "image/svg+xml"           -> "svg";
            case "image/bmp"               -> "bmp";
            case "image/avif"              -> "avif";
            case "video/mp4"               -> "mp4";
            case "video/webm"              -> "webm";
            case "video/quicktime"         -> "mov";
            case "audio/mpeg"              -> "mp3";
            case "audio/mp3"               -> "mp3";
            case "audio/wav"               -> "wav";
            case "audio/x-wav"             -> "wav";
            case "audio/ogg"               -> "ogg";
            case "audio/webm"              -> "weba";
            default -> {
                int slash = mime.indexOf('/');
                yield slash >= 0 && slash + 1 < mime.length()
                        ? mime.substring(slash + 1)
                        : "bin";
            }
        };
    }
}
