package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String messageKey, Object... args) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, args);
    }

    public ResourceNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, details);
    }
}
