package ak.dev.khi_backend.user.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    /**
     * Generates a JWT token for the given user and creates a session
     *
     * @param user    The user for whom the token is being generated
     * @param request HttpServletRequest to extract device info and IP
     * @return Generated JWT token
     */
    public String generateToken(User user, HttpServletRequest request) {
        try {
            String[] claims = extractUserAuthorities(user);
            Instant now = Instant.now();
            Instant expiration = now.plusMillis(expirationTime);

            // Create and store session
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

            String token = JWT.create()
                    .withIssuer(AKAR_ARKAN)
                    .withAudience(AKAR_ARKAN_ADMINISTRATION)
                    .withIssuedAt(Date.from(now))
                    .withSubject(user.getUsername())
                    .withClaim(ID_CLAIM, user.getUserId())
                    .withClaim(ROLE, user.getRole().name())
                    .withArrayClaim(AUTHORITIES, claims)
                    .withClaim("sessionId", session.getSessionId()) // Add sessionId as a claim
                    .withExpiresAt(Date.from(expiration))
                    .sign(Algorithm.HMAC256(secret));

            return token;
        } catch (Exception e) {
            logger.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Extracts user authorities as string array
     *
     * @param user The user whose authorities are to be extracted
     * @return Array of authority strings
     */
    private String[] extractUserAuthorities(User user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toArray(String[]::new);
    }

    /**
     * Creates an Authentication object for the given username and authorities
     *
     * @param username     User's username
     * @param authorities  User's granted authorities
     * @param request      HttpServletRequest
     * @return Authentication object
     */
    public Authentication getAuthentication(String username, List<GrantedAuthority> authorities, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authToken;
    }

    /**
     * Validates the JWT token
     *
     * @param username Username to validate
     * @param token    JWT token
     * @return true if token is valid, false otherwise
     */
    public boolean isTokenValid(String username, String token) {
        if (!StringUtils.hasText(username)) {
            logger.warn("Token validation failed: Empty username");
            return false;
        }

        try {
            JWTVerifier verifier = createJWTVerifier();
            return !isTokenExpired(verifier, token) && !isTokenBlacklisted(token);
        } catch (Exception e) {
            logger.error("Token validation error", e);
            return false;
        }
    }

    /**
     * Extracts username from the token
     *
     * @param token JWT token
     * @return Username
     */
    public String getSubject(String token) {
        try {
            JWTVerifier verifier = createJWTVerifier();
            return verifier.verify(token).getSubject();
        } catch (Exception e) {
            logger.error("Token subject extraction failed", e);
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    /**
     * Creates a JWT verifier
     *
     * @return JWTVerifier
     */
    private JWTVerifier createJWTVerifier() {
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(AKAR_ARKAN)
                    .build();
        } catch (Exception e) {
            logger.error("JWT verifier creation failed", e);
            throw new RuntimeException("Failed to create JWT verifier", e);
        }
    }

    /**
     * Checks if token is expired
     *
     * @param verifier JWTVerifier
     * @param token    JWT token
     * @return true if token is expired, false otherwise
     */
    private boolean isTokenExpired(JWTVerifier verifier, String token) {
        Date expiration = verifier.verify(token).getExpiresAt();
        boolean isExpired = expiration.before(new Date());

        if (isExpired) {
            logger.info("Token has expired");
        }

        return isExpired;
    }

    /**
     * Checks if token is blacklisted by verifying session's active status
     *
     * @param token JWT token
     * @return true if token is blacklisted, false otherwise
     */
    private boolean isTokenBlacklisted(String token) {
        String sessionId = getSessionIdFromToken(token);
        if (sessionId == null) {
            logger.warn("Session ID not found in token");
            return true;
        }
        Optional<Session> sessionOpt = sessionRepository.findBySessionId(sessionId);
        return sessionOpt.map(session -> !session.getIsActive()).orElse(true);
    }

    /**
     * Extracts authorities from the token
     *
     * @param token JWT token
     * @return List of GrantedAuthority
     */
    public List<GrantedAuthority> getAuthorities(String token) {
        String[] claims = extractAuthoritiesFromToken(token);
        return Arrays.stream(claims)
                .map(claim -> new SimpleGrantedAuthority(claim.replace(":", "_").toUpperCase()))
                .collect(Collectors.toList());
    }

    /**
     * Extracts raw authorities from the token
     *
     * @param token JWT token
     * @return Array of authority strings
     */
    public String[] extractAuthoritiesFromToken(String token) {
        try {
            JWTVerifier verifier = createJWTVerifier();
            return verifier.verify(token).getClaim(AUTHORITIES).asArray(String.class);
        } catch (Exception e) {
            logger.error("Failed to extract authorities from token", e);
            return new String[0];
        }
    }

    /**
     * Extracts user ID from the token
     *
     * @param token JWT token
     * @return User ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            DecodedJWT decodedJWT = decodeToken(token);
            return decodedJWT.getClaim(ID_CLAIM).asLong();
        } catch (Exception e) {
            logger.error("Failed to extract user ID from token", e);
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    /**
     * Decodes and verifies the token
     *
     * @param token JWT token
     * @return Decoded JWT
     */
    public DecodedJWT decodeToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("Brwa Salam - Hadi Shop")
                    .build();

            DecodedJWT decodedJWT = verifier.verify(token);

            // Optional: Log claims for debugging
            logger.debug("Issuer: " + decodedJWT.getIssuer());
            logger.debug("Subject: " + decodedJWT.getSubject());
            logger.debug("Audience: " + decodedJWT.getAudience());
            logger.debug("Expiration: " + decodedJWT.getExpiresAt());

            // Log all custom claims
            Map<String, Object> claims = decodedJWT.getClaims().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().as(Object.class)));
            claims.forEach((key, value) -> logger.debug(key + ": " + value));

            return decodedJWT;
        } catch (Exception e) {
            logger.error("Error decoding token", e);
            throw new IllegalArgumentException("Error decoding token", e);
        }
    }

    /**
     * Extracts session ID from the token
     *
     * @param token JWT token
     * @return Session ID
     */
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
