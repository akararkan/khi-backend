package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 404 — The requested project does not exist.
 *
 * Thrown when:
 *   • findById / findOrThrow returns empty
 *   • update or delete targets a non-existent project ID
 */
public class ProjectNotFoundException extends AppException {

    public ProjectNotFoundException(Long projectId) {
        super(ErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "project.not_found", null,
                Map.of("projectId", projectId != null ? String.valueOf(projectId) : "unknown"),
                null);
    }

    public ProjectNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

