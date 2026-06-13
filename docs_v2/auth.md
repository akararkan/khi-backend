# Auth & Session Module

> Spring Boot 3 authentication and session-management subsystem. Issues JJWT-signed JSON Web Tokens, persists a dedicated `sessions` row per device login, blacklists revoked tokens in a `token_blacklist` table, hashes credentials with BCrypt, and locks accounts after repeated failures (`failedAttempts`, `lockTime`, `LOCK_DURATION_MINUTES`). Tokens are delivered both as an `HttpOnly` cookie named `auth_token` and an optional `Authorization: Bearer …` header — the JWT filter accepts either.

## Table of Contents
- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — User](#02--data-model--user)
- [03 · Data Model — Session](#03--data-model--session)
- [04 · Data Model — TokenBlacklist](#04--data-model--tokenblacklist)
- [05 · Roles & Permissions](#05--roles--permissions)
- [06 · Public API](#06--public-api)
- [07 · Internal API (Authenticated)](#07--internal-api-authenticated)
- [08 · DTO Reference](#08--dto-reference)
- [09 · Error Responses](#09--error-responses)
- [10 · Notes](#10--notes)

---

## 01 · Module Overview

- **Base paths**: `/api/auth/*` (controller `UserAPI`), `/api/auth/sessions/*` (controller `SessionAPI`)
- **Tech stack**:
  - Spring Security 6 (`@EnableWebSecurity`, `@EnableMethodSecurity`, `SessionCreationPolicy.STATELESS`)
  - JJWT (signed `Token` payload, see `ak.dev.khi_backend.user.jwt.Token`)
  - BCrypt password hashing (via `AuthenticationProvider` bean injected into `SecurityConfig`)
  - Spring Data JPA (`User`, `Session`, `TokenBlacklist` entities)
  - Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Email`, `@Pattern`)
- **Token transport**: A successful register/login attaches an `HttpOnly` cookie `auth_token` via `JwtCookieService.addAuthCookie(...)`. Subsequent requests may present that cookie OR send `Authorization: Bearer <token>` — the controller helper `extractToken(request, authorizationHeader)` prefers the header when present, otherwise falls back to `jwtCookieService.resolveToken(request)`.
- **CSRF**: disabled (`csrf(AbstractHttpConfigurer::disable)`) — stateless API.
- **Sessions**: Spring HTTP session is `STATELESS`; our own `Session` entity tracks per-device login records in DB.

### Endpoint Summary

| Section | Method | Path | Auth |
| --- | --- | --- | --- |
| Public | `POST` | `/api/auth/register` | Public |
| Public | `POST` | `/api/auth/register-with-image` | Public |
| Public | `POST` | `/api/auth/login` | Public |
| Public | `POST` | `/api/auth/reset-token` | Public |
| Public | `POST` | `/api/auth/reset-password` | Public |
| Internal | `POST` | `/api/auth/logout` | Authenticated |
| Internal | `POST` | `/api/auth/logout-all` | Authenticated |
| Internal | `GET` | `/api/auth/sessions/getAllSessions` | Authenticated |
| Internal | `DELETE` | `/api/auth/sessions/{sessionId}` | Authenticated |
| Internal | `DELETE` | `/api/auth/sessions/revokeAll` | Authenticated |

---

## 02 · Data Model — User

JPA entity `ak.dev.khi_backend.user.model.User` — implements `org.springframework.security.core.userdetails.UserDetails` and `java.io.Serializable`.

**Table**: `users_tbl`
**Unique constraints**:
- `uk_users_username` on `username`
- `uk_users_email` on `email`

| Field | DB Column | Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `userId` | (PK) | `Long` | `@Id`, `IDENTITY` | Primary key. |
| `name` | `name` | `String` | `nullable = false`, `length = 120` | Display name. |
| `profileImage` | `profile_image` | `String` | `length = 500` | Optional profile image URL/path. |
| `username` | `username` | `String` | `nullable = false`, `unique = true`, `length = 80` | Login handle. |
| `email` | `email` | `String` | `nullable = false`, `unique = true`, `length = 160` | Login email. |
| `password` | `password` | `String` | `nullable = false`, `length = 255` | BCrypt hash. **`@JsonIgnore`** — never serialized. |
| `role` | `role` | `Role` (enum) | `EnumType.STRING`, `nullable = false`, `length = 30` | Authorization role. |
| `pincode` | `pincode` | `Long` | `length = 6` | Optional numeric pincode. |
| `isActivated` | `is_activated` | `Boolean` | `nullable = false`, default `true` | Account enabled flag (drives `isEnabled()`). |
| `resetToken` | `reset_token` | `String` | `length = 120` | **`@JsonIgnore`** — opaque password-reset token. |
| `resetTokenExpiration` | `reset_token_expiration` | `Instant` | — | **`@JsonIgnore`** — expiry instant for `resetToken`. |
| `createdAt` | `created_at` | `Instant` | — | Audit: creation timestamp. |
| `updatedAt` | `updated_at` | `Instant` | — | Audit: last-update timestamp. |
| `failedAttempts` | `failed_attempts` | `int` | `nullable = false`, default `0` | Count of consecutive failed logins. |
| `lockTime` | `lock_time` | `Instant` | — | **`@JsonIgnore`** — moment the lock began. |
| `isLocked` | `is_locked` | `Boolean` | `nullable = false`, default `false` | Lock flag. |
| `passwordExpiryDate` | `password_expiry_date` | `Instant` | — | If non-null and in the past, credentials are expired. |

### `UserDetails` methods

| Method | Behavior |
| --- | --- |
| `getAuthorities()` | Returns `role.getAuthorities()` — permission strings plus `ROLE_<NAME>`. |
| `isAccountNonExpired()` | Always `true`. |
| `isAccountNonLocked()` | If `isLocked == true` and `lockTime != null`, unlocks once `(now - lockTime) > LOCK_DURATION_MINUTES * 60 * 1000` ms. Otherwise `!isLocked`. |
| `isCredentialsNonExpired()` | `!isPasswordExpired()` — true unless `passwordExpiryDate` is set and in the past. |
| `isEnabled()` | `Boolean.TRUE.equals(isActivated)`. |
| `isPasswordExpired()` *(helper)* | `passwordExpiryDate != null && passwordExpiryDate.isBefore(Instant.now())`. |

---

## 03 · Data Model — Session

JPA entity `ak.dev.khi_backend.user.model.Session`.

**Table**: `sessions`

| Field | DB Column | Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | (PK) | `Long` | `@Id`, `GenerationType.AUTO` | Primary key. |
| `sessionId` | `session_id` | `String` | `unique = true`, `nullable = false` | UUID/unique identifier surfaced to clients. |
| `user` | `user_id` (FK) | `User` | `@ManyToOne(LAZY)`, `nullable = false` | Owner. |
| `deviceInfo` | `device_info` | `String` | — | User-Agent / device details. |
| `ipAddress` | `ip_address` | `String` | — | Client IP at login time. |
| `loginTimestamp` | `login_timestamp` | `Instant` | `nullable = false` | Session creation time. |
| `expiresAt` | `expires_at` | `Instant` | `nullable = false` | Session expiry time. |
| `isActive` | `is_active` | `Boolean` | `nullable = false`, default `true` | Active flag; cleared on logout/revoke. |
| `logoutTimestamp` | `logout_timestamp` | `Instant` | — | When the session ended (logout/revoke). |

A new `Session` row is created on every successful login (per device). Logout, `logout-all`, single-session revoke, and revoke-all all set `isActive = false` and stamp `logoutTimestamp = Instant.now()`.

---

## 04 · Data Model — TokenBlacklist

JPA entity `ak.dev.khi_backend.user.model.TokenBlacklist`.

**Table**: `token_blacklist`

| Field | DB Column | Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | (PK) | `Long` | `@Id`, `IDENTITY` | Primary key. |
| `token` | `token` | `String` | `nullable = false`, `unique = true`, `length = 512` | The blacklisted JWT (verbatim). |
| `blacklistedAt` | `blacklisted_at` | `Instant` | `nullable = false` | When the token was revoked. |
| `expiresAt` | `expires_at` | `Instant` | `nullable = false` | Token's own JWT expiry — entries can be purged after this. |

Any token added here is rejected at the JWT filter layer.

---

## 05 · Roles & Permissions

### Roles (`ak.dev.khi_backend.user.enums.Role`)

Stored as `EnumType.STRING`. Default for self-registered users is `GUEST`.

| Role | Granted Permissions |
| --- | --- |
| `GUEST` | `USER_READ` |
| `EMPLOYEE` | `USER_CREATE`, `USER_READ`, `USER_UPDATE` |
| `ADMIN` | `USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE` |
| `SUPER_ADMIN` | `USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE` |

`Role.getAuthorities()` emits each permission string PLUS a `ROLE_<NAME>` authority (e.g. `ROLE_ADMIN`) — that's what Spring Security's `hasRole(...)` checks against.

### Permissions (`ak.dev.khi_backend.user.enums.Permission`)

| Enum Constant | Authority String |
| --- | --- |
| `USER_CREATE` | `user:create` |
| `USER_READ` | `user:read` |
| `USER_UPDATE` | `user:update` |
| `USER_DELETE` | `user:delete` |

---

## 06 · Public API

All endpoints below are explicitly listed under `permitAll()` in `SecurityConfig` (or under the public branch for auth endpoints). No token is required to call them.

---

### POST `/api/auth/register`

Register a new user from a JSON payload. On success, returns a `Token` body and attaches the `auth_token` HttpOnly cookie.

- **Authentication**: Public
- **Content-Type**: `application/json`
- **Body**: `RegisterRequestDTO`

| Field | Type | Validation |
| --- | --- | --- |
| `name` | `String` | `@NotBlank` "Name is required"; `@Size(max = 120)` "Name must not exceed 120 characters". |
| `username` | `String` | `@NotBlank`; `@Size(min = 3, max = 80)`; `@Pattern(regexp = "^[A-Za-z0-9_]+$")` "Username can contain only letters, numbers, and underscores". |
| `email` | `String` | `@NotBlank`; `@Email(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$")`; `@Size(max = 160)`. |
| `password` | `String` | `@NotBlank`; `@Size(min = 6, max = 128)`. |
| `pincode` | `Long` | optional. |

- **Response** `200 OK` — body of type `Token`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGFuLmsiLCJpZCI6NDIsImlhdCI6MTcxNzAwMDAwMCwiZXhwIjoxNzE3MDg2NDAwfQ.signature",
  "response": "Registered successfully"
}
```

- **Side effects**:
  - Creates a `users_tbl` row (role defaults to `GUEST`).
  - Inserts a `sessions` row for this device.
  - Sets `Set-Cookie: auth_token=...; HttpOnly`.

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/register' \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: en' \
  -d '{
        "name": "Alan Karam",
        "username": "alan_k",
        "email": "alan.k@example.com",
        "password": "Str0ngPass!",
        "pincode": 482910
      }'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: auth_token=eyJhbGciOiJIUzI1NiJ9...; HttpOnly; Path=/; Secure; SameSite=Strict

{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGFuX2siLCJpZCI6NDIsImlhdCI6MTcxNzAwMDAwMCwiZXhwIjoxNzE3MDg2NDAwfQ.k7s...",
  "response": "Registered successfully"
}
```

---

### POST `/api/auth/register-with-image`

Register a new user, optionally uploading a profile image. Multipart variant of `/register`.

- **Authentication**: Public
- **Content-Type**: `multipart/form-data`
- **Parts**:
  - `data` (required) — JSON `RegisterRequestDTO` (same validation rules as above).
  - `image` (optional) — `MultipartFile` profile image.

- **Response** `200 OK` — `Token` (same shape as `/register`). Sets `auth_token` HttpOnly cookie.

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/register-with-image' \
  -H 'Accept-Language: ku' \
  -F 'data={"name":"Alan Karam","username":"alan_k","email":"alan.k@example.com","password":"Str0ngPass!","pincode":482910};type=application/json' \
  -F 'image=@/Users/alan/avatar.png'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: auth_token=eyJhbGciOiJIUzI1NiJ9...; HttpOnly

{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "response": "Registered successfully"
}
```

- **Side effects**: same as `/register`, plus the uploaded image is stored and its location written to `User.profileImage`.

---

### POST `/api/auth/login`

Authenticate with `username` OR `email` and a password. On success, returns a `Token` and attaches the `auth_token` HttpOnly cookie.

- **Authentication**: Public
- **Content-Type**: `application/json`
- **Body**: `LoginRequestDTO`

| Field | Type | Validation |
| --- | --- | --- |
| `username` | `String` | `@NotBlank` "Username or email is required"; `@Size(max = 160)` "Username or email is too long". May be either a username OR an email. |
| `password` | `String` | `@NotBlank` "Password is required"; `@Size(min = 6, max = 128)` "Password must be at least 6 characters". |

- **Response** `200 OK` — `Token`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "response": "Login successful"
}
```

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/login' \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: en' \
  -d '{
        "username": "alan.k@example.com",
        "password": "Str0ngPass!"
      }'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: auth_token=eyJhbGciOiJIUzI1NiJ9...; HttpOnly; Path=/; Secure; SameSite=Strict

{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGFuX2siLCJpZCI6NDIsImlhdCI6MTcxNzAwMDAwMCwiZXhwIjoxNzE3MDg2NDAwfQ.k7s...",
  "response": "Login successful"
}
```

- **Side effects**:
  - On success: resets `failedAttempts` to 0; creates a new `sessions` row; sets `auth_token` cookie.
  - On failure: increments `failedAttempts`; if it reaches `SecurityConstants.MAX_FAILED_ATTEMPTS` (`5`), sets `isLocked = true` and stamps `lockTime = now`. Locked accounts auto-unlock after `LOCK_DURATION_MINUTES` (`1`).

---

### POST `/api/auth/reset-token`

Issue a password-reset token to the email on file.

- **Authentication**: Public
- **Content-Type**: query parameter (no JSON body)
- **Query parameters**:

| Param | Type | Validation |
| --- | --- | --- |
| `email` | `String` | `@NotBlank` "Email is required"; `@Email(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$")` "Email must be a valid address with a domain (e.g. user@example.com)"; `@Size(max = 160)` "Email must not exceed 160 characters". |

- **Response** `200 OK` — `String` body (human-readable confirmation).

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/reset-token?email=alan.k%40example.com' \
  -H 'Accept-Language: en'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Password reset token sent to alan.k@example.com
```

- **Side effects**: persists an opaque reset token + expiry on `users_tbl.reset_token` / `reset_token_expiration` and (typically) emails it to the user.

---

### POST `/api/auth/reset-password`

Consume a previously issued reset token to set a new password.

- **Authentication**: Public
- **Content-Type**: `application/json`
- **Body**: `PasswordResetRequestDTO`

| Field | Type | Validation |
| --- | --- | --- |
| `email` | `String` | `@NotBlank` "Email is required"; `@Email(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$")`; `@Size(max = 160)`. |
| `resetToken` | `String` | `@NotBlank` "Reset token is required". |
| `newPassword` | `String` | `@NotBlank` "New password is required"; `@Size(min = 6, max = 128)` "New password must be at least 6 characters". |
| `confirmPassword` | `String` | `@NotBlank` "Confirm password is required"; `@Size(min = 6, max = 128)` "Confirm password must be at least 6 characters". |

- **Response** `200 OK` — `String` body.

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/reset-password' \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: en' \
  -d '{
        "email": "alan.k@example.com",
        "resetToken": "f4d2b88a-71f3-4c89-9c8c-1a55d9a7e0d6",
        "newPassword": "N3wStr0ng!",
        "confirmPassword": "N3wStr0ng!"
      }'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Password reset successfully
```

- **Side effects**: clears `resetToken`/`resetTokenExpiration`, writes a fresh BCrypt hash to `password`.

---

## 07 · Internal API (Authenticated)

All endpoints in this section require a valid, non-blacklisted JWT — passed either via the `auth_token` HttpOnly cookie OR `Authorization: Bearer …` header. Any authenticated role is sufficient (`GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`).

---

### POST `/api/auth/logout`

Invalidate the current token and clear the cookie.

- **Authentication**: Required (JWT cookie OR Bearer)
- **Roles**: any authenticated role
- **Content-Type**: none (no body)
- **Headers**:
  - `Authorization: Bearer <token>` *(optional — cookie also accepted)*

- **Response**:
  - `200 OK` with body `"Successfully logged out"` on success.
  - `400 Bad Request` with body `"Authentication token is missing"` if neither cookie nor header carries a token.

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/logout' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....' \
  -H 'Accept-Language: en' \
  --cookie 'auth_token=eyJhbGciOiJIUzI1NiJ9....'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8
Set-Cookie: auth_token=; Max-Age=0; Path=/

Successfully logged out
```

- **Side effects**:
  - Calls `tokenService.blacklistToken(token)` — inserts the token into `token_blacklist`.
  - Calls `jwtCookieService.clearAuthCookie(response)` — emits a `Set-Cookie` that expires the `auth_token` cookie.

---

### POST `/api/auth/logout-all`

Sign out from every device. Deactivates every `Session` row for this user AND blacklists the current token.

- **Authentication**: Required (JWT cookie OR Bearer) — must resolve a `UserDetails` principal.
- **Roles**: any authenticated role
- **Content-Type**: none (no body)
- **Headers**:
  - `Authorization: Bearer <token>` *(optional — cookie also accepted)*

- **Response**:
  - `200 OK` with body `"Logged out from all devices successfully"` on success.
  - `401 Unauthorized` with body `"Not authenticated"` if no principal is bound.

**Example request**

```bash
curl -X POST 'https://api.example.com/api/auth/logout-all' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....' \
  -H 'Accept-Language: ku'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8
Set-Cookie: auth_token=; Max-Age=0; Path=/

Logged out from all devices successfully
```

- **Side effects** (in order):
  1. `sessionRepository.findByUser(user)` → every returned row has `isActive = false` and `logoutTimestamp = Instant.now()`, then `saveAll`.
  2. If a token is present, it is blacklisted.
  3. `auth_token` cookie is cleared.

---

### GET `/api/auth/sessions/getAllSessions`

List every **active** session for the authenticated user.

- **Authentication**: Required (JWT cookie OR Bearer)
- **Roles**: any authenticated role
- **Content-Type**: none
- **Headers**:
  - `Authorization: Bearer <token>` *(optional — cookie also accepted)*

- **Response**:
  - `200 OK` — `List<SessionDTO>`.
  - `401 Unauthorized` with body `"Not authenticated"` if no principal.

```json
[
  {
    "sessionId": "8f3a2b1c-9d7e-4f55-a1b2-77ce0f9c5021",
    "deviceInfo": "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15",
    "ipAddress": "203.0.113.42",
    "loginTimestamp": "2026-05-30T08:14:21Z",
    "expiresAt": "2026-05-31T08:14:21Z",
    "isActive": true,
    "logoutTimestamp": null
  }
]
```

**Example request**

```bash
curl -X GET 'https://api.example.com/api/auth/sessions/getAllSessions' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....' \
  -H 'Accept-Language: en'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "sessionId": "8f3a2b1c-9d7e-4f55-a1b2-77ce0f9c5021",
    "deviceInfo": "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4)",
    "ipAddress": "203.0.113.42",
    "loginTimestamp": "2026-05-30T08:14:21Z",
    "expiresAt": "2026-05-31T08:14:21Z",
    "isActive": true,
    "logoutTimestamp": null
  },
  {
    "sessionId": "1c0fdcdf-1a8c-4e44-b2b1-22ad13b1c7a8",
    "deviceInfo": "okhttp/4.12.0 (Android 14)",
    "ipAddress": "198.51.100.7",
    "loginTimestamp": "2026-05-29T17:02:55Z",
    "expiresAt": "2026-05-30T17:02:55Z",
    "isActive": true,
    "logoutTimestamp": null
  }
]
```

---

### DELETE `/api/auth/sessions/{sessionId}`

Revoke one specific session by its `sessionId`. Caller can only revoke their **own** sessions.

- **Authentication**: Required (JWT cookie OR Bearer)
- **Roles**: any authenticated role
- **Content-Type**: none
- **Path parameters**:

| Param | Type | Description |
| --- | --- | --- |
| `sessionId` | `String` | The opaque `Session.sessionId` (UUID). |

- **Response**:
  - `200 OK` with body `"Session revoked successfully"` on success.
  - `401 Unauthorized` with body `"Not authenticated"` if no principal.
  - `404 Not Found` with body `"Session not found"` if no matching `sessionId`.
  - `403 Forbidden` with body `"You can only revoke your own sessions"` if the session belongs to a different `userId`.

**Example request**

```bash
curl -X DELETE 'https://api.example.com/api/auth/sessions/8f3a2b1c-9d7e-4f55-a1b2-77ce0f9c5021' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....' \
  -H 'Accept-Language: ku'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Session revoked successfully
```

- **Side effects**: the target `Session` row gets `isActive = false` and `logoutTimestamp = Instant.now()` (the current JWT is NOT blacklisted here).

---

### DELETE `/api/auth/sessions/revokeAll`

Revoke every active session for the authenticated user (does NOT blacklist the current JWT — unlike `/api/auth/logout-all`).

- **Authentication**: Required (JWT cookie OR Bearer)
- **Roles**: any authenticated role
- **Content-Type**: none

- **Response**:
  - `200 OK` with body `"All sessions revoked successfully"` on success.
  - `401 Unauthorized` with body `"Not authenticated"` if no principal.

**Example request**

```bash
curl -X DELETE 'https://api.example.com/api/auth/sessions/revokeAll' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....' \
  -H 'Accept-Language: en'
```

**Example response**

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

All sessions revoked successfully
```

- **Side effects**: every row returned from `sessionRepository.findByUserAndIsActive(user, true)` has `isActive = false` and `logoutTimestamp = Instant.now()`, then `saveAll`.

---

## 08 · DTO Reference

### `LoginRequestDTO` (request)

| Field | Type | Validation Annotations |
| --- | --- | --- |
| `username` | `String` | `@NotBlank(message = "Username or email is required")`<br>`@Size(max = 160, message = "Username or email is too long")` |
| `password` | `String` | `@NotBlank(message = "Password is required")`<br>`@Size(min = 6, max = 128, message = "Password must be at least 6 characters")` |

> Note: `username` accepts either a username OR an email.

---

### `RegisterRequestDTO` (request)

| Field | Type | Validation Annotations |
| --- | --- | --- |
| `name` | `String` | `@NotBlank(message = "Name is required")`<br>`@Size(max = 120, message = "Name must not exceed 120 characters")` |
| `username` | `String` | `@NotBlank(message = "Username is required")`<br>`@Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")`<br>`@Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username can contain only letters, numbers, and underscores")` |
| `email` | `String` | `@NotBlank(message = "Email is required")`<br>`@Email(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$", message = "Email must be a valid address with a domain (e.g. user@example.com)")`<br>`@Size(max = 160, message = "Email must not exceed 160 characters")` |
| `password` | `String` | `@NotBlank(message = "Password is required")`<br>`@Size(min = 6, max = 128, message = "Password must be at least 6 characters")` |
| `pincode` | `Long` | none (optional) |

> Note: `profileImage` is NOT part of this DTO — it is handled separately as a `MultipartFile` on `/register-with-image`.

---

### `PasswordResetRequestDTO` (request)

| Field | Type | Validation Annotations |
| --- | --- | --- |
| `email` | `String` | `@NotBlank(message = "Email is required")`<br>`@Email(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$", message = "Email must be a valid address with a domain (e.g. user@example.com)")`<br>`@Size(max = 160, message = "Email must not exceed 160 characters")` |
| `resetToken` | `String` | `@NotBlank(message = "Reset token is required")` |
| `newPassword` | `String` | `@NotBlank(message = "New password is required")`<br>`@Size(min = 6, max = 128, message = "New password must be at least 6 characters")` |
| `confirmPassword` | `String` | `@NotBlank(message = "Confirm password is required")`<br>`@Size(min = 6, max = 128, message = "Confirm password must be at least 6 characters")` |

---

### `SessionDTO` (response)

| Field | Type | Description |
| --- | --- | --- |
| `sessionId` | `String` | UUID identifier (mirrors `Session.sessionId`). |
| `deviceInfo` | `String` | User-Agent / device info captured at login. |
| `ipAddress` | `String` | Client IP at login. |
| `loginTimestamp` | `Instant` | ISO-8601 UTC. |
| `expiresAt` | `Instant` | Session expiry. |
| `isActive` | `Boolean` | `true` while the session is live; `false` after logout/revoke. |
| `logoutTimestamp` | `Instant` | Set when the session is closed (else `null`). |

---

### `Token` (response)

The shared response envelope returned by `/register`, `/register-with-image`, and `/login`.

| Field | Type | Description |
| --- | --- | --- |
| `token` | `String` | The signed JWT. |
| `response` | `String` | Human-readable status message. |

Lombok: `@Data @NoArgsConstructor @AllArgsConstructor @Builder`. Also exposes a convenience constructor `Token(String response)` that leaves `token == null` — used for error/info-only responses.

---

### Validation Patterns (`ValidationPatterns`)

| Constant | Regex | Notes |
| --- | --- | --- |
| `EMAIL` | `^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,10}$` | Stricter than Hibernate's default. Requires at least one dot then 2–10 TLD letters. Rejects `user@localhost`, `@example.com`, `user@.com`. |
| `PASSWORD` | `^.{6,}$` | Minimum length only (upper bound enforced by `@Size`). |
| `USERNAME` | `^[A-Za-z0-9_]+$` | Letters, digits, underscores. |
| `USERNAME_OR_EMPTY` | `^$|^[A-Za-z0-9_]+$` | Same as `USERNAME` plus empty string (for partial-update DTOs). |

### Security Constants (`SecurityConstants`)

| Constant | Value | Use |
| --- | --- | --- |
| `TOKEN_PREFIX` | `"Bearer "` | Stripped from `Authorization` header. |
| `HEADER_STRING` | `"Authorization"` | Header name. |
| `TOKEN_CANNOT_BE_VERIFIED` | `"Token can not be verified"` | Filter-level error message. |
| `AUTHORITIES` | `"authorities"` | JWT claim key. |
| `ID_CLAIM` | `"id"` | JWT claim key. |
| `ROLE` | `"ROLE"` | Authority prefix marker. |
| `FORBIDDEN_MESSAGE` | `"You need to log in to access this page"` | 401 message. |
| `ACCESS_DENIED_MESSAGE` | `"You do not have permission to access this page"` | 403 message. |
| `OPTIONS_HTTP_METHOD` | `"OPTIONS"` | Preflight detection. |
| `MAX_FAILED_ATTEMPTS` | `5` | Threshold for auto-lock. |
| `LOCK_DURATION_MINUTES` | `1` | Auto-unlock window (consumed by `User.isAccountNonLocked()`). |

---

## 09 · Error Responses

Errors are wrapped in the project's bilingual `ApiErrorResponse` envelope.

```json
{
  "timestamp": "2026-05-31T12:34:56.789Z",
  "status": 400,
  "path": "/api/auth/login",
  "method": "POST",
  "traceId": "8e3b4f12-7d0c-4d4f-9aa4-31fbbf8c41aa",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "messageEn": "Validation failed",
  "messageKu": "پشتڕاستکردنەوەی داتا سەرنەکەوت",
  "fieldErrors": [
    {
      "field": "password",
      "message": "Password must be at least 6 characters",
      "messageEn": "Password must be at least 6 characters",
      "messageKu": "وشەی نهێنی دەبێت لانیکەم ٦ پیت بێت"
    }
  ],
  "details": null
}
```

**Codes commonly produced by this module:**

| Code | HTTP | When |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | Any DTO field fails `@NotBlank`, `@Size`, `@Email`, `@Pattern`. |
| `UNAUTHORIZED` | 401 | Missing/invalid/expired JWT, or blacklisted token. Login with bad credentials. |
| `FORBIDDEN` | 403 | Authenticated but lacking the role required for the endpoint (e.g. revoking another user's session). |
| `ACCOUNT_LOCKED` | 401/403 | `User.isLocked == true` and `lockTime` is still within `LOCK_DURATION_MINUTES`. |
| `CONFLICT` | 409 | Duplicate `username` or `email` at register time (violates `uk_users_username` / `uk_users_email`). |
| `NOT_FOUND` | 404 | `sessionId` does not exist (`/api/auth/sessions/{sessionId}`); user not found by email on reset. |

Plain `String` body responses observed in the controllers (not wrapped in the envelope):

| Endpoint | Status | Body |
| --- | --- | --- |
| `POST /api/auth/logout` | 400 | `Authentication token is missing` |
| `POST /api/auth/logout` | 200 | `Successfully logged out` |
| `POST /api/auth/logout-all` | 401 | `Not authenticated` |
| `POST /api/auth/logout-all` | 200 | `Logged out from all devices successfully` |
| `GET /api/auth/sessions/getAllSessions` | 401 | `Not authenticated` |
| `DELETE /api/auth/sessions/{sessionId}` | 401 | `Not authenticated` |
| `DELETE /api/auth/sessions/{sessionId}` | 404 | `Session not found` |
| `DELETE /api/auth/sessions/{sessionId}` | 403 | `You can only revoke your own sessions` |
| `DELETE /api/auth/sessions/{sessionId}` | 200 | `Session revoked successfully` |
| `DELETE /api/auth/sessions/revokeAll` | 401 | `Not authenticated` |
| `DELETE /api/auth/sessions/revokeAll` | 200 | `All sessions revoked successfully` |

---

## 10 · Notes

### HttpOnly cookie behavior
- `register`, `register-with-image`, and `login` all pass through `withAuthCookie(...)` — if the response is `2xx` and the body's `token` is non-blank, `JwtCookieService.addAuthCookie(response, body.getToken())` is invoked, attaching `auth_token` as an `HttpOnly` cookie.
- `logout` and `logout-all` always invoke `jwtCookieService.clearAuthCookie(response)` — even on the missing-token / unauthenticated branches — so a stale cookie is always wiped from the browser.
- The JWT filter accepts the cookie OR a `Bearer` header; `extractToken(...)` prefers the header when supplied.

### Brute-force lockout
- Tracked via `User.failedAttempts` (`int`), `User.lockTime` (`Instant`, `@JsonIgnore`), and `User.isLocked` (`Boolean`).
- Threshold: `SecurityConstants.MAX_FAILED_ATTEMPTS = 5`.
- Auto-unlock window: `SecurityConstants.LOCK_DURATION_MINUTES = 1` — `User.isAccountNonLocked()` re-enables the account once `(now - lockTime) > LOCK_DURATION_MINUTES * 60 * 1000` ms.

### Password expiry
- Optional. If `User.passwordExpiryDate` is non-null and `isBefore(Instant.now())`, `isCredentialsNonExpired()` returns `false` and Spring Security will reject authentication.

### Per-device session tracking
- A `Session` row is written per login, capturing `sessionId` (UUID), `deviceInfo` (User-Agent), `ipAddress`, `loginTimestamp`, and `expiresAt`.
- Clients can introspect their own sessions via `GET /api/auth/sessions/getAllSessions` and revoke either one (`DELETE /api/auth/sessions/{sessionId}`) or all (`DELETE /api/auth/sessions/revokeAll`).
- Revoke endpoints only flip `isActive` / `logoutTimestamp`; they do NOT blacklist the JWT itself.

### Logout-all semantics
`POST /api/auth/logout-all` is stronger than `DELETE /api/auth/sessions/revokeAll` because it ALSO blacklists the current token (via `tokenService.blacklistToken(token)`) and clears the `auth_token` cookie. Use it when the user wants to be fully signed out everywhere (e.g. the `UserProfile.vue` "دەرچوون لە ھەموو ئامێرەکان" button).

### Token blacklist
- Backed by `token_blacklist` (`token`, `blacklisted_at`, `expires_at`). The `token` column is `unique` and `length = 512`.
- Populated by `tokenService.blacklistToken(token)` (called from `/logout` and `/logout-all`).
- Entries can safely be purged once `expires_at` passes — the JWT itself would be expired by then.

### Security config snapshot
- `csrf` disabled, `sessionCreationPolicy = STATELESS`, custom `JWTAuthenticationFilter` registered before `UsernamePasswordAuthenticationFilter`.
- `permitAll()` for register/login/reset endpoints; `authenticated()` for `/api/auth/logout`, `/api/auth/logout-all`, `/api/auth/sessions/**`.
- `OPTIONS` preflight requests on any path are unconditionally permitted.
