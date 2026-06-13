# User Auth API — Internal (Authenticated)

**Base URL:** `/api/auth` (logout & sessions) · `/api/user` (profile)
**Platform:** Spring Boot 3 · JWT (Bearer token or HttpOnly cookie `auth-token`) · BCrypt
**Note:** All endpoints here require a valid JWT. Pass as `Authorization: Bearer <token>` header or it is read automatically from the `auth-token` HttpOnly cookie set at login.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `POST` | `/api/auth/logout` | Yes | Any authenticated | Logout from current device — blacklists token & clears cookie |
| `POST` | `/api/auth/logout-all` | Yes | Any authenticated | Logout from all devices — deactivates all sessions |
| `GET` | `/api/auth/sessions/getAllSessions` | Yes | Any authenticated | List all active sessions for current user |
| `DELETE` | `/api/auth/sessions/{sessionId}` | Yes | Any authenticated | Revoke a specific session by session ID |
| `DELETE` | `/api/auth/sessions/revokeAll` | Yes | Any authenticated | Revoke all active sessions for current user |
| `GET` | `/api/user/me` | Yes | Any authenticated | Get current user's profile |
| `PUT` | `/api/user/profile` | Yes | Any authenticated | Update name and/or username |
| `PUT` | `/api/user/password` | Yes | Any authenticated | Change password |
| `POST` | `/api/user/profile-image` | Yes | Any authenticated | Upload or replace profile image |
| `DELETE` | `/api/user/profile-image` | Yes | Any authenticated | Remove profile image |
| `DELETE` | `/api/user/account` | Yes | Any authenticated | Permanently delete the account |

---

## `POST /api/auth/logout` — Logout (Current Device)

**Auth:** JWT required · Role: Any authenticated user
**Headers:** `Authorization: Bearer <token>` — or uses `auth-token` cookie automatically

Blacklists the current JWT token so it cannot be reused, and clears the `auth-token` HttpOnly cookie.

**Request Body:** None

**Response `200 OK`:**
```
"Successfully logged out"
```

---

## `POST /api/auth/logout-all` — Logout All Devices

**Auth:** JWT required · Role: Any authenticated user

Marks every active `Session` row for the current user as inactive in the database, then blacklists the current JWT so it stops working immediately. Clears the `auth-token` cookie.

**Request Body:** None

**Response `200 OK`:**
```
"Logged out from all devices successfully"
```

---

## `GET /api/auth/sessions/getAllSessions` — List Active Sessions

**Auth:** JWT required · Role: Any authenticated user
**Query Params:** None

**Response `200 OK`:**
```json
[
  {
    "sessionId": "sess_a1b2c3d4e5f6",
    "deviceInfo": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "ipAddress": "192.168.1.10",
    "loginTimestamp": "2026-06-12T08:00:00Z",
    "expiresAt": "2026-06-19T08:00:00Z",
    "isActive": true,
    "logoutTimestamp": null
  },
  {
    "sessionId": "sess_z9y8x7w6v5u4",
    "deviceInfo": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)",
    "ipAddress": "10.0.0.5",
    "loginTimestamp": "2026-06-11T20:00:00Z",
    "expiresAt": "2026-06-18T20:00:00Z",
    "isActive": true,
    "logoutTimestamp": null
  }
]
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | string | Unique session identifier |
| `deviceInfo` | string | Browser/device user-agent string |
| `ipAddress` | string | IP address at login time |
| `loginTimestamp` | string | ISO-8601 login time |
| `expiresAt` | string | ISO-8601 session expiry time |
| `isActive` | boolean | Whether session is still active |
| `logoutTimestamp` | string | ISO-8601 logout time (null if still active) |

---

## `DELETE /api/auth/sessions/{sessionId}` — Revoke a Specific Session

**Auth:** JWT required · Role: Any authenticated user (can only revoke own sessions)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sessionId` | string | **Yes** | The session identifier to revoke |

**Request Body:** None

**Response `200 OK`:**
```
"Session revoked successfully"
```

---

## `DELETE /api/auth/sessions/revokeAll` — Revoke All Sessions

**Auth:** JWT required · Role: Any authenticated user

Deactivates all active sessions for the authenticated user. The user will need to log in again on all devices.

**Request Body:** None

**Response `200 OK`:**
```
"All sessions revoked successfully"
```

---

## `GET /api/user/me` — Get Current User Profile

**Auth:** JWT required · Role: Any authenticated user
**Query Params:** None

**Response `200 OK`:**
```json
{
  "userId": 5,
  "name": "ئارام کریم",
  "username": "aram_karim",
  "email": "aram@example.com",
  "role": "USER",
  "pincode": 1234,
  "isActivated": true,
  "profileImage": "https://cdn.khi.org/users/aram_karim/avatar.jpg",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-06-12T10:00:00Z",
  "passwordExpiryDate": null
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `userId` | long | Unique user identifier |
| `name` | string | Display name |
| `username` | string | Unique username (used for login) |
| `email` | string | Email address |
| `role` | enum | `USER` \| `EMPLOYEE` \| `ADMIN` \| `SUPER_ADMIN` |
| `pincode` | long | Optional security PIN (may be null) |
| `isActivated` | boolean | Whether the account is active |
| `profileImage` | string | Profile image URL (may be null) |
| `createdAt` | string | ISO-8601 account creation time |
| `updatedAt` | string | ISO-8601 last update time |
| `passwordExpiryDate` | string | Password expiry time (may be null) |

---

## `PUT /api/user/profile` — Update Profile

**Auth:** JWT required · Role: Any authenticated user
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | string | No | New username — 3–80 chars, letters/numbers/underscores only |
| `name` | string | No | New display name — max 120 chars |

> At least one field should be provided. Omitting a field leaves it unchanged.

**Request JSON:**
```json
{
  "username": "aram_k_2026",
  "name": "ئارام ئیبراهیم کریم"
}
```

**Response `200 OK`:** Updated user profile object (same shape as `GET /api/user/me`).

---

## `PUT /api/user/password` — Change Password

**Auth:** JWT required · Role: Any authenticated user
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `currentPassword` | string | **Yes** | User's existing/current password (6–128 chars) |
| `newPassword` | string | **Yes** | New password — must be at least 6 chars (6–128 chars) |
| `confirmPassword` | string | **Yes** | Must exactly match `newPassword` — verified server-side |

**Request JSON:**
```json
{
  "currentPassword": "OldPassword123",
  "newPassword": "NewSecurePass456",
  "confirmPassword": "NewSecurePass456"
}
```

**Response `200 OK`:**
```json
{ "message": "Password updated successfully" }
```

---

## `POST /api/user/profile-image` — Upload Profile Image

**Auth:** JWT required · Role: Any authenticated user
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `file` | file | **Yes** | Profile image file (JPEG, PNG, etc.) |

**Response `200 OK`:** Updated user profile object with new `profileImage` URL populated.
```json
{
  "userId": 5,
  "name": "ئارام کریم",
  "username": "aram_karim",
  "email": "aram@example.com",
  "role": "USER",
  "profileImage": "https://cdn.khi.org/users/aram_karim/avatar-new.jpg",
  "isActivated": true,
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-06-12T10:30:00Z"
}
```

---

## `DELETE /api/user/profile-image` — Remove Profile Image

**Auth:** JWT required · Role: Any authenticated user
**Request Body:** None

**Response `200 OK`:** Updated user profile object with `profileImage: null`.
```json
{
  "userId": 5,
  "name": "ئارام کریم",
  "username": "aram_karim",
  "profileImage": null,
  "updatedAt": "2026-06-12T10:35:00Z"
}
```

---

## `DELETE /api/user/account` — Delete Account

**Auth:** JWT required · Role: Any authenticated user

Permanently deletes the authenticated user's account and all associated data. This action is irreversible.

**Request Body:** None

**Response `204 No Content`:** Empty body — account deleted.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Validation failure — missing field, passwords don't match, username format invalid, or password too short/long |
| `401 Unauthorized` | JWT is missing, invalid, expired, or has been blacklisted (logged out) |
| `403 Forbidden` | Attempting to revoke another user's session |
| `404 Not Found` | Session with the given `sessionId` does not exist |
| `409 Conflict` | New username is already taken by another account |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
