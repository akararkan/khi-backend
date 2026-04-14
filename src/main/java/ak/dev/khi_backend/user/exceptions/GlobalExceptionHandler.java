package ak.dev.khi_backend.user.exceptions;

/**
 * ⚠️  This class is intentionally empty.
 *
 * All exception handling (including user/auth exceptions) is centralized in:
 *   {@link ak.dev.khi_backend.khi_app.exceptions.GlobalExceptionHandler}
 *
 * Having two @RestControllerAdvice beans with the same name causes a
 * ConflictingBeanDefinitionException on startup.
 */
final class UserExceptionHandlerPlaceholder {
    private UserExceptionHandlerPlaceholder() {}
}
