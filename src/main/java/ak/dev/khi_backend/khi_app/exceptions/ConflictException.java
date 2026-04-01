package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ConflictException extends AppException {
    public ConflictException(String messageKey, Object... args) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, messageKey, args);
    }

    public ConflictException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, messageKey, details);
    }
}
