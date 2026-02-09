package ak.dev.khi_backend.user.api;

import ak.dev.khi_backend.user.dto.LoginRequestDTO;
import ak.dev.khi_backend.user.dto.PasswordResetRequestDTO;
import ak.dev.khi_backend.user.dto.RegisterRequestDTO;
import ak.dev.khi_backend.user.jwt.Token;
import ak.dev.khi_backend.user.service.TokenService;
import ak.dev.khi_backend.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth") // cleaner than /api/auth/admin
public class UserAPI {

    private final UserService userService;
    private final TokenService tokenService;

    // ======================
    // REGISTER
    // ======================
    @PostMapping("/register")
    public ResponseEntity<Token> register(@RequestBody RegisterRequestDTO dto, HttpServletRequest request) {
        return userService.register(dto, request);
    }

    // ======================
    // LOGIN (identifier = username OR email)
    // ======================
    @PostMapping("/login")
    public ResponseEntity<Token> login(@RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        return userService.login(dto, request);
    }

    // ======================
    // REQUEST RESET TOKEN (best practice)
    // ======================
    @PostMapping("/reset-token")
    public ResponseEntity<String> createResetToken(@RequestParam String email) {
        return userService.createPasswordResetToken(email);
    }

    // ======================
    // RESET PASSWORD (requires token)
    // ======================
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequestDTO dto) {
        return userService.resetPassword(dto);
    }

    // ======================
    // LOGOUT (blacklist token)
    // ======================
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Invalid Authorization header");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token is empty");
        }

        tokenService.blacklistToken(token);
        return ResponseEntity.ok("Successfully logged out");
    }
}
