package ak.dev.khi_backend.khi_app.dto;

// UserSession.java - Serializable Session Data

import java.io.Serializable;

public class UserSession implements Serializable {
    private Long userId;
    private String email;
    private String role;
    private Long createdAt;

    // Constructors, getters, setters
    public UserSession() {}

    public UserSession(Long userId, String email, String role, Long createdAt) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    // Getters and setters...
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
