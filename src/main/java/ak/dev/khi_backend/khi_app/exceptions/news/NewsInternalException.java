package ak.dev.khi_backend.khi_app.exceptions.news;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NewsInternalException extends AppException {
    public NewsInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, cause);
    }

    public NewsInternalException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, null);
    }
}

