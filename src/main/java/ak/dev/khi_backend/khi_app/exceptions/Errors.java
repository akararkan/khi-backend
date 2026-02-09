package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;

public final class Errors {
    private Errors() {}

    public static AppException notFound(String key, Object... args) {
        return new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, key, args);
    }

    public static AppException badRequest(String key, Object... args) {
        return new AppException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, key, args);
    }

    public static AppException conflict(String key, Object... args) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, key, args);
    }

    public static AppException forbidden(String key, Object... args) {
        return new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, key, args);
    }

    public static AppException storage(String key, Object... args) {
        return new AppException(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, key, args);
    }

    public static AppException internal(String key, Object... args) {
        return new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, key, args);
    }
}
