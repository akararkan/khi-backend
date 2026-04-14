package ak.dev.khi_backend.khi_app.exceptions.news;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NewsValidationException extends AppException {
    public NewsValidationException(String messageKey) {
        super(ErrorCode.NEWS_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, null, null);
    }

    public NewsValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.NEWS_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

