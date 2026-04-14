package ak.dev.khi_backend.khi_app.exceptions.publishment.video;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 400 — Video validation failed. */
public class VideoValidationException extends AppException {

    public VideoValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.VIDEO_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

