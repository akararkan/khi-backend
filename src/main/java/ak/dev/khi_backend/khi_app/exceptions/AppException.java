package ak.dev.khi_backend.khi_app.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus httpStatus;

    /**
     * messageKey is used for i18n (messages_*.properties)
     * If null, handler will fallback to a generic message by code.
     */
    private final String messageKey;
    private final Object[] messageArgs;

    /** Safe extra data to include in response (optional). */
    private final Map<String, Object> details;

    public AppException(
            ErrorCode code,
            HttpStatus httpStatus,
            String messageKey,
            Object[] messageArgs,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(messageKey, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
        this.details = details;
    }

    public AppException(ErrorCode code, HttpStatus status, String messageKey) {
        this(code, status, messageKey, null, null, null);
    }

    public AppException(ErrorCode code, HttpStatus status, String messageKey, Object... args) {
        this(code, status, messageKey, args, null, null);
    }

    public AppException(ErrorCode code, HttpStatus status, String messageKey, Map<String, Object> details) {
        this(code, status, messageKey, null, details, null);
    }
}
