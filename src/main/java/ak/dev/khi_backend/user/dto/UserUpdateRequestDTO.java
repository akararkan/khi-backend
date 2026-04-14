package ak.dev.khi_backend.user.dto;

import ak.dev.khi_backend.user.consts.ValidationPatterns;
import ak.dev.khi_backend.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDTO {

    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;

    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Pattern(
        regexp  = ValidationPatterns.USERNAME_OR_EMPTY,
        message = "Username can contain only letters, numbers, and underscores"
    )
    private String username;

    /** Optional — validated only when present (null is allowed). */
    @Email(
        regexp  = ValidationPatterns.EMAIL,
        message = "Email must be a valid address with a domain (e.g. user@example.com)"
    )
    @Size(max = 160, message = "Email must not exceed 160 characters")
    private String email;

    /** Optional — validated only when present (null means no change). */
    @Size(min = 6, max = 128, message = "Password must be at least 6 characters")
    private String password;

    private Long pincode;
    private Role role;
    private Boolean isActivated;
    private Boolean removeProfileImage;  // Flag to explicitly remove image
}