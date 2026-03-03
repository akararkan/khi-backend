package ak.dev.khi_backend.user.configs;

import ak.dev.khi_backend.user.oauth2.CustomOAuth2UserService;
import ak.dev.khi_backend.user.oauth2.OAuth2AuthenticationFailureHandler;
import ak.dev.khi_backend.user.oauth2.OAuth2AuthenticationSuccessHandler;
import ak.dev.khi_backend.user.jwt.JWTAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTAuthenticationFilter          jwtAuthenticationFilter;
    private final AuthenticationProvider           authenticationProvider;
    private final CustomOAuth2UserService          customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)

                // OAuth2 state/code exchange needs a short-lived session;
                // JWT takes over for all subsequent requests.
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(auth -> auth

                        // ── Preflight ──────────────────────────────────────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Public auth & OAuth2 endpoints ─────────────────────────
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/users/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // ── Self-service profile (any authenticated user) ──────────
                        // GET  /api/user/me
                        // PUT  /api/user/profile
                        // PUT  /api/user/password
                        // POST /api/user/profile-image
                        // DEL  /api/user/profile-image
                        // DEL  /api/user/account
                        .requestMatchers("/api/user/**").authenticated()

                        // ── Admin: user management ────────────────────────────────
                        .requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/auth/sessions/**").hasRole("SUPER_ADMIN")

                        // ── Content: public reads ─────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()

                        // ── Content: writes require EMPLOYEE+ ────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        // ── Content: deletes require ADMIN+ ──────────────────────
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Everything else: must be authenticated ────────────────
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ep -> ep.baseUri("/oauth2/authorize"))
                        .redirectionEndpoint(ep -> ep.baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(ui -> ui.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",   // Vite dev server
                "https://yourdomain.com"  // production — replace with real domain
        ));
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}