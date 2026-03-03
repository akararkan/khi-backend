package ak.dev.khi_backend.user.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {
    private String name;
    private String username;
    private String email;
    private String password;
    private Long pincode;
    // Note: profileImage is handled separately as MultipartFile in Controller
}