package ak.dev.khi_backend.user.oauth2;

import ak.dev.khi_backend.user.jwt.JwtTokenProvider;
import ak.dev.khi_backend.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173/oauth2/redirect}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        if (response.isCommitted()) {
            log.warn("Response already committed — cannot redirect.");
            return;
        }

        // 1. Get our User entity from the principal
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // 2. Generate JWT (JwtTokenProvider already persists the Session in the DB)
        String token = jwtTokenProvider.generateToken(user, request);

        // 3. Clear the OAuth2 session attributes — we don't need them anymore
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("SPRING_SECURITY_SAVED_REQUEST");
        }

        // 4. Build the redirect URL and send it
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build()
                .toUriString();

        log.info("OAuth2 login success for: {} — redirecting to frontend", user.getEmail());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}