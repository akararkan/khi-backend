package ak.dev.khi_backend.khi_app.exceptions.news;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NewsConflictException extends AppException {
    public NewsConflictException(Map<String, Object> details) {
        super(ErrorCode.NEWS_CONFLICT, HttpStatus.CONFLICT, "news.conflict", null, details, null);
    }
}

