package ak.dev.khi_backend.user.dto;

import ak.dev.khi_backend.user.enums.Role;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequestDTO {
    private String name;
    private String username;
    private String email;
    private String password;
    private Long pincode;
    private Role role;
    private Boolean isActivated;
}