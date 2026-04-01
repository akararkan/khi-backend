package ak.dev.khi_backend.user.service;

import ak.dev.khi_backend.user.enums.Role;
import ak.dev.khi_backend.user.dto.*;
import ak.dev.khi_backend.user.exceptions.UserAlreadyExistsException;
import ak.dev.khi_backend.user.jwt.JwtTokenProvider;
import ak.dev.khi_backend.user.jwt.Token;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.*;
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
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Duration PASSWORD_EXPIRY = Duration.ofDays(90);
    private static final Duration RESET_TOKEN_EXPIRY = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRepository sessionRepository;

    @Value("${app.upload.dir:uploads/profile-images}")
    private String uploadDir;

    // =======================
    // UserDetailsService
    // =======================
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with username: " + username));
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
    // Register (with optional image)
    // =======================
    public ResponseEntity<Token> register(RegisterRequestDTO dto, MultipartFile profileImage, HttpServletRequest request) {
        try {
            if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
                throw new UserAlreadyExistsException("Username is already taken.");
            }
            if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
                throw new UserAlreadyExistsException("Email is already registered.");
            }

            Instant now = Instant.now();
            String storedImagePath = null;

            // Handle optional profile image
            if (profileImage != null && !profileImage.isEmpty()) {
                storedImagePath = storeProfileImage(profileImage);
            }

            User newUser = User.builder()
                    .name(dto.getName())
                    .username(dto.getUsername())
                    .email(dto.getEmail())
                    .password(passwordEncoder.encode(dto.getPassword()))
                    .pincode(dto.getPincode())
                    .role(Role.GUEST)
                    .provider("local")
                    .isActivated(true)
                    .profileImage(storedImagePath)  // Set local profile image path
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new Token(null, "Invalid image: " + e.getMessage()));
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
    // OAuth2 User Processing
    // =======================
    public User processOAuth2User(String email, String name, String providerId,
                                  String provider, String imageUrl) {
        Instant now = Instant.now();
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setName(name);
            user.setProviderId(providerId);
            user.setImageUrl(imageUrl);      // OAuth provider image URL
            user.setUpdatedAt(now);
            log.info("Existing user logged in via {}: {}", provider, email);
            return userRepository.save(user);
        }

        // New user from OAuth2
        User newUser = User.builder()
                .name(name)
                .username(generateUniqueUsername(email))
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.GUEST)
                .provider(provider)
                .providerId(providerId)
                .imageUrl(imageUrl)             // OAuth provider image
                .profileImage(null)             // No local image yet
                .isActivated(true)
                .createdAt(now)
                .updatedAt(now)
                .failedAttempts(0)
                .isLocked(false)
                .passwordExpiryDate(now.plus(Duration.ofDays(36500)))
                .build();

        log.info("New user created via {}: {}", provider, email);
        return userRepository.save(newUser);
    }

    // =======================
    // Profile Image Operations
    // =======================

    /**
     * Update only the profile image for a user
     */
    public UserResponseDTO updateProfileImage(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Profile image cannot be empty");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        // Delete old image if exists
        deleteOldProfileImage(user.getProfileImage());

        // Store new image
        String imagePath = storeProfileImage(file);
        user.setProfileImage(imagePath);
        user.setUpdatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    /**
     * Remove profile image (revert to null)
     */
    public UserResponseDTO removeProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        deleteOldProfileImage(user.getProfileImage());
        user.setProfileImage(null);
        user.setUpdatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    // =======================
    // Reset Password Token
    // =======================
    public ResponseEntity<String> createPasswordResetToken(String email) {
        try {
            User user = findUserByUsernameOrEmail(email);
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiration(Instant.now().plus(RESET_TOKEN_EXPIRY));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
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
    // Reset Password
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
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid reset token.");
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
    // CRUD Operations (with image support)
    // =======================
    public UserResponseDTO createUser(UserCreateRequestDTO dto, MultipartFile profileImage) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username is already taken.");
        }
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email is already registered.");
        }

        String imagePath = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            imagePath = storeProfileImage(profileImage);
        }

        Instant now = Instant.now();
        User saved = userRepository.save(
                User.builder()
                        .name(dto.getName())
                        .username(dto.getUsername())
                        .email(dto.getEmail())
                        .password(passwordEncoder.encode(dto.getPassword()))
                        .pincode(dto.getPincode())
                        .role(dto.getRole() != null ? dto.getRole() : Role.GUEST)
                        .provider("local")
                        .profileImage(imagePath)
                        .isActivated(dto.getIsActivated() != null ? dto.getIsActivated() : true)
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

    /**
     * Update user - now uses DTO and optional image file
     */
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO dto, MultipartFile newProfileImage) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        if (dto.getUsername() != null && !dto.getUsername().equals(u.getUsername())
                && userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username is already taken.");
        }
        if (dto.getEmail() != null && !dto.getEmail().equals(u.getEmail())
                && userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email is already registered.");
        }

        if (dto.getName() != null) u.setName(dto.getName());
        if (dto.getUsername() != null) u.setUsername(dto.getUsername());
        if (dto.getEmail() != null) u.setEmail(dto.getEmail());
        if (dto.getPincode() != null) u.setPincode(dto.getPincode());
        if (dto.getRole() != null) u.setRole(dto.getRole());
        if (dto.getIsActivated() != null) u.setIsActivated(dto.getIsActivated());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(dto.getPassword()));
            u.setPasswordExpiryDate(Instant.now().plus(PASSWORD_EXPIRY));
        }

        // Handle profile image update
        if (newProfileImage != null) {
            if (!newProfileImage.isEmpty()) {
                deleteOldProfileImage(u.getProfileImage());
                u.setProfileImage(storeProfileImage(newProfileImage));
            }
        } else if (Boolean.TRUE.equals(dto.getRemoveProfileImage())) {
            deleteOldProfileImage(u.getProfileImage());
            u.setProfileImage(null);
        }

        u.setUpdatedAt(Instant.now());
        return toResponse(userRepository.save(u));
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        // Clean up profile image file
        deleteOldProfileImage(user.getProfileImage());

        var sessions = sessionRepository.findByUser(user);
        if (sessions != null && !sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
        }
        userRepository.delete(user);
    }

    // =======================
    // File Storage Helpers
    // =======================

    private String storeProfileImage(MultipartFile file) {
        try {
            // Validate file
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File size exceeds maximum limit of 5MB");
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("Invalid file type. Only JPEG, PNG, GIF and WebP are allowed");
            }

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = originalFilename.contains(".") ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path targetLocation = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path or URL
            return uploadDir + "/" + filename;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store file. Please try again later.", ex);
        }
    }

    private void deleteOldProfileImage(String imagePath) {
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                Path filePath = Paths.get(imagePath);
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete old profile image: {}", imagePath, e);
                // Continue even if deletion fails
            }
        }
    }

    // =======================
    // Other Helpers
    // =======================
    private User findUserByUsernameOrEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("User not found.");
        }
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        String candidate = base;
        int i = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + i++;
        }
        return candidate;
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
            if (Instant.now().isAfter(user.getLockTime().plus(ACCOUNT_LOCK_DURATION))) {
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
                .profileImage(u.getProfileImage())      // Local uploaded image
                .imageUrl(u.getImageUrl())              // OAuth provider image
                .provider(u.getProvider())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}