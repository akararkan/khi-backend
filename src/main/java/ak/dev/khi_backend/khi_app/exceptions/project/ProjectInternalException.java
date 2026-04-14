package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 500 — Unexpected internal error during a project operation.
 *
 * Thrown when:
 *   • An unchecked exception occurs during create / update / delete
 *     that is not already covered by a more specific exception
 *   • Database operations fail for unexpected reasons
 */
public class ProjectInternalException extends AppException {

    public ProjectInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                messageKey, null, details, cause);
    }

    public ProjectInternalException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                messageKey, null, details, null);
    }

    // ── Convenience factory methods ──────────────────────────────

    public static ProjectInternalException createFailed(String traceId, Throwable cause) {
        return new ProjectInternalException("project.create_failed",
                Map.of("operation", "create", "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static ProjectInternalException updateFailed(Long projectId, String traceId, Throwable cause) {
        return new ProjectInternalException("project.update_failed",
                Map.of("operation", "update",
                        "projectId", projectId != null ? projectId : "unknown",
                        "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static ProjectInternalException deleteFailed(Long projectId, String traceId, Throwable cause) {
        return new ProjectInternalException("project.delete_failed",
                Map.of("operation", "delete",
                        "projectId", projectId != null ? projectId : "unknown",
                        "traceId", traceId != null ? traceId : "N/A"), cause);
    }
}

