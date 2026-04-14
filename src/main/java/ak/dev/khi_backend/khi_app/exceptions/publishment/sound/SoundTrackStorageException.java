package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackStorageException extends AppException {

    public SoundTrackStorageException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, messageKey, null, details, cause);
    }

    public static SoundTrackStorageException mediaUploadFailed(String fileName, Throwable cause) {
        return new SoundTrackStorageException("sound.media_upload_failed",
                Map.of("file", fileName != null ? fileName : "unknown"), cause);
    }
}

