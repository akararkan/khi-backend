package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackValidationException extends AppException {
    public SoundTrackValidationException(String messageKey, Map<String, Object> details) {
        super(ErrorCode.SOUND_VALIDATION, HttpStatus.BAD_REQUEST, messageKey, null, details, null);
    }
}

