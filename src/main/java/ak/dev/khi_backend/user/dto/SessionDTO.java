package ak.dev.khi_backend.user.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SessionDTO {
    private String sessionId;
    private String deviceInfo;
    private String ipAddress;
    private Date loginTimestamp;
    private Date expiresAt;
    private Boolean isActive;
    private Date logoutTimestamp;
}
