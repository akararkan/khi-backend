package ak.dev.khi_backend.khi_app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 + Swagger UI configuration for the KHI Backend.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Swagger UI    → {@code /swagger-ui.html} (redirects to {@code /swagger-ui/index.html})</li>
 *   <li>OpenAPI JSON  → {@code /v3/api-docs}</li>
 *   <li>OpenAPI YAML  → {@code /v3/api-docs.yaml}</li>
 * </ul>
 *
 * <p>Two security schemes are registered so endpoints can be exercised either with a
 * {@code Authorization: Bearer <jwt>} header or with the {@code auth_token} HttpOnly cookie —
 * matching {@code JWTAuthenticationFilter}.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String COOKIE_SCHEME = "cookieAuth";

    @Bean
    public OpenAPI khiOpenApi() {
        return new OpenAPI()
                .info(buildInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("https://api.khi.local").description("Production (placeholder)")
                ))
                .tags(buildTags())
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT returned by POST /api/auth/login or /api/auth/register."))
                        .addSecuritySchemes(COOKIE_SCHEME, new SecurityScheme()
                                .name("auth_token")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("HttpOnly cookie set automatically by /api/auth/login. " +
                                        "Browsers send it on every same-site request; tools like curl must set it explicitly.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }

    private Info buildInfo() {
        return new Info()
                .title("KHI Backend API")
                .version("v1")
                .description("""
                        Bilingual (CKB / KMR) content platform — Spring Boot 3 + JWT + S3-backed Tiptap media.

                        ## Authentication
                        Two equivalent carriers:
                        1. `Authorization: Bearer <jwt>` header
                        2. `auth_token` HttpOnly cookie (set automatically on /api/auth/login)

                        Use the **Authorize** button (top right) to set a Bearer token for try-it-out.

                        ## Response envelopes
                        Most endpoints wrap successful responses in `ApiResponse<T>` (`success`, `message`, `data`).
                        Errors come back as `ApiErrorResponse` with bilingual `messageEn` / `messageKu`.

                        ## Modules
                        Endpoints are grouped by module under the **Tags** panel below.
                        """)
                .contact(new Contact()
                        .name("ak.dev")
                        .email("akararkan@example.com")
                        .url("https://ak.dev"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://ak.dev"));
    }

    private List<Tag> buildTags() {
        return List.of(
                new Tag().name("Auth").description("Registration, login, password reset, logout, token blacklist"),
                new Tag().name("Sessions").description("Per-device session listing and revocation"),
                new Tag().name("User Profile").description("Self-service profile: name, username, password, avatar, account deletion"),
                new Tag().name("About").description("Bilingual About pages with Tiptap bodies and structured stats"),
                new Tag().name("Contact").description("Bilingual Contact pages with Tiptap descriptions"),
                new Tag().name("Services").description("Bilingual service catalogue with soft-active and type filtering"),
                new Tag().name("Projects").description("Bilingual projects with content blocks, tags, keywords, audit logs"),
                new Tag().name("News").description("Bilingual news articles with category taxonomy and multi-axis search"),
                new Tag().name("Image Collections").description("Bilingual image collections with album items and topic taxonomy"),
                new Tag().name("Sound Tracks").description("Bilingual sound tracks (SINGLE / MULTI) with files, brochures, attachments"),
                new Tag().name("Videos").description("Bilingual videos with multipart uploads and co-located topic CRUD"),
                new Tag().name("Writings").description("Bilingual books with multi-genre, series linking, multi-axis search"),
                new Tag().name("Publishment Topics").description("Central VIDEO / SOUND / IMAGE / WRITING taxonomy"),
                new Tag().name("Media").description("Shared S3 upload pipeline used by every Tiptap editor"),
                new Tag().name("Global Search").description("One endpoint searching every content type at once")
        );
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public API (no auth)")
                .pathsToMatch(
                        "/api/auth/register",
                        "/api/auth/register-with-image",
                        "/api/auth/login",
                        "/api/auth/reset-token",
                        "/api/auth/reset-password",
                        "/api/v1/about/**",
                        "/api/v1/contact/**",
                        "/api/v1/services/**",
                        "/api/v1/projects/**",
                        "/api/v1/news/**",
                        "/api/v1/image-collections/**",
                        "/api/v1/sound-tracks/**",
                        "/api/v1/videos/**",
                        "/api/v1/writings/**",
                        "/api/v1/topics/**",
                        "/api/v1/search/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal API (JWT required)")
                .pathsToMatch(
                        "/api/auth/logout",
                        "/api/auth/logout-all",
                        "/api/auth/sessions/**",
                        "/api/user/**",
                        "/api/v1/media/**")
                .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All endpoints")
                .pathsToMatch("/api/**")
                .build();
    }
}
