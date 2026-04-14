package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 500 — Unexpected internal error within image collection operations. */
public class ImageCollectionInternalException extends AppException {

    public ImageCollectionInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, cause);
    }

    public static ImageCollectionInternalException createFailed(String traceId, Throwable cause) {
        return new ImageCollectionInternalException("image.create_failed", Map.of("traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static ImageCollectionInternalException updateFailed(Long id, String traceId, Throwable cause) {
        return new ImageCollectionInternalException("image.update_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static ImageCollectionInternalException deleteFailed(Long id, String traceId, Throwable cause) {
        return new ImageCollectionInternalException("image.delete_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }
}

