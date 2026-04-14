package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackMediaException extends AppException {
    public SoundTrackMediaException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.SOUND_MEDIA_INVALID, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

