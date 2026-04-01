package ak.dev.khi_backend.user.jwt;

import ak.dev.khi_backend.user.configs.JwtCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieServiceTests {

    @Test
    void addsConfiguredAuthCookie() {
        JwtCookieProperties properties = new JwtCookieProperties();
        properties.setCookieName("khi_auth_token");
        properties.setCookieHttpOnly(true);
        properties.setCookieSecure(false);
        properties.setCookieSameSite("Strict");
        properties.setCookiePath("/");
        properties.setCookieMaxAge(3600);

        JwtCookieService service = new JwtCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.addAuthCookie(response, "jwt-value");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("khi_auth_token=jwt-value");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Max-Age=3600");
    }

    @Test
    void resolvesCookieTokenFromRequest() {
        JwtCookieProperties properties = new JwtCookieProperties();
        properties.setCookieName("khi_auth_token");
        JwtCookieService service = new JwtCookieService(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("khi_auth_token", "cookie-token"));

        assertThat(service.resolveToken(request)).isEqualTo("cookie-token");
    }

    @Test
    void clearsAuthCookieWithZeroMaxAge() {
        JwtCookieProperties properties = new JwtCookieProperties();
        properties.setCookieName("khi_auth_token");
        JwtCookieService service = new JwtCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearAuthCookie(response);

        assertThat(response.getHeader("Set-Cookie")).contains("khi_auth_token=");
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }
}

