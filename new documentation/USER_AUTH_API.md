# KHI Backend — User & Auth API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT + HttpOnly Cookie · Spring Security 6 · BCrypt · 16 Endpoints · 4 Roles · Session Tracking

Complete documentation for all user authentication, session, and profile management endpoints — including request & response schemas, validation rules, role-based access control, and the full data model.

---

## Table of Contents

- [01 · Project Overview & Goals](#01--project-overview--goals)
- [02 · Data Model — User Entity](#02--data-model--user-entity)
- [03 · Data Model — Session Entity](#03--data-model--session-entity)
- [04 · Data Model — TokenBlacklist Entity](#04--data-model--tokenblacklist-entity)
- [05 · Roles & Permissions](#05--roles--permissions)
- [06 · Authentication API](#06--authentication-api)
  - `POST /api/auth/register`
  - `POST /api/auth/register-with-image`
  - `POST /api/auth/login`
  - `POST /api/auth/reset-token`
  - `POST /api/auth/reset-password`
  - `POST /api/auth/logout`
  - `POST /api/auth/logout-all`
- [07 · Session API](#07--session-api) — `/api/auth/sessions/*`
- [08 · User Profile API](#08--user-profile-api) — `/api/user/*`
- [09 · DTO Reference](#09--dto-reference)
- [10 · Error Responses](#10--error-responses)
- [11 · Change Log — Old vs. New](#11--change-log--old-vs-new)

---

## 01 · Project Overview & Goals

The User module of the KHI Backend provides secure, production-grade authentication and profile management built on Spring Security, JWT tokens, and JPA/Hibernate.

| Goal | Description |
| --- | --- |
| 🔐 **Secure Authentication** | JWT-based auth with HttpOnly cookie delivery, token blacklisting on logout, and per-device session tracking |
| 👤 **Self-Service Profile** | Users update their name, username, profile image, and password via dedicated self-service endpoints |
| 🔑 **Role-Based Access** | Four roles — GUEST, EMPLOYEE, ADMIN, SUPER_ADMIN — each with a fine-grained permission set |
| 🛡️ **Account Security** | Brute-force lockout after failed attempts, password expiry enforcement, and a secure reset-token flow |
| 📱 **Multi-Device Sessions** | Sessions tracked per device in the `sessions` table — logout-all and per-session revocation invalidate every active session server-side |

### Base URL

```
# Auth group — mostly public (logout/logout-all require auth)
/api/auth/*

# Session group — requires valid JWT
/api/auth/sessions/*

# Profile group — requires valid JWT
/api/user/*
```

### Tech Stack

| Layer | Technology |
| --- | --- |
| Framework | Spring Boot 3.x |
| Security | Spring Security 6 + JWT (JJWT) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation (`@Valid` / `@Validated`) |
| Token Delivery | HttpOnly Secure cookie (`auth_token`) + `Authorization: Bearer …` header (both accepted) |
| Password Hash | BCrypt |
| Auth Flow | Stateless JWT + server-side `sessions` table (for logout-all and per-session revocation) + `token_blacklist` table (for immediate token invalidation) |

> 🚧 **OAuth2 status:** the old doc advertised OAuth2-readiness with `provider` / `providerId` / `imageUrl` fields on the User entity. **These fields do not exist in the current code.** All auth is local (email/username + BCrypt password). Treat OAuth2 as a future addition, not a current capability.

---

## 02 · Data Model — User Entity

JPA entity mapped to the `users_tbl` table. Implements Spring Security's `UserDetails`. Both `username` and `email` carry DB-level unique constraints (`uk_users_username`, `uk_users_email`).

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `userId` | `user_id` | BIGINT | PK / AUTO (IDENTITY) | Auto-increment primary key |
| `name` | `name` | VARCHAR(120) | NOT NULL | Full display name |
| `profileImage` | `profile_image` | VARCHAR(500) | NULLABLE | Path/URL of locally uploaded avatar |
| `username` | `username` | VARCHAR(80) | **NOT NULL, UNIQUE** | Unique @handle — URL-safe, letters/numbers/underscores |
| `email` | `email` | VARCHAR(160) | **NOT NULL, UNIQUE** | Primary login identity |
| `password` | `password` | VARCHAR(255) | **NOT NULL** | BCrypt hashed — `@JsonIgnore` on the entity |
| `role` | `role` | ENUM (STRING, 30) | NOT NULL | `GUEST` \| `EMPLOYEE` \| `ADMIN` \| `SUPER_ADMIN` |
| `pincode` | `pincode` | BIGINT (length=6) | NULLABLE | Optional 6-digit numeric PIN |
| `isActivated` | `is_activated` | BOOLEAN | NOT NULL (default `true`) | Account enabled flag — drives Spring Security `isEnabled()` |
| `resetToken` | `reset_token` | VARCHAR(120) | NULLABLE | Active password-reset token. `@JsonIgnore` |
| `resetTokenExpiration` | `reset_token_expiration` | TIMESTAMP | NULLABLE | Expiry instant for `resetToken`. `@JsonIgnore` |
| `createdAt` | `created_at` | TIMESTAMP | NULLABLE | Set on registration. `Instant` |
| `updatedAt` | `updated_at` | TIMESTAMP | NULLABLE | Set on every update. `Instant` |
| `failedAttempts` | `failed_attempts` | INT | NOT NULL (default `0`) | Consecutive failed login counter |
| `lockTime` | `lock_time` | TIMESTAMP | NULLABLE | When the account was locked. `@JsonIgnore` |
| `isLocked` | `is_locked` | BOOLEAN | NOT NULL (default `false`) | Brute-force lockout flag — drives `isAccountNonLocked()` (auto-expires after `SecurityConstants.LOCK_DURATION_MINUTES`) |
| `passwordExpiryDate` | `password_expiry_date` | TIMESTAMP | NULLABLE | When the current password expires. `null` = never |

> ⛔ **Fields that DO NOT exist (despite the old doc claiming them):** `provider`, `providerId`, `imageUrl`. The User entity has no OAuth2 fields in the current code.

**Spring Security `UserDetails` methods on `User`:**

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getAuthorities()` | `Collection<GrantedAuthority>` | Role permissions + `ROLE_<NAME>` |
| `isAccountNonExpired()` | `boolean` | Always `true` |
| `isAccountNonLocked()` | `boolean` | `true` unless `isLocked == true` AND lock is still within `LOCK_DURATION_MINUTES` |
| `isCredentialsNonExpired()` | `boolean` | `!isPasswordExpired()` |
| `isEnabled()` | `boolean` | `isActivated == true` |
| `isPasswordExpired()` | `boolean` | `passwordExpiryDate != null && passwordExpiryDate.isBefore(now)` |

---

## 03 · Data Model — Session Entity

JPA entity mapped to the `sessions` table. One row per login. Powers per-session revocation and the logout-all flow.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Internal primary key |
| `sessionId` | `session_id` | VARCHAR (UNIQUE, NOT NULL) | UNIQUE | Public session identifier (UUID) |
| `user` | `user_id` | FK → users_tbl | NOT NULL, LAZY | Owning user |
| `deviceInfo` | `device_info` | VARCHAR | NULLABLE | User-Agent / device string captured at login |
| `ipAddress` | `ip_address` | VARCHAR | NULLABLE | Client IP at login time |
| `loginTimestamp` | `login_timestamp` | TIMESTAMP | NOT NULL | When the session was created |
| `expiresAt` | `expires_at` | TIMESTAMP | NOT NULL | JWT expiry instant |
| `isActive` | `is_active` | BOOLEAN | NOT NULL (default `true`) | Whether the session is still valid |
| `logoutTimestamp` | `logout_timestamp` | TIMESTAMP | NULLABLE | When the session was ended. `null` if still active |

---

## 04 · Data Model — TokenBlacklist Entity

JPA entity mapped to the `token_blacklist` table. Holds individual JWT strings that have been explicitly invalidated (e.g. via logout). The `JWTAuthenticationFilter` rejects any incoming token present in this table.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Primary key |
| `token` | `token` | VARCHAR(512) | NOT NULL, UNIQUE | The blacklisted JWT string |
| `blacklistedAt` | `blacklisted_at` | TIMESTAMP | NOT NULL | When the token was blacklisted |
| `expiresAt` | `expires_at` | TIMESTAMP | NOT NULL | Original JWT expiry — used to garbage-collect old rows |

---

## 05 · Roles & Permissions

Four roles control access. Each role's permissions are exposed as Spring Security `GrantedAuthority` objects alongside the `ROLE_<NAME>` prefix (e.g. `ROLE_ADMIN`). Permissions use the string format `"user:<action>"`.

| Permission key | Constant |
| --- | --- |
| `user:create` | `USER_CREATE` |
| `user:read`   | `USER_READ` |
| `user:update` | `USER_UPDATE` |
| `user:delete` | `USER_DELETE` |

| Role | `user:create` | `user:read` | `user:update` | `user:delete` |
| --- | :---: | :---: | :---: | :---: |
| `GUEST` | — | ✅ | — | — |
| `EMPLOYEE` | ✅ | ✅ | ✅ | — |
| `ADMIN` | ✅ | ✅ | ✅ | ✅ |
| `SUPER_ADMIN` | ✅ | ✅ | ✅ | ✅ |

> ℹ️ `SUPER_ADMIN` currently has the **same** permission set as `ADMIN` in the code (it's reserved for platform owners, but enforcement is identical right now).

---

## 06 · Authentication API

Base path: `/api/auth`. All endpoints here are **public** except `/logout` and `/logout-all`. On successful register / login the server sets an HttpOnly `auth_token` cookie in addition to returning the JWT in the response body.

---

### `POST /api/auth/register`

🔓 **Public**

Create a new user account with a JSON body. No profile image. Role defaults to **GUEST**.

### Request Body

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `name` | String | Yes | `@NotBlank` · `@Size(max=120)` |
| `username` | String | Yes | `@NotBlank` · `@Size(min=3, max=80)` · `@Pattern(ValidationPatterns.USERNAME)` — letters / numbers / underscores |
| `email` | String | Yes | `@NotBlank` · `@Email(regexp=ValidationPatterns.EMAIL)` · `@Size(max=160)` |
| `password` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `pincode` | Long | No | Optional numeric PIN (column `length=6` recommended) |

### Request JSON

```json
{
  "name":     "Akar Arkan",
  "username": "akar_dev",
  "email":    "akar@example.com",
  "password": "MySecure@123",
  "pincode":  123456
}
```

### Response · 200 OK

```json
{
  "token":     "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400000
}
```

> The actual response shape is the `Token` DTO. Field names depend on the `Token` class — typically `token` and `expiresIn`.

### Cookie set automatically

```
Set-Cookie: auth_token=eyJhbGci...; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=86400
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | Validation failure |
| `409` | Username or email already exists |

---

### `POST /api/auth/register-with-image`

🔓 **Public** · `Content-Type: multipart/form-data`

Create a new user account and upload a profile image in a single multipart request.

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON Part | **Yes** | `RegisterRequestDTO` fields serialised as JSON with `Content-Type: application/json` |
| `image` | File (image/*) | No | Profile picture — PNG, JPEG, WEBP. Stored server-side and saved to `profileImage` |

### Request · curl Example

```bash
curl -X POST https://api.khi.iq/api/auth/register-with-image \
  -F 'data={"name":"Akar Arkan","username":"akar_dev","email":"akar@example.com","password":"MySecure@123","pincode":123456};type=application/json' \
  -F "image=@avatar.png;type=image/png"
```

### Response · 200 OK

Same `Token` response as `POST /register`.

---

### `POST /api/auth/login`

🔓 **Public**

Authenticate with username **or** email and password. Returns a signed JWT and sets the HttpOnly cookie.

### Request Body

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `username` | String | Yes | `@NotBlank` · `@Size(max=160)` — accepts both username and email |
| `password` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |

### Request · Using Username

```json
{ "username": "akar_dev", "password": "MySecure@123" }
```

### Request · Using Email

```json
{ "username": "akar@example.com", "password": "MySecure@123" }
```

### Response · 200 OK

```json
{
  "token":     "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400000
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Wrong credentials |
| `403` | Account locked (driven by `isAccountNonLocked()`) |
| `403` | Account disabled (`isActivated = false` → `isEnabled() == false`) |
| `403` | Password expired (`isCredentialsNonExpired() == false`) |

> ⚠️ After consecutive failed login attempts the account is locked for `SecurityConstants.LOCK_DURATION_MINUTES`. The `isAccountNonLocked()` override auto-expires the lock when that window elapses, so retry behaviour is automatic.

---

### `POST /api/auth/reset-token`

🔓 **Public**

Request a password-reset token. The token is hashed and stored alongside `resetTokenExpiration`. Delivery is handled by a `PasswordResetDeliveryService` (the logging implementation is the default; mail integration is a separate concern).

### Query Parameter

| Param | Type | Required | Validation |
| --- | --- | --- | --- |
| `email` | String | **Yes** | `@NotBlank` · `@Email(regexp=ValidationPatterns.EMAIL)` · `@Size(max=160)` |

### Request

```
POST /api/auth/reset-token?email=akar%40example.com
```

### Response · 200 OK

Plain text response delivered by `UserService.createPasswordResetToken`.

```
"Password reset token sent to your email."
```

---

### `POST /api/auth/reset-password`

🔓 **Public**

Consume a reset token and set a new password. Token must not be expired. `newPassword` and `confirmPassword` are validated in the service layer.

### Request Body

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `email` | String | Yes | `@NotBlank` · `@Email` · `@Size(max=160)` |
| `resetToken` | String | Yes | `@NotBlank` |
| `newPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `confirmPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` · must match `newPassword` |

### Request JSON

```json
{
  "email":           "akar@example.com",
  "resetToken":      "a3f8b2c1d4e5f6a7b8c9d0e1f2a3b4c5",
  "newPassword":     "NewPass@2026!",
  "confirmPassword": "NewPass@2026!"
}
```

### Response · 200 OK

```
"Password has been reset successfully."
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | Token expired or invalid |
| `400` | `newPassword` does not match `confirmPassword` |
| `400` | Validation failure |
| `404` | Email not found |

---

### `POST /api/auth/logout`

🔒 **Auth Required**

Blacklist the current JWT (insert into `token_blacklist`) and clear the HttpOnly cookie. The token is extracted from the `Authorization: Bearer …` header first, then from the cookie.

### Authentication — send one of

```
Cookie: auth_token=eyJhbGci...
# or
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```
"Successfully logged out"
```

### Cookie cleared

```
Set-Cookie: auth_token=; Max-Age=0; HttpOnly; Secure; SameSite=Strict; Path=/
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | Authentication token missing (cookie and header both absent) — body: `"Authentication token is missing"` |

---

### `POST /api/auth/logout-all`

🔒 **Auth Required**

For the current user:

1. Marks every `Session` row as `isActive = false` and sets `logoutTimestamp = now`.
2. Blacklists the current JWT.
3. Clears the HttpOnly cookie.

### Authentication

Cookie or `Authorization: Bearer …`.

### Response · 200 OK

```
"Logged out from all devices successfully"
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Not authenticated — body: `"Not authenticated"` |

---

## 07 · Session API

Base path: `/api/auth/sessions`. All endpoints require a valid JWT. The principal is resolved via `@AuthenticationPrincipal User` — when the principal is `null`, a `401` is returned with the body `"Not authenticated"`.

> ⚠️ The old doc **did not mention this controller at all**. These three endpoints exist and are fully wired in `SessionAPI`.

---

### `GET /api/auth/sessions/getAllSessions`

🔒 **Auth Required**

Return all active sessions (`isActive = true`) for the authenticated user.

### Response · 200 OK — `List<SessionDTO>`

```json
[
  {
    "sessionId":       "8b3c2a01-…-…",
    "deviceInfo":      "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)…",
    "ipAddress":       "94.32.18.7",
    "loginTimestamp":  "2026-04-11T21:30:00Z",
    "expiresAt":       "2026-04-12T21:30:00Z",
    "isActive":        true,
    "logoutTimestamp": null
  }
]
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Not authenticated — body: `"Not authenticated"` |

---

### `DELETE /api/auth/sessions/{sessionId}`

🔒 **Auth Required**

Revoke a specific session by its UUID. The service refuses to revoke sessions that belong to another user (`403`).

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | String | **Yes** | The public session UUID (matches `Session.sessionId`) |

### Response · 200 OK

```
"Session revoked successfully"
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Not authenticated |
| `403` | Body: `"You can only revoke your own sessions"` — the session belongs to a different user |
| `404` | Body: `"Session not found"` |

---

### `DELETE /api/auth/sessions/revokeAll`

🔒 **Auth Required**

Revoke every active session for the authenticated user. Identical effect to `POST /api/auth/logout-all` but **does not** blacklist the current JWT or clear the cookie. If you want the current device kicked out too, prefer `/logout-all`.

### Response · 200 OK

```
"All sessions revoked successfully"
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Not authenticated |

---

## 08 · User Profile API

Base path: `/api/user`. All endpoints require a valid JWT. The username is resolved from the JWT principal via `Authentication#getName()` — this works whether the principal is a `String` or a `UserDetails`.

> ℹ️ The controller intentionally uses `Authentication#getName()` rather than `@AuthenticationPrincipal UserDetails`, because the JWT filter previously stored a plain `String` as the principal. `@AuthenticationPrincipal` silently injects `null` for non-UserDetails principals, which would cause NPEs.

---

### `GET /api/user/me`

🔒 **Auth Required**

Return the complete `UserResponseDTO` for the authenticated user.

### Response · 200 OK

```json
{
  "userId":             42,
  "name":               "Akar Arkan",
  "username":           "akar_dev",
  "email":              "akar@example.com",
  "role":               "GUEST",
  "pincode":            123456,
  "isActivated":        true,
  "profileImage":       "/uploads/avatars/akar_dev.png",
  "createdAt":          "2026-01-15T10:30:00Z",
  "updatedAt":          "2026-04-10T08:20:00Z",
  "passwordExpiryDate": "2026-10-15T10:30:00Z"
}
```

> ⛔ The response has **no** `imageUrl` or `provider` fields. Those were promised by the old doc but are not present in the entity or the DTO.

---

### `PUT /api/user/profile`

🔒 **Auth Required**

Update the authenticated user's `username` and/or `name`. Both fields are optional — omit any field to keep its current value. Empty string is permitted on `username` thanks to the regex `^$|^[A-Za-z0-9_]+$`.

### Request Body — All Fields Optional

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `username` | String | No | `@Size(min=3, max=80)` · `@Pattern(^$|^[A-Za-z0-9_]+$)` |
| `name` | String | No | `@Size(max=120)` |

### Request · Change Username Only

```json
{ "username": "akar_arkan" }
```

### Request · Change Both

```json
{
  "username": "akar_arkan",
  "name":     "Akar Arkan KHI"
}
```

### Response · 200 OK

Returns the full updated `UserResponseDTO` (same shape as `GET /me`).

### Error Responses

| Status | Description |
| --- | --- |
| `400` | Validation failure on username format or length |
| `409` | New username is already taken by another user |

---

### `PUT /api/user/password`

🔒 **Auth Required**

Change the authenticated user's password. The current password is required for verification. `newPassword` and `confirmPassword` must match.

### Request Body

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `currentPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `newPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `confirmPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` · must equal `newPassword` (service-layer check) |

### Request JSON

```json
{
  "currentPassword": "MySecure@123",
  "newPassword":     "NewPass@2026!",
  "confirmPassword": "NewPass@2026!"
}
```

### Response · 200 OK

```json
{ "message": "Password updated successfully" }
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `currentPassword` is incorrect |
| `400` | `newPassword` does not match `confirmPassword` |
| `400` | Validation failure |

---

### `POST /api/user/profile-image`

🔒 **Auth Required** · `Content-Type: multipart/form-data`

Upload or replace the authenticated user's profile picture. Single file part.

### Form Part

| Part Name | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | File (image/*) | **Yes** | Image to upload. Replaces any existing `profileImage` |

### Request · curl

```bash
curl -X POST https://api.khi.iq/api/user/profile-image \
  -H "Authorization: Bearer eyJhbGci..." \
  -F "file=@new_avatar.jpg;type=image/jpeg"
```

### Response · 200 OK

Returns the full updated `UserResponseDTO`.

---

### `DELETE /api/user/profile-image`

🔒 **Auth Required**

Remove the authenticated user's profile picture. Sets `profileImage = null`.

### Response · 200 OK

Returns the full `UserResponseDTO` with `profileImage: null`.

---

### `DELETE /api/user/account`

🔒 **Auth Required**

Permanently delete the authenticated user's account.

### Response · 204 No Content

```
HTTP/1.1 204 No Content
(empty body)
```

> ⛔ **Irreversible.** The account row is hard-deleted. Related sessions / tokens / profile data are removed according to JPA cascade rules.

---

## 09 · DTO Reference

### RegisterRequestDTO

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `name` | String | Yes | `@NotBlank` · `@Size(max=120)` |
| `username` | String | Yes | `@NotBlank` · `@Size(min=3, max=80)` · `@Pattern(ValidationPatterns.USERNAME)` |
| `email` | String | Yes | `@NotBlank` · `@Email(regexp=ValidationPatterns.EMAIL)` · `@Size(max=160)` |
| `password` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `pincode` | Long | No | No constraint |

### LoginRequestDTO

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `username` | String | Yes | `@NotBlank` · `@Size(max=160)` — accepts username or email |
| `password` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |

### PasswordResetRequestDTO

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `email` | String | Yes | `@NotBlank` · `@Email(regexp=ValidationPatterns.EMAIL)` · `@Size(max=160)` |
| `resetToken` | String | Yes | `@NotBlank` |
| `newPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `confirmPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` · must equal `newPassword` (service-level check) |

### UpdateProfileRequestDTO

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `username` | String | No | `@Size(min=3, max=80)` · `@Pattern(^$|^[A-Za-z0-9_]+$)` |
| `name` | String | No | `@Size(max=120)` |

### ChangePasswordRequestDTO

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `currentPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `newPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` |
| `confirmPassword` | String | Yes | `@NotBlank` · `@Size(min=6, max=128)` · must equal `newPassword` (service-level check) |

### SessionDTO

| Field | Type | Description |
| --- | --- | --- |
| `sessionId` | String | Unique session identifier (UUID) |
| `deviceInfo` | String | User-Agent / device string captured at login |
| `ipAddress` | String | Client IP at login time |
| `loginTimestamp` | `Instant` | When the session was created |
| `expiresAt` | `Instant` | JWT expiry instant |
| `isActive` | Boolean | Whether the session is still valid |
| `logoutTimestamp` | `Instant` | When the session ended. `null` if still active |

### UserResponseDTO

| Field | Type | Description |
| --- | --- | --- |
| `userId` | Long | Internal numeric user ID |
| `name` | String | Full display name |
| `username` | String | Unique @handle |
| `email` | String | Primary login email |
| `role` | `Role` | `GUEST` \| `EMPLOYEE` \| `ADMIN` \| `SUPER_ADMIN` |
| `pincode` | Long | Optional PIN — `null` if not set |
| `isActivated` | Boolean | Account enabled flag |
| `profileImage` | String | Local upload path/URL — `null` if none |
| `createdAt` | `Instant` | Account creation timestamp |
| `updatedAt` | `Instant` | Last update timestamp |
| `passwordExpiryDate` | `Instant` | When current password expires — `null` = never |

> ⛔ **No `imageUrl` or `provider` fields** on this DTO. OAuth2 is not wired in the current code.

### Token (login/register response)

The exact `Token` DTO fields depend on the `Token` class (in `ak.dev.khi_backend.user.jwt`). Typically:

| Field | Type | Description |
| --- | --- | --- |
| `token` | String | The signed JWT |
| `expiresIn` | Long | Token TTL in milliseconds |

---

## 10 · Error Responses

The API returns standard HTTP status codes. Validation errors from `@Valid` / `@Validated` are handled globally by `GlobalExceptionHandler` and return a structured body.

### Status Code Reference

| Status | Description |
| --- | --- |
| `200 OK` | Success — response body varies |
| `204 No Content` | Account delete succeeded |
| `400 Bad Request` | Validation failure or business rule violation |
| `401 Unauthorized` | Missing, expired, or blacklisted JWT |
| `403 Forbidden` | Account locked, disabled, password expired, or cross-user session revoke |
| `404 Not Found` | Resource not found |
| `409 Conflict` | Duplicate unique field (username or email) |
| `500 Internal Error` | Unexpected server error |

### Validation Error Body — `400`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    400,
  "errors": [
    { "field": "email",    "message": "Email must be a valid address with a domain (e.g. user@example.com)" },
    { "field": "password", "message": "Password must be at least 6 characters" }
  ]
}
```

### Auth / Business Error Body — `401 / 403 / 404 / 409`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Authentication token is missing or expired"
}
```

> ℹ️ Plain-text bodies (not JSON) are returned by a few endpoints — specifically `/api/auth/logout`, `/api/auth/logout-all`, `/api/auth/reset-token`, `/api/auth/reset-password`, and all three `/api/auth/sessions/*` endpoints. Everything else is JSON.

---

## 11 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /api/auth/register` | JSON register | JSON register | ⚪ Unchanged |
| `POST /api/auth/register-with-image` | Multipart register | Multipart register | ⚪ Unchanged |
| `POST /api/auth/login` | Login | Login | ⚪ Unchanged |
| `POST /api/auth/reset-token` | Request reset | Request reset | ⚪ Unchanged |
| `POST /api/auth/reset-password` | Consume reset | Consume reset | ⚪ Unchanged |
| `POST /api/auth/logout` | Logout | Logout — blacklists JWT + clears cookie | ⚪ Unchanged |
| `POST /api/auth/logout-all` | Logout from all devices | Marks all sessions inactive + blacklists current JWT + clears cookie | ⚪ Unchanged |
| `GET /api/auth/sessions/getAllSessions` | — | List active sessions for the user | 🟢 **Added (was missing from old doc)** |
| `DELETE /api/auth/sessions/{sessionId}` | — | Revoke a specific session (owner-checked) | 🟢 **Added** |
| `DELETE /api/auth/sessions/revokeAll` | — | Revoke all active sessions (no current-token blacklist — use `/logout-all` for that) | 🟢 **Added** |
| `GET /api/user/me` | Profile | Profile | ⚪ Unchanged |
| `PUT /api/user/profile` | Update username/name | Update username/name | ⚪ Unchanged |
| `PUT /api/user/password` | Change password | Change password | ⚪ Unchanged |
| `POST /api/user/profile-image` | Upload avatar | Upload avatar | ⚪ Unchanged |
| `DELETE /api/user/profile-image` | Remove avatar | Remove avatar | ⚪ Unchanged |
| `DELETE /api/user/account` | Delete account | Delete account — returns `204 No Content` | ⚪ Unchanged |

**Endpoint count:** Old doc claimed **13 endpoints**; the controllers actually expose **16** — the old doc completely missed the three `SessionAPI` endpoints under `/api/auth/sessions/*`. The new doc documents all 16.

### B) Data model comparison — User entity

| Item | Old doc | Code reality |
| --- | --- | --- |
| `userId`, `name`, `username`, `email`, `password`, `role`, `pincode`, `isActivated`, `profileImage`, `failedAttempts`, `isLocked`, `lockTime`, `passwordExpiryDate`, `resetToken`, `resetTokenExpiration`, `createdAt`, `updatedAt` | Documented | ⚪ Unchanged — all present |
| `imageUrl` | Documented as `TEXT(500)` "OAuth2 provider picture" | 🔴 **Does NOT exist in the entity** |
| `provider` | Documented as `VARCHAR(30)` (`"local"` / `"google"` / …) | 🔴 **Does NOT exist in the entity** |
| `providerId` | Documented as `VARCHAR(120)` (Google `sub` etc.) | 🔴 **Does NOT exist in the entity** |
| `password` nullability | Old doc said "NULL for OAuth2-only users" | 🟡 Actually `nullable = false` — password is required because there's no OAuth2 path yet |
| `failedAttempts` type | "INT … NOT NULL … Default: 0" | ⚪ Confirmed — primitive `int` with builder default `0` |
| `isLocked` auto-expiry | Not documented | 🟢 `isAccountNonLocked()` auto-expires the lock after `SecurityConstants.LOCK_DURATION_MINUTES` |
| DB unique constraints | Implied | 🟢 Documented: `uk_users_username`, `uk_users_email` |
| `@JsonIgnore` annotations | Not documented | 🟢 Confirmed on `password`, `resetToken`, `resetTokenExpiration`, `lockTime` |

### C) Data model — newly documented entities

| Entity | Old doc | New doc |
| --- | --- | --- |
| `Session` (`sessions` table) | Only mentioned indirectly via `SessionDTO` | 🟢 Full entity now documented: `id`, `sessionId` (UUID, unique), FK `user_id`, `deviceInfo`, `ipAddress`, `loginTimestamp`, `expiresAt`, `isActive`, `logoutTimestamp` |
| `TokenBlacklist` (`token_blacklist` table) | Not documented | 🟢 New table for logout-on-the-fly: `token` (unique, 512), `blacklistedAt`, `expiresAt` |

### D) Roles & Permissions

| Item | Old | New |
| --- | --- | --- |
| 4 roles (`GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`) | Documented | ⚪ Unchanged |
| Permission strings | Old presented as `read/create/update/delete` checkboxes only | 🟢 Now documented as the actual `Permission` enum constants (`USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE`) with the colon-format permission keys (`user:create`, …) |
| `SUPER_ADMIN` vs. `ADMIN` | Implied identical | 🟡 Clarified: identical permission sets in the current code |
| `ROLE_<NAME>` prefix | Not documented | 🟢 Documented — Spring Security adds it alongside the permission authorities |

### E) DTO comparison

| Item | Old doc | Code reality |
| --- | --- | --- |
| `RegisterRequestDTO` | Documented | ⚪ Unchanged |
| `LoginRequestDTO` | Documented | ⚪ Unchanged |
| `PasswordResetRequestDTO` | Documented | ⚪ Unchanged |
| `UpdateProfileRequestDTO` | Documented (empty-string allowed via `^$|^[A-Za-z0-9_]+$`) | ⚪ Unchanged |
| `ChangePasswordRequestDTO` | Documented | ⚪ Unchanged |
| `SessionDTO` | Documented | ⚪ Unchanged |
| `UserResponseDTO.imageUrl` | Documented | 🔴 **Does NOT exist** — DTO has no `imageUrl` field |
| `UserResponseDTO.provider` | Documented | 🔴 **Does NOT exist** — DTO has no `provider` field |
| `Token` DTO | Implied | 🟡 Light-touch documentation — exact field names depend on the `Token` class in `user.jwt` |

### F) Error / response envelope

| Endpoint group | Old | New |
| --- | --- | --- |
| Plain-text bodies on `/logout`, `/logout-all`, `/reset-token`, `/reset-password`, and `/sessions/*` | Implied JSON | 🟡 **Clarified**: these endpoints return plain `String` bodies, not JSON envelopes |
| `/api/user/password` response | `{ "message": "Password updated successfully" }` | ⚪ Confirmed — `Map<String, String>` with `message` key |
| `/api/user/account` response | `204 No Content` with empty body | ⚪ Confirmed |
| Validation error body | `{ timestamp, status, errors[] }` | ⚪ Confirmed via `GlobalExceptionHandler` |

### G) Summary

- 🟢 **Added (endpoints):** the three `SessionAPI` endpoints under `/api/auth/sessions/*` (`getAllSessions`, `DELETE /{sessionId}`, `revokeAll`) were completely missing from the old doc but are fully wired in the code.
- 🟢 **Added (data models):** the `Session` and `TokenBlacklist` entities are now documented with their full column lists, constraints, and roles in the auth flow.
- 🟢 **Added (auth model details):** the `Permission` enum with its colon-format permission strings (`user:create`, etc.); the `ROLE_<NAME>` prefix added by Spring Security; the auto-expiry behaviour of `isAccountNonLocked()` after `SecurityConstants.LOCK_DURATION_MINUTES`; the `@JsonIgnore` markers on sensitive User fields.
- 🔴 **Removed (false claims):** the User entity has no `imageUrl`, `provider`, or `providerId` columns — OAuth2 was advertised in the old doc but is **not implemented** in the current code. `UserResponseDTO` likewise has no `imageUrl` / `provider` fields. The `password` column is `nullable = false` (the old doc's "NULL for OAuth2-only users" caveat does not apply).
- 🟡 **Clarified:** plain-text response bodies on `/logout`, `/logout-all`, `/reset-token`, `/reset-password`, and `/sessions/*` endpoints (these are `String`, not JSON envelopes); the `Authentication#getName()` rather than `@AuthenticationPrincipal UserDetails` pattern in `UserProfileAPI` (and the reason — the JWT filter previously stored a plain `String` as the principal); `DELETE /api/auth/sessions/{sessionId}` enforces owner-only revocation (cross-user revoke returns `403`); `DELETE /api/auth/sessions/revokeAll` does NOT blacklist the current JWT or clear the cookie — use `/logout-all` for that.
- ⚪ **Unchanged:** all 13 endpoints from the old doc still exist with the same paths, params, and behaviour; all four roles; all field-level validation rules; the HttpOnly cookie + Bearer header dual delivery; brute-force lockout; reset-token flow.
