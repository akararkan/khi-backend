package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackInternalException extends AppException {

    public SoundTrackInternalException(String messageKey, Map<String, Object> details, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, null, details, cause);
    }

    public static SoundTrackInternalException createFailed(String traceId, Throwable cause) {
        return new SoundTrackInternalException("sound.create_failed", Map.of("traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static SoundTrackInternalException updateFailed(Long id, String traceId, Throwable cause) {
        return new SoundTrackInternalException("sound.update_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }

    public static SoundTrackInternalException deleteFailed(Long id, String traceId, Throwable cause) {
        return new SoundTrackInternalException("sound.delete_failed", Map.of("id", id != null ? String.valueOf(id) : "unknown", "traceId", traceId != null ? traceId : "N/A"), cause);
    }
}

