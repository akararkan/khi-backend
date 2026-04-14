package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 409 — Conflict in image collection domain (duplicate / unique constraint). */
public class ImageCollectionConflictException extends AppException {
    public ImageCollectionConflictException(Map<String, Object> details) {
        super(ErrorCode.IMAGE_CONFLICT, HttpStatus.CONFLICT, "imageCollection.conflict", null, details, null);
    }
}

