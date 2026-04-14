package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 400 — Validation failure for image collection requests. */
public class ImageCollectionValidationException extends AppException {

    public ImageCollectionValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.IMAGE_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

