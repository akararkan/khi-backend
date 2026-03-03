package ak.dev.khi_backend.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequestDTO {
    private String email;
    private String resetToken;
    private String newPassword;
    private String confirmPassword;
}
