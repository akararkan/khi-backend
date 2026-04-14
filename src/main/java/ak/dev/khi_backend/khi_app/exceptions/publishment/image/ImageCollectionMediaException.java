package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 400 — Media-specific validation errors for image collections. */
public class ImageCollectionMediaException extends AppException {
    public ImageCollectionMediaException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.IMAGE_MEDIA_INVALID, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

