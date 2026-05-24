package ak.dev.khi_backend.user.configs;

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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider  authenticationProvider;
    private final AppCorsProperties       corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(auth -> auth

                        // ── Preflight ──────────────────────────────────────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Session management ─────────────────────────────────────
                        .requestMatchers("/api/auth/sessions/**").authenticated()

                        // ── Logout ─────────────────────────────────────────────────
                        .requestMatchers("/api/auth/logout", "/api/auth/logout-all").authenticated()

                        // ── Public auth endpoints ──────────────────────────────────
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/register-with-image",
                                "/api/auth/login",
                                "/api/auth/reset-token",
                                "/api/auth/reset-password",
                                "/api/users/auth/**"
                        ).permitAll()

                        // ── Self-service profile ───────────────────────────────────
                        .requestMatchers("/api/user/**").authenticated()

                        // ── Admin: user management ────────────────────────────────
                        .requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")

                        // ── About Page: public reads, admin writes ────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/about/**").permitAll()
                        .requestMatchers("/api/v1/about/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Services: public reads, admin writes ──────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/services/**").permitAll()
                        .requestMatchers("/api/v1/services/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Content: public reads ─────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()

                        // ── Content: writes require EMPLOYEE+ ────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/projects/**", "/api/v1/news/**", "/api/v1/films/**",
                                "/api/v1/image-collections/**", "/api/v1/soundtracks/**",
                                "/api/v1/albums/**", "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/projects/**", "/api/v1/news/**", "/api/v1/films/**",
                                "/api/v1/image-collections/**", "/api/v1/soundtracks/**",
                                "/api/v1/albums/**", "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        // ── Content: deletes require ADMIN+ ──────────────────────
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/projects/**", "/api/v1/news/**", "/api/v1/films/**",
                                "/api/v1/image-collections/**", "/api/v1/soundtracks/**",
                                "/api/v1/albums/**", "/api/v1/writings/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginsList());
        configuration.setAllowedMethods(corsProperties.getAllowedMethodsList());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeadersList());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
