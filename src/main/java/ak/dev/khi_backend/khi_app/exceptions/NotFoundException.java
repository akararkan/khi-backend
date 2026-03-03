package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class NotFoundException extends AppException {

    public NotFoundException(String messageKey, Map<String, Object> details) {
        super(
                ErrorCode.NOT_FOUND,        // ← must exist in your enum
                HttpStatus.NOT_FOUND,
                messageKey,
                details
        );
    }
}
