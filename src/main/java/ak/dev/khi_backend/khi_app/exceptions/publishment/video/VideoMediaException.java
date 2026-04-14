package ak.dev.khi_backend.khi_app.exceptions.publishment.video;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 400 — Video media invalid. */
public class VideoMediaException extends AppException {

    public VideoMediaException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.VIDEO_MEDIA_INVALID, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

