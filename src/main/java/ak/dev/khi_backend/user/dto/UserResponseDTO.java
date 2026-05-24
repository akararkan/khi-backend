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
    private String profileImage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant passwordExpiryDate;
}
