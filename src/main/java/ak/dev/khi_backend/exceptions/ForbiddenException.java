package ak.dev.khi_backend.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ForbiddenException extends AppException {
    public ForbiddenException(String messageKey, Object... args) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, messageKey, args);
    }

    public ForbiddenException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, messageKey, details);
    }
}
