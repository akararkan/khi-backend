package ak.dev.khi_backend.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequestDTO {
    private String username;
    private String name;
}