package ak.dev.khi_backend.khi_app.exceptions.publishment.writing;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 500 — Unexpected internal errors for writing domain. */
public class WritingInternalException extends AppException {

    public WritingInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, cause);
    }

    public static WritingInternalException createFailed(String traceId, Throwable cause) {
        return new WritingInternalException("writing.create_failed", Map.of("traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static WritingInternalException updateFailed(Long id, String traceId, Throwable cause) {
        return new WritingInternalException("writing.update_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static WritingInternalException deleteFailed(Long id, String traceId, Throwable cause) {
        return new WritingInternalException("writing.delete_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }
}

