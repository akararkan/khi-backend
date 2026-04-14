package ak.dev.khi_backend.khi_app.exceptions.publishment.writing;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class WritingStorageException extends AppException {

    public WritingStorageException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, messageKey, null, details, cause);
    }

    public static WritingStorageException mediaUploadFailed(String fileName, Throwable cause) {
        return new WritingStorageException("writing.media_upload_failed",
                Map.of("file", fileName != null ? fileName : "unknown"), cause);
    }
}

