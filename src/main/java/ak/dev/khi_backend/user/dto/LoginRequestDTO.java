package ak.dev.khi_backend.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    private String username; // username OR email
    private String password;
}
