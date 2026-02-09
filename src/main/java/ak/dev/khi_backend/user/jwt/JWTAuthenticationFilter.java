package ak.dev.khi_backend.user.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ak.dev.khi_backend.user.service.TokenService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static ak.dev.khi_backend.user.consts.SecurityConstants.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
@RequiredArgsConstructor
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    @Qualifier("userService") // Tell Spring to use this specific bean
    private final UserDetailsService userDetailsService;
    private final TokenService tokenService; // If still needed for other purposes

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            // Allow OPTIONS requests to proceed (for CORS preflight)
            if (request.getMethod().equalsIgnoreCase(OPTIONS_HTTP_METHOD)) {
                response.setStatus(HttpStatus.OK.value());
                return;
            }

            // Extract JWT token from Authorization header
            String authorizationHeader = request.getHeader(AUTHORIZATION);

            if (!hasText(authorizationHeader) || !authorizationHeader.startsWith(TOKEN_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract token by removing the "Bearer " prefix
            String token = authorizationHeader.substring(TOKEN_PREFIX.length());

            // Extract username from token
            String username = jwtTokenProvider.getSubject(token);

            // Validate token and ensure user is not already authenticated
            if (hasText(username) && jwtTokenProvider.isTokenValid(username, token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load user details from UserDetailsService
                // (Optional if you have already set the necessary details in the token)
                // UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Extract authorities from token
                java.util.List<GrantedAuthority> authorities = jwtTokenProvider.getAuthorities(token);

                // Create Authentication object
                Authentication authentication = jwtTokenProvider.getAuthentication(username, authorities, request);

                // Set Authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                logger.debug("User '{}' authenticated successfully with token.", username);
            } else {
                logger.warn("Invalid token or user already authenticated.");
            }

        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            // Optionally, you can send a custom error response here
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access Denied: Invalid Token");
            return; // Stop further processing
        }

        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Utility method to check if a string has text.
     *
     * @param str The string to check.
     * @return true if the string has text, false otherwise.
     */
    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
