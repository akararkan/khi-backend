package ak.dev.khi_backend.khi_app.exceptions.publishment.sound;

import ak.dev.khi_backend.khi_app.exceptions.AppException;
import ak.dev.khi_backend.khi_app.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SoundTrackConflictException extends AppException {
    public SoundTrackConflictException(Map<String, Object> details) {
        super(ErrorCode.SOUND_CONFLICT, HttpStatus.CONFLICT, "soundTrack.conflict", null, details, null);
    }
}

