package ak.dev.khi_backend.khi_app.exceptions.publishment.video;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 500 — Unexpected internal errors for video domain. */
public class VideoInternalException extends AppException {

    public VideoInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, cause);
    }

    public static VideoInternalException createFailed(String traceId, Throwable cause) {
        return new VideoInternalException("video.create_failed", Map.of("traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static VideoInternalException updateFailed(Long id, String traceId, Throwable cause) {
        return new VideoInternalException("video.update_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static VideoInternalException deleteFailed(Long id, String traceId, Throwable cause) {
        return new VideoInternalException("video.delete_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }
}

