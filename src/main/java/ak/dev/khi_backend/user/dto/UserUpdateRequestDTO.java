package ak.dev.khi_backend.user.dto;

import ak.dev.khi_backend.user.enums.Role;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDTO {
    private String name;
    private String username;
    private String email;
    private String password;        // Nullable - only update if provided
    private Long pincode;
    private Role role;
    private Boolean isActivated;
    private Boolean removeProfileImage;  // Flag to explicitly remove image
}