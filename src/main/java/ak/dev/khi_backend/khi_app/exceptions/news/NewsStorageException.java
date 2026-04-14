package ak.dev.khi_backend.khi_app.exceptions.news;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NewsStorageException extends AppException {
    public NewsStorageException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, messageKey, null, details, cause);
    }

    public NewsStorageException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, messageKey, null, details, null);
    }
}

