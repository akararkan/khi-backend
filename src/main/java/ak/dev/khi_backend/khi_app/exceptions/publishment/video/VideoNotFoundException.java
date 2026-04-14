package ak.dev.khi_backend.khi_app.exceptions.publishment.video;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 404 — The requested video does not exist. */
public class VideoNotFoundException extends AppException {

    public VideoNotFoundException(Long id) {
        super(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND,
                "video.not_found", null,
                Map.of("id", id != null ? String.valueOf(id) : "unknown"), null);
    }

    public VideoNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

