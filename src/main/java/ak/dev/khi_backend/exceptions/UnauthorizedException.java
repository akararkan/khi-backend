package ak.dev.khi_backend.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(String messageKey, Object... args) {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, messageKey, args);
    }

    public UnauthorizedException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, messageKey, details);
    }
}
