package ak.dev.khi_backend.user.oauth2;


import ak.dev.khi_backend.user.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Wraps the raw OAuth2User from Google and our local User entity together,
 * so Spring Security's OAuth2 machinery can work with it while we keep access
 * to our full User object downstream (in the success handler).
 */
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User oauth2User;

    @Getter
    private final User user;

    public CustomOAuth2User(OAuth2User oauth2User, User user) {
        this.oauth2User = oauth2User;
        this.user = user;
    }

    /** Raw attributes from Google (sub, email, name, picture, …) */
    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    /** Authorities come from our Role enum, not from Google */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    /** Spring uses this as the "principal name" */
    @Override
    public String getName() {
        return user.getEmail();
    }
}