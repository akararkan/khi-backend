package ak.dev.khi_backend.khi_app.exceptions;

import ak.dev.khi_backend.khi_app.exceptions.project.*;
import ak.dev.khi_backend.khi_app.exceptions.publishment.image.*;
import ak.dev.khi_backend.khi_app.exceptions.publishment.sound.*;
import ak.dev.khi_backend.khi_app.exceptions.publishment.video.*;
import ak.dev.khi_backend.khi_app.exceptions.publishment.writing.*;
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

    // ═════════════════════════════════════════════════════════════
    // PROJECT-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════

    /** 404 — Project with given ID not found */
    public static ProjectNotFoundException projectNotFound(Long projectId) {
        return new ProjectNotFoundException(projectId);
    }

    /** 409 — Duplicate project data */
    public static ProjectConflictException projectConflict(String traceId) {
        return new ProjectConflictException(Map.of("traceId", traceId != null ? traceId : "N/A"));
    }

    /** 400 — Project field validation failed */
    public static ProjectValidationException projectValidation(String messageKey, Map<String, Object> details) {
        return new ProjectValidationException(messageKey, details);
    }

    /** 502 — Cover upload failed (legacy — covers now go through the shared media endpoint) */
    public static ProjectStorageException projectCoverUploadFailed(Throwable cause) {
        return ProjectStorageException.coverUploadFailed(cause);
    }

    /** 500 — Unexpected error during project create */
    public static ProjectInternalException projectCreateFailed(String traceId, Throwable cause) {
        return ProjectInternalException.createFailed(traceId, cause);
    }

    /** 500 — Unexpected error during project update */
    public static ProjectInternalException projectUpdateFailed(Long projectId, String traceId, Throwable cause) {
        return ProjectInternalException.updateFailed(projectId, traceId, cause);
    }

    /** 500 — Unexpected error during project delete */
    public static ProjectInternalException projectDeleteFailed(Long projectId, String traceId, Throwable cause) {
        return ProjectInternalException.deleteFailed(projectId, traceId, cause);
    }

    // ═════════════════════════════════════════════════════════════
    // NEWS-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════

    /** 404 — News with given ID not found */
    public static ak.dev.khi_backend.khi_app.exceptions.news.NewsNotFoundException newsNotFound(Long id) {
        return new ak.dev.khi_backend.khi_app.exceptions.news.NewsNotFoundException(id);
    }

    /** 409 — Duplicate news data */
    public static ak.dev.khi_backend.khi_app.exceptions.news.NewsConflictException newsConflict(String traceId) {
        return new ak.dev.khi_backend.khi_app.exceptions.news.NewsConflictException(
                Map.of("traceId", traceId != null ? traceId : "N/A"));
    }

    /** 400 — News validation failed */
    public static ak.dev.khi_backend.khi_app.exceptions.news.NewsValidationException newsValidation(String key, Map<String, Object> details) {
        return new ak.dev.khi_backend.khi_app.exceptions.news.NewsValidationException(key, details);
    }

    /** 502 — News storage error */
    public static ak.dev.khi_backend.khi_app.exceptions.news.NewsStorageException newsStorageFailed(String key, Map<String, Object> details, Throwable cause) {
        return new ak.dev.khi_backend.khi_app.exceptions.news.NewsStorageException(key, details, cause);
    }

    /** 500 — Unexpected news internal error */
    public static ak.dev.khi_backend.khi_app.exceptions.news.NewsInternalException newsInternal(String key, Map<String, Object> details, Throwable cause) {
        return new ak.dev.khi_backend.khi_app.exceptions.news.NewsInternalException(key, details, cause);
    }

    // ═════════════════════════════════════════════════════════════════════
    // IMAGE-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════════════

    /** 404 — Image collection not found */
    public static ImageCollectionNotFoundException imageNotFound(Long id) {
        return new ImageCollectionNotFoundException(id);
    }

    /** 409 — Image collection conflict */
    public static ImageCollectionConflictException imageConflict(String traceId) {
        return new ImageCollectionConflictException(Map.of("traceId", traceId != null ? traceId : "N/A"));
    }

    /** 400 — Image validation failed */
    public static ImageCollectionValidationException imageValidation(String key, Map<String, Object> details) {
        return new ImageCollectionValidationException(key, details);
    }

    /** 400 — Image media invalid */
    public static ImageCollectionMediaException imageMediaInvalid(String key, Map<String, Object> details) {
        return new ImageCollectionMediaException(key, details);
    }

    /** 502 — Image storage failed */
    public static ImageCollectionStorageException imageStorageFailed(String key, Map<String, Object> details, Throwable cause) {
        return new ImageCollectionStorageException(key, details, cause);
    }

    /** 500 — Image internal error */
    public static ImageCollectionInternalException imageInternal(String key, Map<String, Object> details, Throwable cause) {
        return new ImageCollectionInternalException(key, details, cause);
    }

    // ═════════════════════════════════════════════════════════════════════
    // SOUND-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════════════

    /** 404 — SoundTrack not found */
    public static SoundTrackNotFoundException soundNotFound(Long id) {
        return new SoundTrackNotFoundException(id);
    }

    /** 400 — SoundTrack validation failed */
    public static SoundTrackValidationException soundValidation(String key, Map<String, Object> details) {
        return new SoundTrackValidationException(key, details);
    }

    /** 400 — SoundTrack media invalid */
    public static SoundTrackMediaException soundMediaInvalid(String key, Map<String, Object> details) {
        return new SoundTrackMediaException(key, details);
    }

    /** 502 — SoundTrack storage failed */
    public static SoundTrackStorageException soundStorageFailed(String key, Map<String, Object> details, Throwable cause) {
        return new SoundTrackStorageException(key, details, cause);
    }

    /** 500 — SoundTrack internal error */
    public static SoundTrackInternalException soundInternal(String key, Map<String, Object> details, Throwable cause) {
        return new SoundTrackInternalException(key, details, cause);
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // VIDEO-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════════════════════

    /** 404 — Video with given ID not found */
    public static VideoNotFoundException videoNotFound(Long id) {
        return new VideoNotFoundException(id);
    }

    /** 400 — Video validation failed */
    public static VideoValidationException videoValidation(String key, Map<String, Object> details) {
        return new VideoValidationException(key, details);
    }

    /** 400 — Video media invalid */
    public static VideoMediaException videoMediaInvalid(String key, Map<String, Object> details) {
        return new VideoMediaException(key, details);
    }

    /** 502 — Video storage failed */
    public static VideoStorageException videoStorageFailed(String key, Map<String, Object> details, Throwable cause) {
        return new VideoStorageException(key, details, cause);
    }

    /** 500 — Video internal error */
    public static VideoInternalException videoInternal(String key, Map<String, Object> details, Throwable cause) {
        return new VideoInternalException(key, details, cause);
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // WRITING-SPECIFIC factories
    // ═════════════════════════════════════════════════════════════════════════════

    /** 404 — Writing with given ID not found */
    public static WritingNotFoundException writingNotFound(Long id) {
        return new WritingNotFoundException(id);
    }

    /** 400 — Writing validation failed */
    public static WritingValidationException writingValidation(String key, Map<String, Object> details) {
        return new WritingValidationException(key, details);
    }

    /** 400 — Writing media invalid */
    public static WritingMediaException writingMediaInvalid(String key, Map<String, Object> details) {
        return new WritingMediaException(key, details);
    }

    /** 502 — Writing storage failed */
    public static WritingStorageException writingStorageFailed(String key, Map<String, Object> details, Throwable cause) {
        return new WritingStorageException(key, details, cause);
    }

    /** 500 — Writing internal error */
    public static WritingInternalException writingInternal(String key, Map<String, Object> details, Throwable cause) {
        return new WritingInternalException(key, details, cause);
    }
}