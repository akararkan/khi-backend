package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackNotFoundException extends AppException {
    public SoundTrackNotFoundException(Long id) {
        super(ErrorCode.SOUND_NOT_FOUND, HttpStatus.NOT_FOUND,
                "soundTrack.not_found", null,
                Map.of("id", id != null ? String.valueOf(id) : "unknown"), null);
    }

    public SoundTrackNotFoundException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.SOUND_NOT_FOUND, HttpStatus.NOT_FOUND, messageKey, null, details, null);
    }
}

