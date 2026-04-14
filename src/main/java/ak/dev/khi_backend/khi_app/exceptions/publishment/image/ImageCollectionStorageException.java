package ak.dev.khi_backend.khi_app.exceptions.publishment.image;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 502 — Storage / S3 related errors for image uploads. */
public class ImageCollectionStorageException extends AppException {

    public ImageCollectionStorageException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, messageKey, null, details, cause);
    }

    public static ImageCollectionStorageException coverUploadFailed(Throwable cause) {
        return new ImageCollectionStorageException("image.cover_upload_failed", Map.of(), cause);
    }

    public static ImageCollectionStorageException mediaUploadFailed(String fileName, Throwable cause) {
        return new ImageCollectionStorageException("image.media_upload_failed",
                Map.of("file", fileName != null ? fileName : "unknown"), cause);
    }
}

