package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 400 — Invalid or incomplete project media data.
 *
 * Thrown when:
 *   • The media type string cannot be parsed to ProjectMediaType enum
 *   • AUDIO / VIDEO media is missing all of: url, externalUrl, embedUrl
 *   • IMAGE / PDF / DOCUMENT / TEXT media is missing both url and textBody
 *   • Any other media-specific validation failure
 */
public class ProjectMediaException extends AppException {

    public ProjectMediaException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.PROJECT_MEDIA_INVALID, HttpStatus.BAD_REQUEST,
                messageKey, null, details, null);
    }

    public ProjectMediaException(String messageKey, Object[] args, Map<String, Object> details) {
        super(ErrorCode.PROJECT_MEDIA_INVALID, HttpStatus.BAD_REQUEST,
                messageKey, args, details, null);
    }

    // ── Convenience factory methods ──────────────────────────────

    public static ProjectMediaException invalidType(String providedType) {
        return new ProjectMediaException("media.type_invalid",
                new Object[]{providedType},
                Map.of("providedType", providedType != null ? providedType : "null",
                        "allowedTypes", "IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, TEXT",
                        "hint", "Media type must be one of: IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, TEXT."));
    }

    public static ProjectMediaException audioVideoRequiresLink(String mediaType) {
        return new ProjectMediaException("media.audio_video_requires_url_or_link",
                Map.of("mediaType", mediaType,
                        "hint", "AUDIO or VIDEO media requires at least one of: url, externalUrl, or embedUrl."));
    }

    public static ProjectMediaException urlOrTextRequired(String mediaType) {
        return new ProjectMediaException("media.url_or_text_required",
                Map.of("mediaType", mediaType,
                        "hint", "This media type requires either a url or textBody to be provided."));
    }
}

