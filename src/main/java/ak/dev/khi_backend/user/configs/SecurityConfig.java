package ak.dev.khi_backend.user.configs;

import ak.dev.khi_backend.user.jwt.JWTAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ✅ IMPORTANT: enable CORS here (Spring Security otherwise blocks it)
                .cors(Customizer.withDefaults())

                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(auth -> auth

                        // ✅ Preflight requests must be allowed
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // =========================
                        // PUBLIC AUTH
                        // =========================
                        .requestMatchers("/api/auth/**", "/api/users/auth/**").permitAll()

                        // =========================
                        // SUPER ADMIN: users control
                        // =========================
                        .requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/auth/sessions/**").hasRole("SUPER_ADMIN")

                        // =========================
                        // CONTENT MODELS
                        // GET: GUEST + EMPLOYEE + ADMIN + SUPER_ADMIN
                        // =========================
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("GUEST", "EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        // POST: EMPLOYEE+
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        // PUT: EMPLOYEE+
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN", "SUPER_ADMIN")

                        // DELETE: ADMIN+
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/projects/**",
                                "/api/v1/news/**",
                                "/api/v1/films/**",
                                "/api/v1/image-collections/**",
                                "/api/v1/soundtracks/**",
                                "/api/v1/albums/**",
                                "/api/v1/writings/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // everything else requires login
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
