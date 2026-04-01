package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BadRequestException extends AppException {
    public BadRequestException(String messageKey, Object... args) {
        super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, messageKey, args);
    }

    public BadRequestException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, messageKey, details);
    }
}
