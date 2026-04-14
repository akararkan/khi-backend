package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 502 — S3 / file-storage upload failure during project creation or update.
 *
 * Thrown when:
 *   • Cover image upload to S3 fails (IOException, timeout, etc.)
 *   • Media file upload to S3 fails
 *   • Any I/O error reading the multipart file bytes
 */
public class ProjectStorageException extends AppException {

    public ProjectStorageException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY,
                messageKey, null, details, cause);
    }

    public ProjectStorageException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY,
                messageKey, null, details, null);
    }

    // ── Convenience factory methods ──────────────────────────────

    public static ProjectStorageException coverUploadFailed(Throwable cause) {
        return new ProjectStorageException("project.cover_upload_failed",
                Map.of("hint", "Cover image upload to storage failed. Please try again.",
                        "fileType", "cover"),
                cause);
    }

    public static ProjectStorageException mediaUploadFailed(String fileName, Throwable cause) {
        return new ProjectStorageException("project.media_upload_failed",
                Map.of("hint", "Media file upload to storage failed. Please try again.",
                        "fileName", fileName != null ? fileName : "unknown",
                        "fileType", "media"),
                cause);
    }
}

