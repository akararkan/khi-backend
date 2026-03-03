package ak.dev.khi_backend.user.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import ak.dev.khi_backend.user.model.Session;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static ak.dev.khi_backend.user.consts.SecurityConstants.*;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationTime;

    private final TokenService tokenService;
    private final SessionRepository sessionRepository;

    public String generateToken(User user, HttpServletRequest request) {
        try {
            String[] claims = extractUserAuthorities(user);
            Instant now = Instant.now();
            Instant expiration = now.plusMillis(expirationTime);

            Session session = Session.builder()
                    .sessionId(UUID.randomUUID().toString())
                    .user(user)
                    .deviceInfo(request.getHeader("User-Agent"))
                    .ipAddress(request.getRemoteAddr())
                    .loginTimestamp(Date.from(now))
                    .expiresAt(Date.from(expiration))
                    .isActive(true)
                    .build();

            sessionRepository.save(session);

            return JWT.create()
                    .withIssuer(AKAR_ARKAN)
                    .withAudience(AKAR_ARKAN_ADMINISTRATION)
                    .withIssuedAt(Date.from(now))
                    .withSubject(user.getUsername())
                    .withClaim(ID_CLAIM, user.getUserId())
                    .withClaim(ROLE, user.getRole().name())
                    .withArrayClaim(AUTHORITIES, claims)
                    .withClaim("sessionId", session.getSessionId())
                    .withExpiresAt(Date.from(expiration))
                    .sign(Algorithm.HMAC256(secret));

        } catch (Exception e) {
            logger.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    private String[] extractUserAuthorities(User user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toArray(String[]::new);
    }

    /**
     * ── FIX ──────────────────────────────────────────────────────────────────
     * Principal is now a UserDetails instance instead of a plain String.
     * This makes @AuthenticationPrincipal UserDetails resolve correctly in
     * every controller (e.g. UserProfileAPI#getMe).
     * ─────────────────────────────────────────────────────────────────────────
     */
    public Authentication getAuthentication(UserDetails userDetails,
                                            List<GrantedAuthority> authorities,
                                            HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authToken;
    }

    /**
     * Validates token (checks signature and expiration).
     * Throws TokenExpiredException if token is expired.
     */
    public String getSubject(String token) throws TokenExpiredException {
        JWTVerifier verifier = createJWTVerifier();
        return verifier.verify(token).getSubject();
    }

    public boolean isTokenBlacklisted(String token) {
        String sessionId = getSessionIdFromToken(token);
        if (sessionId == null) return true;
        Optional<Session> sessionOpt = sessionRepository.findBySessionId(sessionId);
        return sessionOpt.map(session -> !session.getIsActive()).orElse(true);
    }

    private JWTVerifier createJWTVerifier() {
        return JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(AKAR_ARKAN)
                .build();
    }

    public List<GrantedAuthority> getAuthorities(String token) {
        String[] claims = extractAuthoritiesFromToken(token);
        return Arrays.stream(claims)
                .map(claim -> new SimpleGrantedAuthority(claim.replace(":", "_").toUpperCase()))
                .collect(Collectors.toList());
    }

    public String[] extractAuthoritiesFromToken(String token) {
        try {
            JWTVerifier verifier = createJWTVerifier();
            return verifier.verify(token).getClaim(AUTHORITIES).asArray(String.class);
        } catch (Exception e) {
            logger.error("Failed to extract authorities from token", e);
            return new String[0];
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            DecodedJWT decodedJWT = decodeToken(token);
            return decodedJWT.getClaim(ID_CLAIM).asLong();
        } catch (Exception e) {
            logger.error("Failed to extract user ID from token", e);
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public DecodedJWT decodeToken(String token) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(AKAR_ARKAN)
                .build();
        return verifier.verify(token);
    }

    public String getSessionIdFromToken(String token) {
        try {
            DecodedJWT decodedJWT = decodeToken(token);
            return decodedJWT.getClaim("sessionId").asString();
        } catch (Exception e) {
            logger.error("Failed to extract session ID from token", e);
            return null;
        }
    }
}