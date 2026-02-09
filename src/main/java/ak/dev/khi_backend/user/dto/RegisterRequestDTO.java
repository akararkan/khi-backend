package ak.dev.khi_backend.user.dto;

import ak.dev.khi_backend.user.enums.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {
    private String name;
    private String username;
    private String email;
    private String password;
    private Long pincode;
    private Role role;
}
