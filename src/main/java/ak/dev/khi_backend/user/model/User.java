package ak.dev.khi_backend.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import ak.dev.khi_backend.user.enums.Role;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;

@Entity
@Table(
        name = "users_tbl",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class User implements Serializable, UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // better for PostgreSQL
    private Long userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "username", nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @JsonIgnore
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    @Column(name = "pincode", length = 6)
    private Long pincode;

    @Column(name = "is_activated", nullable = false)
    private Boolean isActivated = true;

    // ===== Password Reset =====
    @JsonIgnore
    @Column(name = "reset_token", length = 120)
    private String resetToken;

    @JsonIgnore
    @Column(name = "reset_token_expiration")
    private Instant resetTokenExpiration;

    // ===== Audit =====
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ===== Locking =====
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @JsonIgnore
    @Column(name = "lock_time")
    private Instant lockTime;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    // ===== Password expiry =====
    @Column(name = "password_expiry_date")
    private Instant passwordExpiryDate;

    // ---------------- UserDetails ----------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.getAuthorities();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Spring checks this, but DB update happens in service.
        if (Boolean.TRUE.equals(isLocked) && lockTime != null) {
            long lockMs = Instant.now().toEpochMilli() - lockTime.toEpochMilli();
            return lockMs > (5 * 60 * 1000); // unlock after 5 minutes (service will persist)
        }
        return !Boolean.TRUE.equals(isLocked);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !isPasswordExpired();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(isActivated);
    }

    public boolean isPasswordExpired() {
        return passwordExpiryDate != null && passwordExpiryDate.isBefore(Instant.now());
    }
}
