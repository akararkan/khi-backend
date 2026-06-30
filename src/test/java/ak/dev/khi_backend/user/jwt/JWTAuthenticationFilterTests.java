package ak.dev.khi_backend.user.jwt;

import ak.dev.khi_backend.user.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JWTAuthenticationFilterTests {

    private JwtTokenProvider jwtTokenProvider;
    private UserDetailsService userDetailsService;
    private TokenService tokenService;
    private JwtCookieService jwtCookieService;
    private JWTAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(UserDetailsService.class);
        tokenService = mock(TokenService.class);
        jwtCookieService = mock(JwtCookieService.class);
        filter = new JWTAuthenticationFilter(
                jwtTokenProvider,
                userDetailsService,
                tokenService,
                jwtCookieService
        );
    }

    @Test
    void loginIgnoresExistingBearerTokenAndContinuesToController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtTokenProvider, userDetailsService, tokenService, jwtCookieService);
    }

    @Test
    void publicAuthEndpointIsRecognizedBehindAContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backend/api/auth/login");
        request.setContextPath("/backend");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void protectedAuthEndpointsStillRequireJwtFiltering() {
        MockHttpServletRequest logout = new MockHttpServletRequest("POST", "/api/auth/logout");
        MockHttpServletRequest sessions = new MockHttpServletRequest("GET", "/api/auth/sessions/getAllSessions");

        assertThat(filter.shouldNotFilter(logout)).isFalse();
        assertThat(filter.shouldNotFilter(sessions)).isFalse();
    }
}
