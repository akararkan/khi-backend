# User Profile Module (Self-Service)

> Elevator pitch: authenticated users manage their own name, username, password, and profile image via `/api/user/*`.

## Table of Contents
- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — UserResponseDTO](#02--data-model--userresponsedto)
- [03 · Public API](#03--public-api)
- [04 · Internal API (Self-Service)](#04--internal-api-self-service)
- [05 · DTO Reference](#05--dto-reference)
- [06 · Error Responses](#06--error-responses)
- [07 · Notes](#07--notes)

---

## 01 · Module Overview

- **Base path:** `/api/user/*` (singular — not `/api/users`)
- **Authentication:** all endpoints require a valid JWT supplied either as the `jwt` HttpOnly cookie OR an `Authorization: Bearer <token>` header.
- **Authorization:** any authenticated role may operate on its own profile (`GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`).
- **Principal resolution:** the controller calls `Authentication#getName()` rather than `@AuthenticationPrincipal UserDetails`. The principal stored by the JWT filter is a plain `String` (the username), so `@AuthenticationPrincipal` would inject `null`. `Authentication#getName()` is safe for both `String` and `UserDetails` principals.

### Endpoint summary

| Method | Path                       | Description                              | Content-Type            |
|--------|----------------------------|------------------------------------------|-------------------------|
| GET    | `/api/user/me`             | Return the currently authenticated user. | —                       |
| PUT    | `/api/user/profile`        | Update own `name` and/or `username`.     | `application/json`      |
| PUT    | `/api/user/password`       | Change own password.                     | `application/json`      |
| POST   | `/api/user/profile-image`  | Upload/replace own profile image.        | `multipart/form-data`   |
| DELETE | `/api/user/profile-image`  | Clear own profile image.                 | —                       |
| DELETE | `/api/user/account`        | Hard-delete own account.                 | —                       |

---

## 02 · Data Model — UserResponseDTO

The public shape returned to the authenticated user about themselves. Source: `UserResponseDTO.java`. The password column on the entity is annotated `@JsonIgnore` and is never returned.

| Field                | Type                  | Description                                                   |
|----------------------|-----------------------|---------------------------------------------------------------|
| `userId`             | `Long`                | Primary key from `users_tbl`.                                 |
| `name`               | `String`              | Display name (max 120 chars on entity).                       |
| `username`           | `String`              | Unique handle (max 80 chars, letters/digits/underscore).      |
| `email`              | `String`              | Unique email (max 160 chars).                                 |
| `role`               | `Role` (enum)         | One of `GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`.           |
| `pincode`            | `Long`                | 6-digit code persisted on the user row.                       |
| `isActivated`        | `Boolean`             | Account activation flag.                                      |
| `profileImage`       | `String`              | Full public S3 HTTPS URL, or `null` when no image is set.     |
| `createdAt`          | `Instant` (ISO-8601)  | Creation timestamp.                                           |
| `updatedAt`          | `Instant` (ISO-8601)  | Last update timestamp (refreshed on every mutation).          |
| `passwordExpiryDate` | `Instant` (ISO-8601)  | Set to `now + 90 days` after every successful password change. |

---

## 03 · Public API

All endpoints in this module require authentication — see §04.

---

## 04 · Internal API (Self-Service)

### GET `/api/user/me`

Returns the full `UserResponseDTO` for the currently authenticated principal. The username is looked up via `userRepository.findByUsername(auth.getName())`.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** —
- **Request body:** none
- **Response:** `200 OK` with `UserResponseDTO`

#### Example request

```bash
curl -X GET https://api.example.com/api/user/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### Example response — `200 OK`

```json
{
  "userId": 42,
  "name": "Avesta Karim",
  "username": "avesta_k",
  "email": "avesta@example.com",
  "role": "EMPLOYEE",
  "pincode": 482931,
  "isActivated": true,
  "profileImage": "https://khi-web-folders.s3.eu-central-1.amazonaws.com/images/9f3a-user_profile_images_avatar.png",
  "createdAt": "2026-01-12T09:14:22Z",
  "updatedAt": "2026-05-30T17:02:11Z",
  "passwordExpiryDate": "2026-08-28T17:02:11Z"
}
```

---

### PUT `/api/user/profile`

Updates the authenticated user's `name` and/or `username`. Both fields are optional; blank `username` is rejected by the regex. If a new `username` is supplied and is already taken by another user, a 409 `UserAlreadyExistsException` is thrown (message: `ناوی بەکارهێنەر پێشتر بەکارهاتووە`). `updatedAt` is refreshed.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** `application/json`
- **Request body:** `UpdateProfileRequestDTO` — see §05

| Field      | Type     | Required | Validation                                                                                                |
|------------|----------|----------|-----------------------------------------------------------------------------------------------------------|
| `username` | `String` | optional | `@Size(min=3,max=80)`, `@Pattern("^$\|^[A-Za-z0-9_]+$")` (empty allowed = no change)                       |
| `name`     | `String` | optional | `@Size(max=120)`                                                                                          |

- **Response:** `200 OK` with the updated `UserResponseDTO`

#### Example request

```bash
curl -X PUT https://api.example.com/api/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
        "username": "avesta_2026",
        "name": "Avesta K."
      }'
```

#### Example response — `200 OK`

```json
{
  "userId": 42,
  "name": "Avesta K.",
  "username": "avesta_2026",
  "email": "avesta@example.com",
  "role": "EMPLOYEE",
  "pincode": 482931,
  "isActivated": true,
  "profileImage": "https://khi-web-folders.s3.eu-central-1.amazonaws.com/images/9f3a-user_profile_images_avatar.png",
  "createdAt": "2026-01-12T09:14:22Z",
  "updatedAt": "2026-05-31T10:48:09Z",
  "passwordExpiryDate": "2026-08-28T17:02:11Z"
}
```

---

### PUT `/api/user/password`

Changes the authenticated user's password. The service:

1. Verifies `currentPassword` against the stored hash (`BadCredentialsException` → 401 if wrong; message `وشەی نهێنیی ئێستا هەڵەیە`).
2. Verifies `newPassword.equals(confirmPassword)` (`IllegalArgumentException` if not).
3. Runs `UserValidator.validatePassword(newPassword, username, email, name)` — complexity, personal-info, blocklist, sequences.
4. Runs `UserValidator.validatePasswordNotReused(newPassword, oldHash, encoder)` — prevents reusing the current password.
5. Hashes and persists the new password, sets `passwordExpiryDate = now + 90 days`, and refreshes `updatedAt`.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** `application/json`
- **Request body:** `ChangePasswordRequestDTO` — see §05

| Field             | Type     | Required | Validation                                  |
|-------------------|----------|----------|---------------------------------------------|
| `currentPassword` | `String` | yes      | `@NotBlank`, `@Size(min=6,max=128)`         |
| `newPassword`     | `String` | yes      | `@NotBlank`, `@Size(min=6,max=128)`         |
| `confirmPassword` | `String` | yes      | `@NotBlank`, `@Size(min=6,max=128)`         |

- **Response:** `200 OK` with `{"message":"Password updated successfully"}`

#### Example request

```bash
curl -X PUT https://api.example.com/api/user/password \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
        "currentPassword": "OldPass1!",
        "newPassword":     "NewStr0ng#Pass",
        "confirmPassword": "NewStr0ng#Pass"
      }'
```

#### Example response — `200 OK`

```json
{
  "message": "Password updated successfully"
}
```

---

### POST `/api/user/profile-image`

Uploads (or replaces) the authenticated user's profile picture. The image is stored in S3 under the `user_profile_images` logical folder, and the full public HTTPS URL is persisted on `users_tbl.profile_image`. When replacing, the previous S3 object is deleted first. Legacy local-filesystem paths are silently skipped on delete.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** `multipart/form-data`
- **Multipart parts:**

| Part   | Type           | Required | Constraints                                                                                  |
|--------|----------------|----------|----------------------------------------------------------------------------------------------|
| `file` | `MultipartFile`| yes      | non-empty; `size ≤ 5 MB`; `Content-Type ∈ { image/jpeg, image/png, image/gif, image/webp }`  |

Service-enforced validation errors (`IllegalArgumentException`, message in Kurdish):

| Condition                              | Message                                                |
|----------------------------------------|--------------------------------------------------------|
| file is null/empty                     | `فایلەکە بەتاڵە`                                       |
| file size > 5 MB                       | `قەبارەی وێنە دەبێت لە ٥ مێگابایت کەمتر بێت`           |
| content-type not in allowed list       | `تەنها JPEG, PNG, GIF, WebP قبوڵ دەکرێت`               |
| IOException reading bytes              | `بارکردنی فایل سەرکەوتوو نەبوو`                        |

- **Response:** `200 OK` with the updated `UserResponseDTO` (new `profileImage` URL).

#### Example request

```bash
curl -X POST https://api.example.com/api/user/profile-image \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -F "file=@./avatar.png;type=image/png"
```

#### Example response — `200 OK`

```json
{
  "userId": 42,
  "name": "Avesta K.",
  "username": "avesta_2026",
  "email": "avesta@example.com",
  "role": "EMPLOYEE",
  "pincode": 482931,
  "isActivated": true,
  "profileImage": "https://khi-web-folders.s3.eu-central-1.amazonaws.com/images/a1b2c3-user_profile_images_avatar.png",
  "createdAt": "2026-01-12T09:14:22Z",
  "updatedAt": "2026-05-31T10:55:01Z",
  "passwordExpiryDate": "2026-08-28T17:02:11Z"
}
```

---

### DELETE `/api/user/profile-image`

Clears the authenticated user's `profileImage` field. The corresponding S3 object (if any and if owned by our bucket) is deleted; legacy local paths are skipped. `updatedAt` is refreshed.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** —
- **Request body:** none
- **Response:** `200 OK` with the updated `UserResponseDTO` (`profileImage` is `null`).

#### Example request

```bash
curl -X DELETE https://api.example.com/api/user/profile-image \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### Example response — `200 OK`

```json
{
  "userId": 42,
  "name": "Avesta K.",
  "username": "avesta_2026",
  "email": "avesta@example.com",
  "role": "EMPLOYEE",
  "pincode": 482931,
  "isActivated": true,
  "profileImage": null,
  "createdAt": "2026-01-12T09:14:22Z",
  "updatedAt": "2026-05-31T10:57:43Z",
  "passwordExpiryDate": "2026-08-28T17:02:11Z"
}
```

---

### DELETE `/api/user/account`

Permanently deletes the authenticated user's account. The service:

1. Deletes the user's S3 profile image (if owned by our bucket).
2. Deletes all `Session` rows belonging to the user via `sessionRepository.deleteAll(sessionRepository.findByUser(user))`.
3. Hard-deletes the row from `users_tbl`.

- **Authentication:** Required (JWT cookie OR Bearer header)
- **Roles:** any authenticated role
- **Content-Type:** —
- **Request body:** none
- **Response:** `204 No Content` (empty body)

#### Example request

```bash
curl -X DELETE https://api.example.com/api/user/account \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -i
```

#### Example response

```
HTTP/1.1 204 No Content
```

---

## 05 · DTO Reference

### `UpdateProfileRequestDTO`

```java
@Data
public class UpdateProfileRequestDTO {
    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Pattern(regexp = "^$|^[A-Za-z0-9_]+$", message = "Username can contain only letters, numbers, and underscores")
    private String username;

    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;
}
```

| Field      | Type     | Required | Annotations                                                                                                                                                                       | Notes                                                                                  |
|------------|----------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `username` | `String` | no       | `@Size(min=3, max=80, message="Username must be between 3 and 80 characters")`, `@Pattern(regexp="^$\|^[A-Za-z0-9_]+$", message="Username can contain only letters, numbers, and underscores")` | Empty string is accepted by the regex (`^$\|...`) and is treated as "no change". Source pattern: `ValidationPatterns.USERNAME_OR_EMPTY`. |
| `name`     | `String` | no       | `@Size(max=120, message="Name must not exceed 120 characters")`                                                                                                                   | No regex; any characters allowed up to 120.                                            |

### `ChangePasswordRequestDTO`

```java
@Data
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Current password is required")
    @Size(min = 6, max = 128, message = "Current password must be at least 6 characters")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 128, message = "New password must be at least 6 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Size(min = 6, max = 128, message = "Confirm password must be at least 6 characters")
    private String confirmPassword;
}
```

| Field             | Type     | Required | Annotations                                                                                                                                  | Notes                                                                                                    |
|-------------------|----------|----------|----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `currentPassword` | `String` | yes      | `@NotBlank(message="Current password is required")`, `@Size(min=6, max=128, message="Current password must be at least 6 characters")`        | Compared to the stored bcrypt hash; `BadCredentialsException` if it does not match.                      |
| `newPassword`     | `String` | yes      | `@NotBlank(message="New password is required")`, `@Size(min=6, max=128, message="New password must be at least 6 characters")`                | Must pass `UserValidator.validatePassword(...)` (complexity, personal-info, blocklist, sequences) and `validatePasswordNotReused(...)`. |
| `confirmPassword` | `String` | yes      | `@NotBlank(message="Confirm password is required")`, `@Size(min=6, max=128, message="Confirm password must be at least 6 characters")`        | Must be string-equal to `newPassword` (checked in the service layer).                                    |

---

## 06 · Error Responses

All errors are emitted by the global exception handler as a bilingual `ApiErrorResponse` envelope:

```json
{
  "timestamp":  "2026-05-31T10:48:09.482Z",
  "status":     400,
  "path":       "/api/user/profile",
  "method":     "PUT",
  "traceId":    "0a3f9b4c8d2e",
  "code":       "VALIDATION_ERROR",
  "message":    "Validation failed",
  "messageEn":  "Validation failed",
  "messageKu":  "زانیارییەکان دروست نین",
  "fieldErrors": {
    "username": "Username can contain only letters, numbers, and underscores"
  },
  "details":    null
}
```

Common codes used by this module:

| HTTP | `code`               | When                                                                                          |
|------|----------------------|-----------------------------------------------------------------------------------------------|
| 401  | `UNAUTHORIZED`       | Missing/invalid JWT cookie or Bearer; or `currentPassword` is wrong on `PUT /api/user/password`. |
| 400  | `VALIDATION_ERROR`   | DTO `@Valid` constraints fail (e.g. `username` regex, `@Size`, `@NotBlank`).                  |
| 404  | `NOT_FOUND`          | `UsernameNotFoundException` — authenticated principal cannot be found in `users_tbl`.         |
| 409  | `CONFLICT`           | `UpdateProfileRequestDTO.username` is already in use by another user (`UserAlreadyExistsException`). |
| 413  | `PAYLOAD_TOO_LARGE`  | Uploaded image exceeds `5 MB` on `POST /api/user/profile-image`.                              |
| 400  | `VALIDATION_ERROR`   | Uploaded image has a disallowed content-type, or is null/empty.                               |

---

## 07 · Notes

- `DELETE /api/user/account` is a **hard delete** — the user row is removed; sessions are removed first; the controller returns `204 No Content` with no body.
- `POST /api/user/profile-image` accepts a single `file` part (`multipart/form-data`). The response is the updated `UserResponseDTO` with the new `profileImage` URL pointing to S3.
- `DELETE /api/user/profile-image` clears the `profileImage` field on the user. The S3 object is deleted only if the stored URL is recognised as belonging to our bucket (`S3Service#isOurS3Url`); legacy local-filesystem paths are silently skipped.
- Authentication principal is extracted via `Authentication#getName()` — Spring's `@AuthenticationPrincipal` is **not** used here because the JWT filter stores a plain `String` principal, which would cause `@AuthenticationPrincipal UserDetails` to inject `null`. See the JavaDoc on `UserProfileAPI` for the full rationale.
- Allowed image content-types are fixed in the service: `image/jpeg`, `image/png`, `image/gif`, `image/webp`. Max file size is `5 * 1024 * 1024` bytes (5 MB).
- Successful password changes set `passwordExpiryDate = Instant.now().plus(Duration.ofDays(90))`.
