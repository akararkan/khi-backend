package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 409 — Duplicate project data violates a uniqueness constraint.
 *
 * Thrown when:
 *   • DataIntegrityViolationException during create/update
 *   • A project with the same unique field(s) already exists
 */
public class ProjectConflictException extends AppException {

    public ProjectConflictException(Map<String, Object> details) {
        super(ErrorCode.PROJECT_CONFLICT, HttpStatus.CONFLICT,
                "project.conflict", null, details, null);
    }

    public ProjectConflictException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.PROJECT_CONFLICT, HttpStatus.CONFLICT, messageKey, null, details, null);
    }
}

