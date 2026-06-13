# OpenAPI 3 & Swagger UI

> **springdoc-openapi 2.8.6** · OpenAPI 3.1 spec auto-generated from the controllers + DTOs + Spring Security config.

The KHI Backend ships a live, browsable, try-it-out API explorer powered by springdoc-openapi. No separate spec file is maintained — the spec is regenerated from the source on every restart so it can never drift from the code.

---

## 01 · Endpoints

| Surface | URL | Purpose |
| --- | --- | --- |
| Swagger UI | [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) | Interactive API explorer |
| OpenAPI JSON | [`/v3/api-docs`](http://localhost:8080/v3/api-docs) | Machine-readable spec |
| OpenAPI YAML | [`/v3/api-docs.yaml`](http://localhost:8080/v3/api-docs.yaml) | Same spec, YAML flavour |
| Public-only group | `/v3/api-docs/public` | Endpoints reachable without a JWT |
| Internal-only group | `/v3/api-docs/internal` | Endpoints that require a JWT |
| All endpoints | `/v3/api-docs/all` | Everything in one document |

The three "groups" are also available in Swagger UI via the dropdown at the top of the page.

---

## 02 · Authentication in Swagger UI

Two security schemes are registered, mirroring the JWT filter:

1. **bearerAuth** — `Authorization: Bearer <jwt>` header.
2. **cookieAuth** — `auth_token` HttpOnly cookie (set automatically on login).

### Try-it-out flow

1. Open `/swagger-ui.html`.
2. Expand **Auth → `POST /api/auth/login`** and click **Try it out**.
3. Send a login payload — copy the returned `token`.
4. Click the green **Authorize** button (top right).
5. Paste the token into the `bearerAuth` field, click **Authorize**, then **Close**.
6. The token is now attached to every subsequent request **and** persisted across page reloads (we set `springdoc.swagger-ui.persist-authorization=true`).

Cookie-based auth works automatically if you are logged in via the frontend on the same origin — Swagger UI will send the cookie alongside its requests.

---

## 03 · Tag-based Module Grouping

Every controller carries an `@Tag` annotation so Swagger UI groups endpoints by module. The 15 tags match the per-module docs in this folder:

| Tag | Controller | Doc |
| --- | --- | --- |
| Auth | `UserAPI` | [`auth.md`](auth.md) |
| Sessions | `SessionAPI` | [`auth.md` §07](auth.md) |
| User Profile | `UserProfileAPI` | [`profile.md`](profile.md) |
| About | `AboutController` | [`about.md`](about.md) |
| Contact | `ContactController` | [`contact.md`](contact.md) |
| Services | `ServiceController` | [`services.md`](services.md) |
| Projects | `ProjectController` | [`projects.md`](projects.md) |
| News | `NewsController` | [`news.md`](news.md) |
| Image Collections | `ImageCollectionController` | [`image-collections.md`](image-collections.md) |
| Sound Tracks | `SoundTrackController` | [`soundtracks.md`](soundtracks.md) |
| Videos | `VideoController` | [`videos.md`](videos.md) |
| Writings | `WritingController` | [`writings.md`](writings.md) |
| Publishment Topics | `PublishmentTopicController` | [`topics.md`](topics.md) |
| Media | `MediaController` | [`media.md`](media.md) |
| Global Search | `GlobalSearchController` | [`search.md`](search.md) |

Tags are sorted alphabetically (`tagsSorter: alpha`); operations inside each tag are sorted by HTTP method (`operationsSorter: method`).

---

## 04 · What Springdoc Picks Up Automatically

Without any per-method annotation, springdoc infers the following from the existing code:

| Source | Surfaced as |
| --- | --- |
| `@RequestMapping` / `@GetMapping` / `@PostMapping` etc. | path + HTTP method |
| Method parameters with `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestPart` | parameter location and required/optional |
| DTO field annotations (`@NotNull`, `@Size`, `@Email`, `@Pattern`, `@JsonIgnore`, ...) | request / response schema with validation |
| Return type (`ResponseEntity<ApiResponse<T>>`, `Page<T>`, raw DTO) | response schema |
| Javadoc on the method (when source is on the classpath) | operation description |
| `@RestControllerAdvice` exception handlers | `4xx` / `5xx` response shapes (`ApiErrorResponse`) |

The result is a fully populated OpenAPI 3 document on first boot — no manual schema upkeep required.

---

## 05 · Adding Richer Annotations (Optional)

When a description is non-obvious, add an `@Operation` on the method:

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Operation(
    summary     = "Create a new About page",
    description = "Bilingual page with Tiptap HTML bodies. Requires ADMIN or SUPER_ADMIN.")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Page created"),
    @ApiResponse(responseCode = "409", description = "slugCkb already exists"),
    @ApiResponse(responseCode = "403", description = "Caller is not ADMIN+")
})
@PostMapping
public ResponseEntity<AboutDTOs.AboutResponse> create(@RequestBody AboutDTOs.AboutRequest request) { ... }
```

For DTOs, `@Schema(description = "…", example = "…")` on each field is the lowest-effort win.

---

## 06 · Generating Client SDKs

The OpenAPI spec at `/v3/api-docs` feeds every major client generator:

```bash
# TypeScript client via openapi-generator
npx @openapitools/openapi-generator-cli generate \
    -i http://localhost:8080/v3/api-docs \
    -g typescript-axios \
    -o ./gen/khi-client

# Or fetch the YAML form for offline use
curl -o khi-openapi.yaml http://localhost:8080/v3/api-docs.yaml
```

Other useful generators: `typescript-fetch`, `dart-dio`, `kotlin`, `swift5`, `java`, `python`.

---

## 07 · Configuration Summary

The behaviour above is controlled by these settings:

`pom.xml`:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

`application.yaml`:
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method
    tagsSorter: alpha
    persist-authorization: true
    deep-linking: true
    display-request-duration: true
    doc-expansion: none
  packages-to-scan: ak.dev.khi_backend
  paths-to-match: /api/**
  show-actuator: false
```

`SecurityConfig.java` — Swagger paths are whitelisted before any authentication rules apply:
```java
.requestMatchers(
        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
        "/swagger-ui.html", "/swagger-ui/**",
        "/swagger-resources/**", "/webjars/**"
).permitAll()
```

`OpenApiConfig.java` — declares the bilingual `@Info` block, both security schemes, the server list, the 15 module `Tag`s, and three `GroupedOpenApi` views (`public`, `internal`, `all`).

---

## 08 · Production Notes

- **Public exposure**: Swagger UI is permitted publicly in `SecurityConfig`. For production, either restrict the path via a reverse proxy or gate the `/v3/api-docs/**` + `/swagger-ui/**` matchers behind an admin role.
- **CORS**: the existing `AppCorsProperties` (allowed origins list) applies to `/v3/api-docs` as well, so cross-origin client generators just work from whitelisted origins.
- **Servers list**: `OpenApiConfig` ships with `http://localhost:8080` and a placeholder `https://api.khi.local`. Replace the second URL with your real production host before going live.
- **OAuth2 hooks**: if you add OAuth2 later, register a third `SecurityScheme` of type `OAUTH2` in `OpenApiConfig.khiOpenApi()` — no other code changes are required.
