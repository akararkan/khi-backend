package ak.dev.khi_backend.user.dto;

import ak.dev.khi_backend.user.enums.Role;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long userId;
    private String name;
    private String username;
    private String email;
    private Role role;
    private Long pincode;
    private Boolean isActivated;

    // Profile images
    private String profileImage;    // Path/URL to locally uploaded image
    private String imageUrl;        // URL from OAuth2 provider (Google, etc.)

    // Additional info
    private String provider;        // "local", "google", etc.
    private Instant createdAt;
    private Instant updatedAt;
    /** Needed by frontend to show password-expiry warning */
    private Instant passwordExpiryDate;
}