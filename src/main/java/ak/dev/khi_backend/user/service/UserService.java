package ak.dev.khi_backend.user.service;

import jakarta.servlet.http.HttpServletRequest;
import ak.dev.khi_backend.user.dto.*;
import ak.dev.khi_backend.user.exceptions.UserAlreadyExistsException;
import ak.dev.khi_backend.user.jwt.JwtTokenProvider;
import ak.dev.khi_backend.user.jwt.Token;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
@Primary
public class UserService implements UserDetailsService {

    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(5);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration PASSWORD_EXPIRY = Duration.ofDays(90);
    private static final Duration RESET_TOKEN_EXPIRY = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRepository sessionRepository;

    // =======================
    // UserDetailsService
    // =======================
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with username: " + username));

        // Persist unlock if lock time passed
        unlockIfLockExpired(user);

        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new LockedException("Account is locked. Please try again later.");
        }
        if (user.isPasswordExpired()) {
            throw new LockedException("Your password has expired. Please reset it.");
        }
        return user;
    }

    // =======================
    // Register
    // =======================
    public ResponseEntity<Token> register(RegisterRequestDTO dto, HttpServletRequest request) {
        try {
            if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
                throw new UserAlreadyExistsException("Username is already taken.");
            }
            if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
                throw new UserAlreadyExistsException("Email is already registered.");
            }

            Instant now = Instant.now();

            User newUser = User.builder()
                    .name(dto.getName())
                    .username(dto.getUsername())
                    .email(dto.getEmail())
                    .password(passwordEncoder.encode(dto.getPassword()))
                    .pincode(dto.getPincode())
                    .role(dto.getRole())
                    .isActivated(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .failedAttempts(0)
                    .isLocked(false)
                    .lockTime(null)
                    .passwordExpiryDate(now.plus(PASSWORD_EXPIRY))
                    .build();

            userRepository.save(newUser);

            String jwt = jwtTokenProvider.generateToken(newUser, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Token.builder()
                            .token(jwt)
                            .response("Registration successful. You can now login.")
                            .build()
            );

        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new Token(null, e.getMessage()));
        } catch (Exception e) {
            log.error("Register error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Token(null, "An unexpected error occurred. Please try again later."));
        }
    }

    // =======================
    // Login
    // =======================
    public ResponseEntity<Token> login(LoginRequestDTO dto, HttpServletRequest request) {
        try {
            User existingUser = findUserByUsernameOrEmail(dto.getUsername());

            unlockIfLockExpired(existingUser);

            if (Boolean.TRUE.equals(existingUser.getIsLocked())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new Token(null, "Account is locked. Please try again after 5 minutes."));
            }

            if (!passwordEncoder.matches(dto.getPassword(), existingUser.getPassword())) {
                recordFailedLoginAttempt(existingUser);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new Token(null, "Invalid credentials"));
            }

            resetFailedAttempts(existingUser);

            if (existingUser.isPasswordExpired()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new Token(null, "Your password has expired. Please reset it."));
            }

            String jwt = jwtTokenProvider.generateToken(existingUser, request);

            return ResponseEntity.ok(
                    Token.builder()
                            .token(jwt)
                            .response("Login successfully done.")
                            .build()
            );

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Token(null, "Invalid credentials"));
        } catch (Exception e) {
            log.error("Login error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Token(null, "An unexpected error occurred. Please try again later."));
        }
    }

    // =======================
    // Reset token generator (optional but best)
    // =======================
    public ResponseEntity<String> createPasswordResetToken(String email) {
        try {
            User user = findUserByUsernameOrEmail(email);

            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiration(Instant.now().plus(RESET_TOKEN_EXPIRY));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            // In real life: send token by email
            return ResponseEntity.ok("Reset token generated. (For dev: token = " + token + ")");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        } catch (Exception e) {
            log.error("createPasswordResetToken error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while creating reset token.");
        }
    }

    // =======================
    // Reset password (SECURE)
    // =======================
    public ResponseEntity<String> resetPassword(PasswordResetRequestDTO req) {
        try {
            User user = findUserByUsernameOrEmail(req.getEmail());

            if (req.getNewPassword() == null || !req.getNewPassword().equals(req.getConfirmPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("New password and confirm password do not match.");
            }

            if (user.getResetToken() == null || user.getResetTokenExpiration() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("No reset token found. Please request a new reset token.");
            }

            if (!Objects.equals(user.getResetToken(), req.getResetToken())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid reset token.");
            }

            if (user.getResetTokenExpiration().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Reset token expired. Please request a new reset token.");
            }

            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
            user.setResetToken(null);
            user.setResetTokenExpiration(null);
            user.setPasswordExpiryDate(Instant.now().plus(PASSWORD_EXPIRY));
            user.setUpdatedAt(Instant.now());

            // also unlock after reset
            user.setIsLocked(false);
            user.setFailedAttempts(0);
            user.setLockTime(null);

            userRepository.save(user);

            return ResponseEntity.ok("Password has been successfully reset.");

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        } catch (Exception e) {
            log.error("resetPassword error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while resetting your password.");
        }
    }

    // =======================
    // CRUD helpers returning safe DTO
    // =======================
    public UserResponseDTO createUser(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username is already taken.");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email is already registered.");
        }

        Instant now = Instant.now();

        User saved = userRepository.save(
                User.builder()
                        .name(user.getName())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .password(passwordEncoder.encode(user.getPassword()))
                        .pincode(user.getPincode())
                        .role(user.getRole())
                        .isActivated(user.getIsActivated() != null ? user.getIsActivated() : true)
                        .createdAt(now)
                        .updatedAt(now)
                        .failedAttempts(0)
                        .isLocked(false)
                        .passwordExpiryDate(now.plus(PASSWORD_EXPIRY))
                        .build()
        );

        return toResponse(saved);
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponseDTO getUserById(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        return toResponse(u);
    }

    public UserResponseDTO updateUser(Long userId, User updates) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        if (updates.getUsername() != null && !updates.getUsername().equals(u.getUsername())
                && userRepository.findByUsername(updates.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username is already taken.");
        }

        if (updates.getEmail() != null && !updates.getEmail().equals(u.getEmail())
                && userRepository.findByEmail(updates.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email is already registered.");
        }

        if (updates.getName() != null) u.setName(updates.getName());
        if (updates.getUsername() != null) u.setUsername(updates.getUsername());
        if (updates.getEmail() != null) u.setEmail(updates.getEmail());
        if (updates.getPincode() != null) u.setPincode(updates.getPincode());
        if (updates.getRole() != null) u.setRole(updates.getRole());
        if (updates.getIsActivated() != null) u.setIsActivated(updates.getIsActivated());

        if (updates.getPassword() != null && !updates.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(updates.getPassword()));
            u.setPasswordExpiryDate(Instant.now().plus(PASSWORD_EXPIRY));
        }

        u.setUpdatedAt(Instant.now());
        return toResponse(userRepository.save(u));
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        // Delete sessions first
        var sessions = sessionRepository.findByUser(user);
        if (sessions != null && !sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
        }

        userRepository.delete(user);
    }

    // =======================
    // Internals
    // =======================
    private User findUserByUsernameOrEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("User not found.");
        }

        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    private void recordFailedLoginAttempt(User user) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);

        if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.setIsLocked(true);
            user.setLockTime(Instant.now());
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        user.setIsLocked(false);
        user.setLockTime(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private void unlockIfLockExpired(User user) {
        if (Boolean.TRUE.equals(user.getIsLocked()) && user.getLockTime() != null) {
            Instant lockedAt = user.getLockTime();
            if (Instant.now().isAfter(lockedAt.plus(ACCOUNT_LOCK_DURATION))) {
                user.setIsLocked(false);
                user.setFailedAttempts(0);
                user.setLockTime(null);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            }
        }
    }

    public String getPasswordExpiryWarning(User user) {
        if (user.getPasswordExpiryDate() == null) return null;
        long daysRemaining = Duration.between(Instant.now(), user.getPasswordExpiryDate()).toDays();
        if (daysRemaining <= 7 && daysRemaining >= 0) {
            return "Your password will expire in " + daysRemaining + " days. Please update it soon.";
        }
        return null;
    }

    private UserResponseDTO toResponse(User u) {
        return UserResponseDTO.builder()
                .userId(u.getUserId())
                .name(u.getName())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .pincode(u.getPincode())
                .isActivated(u.getIsActivated())
                .build();
    }
}
