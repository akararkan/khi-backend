package ak.dev.khi_backend.khi_app.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class Errors {
    private Errors() {}

    // ── NOT FOUND ────────────────────────────────────────────────

    public static AppException notFound(String key, Object... args) {
        return new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, key, args);
    }

    public static AppException notFound(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, key, details);
    }

    // ── BAD REQUEST ──────────────────────────────────────────────

    public static AppException badRequest(String key, Object... args) {
        return new AppException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, key, args);
    }

    public static AppException badRequest(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, key, details);
    }

    // ── CONFLICT ─────────────────────────────────────────────────

    public static AppException conflict(String key, Object... args) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, key, args);
    }

    public static AppException conflict(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, key, details);
    }

    // ── FORBIDDEN ────────────────────────────────────────────────

    public static AppException forbidden(String key, Object... args) {
        return new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, key, args);
    }

    public static AppException forbidden(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, key, details);
    }

    // ── STORAGE ──────────────────────────────────────────────────

    public static AppException storage(String key, Object... args) {
        return new AppException(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, key, args);
    }

    public static AppException storage(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY, key, details);
    }

    // ── INTERNAL ─────────────────────────────────────────────────

    public static AppException internal(String key, Object... args) {
        return new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, key, args);
    }

    public static AppException internal(String key, Map<String, Object> details) {
        return new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, key, details);
    }
}