# User Auth API — External (Public)

**Base URL:** `/api/auth`
**Platform:** Spring Boot 3 · JWT + HttpOnly Cookie · BCrypt · Spring Security 6
**Note:** No authentication required for these endpoints. On successful register/login, a JWT is returned in the response body **and** set as an HttpOnly cookie (`auth-token`). The frontend can use either method for subsequent authenticated requests.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `POST` | `/api/auth/register` | No | Register with JSON body (no image) |
| `POST` | `/api/auth/register-with-image` | No | Register with profile image (multipart) |
| `POST` | `/api/auth/login` | No | Login with username or email |
| `POST` | `/api/auth/reset-token` | No | Request a password-reset token via email |
| `POST` | `/api/auth/reset-password` | No | Reset password using the emailed token |

---

## `POST /api/auth/register` — Register (JSON)

**Auth:** None — public
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | **Yes** | Display name — max 120 characters |
| `username` | string | **Yes** | Unique username — 3–80 chars, only letters, numbers, and underscores (`_`) |
| `email` | string | **Yes** | Valid email address — max 160 characters |
| `password` | string | **Yes** | Account password — 6–128 characters |
| `pincode` | long | No | Optional numeric security PIN |

**Request JSON:**
```json
{
  "name": "ئارام کریم",
  "username": "aram_karim",
  "email": "aram@example.com",
  "password": "SecurePass123",
  "pincode": 1234
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhcmFtX2thcmltIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MTgxODAwMDAsImV4cCI6MTcxODc4NDgwMH0.signature",
  "userId": 5,
  "username": "aram_karim",
  "role": "USER"
}
```

> JWT is also set as `auth-token` HttpOnly cookie on the response. New accounts are assigned `role: USER` by default.

---

## `POST /api/auth/register-with-image` — Register with Profile Image (Multipart)

**Auth:** None — public
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `data` | string (JSON) | **Yes** | Serialized registration object — same fields as `/register` |
| `image` | file | No | Profile image file (JPEG, PNG, etc.) — optional |

**`data` JSON (same as `/register`):**
```json
{
  "name": "ئارام کریم",
  "username": "aram_karim",
  "email": "aram@example.com",
  "password": "SecurePass123",
  "pincode": 1234
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 5,
  "username": "aram_karim",
  "role": "USER"
}
```

> JWT is also set as `auth-token` HttpOnly cookie. Profile image URL is saved to the user record.

---

## `POST /api/auth/login` — Login

**Auth:** None — public
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | string | **Yes** | The user's username **or** email address — max 160 characters |
| `password` | string | **Yes** | Account password — 6–128 characters |

**Request JSON (login with username):**
```json
{
  "username": "aram_karim",
  "password": "SecurePass123"
}
```

**Request JSON (login with email):**
```json
{
  "username": "aram@example.com",
  "password": "SecurePass123"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhcmFtX2thcmltIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MTgxODAwMDAsImV4cCI6MTcxODc4NDgwMH0.signature",
  "userId": 5,
  "username": "aram_karim",
  "role": "USER"
}
```

> JWT is also set as `auth-token` HttpOnly cookie. A new `Session` record is created with device info and IP address.

---

## `POST /api/auth/reset-token` — Request Password Reset Token

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `email` | string | **Yes** | Registered email address — valid format, max 160 chars |

**Request Body:** None (email is passed as query param)

**Example:** `POST /api/auth/reset-token?email=aram@example.com`

**Response `200 OK`:**
```
"Password reset token sent to your email"
```

> A time-limited reset token is generated and sent to the provided email address. The token is required for the next step.

---

## `POST /api/auth/reset-password` — Reset Password

**Auth:** None — public
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `email` | string | **Yes** | Registered email address — valid format, max 160 chars |
| `resetToken` | string | **Yes** | Token received in the password-reset email |
| `newPassword` | string | **Yes** | New password — 6–128 characters |
| `confirmPassword` | string | **Yes** | Must exactly match `newPassword` — verified server-side |

**Request JSON:**
```json
{
  "email": "aram@example.com",
  "resetToken": "a1b2c3d4e5f6g7h8i9j0",
  "newPassword": "NewSecurePass456",
  "confirmPassword": "NewSecurePass456"
}
```

**Response `200 OK`:**
```
"Password has been reset successfully"
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required field; `name` > 120 chars; `username` < 3 or > 80 chars or contains invalid characters; `email` invalid format; `password` < 6 or > 128 chars; `confirmPassword` does not match `newPassword` |
| `401 Unauthorized` | Wrong username/email or password on login |
| `404 Not Found` | Email address not registered (reset-token) |
| `409 Conflict` | Username or email already registered (register) |
| `422 Unprocessable Entity` | Reset token is invalid, expired, or already used |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
