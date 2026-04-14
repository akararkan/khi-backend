package ak.dev.khi_backend.khi_app.exceptions.project;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 400 — Search request parameter is blank or missing.
 *
 * Thrown when:
 *   • Tag search parameter is blank
 *   • Keyword search parameter is blank
 *   • Global search query "q" is blank
 */
public class ProjectSearchValidationException extends AppException {

    public ProjectSearchValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.PROJECT_VALIDATION, HttpStatus.BAD_REQUEST,
                messageKey, null, details, null);
    }

    // ── Convenience factory methods ──────────────────────────────

    public static ProjectSearchValidationException tagRequired() {
        return new ProjectSearchValidationException("project.search.tag_required",
                Map.of("hint", "The 'tag' query parameter must not be blank.",
                        "parameter", "tag"));
    }

    public static ProjectSearchValidationException keywordRequired() {
        return new ProjectSearchValidationException("project.search.keyword_required",
                Map.of("hint", "The 'keyword' query parameter must not be blank.",
                        "parameter", "keyword"));
    }

    public static ProjectSearchValidationException queryRequired() {
        return new ProjectSearchValidationException("project.search.query_required",
                Map.of("hint", "The 'q' search query must not be blank.",
                        "parameter", "q"));
    }
}

