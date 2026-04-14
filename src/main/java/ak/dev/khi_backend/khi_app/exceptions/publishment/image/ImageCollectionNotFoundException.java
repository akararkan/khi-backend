package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 404 — The requested image collection does not exist. */
public class ImageCollectionNotFoundException extends AppException {

    public ImageCollectionNotFoundException(Long id) {
        super(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "imageCollection.not_found", null,
                Map.of("id", id != null ? String.valueOf(id) : "unknown"), null);
    }

    public ImageCollectionNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

