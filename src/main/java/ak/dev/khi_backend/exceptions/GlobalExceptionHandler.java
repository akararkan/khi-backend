package ak.dev.khi_backend.exceptions;

import ak.dev.khi_backend.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.*;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleApp(AppException ex, HttpServletRequest req, Locale locale) {
        ApiErrorResponse body = base(req, ex.getHttpStatus().value(), ex.getCode());

        // localized main message
        body.setMessage(resolve(locale, ex.getMessageKey(), ex.getMessageArgs(), fallbackByCode(ex.getCode(), "en")));
        body.setMessageEn(resolve(Locale.ENGLISH, ex.getMessageKey(), ex.getMessageArgs(), fallbackByCode(ex.getCode(), "en")));
        body.setMessageKu(resolve(new Locale("ku"), ex.getMessageKey(), ex.getMessageArgs(), fallbackByCode(ex.getCode(), "ku")));

        body.setDetails(ex.getDetails());

        log.warn("Handled AppException code={} status={} path={} traceId={}",
                ex.getCode(), ex.getHttpStatus().value(), req.getRequestURI(), body.getTraceId());

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req, Locale locale) {
        ApiErrorResponse body = base(req, 400, ErrorCode.VALIDATION_ERROR);

        body.setMessage(resolve(locale, "error.validation", null, fallbackByCode(ErrorCode.VALIDATION_ERROR, "en")));
        body.setMessageEn(resolve(Locale.ENGLISH, "error.validation", null, fallbackByCode(ErrorCode.VALIDATION_ERROR, "en")));
        body.setMessageKu(resolve(new Locale("ku"), "error.validation", null, fallbackByCode(ErrorCode.VALIDATION_ERROR, "ku")));

        List<ApiFieldError> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String key = fe.getDefaultMessage(); // you can set keys in annotations
            String defEn = fe.getDefaultMessage();

            fieldErrors.add(ApiFieldError.builder()
                    .field(fe.getField())
                    .message(resolve(locale, key, null, defEn))
                    .messageEn(resolve(Locale.ENGLISH, key, null, defEn))
                    .messageKu(resolve(new Locale("ku"), key, null, defEn))
                    .build());
        }
        body.setFieldErrors(fieldErrors);

        log.warn("Validation error path={} traceId={} errors={}",
                req.getRequestURI(), body.getTraceId(), fieldErrors.size());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex, HttpServletRequest req, Locale locale) {
        ApiErrorResponse body = base(req, 500, ErrorCode.INTERNAL_ERROR);

        body.setMessage(resolve(locale, "error.internal", null, "Internal server error"));
        body.setMessageEn(resolve(Locale.ENGLISH, "error.internal", null, "Internal server error"));
        body.setMessageKu(resolve(new Locale("ku"), "error.internal", null, "هەڵەی ناوخۆیی سێرڤەر"));

        // log full stack trace (server side)
        log.error("Unhandled exception path={} traceId={}", req.getRequestURI(), body.getTraceId(), ex);

        return ResponseEntity.status(500).body(body);
    }

    // ---------------- helpers ----------------

    private ApiErrorResponse base(HttpServletRequest req, int status, ErrorCode code) {
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .code(code)
                .path(req.getRequestURI())
                .method(req.getMethod())
                .traceId(Optional.ofNullable(org.slf4j.MDC.get(TraceIdFilter.TRACE_ID)).orElse(null))
                .build();
    }

    private String resolve(Locale locale, String key, Object[] args, String defaultMessage) {
        if (key == null || key.isBlank()) return defaultMessage;
        try {
            return messageSource.getMessage(key, args, defaultMessage, locale);
        } catch (Exception ignore) {
            return defaultMessage;
        }
    }

    private String fallbackByCode(ErrorCode code, String lang) {
        // simple fallback text if key is missing
        return switch (code) {
            case NOT_FOUND -> lang.equals("ku") ? "سەرچاوە نەدۆزرایەوە" : "Resource not found";
            case BAD_REQUEST -> lang.equals("ku") ? "داواکاری هەڵەیە" : "Bad request";
            case CONFLICT -> lang.equals("ku") ? "کێشەی تێکچوون هەیە" : "Conflict";
            case UNAUTHORIZED -> lang.equals("ku") ? "ڕێگەپێنەدراو" : "Unauthorized";
            case FORBIDDEN -> lang.equals("ku") ? "قەدەغەکراوە" : "Forbidden";
            case VALIDATION_ERROR -> lang.equals("ku") ? "هەڵەی پێداچوونەوە" : "Validation error";
            default -> lang.equals("ku") ? "هەڵەی ناوخۆیی" : "Internal error";
        };
    }
}
