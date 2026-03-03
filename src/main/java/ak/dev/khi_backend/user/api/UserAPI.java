package ak.dev.khi_backend.user.api;

import ak.dev.khi_backend.user.dto.LoginRequestDTO;
import ak.dev.khi_backend.user.dto.PasswordResetRequestDTO;
import ak.dev.khi_backend.user.dto.RegisterRequestDTO;
import ak.dev.khi_backend.user.jwt.Token;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.service.TokenService;
import ak.dev.khi_backend.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserAPI {

    private final UserService    userService;
    private final TokenService   tokenService;
    private final SessionRepository sessionRepository;

    // ── REGISTER (JSON, no image) ─────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Token> register(
            @RequestBody RegisterRequestDTO dto,
            HttpServletRequest request
    ) {
        return userService.register(dto, null, request);
    }

    // ── REGISTER with optional profile image (multipart) ─────────────────────
    @PostMapping(value = "/register-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Token> registerWithImage(
            @RequestPart("data")                       RegisterRequestDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile profileImage,
            HttpServletRequest request
    ) {
        return userService.register(dto, profileImage, request);
    }

    // ── LOGIN (username OR email) ─────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Token> login(
            @RequestBody LoginRequestDTO dto,
            HttpServletRequest request
    ) {
        return userService.login(dto, request);
    }

    // ── REQUEST PASSWORD-RESET TOKEN ─────────────────────────────────────────
    @PostMapping("/reset-token")
    public ResponseEntity<String> createResetToken(@RequestParam String email) {
        return userService.createPasswordResetToken(email);
    }

    // ── RESET PASSWORD (requires token) ──────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequestDTO dto) {
        return userService.resetPassword(dto);
    }

    // ── LOGOUT (invalidate current session / blacklist token) ─────────────────
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
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

    // ── LOGOUT ALL DEVICES (invalidate every active session for this user) ────
    /**
     * Marks every active Session row for the current user as inactive,
     * and blacklists the current token so it stops working immediately.
     *
     * Called by UserProfile.vue → "دەرچوون لە ھەموو ئامێرەکان"
     */
    @PostMapping("/logout-all")
    public ResponseEntity<String> logoutAll(
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }

        // 1. Deactivate all sessions in DB for this user
        User user = (User) principal;
        var sessions = sessionRepository.findByUser(user);
        if (sessions != null && !sessions.isEmpty()) {
            sessions.forEach(s -> {
                s.setIsActive(false);
                s.setLogoutTimestamp(Date.from(java.time.Instant.now()));
            });
            sessionRepository.saveAll(sessions);
        }

        // 2. Blacklist the current token
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length()).trim();
            if (!token.isEmpty()) {
                tokenService.blacklistToken(token);
            }
        }

        return ResponseEntity.ok("Logged out from all devices successfully");
    }
}