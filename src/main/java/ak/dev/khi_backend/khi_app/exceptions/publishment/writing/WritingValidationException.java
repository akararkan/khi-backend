package ak.dev.khi_backend.khi_app.exceptions.publishment.writing;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 400 — Writing validation failed. */
public class WritingValidationException extends AppException {

    public WritingValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.WRITING_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

