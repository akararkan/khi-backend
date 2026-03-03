package ak.dev.khi_backend.user.oauth2;

import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Called by Spring Security after Google returns the user-info response.
 * We extract the attributes, upsert into our users_tbl, and return a
 * CustomOAuth2User that carries both the Google attributes and our User entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 1. Let the default service call Google's userinfo endpoint
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (OAuth2AuthenticationException ex) {
            throw ex; // rethrow Spring Security exceptions as-is
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("processing_error"), ex.getMessage(), ex);
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {

        // 2. Extract claims from Google's userinfo response
        String email     = oAuth2User.getAttribute("email");
        String name      = oAuth2User.getAttribute("name");
        String googleId  = oAuth2User.getAttribute("sub");      // Google's stable unique ID
        String imageUrl  = oAuth2User.getAttribute("picture");
        String provider  = userRequest.getClientRegistration().getRegistrationId(); // "google"

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not found from OAuth2 provider");
        }

        // 3. Delegate the upsert logic to UserService so the business rules live in one place
        User user = userService.processOAuth2User(email, name, googleId, provider, imageUrl);

        // 4. Wrap together and return
        return new CustomOAuth2User(oAuth2User, user);
    }
}