package ak.dev.khi_backend.khi_app.exceptions.publishment.writing;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 404 — The requested writing does not exist. */
public class WritingNotFoundException extends AppException {

    public WritingNotFoundException(Long id) {
        super(ErrorCode.WRITING_NOT_FOUND, HttpStatus.NOT_FOUND,
                "writing.not_found", null,
                Map.of("id", id != null ? String.valueOf(id) : "unknown"), null);
    }

    public WritingNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.WRITING_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

