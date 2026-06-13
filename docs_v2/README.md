# KHI Backend — API Reference (v2)

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT + HttpOnly Cookie · Spring Security 6 · Bilingual CKB / KMR · Tiptap-aware media pipeline

A complete, per-module API reference for the KHI Backend, generated directly from the controller/DTO/entity source code. Every endpoint is grouped under its own module file with two clearly labelled sections — **Public API** (no authentication required) and **Internal API** (authentication / role required).

---

## 01 · Tech Stack

| Layer | Technology |
| --- | --- |
| Framework | Spring Boot 3.x |
| Security | Spring Security 6 + JJWT |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation (`@Valid` / `@Validated`) |
| Token Delivery | HttpOnly Secure cookie (`auth_token`) **and** `Authorization: Bearer …` header (both accepted by the JWT filter) |
| Password Hash | BCrypt |
| Storage | S3 (via shared `/api/v1/media/upload`) |
| Rich Text | Tiptap HTML stored in bilingual `body` / `description` columns |
| i18n | Bilingual CKB (Sorani) + KMR (Kurmanji); bilingual error envelope (`messageEn`, `messageKu`) |

---

## 02 · Base URL Map

| Group | Base Path | Notes |
| --- | --- | --- |
| Auth | `/api/auth/*` | login / register / reset / logout — mostly public |
| Sessions | `/api/auth/sessions/*` | authenticated only |
| User Profile (self-service) | `/api/user/*` | authenticated only |
| About | `/api/v1/about/*` | public GET, admin write |
| Contact | `/api/v1/contact/*` | public GET, authenticated write |
| Services | `/api/v1/services/*` | public GET, admin write |
| Projects | `/api/v1/projects/*` | public GET, role-gated write |
| News | `/api/v1/news/*` | public GET, role-gated write |
| Image Collections | `/api/v1/image-collections/*` | public GET, role-gated write |
| Sound Tracks | `/api/v1/sound-tracks/*` | public GET, authenticated write |
| Videos | `/api/v1/videos/*` | public GET, authenticated write |
| Writings (Books) | `/api/v1/writings/*` | public GET, role-gated write |
| Publishment Topics | `/api/v1/topics/*` | public GET, authenticated write |
| Media (shared upload) | `/api/v1/media/*` | authenticated only |
| Global Search | `/api/v1/search` | public |

---

## 03 · Module Index

Each module file documents every endpoint with HTTP method, path, authentication requirement, role gate, request schema (path / query / body / multipart parts), response schema, example request, example response, and error codes.

| # | Module | File | Endpoints | Public | Internal |
| --- | --- | --- | --- | --- | --- |
| 01 | Auth & Sessions | [`auth.md`](auth.md) | 10 | 5 | 5 |
| 02 | User Profile (self-service) | [`profile.md`](profile.md) | 6 | 0 | 6 |
| 03 | About | [`about.md`](about.md) | 5 | 2 | 3 |
| 04 | Contact | [`contact.md`](contact.md) | 7 | 4 | 3 |
| 05 | Services | [`services.md`](services.md) | 11 | 4 | 7 |
| 06 | Projects | [`projects.md`](projects.md) | 6 | 3 | 3 |
| 07 | News | [`news.md`](news.md) | 13 | 8 | 5 |
| 08 | Image Collections | [`image-collections.md`](image-collections.md) | 7 | 3 | 4 |
| 09 | Sound Tracks | [`soundtracks.md`](soundtracks.md) | 13 | 10 | 3 |
| 10 | Videos | [`videos.md`](videos.md) | 9 | 5 | 4 |
| 11 | Writings (Books) | [`writings.md`](writings.md) | 12 | 8 | 4 |
| 12 | Publishment Topics | [`topics.md`](topics.md) | 5 | 2 | 3 |
| 13 | Media (shared upload) | [`media.md`](media.md) | 3 | 0 | 3 |
| 14 | Global Search | [`search.md`](search.md) | 1 | 1 | 0 |

Counts mirror the actual controllers; endpoints with aliases (e.g. `News.deleteNewsAlt`) are counted as separate routes.

### Live API explorer

The same endpoints are exposed as a fully interactive **Swagger UI** at `/swagger-ui.html` (powered by springdoc-openapi v2). See [`swagger.md`](swagger.md) for the setup, try-it-out flow, and client-SDK generation.

| URL | Purpose |
| --- | --- |
| `/swagger-ui.html` | Interactive API explorer |
| `/v3/api-docs` | OpenAPI 3 JSON spec |
| `/v3/api-docs.yaml` | Same spec, YAML |
| `/v3/api-docs/public` | Group: endpoints reachable without a JWT |
| `/v3/api-docs/internal` | Group: endpoints that require a JWT |

---

## 04 · Authentication Model

The KHI Backend uses **stateless JWT** with two equally valid carriers — pick whichever fits the client:

1. **HttpOnly Secure cookie** `auth_token` — set automatically on successful `/api/auth/login`, `/api/auth/register`, `/api/auth/register-with-image`; cleared by `/api/auth/logout` and `/api/auth/logout-all`.
2. **Authorization header** `Authorization: Bearer <jwt>` — same token, useful for non-browser clients.

The JWT filter `JWTAuthenticationFilter` checks the header first, then falls back to the cookie. A successful login also writes a row into the `sessions` table (per-device); logout-all deactivates every session for the user and blacklists the current token immediately.

### Roles

Four roles defined in `Role.java`. Each carries a fine-grained set of `Permission` values:

| Role | Typical Use |
| --- | --- |
| `GUEST` | newly registered, no editorial access |
| `EMPLOYEE` | can create / update most content (projects, news, image-collections, writings) |
| `ADMIN` | EMPLOYEE rights + delete on those modules + about/services CRUD |
| `SUPER_ADMIN` | full access including user management |

See [`auth.md` §05](auth.md) for the complete permission matrix.

---

## 05 · Shared Response Envelope

Most controllers wrap successful responses in `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Human-readable status",
  "data":    { /* payload */ }
}
```

A few controllers (About, Videos, central Topics) return the raw entity / list / page directly without the envelope. Each module file calls out its own behaviour explicitly.

Paginated endpoints return Spring Data `Page<T>`:

```json
{
  "content": [ /* items */ ],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

---

## 06 · Shared Error Envelope (Bilingual)

Errors flow through the `GlobalExceptionHandler` and come back as `ApiErrorResponse`:

```json
{
  "timestamp":   "2026-05-31T09:30:11.482Z",
  "status":      404,
  "path":        "/api/v1/about/missing-slug",
  "method":      "GET",
  "traceId":     "8e2a…",
  "code":        "NOT_FOUND",
  "message":     "Localised message (Accept-Language)",
  "messageEn":   "About page not found",
  "messageKu":   "پەڕەی دەربارە نەدۆزرایەوە",
  "fieldErrors": null,
  "details":     null
}
```

Every response always carries both `messageEn` and `messageKu` so any frontend locale works. `code` values are defined in `ErrorCode.java` — common ones:

| Code | Meaning |
| --- | --- |
| `VALIDATION_ERROR` | `@Valid` / `@Validated` constraint failed |
| `NOT_FOUND` | resource missing |
| `UNAUTHORIZED` | wrong credentials / missing JWT |
| `FORBIDDEN` | authenticated but insufficient role |
| `ACCOUNT_LOCKED` | brute-force lockout active |
| `CONFLICT` | duplicate username / email / slug |
| `BAD_REQUEST` | malformed input |
| `PAYLOAD_TOO_LARGE` | upload exceeded size limit |
| `<MODULE>_*` | module-specific (e.g. `PROJECT_NOT_FOUND`, `NEWS_MEDIA_INVALID`) |

---

## 07 · Tiptap Media Pipeline (Shared)

Every content module that ships a bilingual rich-text body (`About`, `Contact`, `News`, `Projects`, `Services`, `SoundTrack`, `Video`, `Image`, `Writing`) uses the same upload flow:

1. Editor drops a file → frontend POSTs it to `POST /api/v1/media/upload` (`multipart/form-data`).
2. Backend uploads to S3 (folder hint from the `type` part — `image | audio | video | document | gallery`).
3. Response returns `fileUrl` → frontend inserts it into the Tiptap HTML.
4. Frontend submits the entity body as JSON with the URL already baked in.
5. On save, `TiptapHtmlProcessor` runs as a safety net: it rewrites any leftover inline base64 payload into an uploaded URL and sanitises tags.

See [`media.md`](media.md) for the full pipeline.

---

## 08 · How to Read a Module File

Every module file follows the same skeleton so you can scan them quickly:

1. **Module Overview** — base path, one-line purpose, endpoint summary table.
2. **Data Model(s)** — every JPA entity / embeddable / record involved, with `@Column` length, constraint, and description.
3. **Authentication & Roles** — endpoint-by-endpoint auth matrix.
4. **Public API** — endpoints reachable without a JWT.
5. **Internal API** — endpoints that require a JWT (and, where applicable, a specific role).
6. **DTO Reference** — every request / response DTO field-by-field, with validation annotations transcribed verbatim.
7. **Response Envelope** — module-specific wrapper / page shape.
8. **Error Responses** — common `ErrorCode` values for the module.
9. **Notes** — caveats, gotchas, and cross-references.

Each documented endpoint includes:

- HTTP method + path
- Description (inferred only from the controller code)
- Authentication requirement (Public / Required)
- Role gate (if any)
- Request parameters — path, query, body, or multipart parts — with validation
- Response status and JSON schema
- Example `curl` request
- Example response

---

## 09 · Conventions Used in This Doc Set

- **Bilingual fields** are always paired as `<field>Ckb` / `<field>Kmr`.
- **Slugs** must be URL-safe; CKB slug is required, KMR slug is optional.
- **`active`** boolean controls public visibility for content with separate admin/public surfaces.
- **`displayOrder`** integer controls list sort order.
- **Multipart `data` part** is always a JSON string of the request DTO, accompanying optional binary file parts.
- **Timestamps** are `Instant` / `LocalDateTime` set by `@CreationTimestamp` / `@UpdateTimestamp`.

---

## 10 · Source of Truth

Every fact in this doc set was extracted from the actual Java sources:

- Controllers under `src/main/java/ak/dev/khi_backend/**/api/**/*Controller.java` and `**/api/**/*API.java`
- DTOs under `src/main/java/ak/dev/khi_backend/**/dto/**/*.java`
- Entities under `src/main/java/ak/dev/khi_backend/**/model/**/*.java`
- Enums under `src/main/java/ak/dev/khi_backend/**/enums/**/*.java`
- `SecurityConfig.java` for authentication & role gates
- `GlobalExceptionHandler.java` + `ErrorCode.java` for error envelopes

Nothing has been invented or assumed. Where a controller path does not match a `SecurityConfig` matcher (e.g. `/api/v1/sound-tracks` vs the config's `/api/v1/soundtracks`), the doc surfaces this as a real caveat rather than glossing over it.
