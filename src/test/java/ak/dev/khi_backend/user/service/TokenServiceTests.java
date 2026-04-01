package ak.dev.khi_backend.user.service;

import ak.dev.khi_backend.user.jwt.JwtTokenProvider;
import ak.dev.khi_backend.user.model.Session;
import ak.dev.khi_backend.user.model.TokenBlacklist;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.repo.TokenBlacklistRepository;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTests {

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(tokenBlacklistRepository, sessionRepository, jwtTokenProvider);
    }

    @Test
    void considersTokenRevokedWhenSessionIsInactive() {
        Session session = Session.builder()
                .sessionId("session-1")
                .isActive(false)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(tokenBlacklistRepository.findByToken("jwt-token")).thenReturn(Optional.empty());
        when(jwtTokenProvider.getSessionIdFromToken("jwt-token")).thenReturn("session-1");
        when(sessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThat(tokenService.isTokenBlacklisted("jwt-token")).isTrue();
    }

    @Test
    void blacklistTokenStoresBlacklistEntryAndDeactivatesSession() {
        Session session = Session.builder()
                .sessionId("session-2")
                .isActive(true)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Instant expiration = Instant.now().plusSeconds(600);

        when(tokenBlacklistRepository.findByToken("jwt-token")).thenReturn(Optional.empty());
        when(jwtTokenProvider.decodeToken("jwt-token")).thenReturn(decodedJWT);
        when(decodedJWT.getExpiresAtAsInstant()).thenReturn(expiration);
        when(jwtTokenProvider.getSessionIdFromToken("jwt-token")).thenReturn("session-2");
        when(sessionRepository.findBySessionId("session-2")).thenReturn(Optional.of(session));
        when(tokenBlacklistRepository.save(any(TokenBlacklist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tokenService.blacklistToken("jwt-token");

        ArgumentCaptor<TokenBlacklist> blacklistCaptor = ArgumentCaptor.forClass(TokenBlacklist.class);
        verify(tokenBlacklistRepository).save(blacklistCaptor.capture());
        assertThat(blacklistCaptor.getValue().getToken()).isEqualTo("jwt-token");

        verify(sessionRepository).save(session);
        assertThat(session.getIsActive()).isFalse();
        assertThat(session.getLogoutTimestamp()).isNotNull();
    }
}

