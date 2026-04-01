package ak.dev.khi_backend.user.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import ak.dev.khi_backend.user.model.TokenBlacklist;
import ak.dev.khi_backend.user.repo.TokenBlacklistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class TokenService {

    @Autowired
    private TokenBlacklistRepository tokenBlacklistRepository;

    // Method to check if a token is blacklisted
    public boolean isTokenBlacklisted(String token) {
        return tokenBlacklistRepository.findByToken(token).isPresent();
    }

    // Method to blacklist a token (typically called during logout)
    public void blacklistToken(String token) {
        TokenBlacklist tokenBlacklist = new TokenBlacklist();
        tokenBlacklist.setToken(token);
        tokenBlacklist.setBlacklistedAt(new Date());
        tokenBlacklist.setExpiresAt(getExpirationDateFromToken(token));
        tokenBlacklistRepository.save(tokenBlacklist);
    }

    // Get expiration date from the token (this assumes you're storing an expiration date in the token)
    public Date getExpirationDateFromToken(String token) {
        // Implement logic to extract expiration date from the token (e.g., from JWT claims)
        // For example:
        DecodedJWT decodedJWT = JWT.decode(token);
        return decodedJWT.getExpiresAt();
    }
}
