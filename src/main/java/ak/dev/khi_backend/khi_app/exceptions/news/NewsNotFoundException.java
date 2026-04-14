package ak.dev.khi_backend.khi_app.exceptions.news;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NewsNotFoundException extends AppException {
    public NewsNotFoundException(Long id) {
        super(ErrorCode.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND,
                "news.not_found", null,
                Map.of("id", id != null ? String.valueOf(id) : "unknown"), null);
    }

    public NewsNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

