package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 400 — A required or invalid field in the project create/update request.
 *
 * Thrown when:
 *   • The request body is null
 *   • contentLanguages is missing or empty
 *   • CKB/KMR title is missing when language is selected
 *   • CKB/KMR project type is missing when language is selected
 *   • Cover URL is missing (for JSON-only create)
 *   • Any other project field-level validation failure
 */
public class ProjectValidationException extends AppException {

    public ProjectValidationException(String messageKey) {
        super(ErrorCode.PROJECT_VALIDATION, HttpStatus.BAD_REQUEST,
                messageKey, null, null, null);
    }

    public ProjectValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.PROJECT_VALIDATION, HttpStatus.BAD_REQUEST,
                messageKey, null, details, null);
    }

    public ProjectValidationException(String messageKey, Object[] args, Map<String, Object> details) {
        super(ErrorCode.PROJECT_VALIDATION, HttpStatus.BAD_REQUEST,
                messageKey, args, details, null);
    }

    // ── Convenience factory methods ──────────────────────────────

    public static ProjectValidationException requestRequired() {
        return new ProjectValidationException("project.request_required",
                Map.of("hint", "The request body must not be null."));
    }

    public static ProjectValidationException languagesRequired() {
        return new ProjectValidationException("project.languages_required",
                Map.of("hint", "Provide at least one content language: CKB, KMR, or both."));
    }

    public static ProjectValidationException coverRequired() {
        return new ProjectValidationException("project.cover_required",
                Map.of("hint", "Cover image URL is required. Upload a cover file or provide coverUrl in the JSON body."));
    }

    public static ProjectValidationException ckbTypeRequired() {
        return new ProjectValidationException("project.ckb_type_required",
                Map.of("hint", "projectTypeCkb is required when contentLanguages includes CKB."));
    }

    public static ProjectValidationException kmrTypeRequired() {
        return new ProjectValidationException("project.kmr_type_required",
                Map.of("hint", "projectTypeKmr is required when contentLanguages includes KMR."));
    }

    public static ProjectValidationException ckbTitleRequired() {
        return new ProjectValidationException("project.ckb_title_required",
                Map.of("hint", "ckbContent.title is required when contentLanguages includes CKB."));
    }

    public static ProjectValidationException kmrTitleRequired() {
        return new ProjectValidationException("project.kmr_title_required",
                Map.of("hint", "kmrContent.title is required when contentLanguages includes KMR."));
    }
}

