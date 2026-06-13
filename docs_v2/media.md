# Media Module (Shared S3 Upload Pipeline)

> Single upload endpoint reused by every Tiptap editor across the platform (News, Projects, About, Contact, Services, SoundTrack, Video, Image, Writing).

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Upload Flow](#02--upload-flow)
- [03 · Authentication & Roles](#03--authentication--roles)
- [04 · Public API](#04--public-api)
- [05 · Internal API (Authenticated)](#05--internal-api-authenticated)
- [06 · DTO Reference](#06--dto-reference)
- [07 · Response Envelope](#07--response-envelope)
- [08 · Error Responses](#08--error-responses)
- [09 · Notes — TiptapHtmlProcessor (Safety Net)](#09--notes--tiptaphtmlprocessor-safety-net)

---

## 01 · Module Overview

The Media module is a thin, shared HTTP surface that pushes binary assets into S3 on behalf of every Tiptap-powered editor in the platform. It is intentionally neutral — it does not own any database row, it has no relationship to a parent entity, and it does not enforce a content schema. Its only job is to take a multipart upload, hand the bytes to `S3Service`, and return the public URL the editor will splice into its HTML.

- **Base path:** `/api/v1/media`
- **Controller:** `MediaController`
- **Service:** `MediaService` (delegates to `S3Service`)
- **DTO bundle:** `MediaDtos`
- **Auth model:** any authenticated user (any role)

### Endpoint summary

| Method | Path                           | Description                                                  | Auth                |
| ------ | ------------------------------ | ------------------------------------------------------------ | ------------------- |
| POST   | `/api/v1/media/upload`          | Upload a single media file (multipart) and receive its S3 URL. | Authenticated (any role) |
| POST   | `/api/v1/media/upload/multiple` | Upload N files in one request; returns a list of URLs.        | Authenticated (any role) |
| DELETE | `/api/v1/media`                 | Delete a previously uploaded asset by its `fileUrl`.          | Authenticated (any role) |

### `type` part values

The optional `type` multipart part is treated as a folder hint by `MediaService.resolveMediaType(...)`.

| `type` value | Resolved folder (`ProjectMediaType`) |
| ------------ | ------------------------------------ |
| `image`      | `IMAGE`                              |
| `gallery`    | `IMAGE` (alias)                      |
| `video`      | `VIDEO`                              |
| `audio`      | `AUDIO`                              |
| `document`   | `DOCUMENT`                           |
| `pdf`        | `DOCUMENT` (alias)                   |
| _(omitted / blank / unknown)_ | folder is inferred from the file's content type by `S3Service` |

The controller defaults to `"image"` when the `type` part is missing or blank.

---

## 02 · Upload Flow

1. User drops a file (image, audio, video, PDF, etc.) into a Tiptap editor on the frontend.
2. Frontend POSTs the file as `multipart/form-data` to `POST /api/v1/media/upload` with an optional `type` hint.
3. Backend (`MediaService.upload`) hands the bytes to `S3Service`, which writes to the folder implied by `type` and returns a public `fileUrl`.
4. Frontend inserts the returned `fileUrl` into the editor HTML as `<img src="...">`, `<video src="...">`, `<a href="...">`, etc.
5. The final HTML is persisted into the owning entity's bilingual `description` / `body` column (News, Projects, About, Contact, Services, SoundTrack, Video, Image, Writing). The database never sees the raw bytes.

A safety net (see §09) re-runs the same logic server-side at save time: any inline `data:` URI that slipped through is decoded, uploaded, and rewritten to an S3 URL before the row is committed.

---

## 03 · Authentication & Roles

- All three endpoints fall through to `SecurityConfig`'s default `.anyRequest().authenticated()` rule — there is no role-based matcher for `/api/v1/media/**` on write methods.
- Any authenticated user, regardless of role, may upload or delete media. This is intentional: editors of every role need to drop assets into their Tiptap content.
- There is no rate limiting, no per-user quota, and no ownership check on `DELETE` — any authenticated caller may delete any S3 object by URL. Treat the delete endpoint as an admin-grade tool.
- The module exposes no anonymous (`GET`) endpoints.

---

## 04 · Public API

**This module has no public endpoints.** Media URLs themselves (S3 / CDN) are served publicly outside the application, but every endpoint under `/api/v1/media/**` requires authentication.

---

## 05 · Internal API (Authenticated)

All endpoints below require a valid `Authorization: Bearer <jwt>` header.

### 5.1 · `POST /api/v1/media/upload`

Upload a single media file to S3.

- **Content-Type:** `multipart/form-data`
- **Multipart parts:**

| Part   | Required | Type            | Description                                                                                  |
| ------ | -------- | --------------- | -------------------------------------------------------------------------------------------- |
| `file` | yes      | `MultipartFile` | The binary payload to upload.                                                                |
| `type` | no       | `String`        | Folder hint — `image` (default), `audio`, `video`, `document`, `gallery`, or `pdf` alias.    |

- **Response:** `ApiResponse<UploadResponse>`

**curl**

```bash
curl -X POST 'https://api.example.com/api/v1/media/upload' \
  -H 'Authorization: Bearer eyJhbGciOi...' \
  -F 'file=@/path/to.jpg' \
  -F 'type=image'
```

**Sample 200 response**

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "fileUrl": "https://cdn.example.com/images/2026/05/31/abc123.jpg",
    "fileName": "to.jpg",
    "fileSize": 184320,
    "contentType": "image/jpeg"
  }
}
```

---

### 5.2 · `POST /api/v1/media/upload/multiple`

Upload several files in a single multipart request. Useful for Tiptap drag-and-drop of a folder, or for "insert multiple images" flows in the editor.

- **Content-Type:** `multipart/form-data`
- **Multipart parts:**

| Part    | Required | Type                  | Description                                                                                  |
| ------- | -------- | --------------------- | -------------------------------------------------------------------------------------------- |
| `files` | yes      | `List<MultipartFile>` | One or more file parts, each named `files`.                                                  |
| `type`  | no       | `String`              | Folder hint applied to every file in the batch — `image` (default), `audio`, `video`, `document`, `gallery`, `pdf`. |

- **Response:** `ApiResponse<List<UploadResponse>>`

If any individual file fails to upload, the controller bubbles the error up (the partial successes already written to S3 are not rolled back).

**curl**

```bash
curl -X POST 'https://api.example.com/api/v1/media/upload/multiple' \
  -H 'Authorization: Bearer eyJhbGciOi...' \
  -F 'files=@/path/to/one.jpg' \
  -F 'files=@/path/to/two.png' \
  -F 'files=@/path/to/three.webp' \
  -F 'type=gallery'
```

**Sample 200 response**

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": [
    {
      "fileUrl": "https://cdn.example.com/images/2026/05/31/aaa.jpg",
      "fileName": "one.jpg",
      "fileSize": 184320,
      "contentType": "image/jpeg"
    },
    {
      "fileUrl": "https://cdn.example.com/images/2026/05/31/bbb.png",
      "fileName": "two.png",
      "fileSize": 92010,
      "contentType": "image/png"
    },
    {
      "fileUrl": "https://cdn.example.com/images/2026/05/31/ccc.webp",
      "fileName": "three.webp",
      "fileSize": 41280,
      "contentType": "image/webp"
    }
  ]
}
```

---

### 5.3 · `DELETE /api/v1/media?fileUrl=<encoded-url>`

Delete a previously uploaded media file from S3 by its public URL. Reserved for future S3-orphan cleanup; current Tiptap content rarely needs to delete individual assets.

- **Content-Type:** _none_
- **Query parameters:**

| Name      | Required | Type     | Description                                                                          |
| --------- | -------- | -------- | ------------------------------------------------------------------------------------ |
| `fileUrl` | yes      | `String` | URL of the asset to delete. Must be URL-encoded if it contains reserved characters.  |

- **Response:** `ApiResponse<Void>` (`data` is `null`).

If `fileUrl` is null or blank, the service is a no-op (no exception is thrown), but the controller still returns `200 OK`. Missing the query parameter entirely yields a 400 from Spring's parameter binding.

**curl**

```bash
curl -X DELETE \
  'https://api.example.com/api/v1/media?fileUrl=https%3A%2F%2Fcdn.example.com%2Fimages%2F2026%2F05%2F31%2Fabc123.jpg' \
  -H 'Authorization: Bearer eyJhbGciOi...'
```

**Sample 200 response**

```json
{
  "success": true,
  "message": "Media deleted successfully",
  "data": null
}
```

---

## 06 · DTO Reference

### `MediaDtos.UploadResponse`

The only DTO returned by `MediaController`. Built by `MediaService` from the values reported by `MultipartFile` plus the URL produced by `S3Service`.

| Field         | Type     | Description                                                                                      |
| ------------- | -------- | ------------------------------------------------------------------------------------------------ |
| `fileUrl`     | `String` | Public S3 / CDN URL of the uploaded asset. This is the value the frontend bakes into editor HTML. |
| `fileName`    | `String` | Original filename as supplied by the client (`MultipartFile.getOriginalFilename()`).             |
| `fileSize`    | `Long`   | Size of the uploaded file in bytes (`MultipartFile.getSize()`).                                  |
| `contentType` | `String` | MIME type reported by the client (`MultipartFile.getContentType()`).                             |

### `MediaDtos.UploadEnvelope<T>` _(defined but unused by the controller)_

A standalone success envelope kept in `MediaDtos` for callers that need a media-specific wrapper without depending on `ApiResponse`. The controller does not return this type — `ApiResponse<T>` is used instead.

| Field     | Type      | Description                              |
| --------- | --------- | ---------------------------------------- |
| `success` | `boolean` | Always `true` when built via `ok(...)`.  |
| `message` | `String`  | Human-readable status string.            |
| `data`    | `T`       | Payload (typically an `UploadResponse`). |

### `MediaDtos.BulkUploadResponse` _(defined but unused by the controller)_

An alternate batch wrapper. The controller's multi-upload endpoint returns `List<UploadResponse>` directly, not this type.

| Field   | Type                  | Description                          |
| ------- | --------------------- | ------------------------------------ |
| `files` | `List<UploadResponse>` | One entry per uploaded asset.       |

---

## 07 · Response Envelope

Every controller method returns `ResponseEntity<ApiResponse<T>>`. The on-the-wire JSON shape is:

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": { /* T — UploadResponse, List<UploadResponse>, or null */ }
}
```

- `success` — `true` for 2xx responses.
- `message` — the static message set by the controller (`"Media uploaded successfully"` or `"Media deleted successfully"`).
- `data` — the typed payload, or `null` for the delete endpoint.

---

## 08 · Error Responses

Errors flow through the global `ApiErrorResponse` envelope (timestamp, status, path, method, traceId, code, message, messageEn, messageKu, fieldErrors, details). Typical shape:

```json
{
  "timestamp": "2026-05-31T10:15:30.123Z",
  "status": 400,
  "path": "/api/v1/media/upload",
  "method": "POST",
  "traceId": "8d9e2f1c3a4b5c6d",
  "code": "BAD_REQUEST",
  "message": "File is required",
  "messageEn": "File is required",
  "messageKu": "پێویستە فایل دابنرێت",
  "fieldErrors": [],
  "details": null
}
```

### Common codes for this module

| HTTP | Code               | When it fires                                                                                   |
| ---- | ------------------ | ----------------------------------------------------------------------------------------------- |
| 400  | `BAD_REQUEST`      | `file` part is missing or empty (`IllegalArgumentException("File is required")`), or `fileUrl` query param is missing on `DELETE`. |
| 400  | `VALIDATION_ERROR` | Malformed multipart request that fails Spring binding before reaching the controller.           |
| 401  | `UNAUTHORIZED`     | Missing or invalid JWT — `SecurityConfig` rejects before the controller runs.                   |
| 413  | `PAYLOAD_TOO_LARGE`| Any individual upload exceeds the configured limit (5 MB per file by default).                  |
| 500  | `STORAGE_ERROR`    | `S3Service.upload(...)` / `S3Service.deleteFile(...)` failure (network, credentials, bucket policy, etc.). |

Note: in the multi-upload endpoint, the first failed file aborts the batch by throwing `RuntimeException("Upload failed: <name>")`; earlier successful uploads remain in S3 as orphans and are not cleaned up automatically.

---

## 09 · Notes — TiptapHtmlProcessor (Safety Net)

`TiptapHtmlProcessor` (in `service/media/`) is the single entry-point for ALL media (image, video, audio/voice, document, or any other file) embedded inside Tiptap HTML. Owning services (About, Contact, Service, News, Project, Sound, Video, Image, Writing) run their incoming Tiptap blobs through it before persisting.

What it does, per the class-level Javadoc:

- Scans the inbound HTML for inline base64 data URIs on:
  - `src="data:..."` of `<img>`, `<video>`, `<audio>`, and `<source>` tags (images, videos, voice recordings).
  - `href="data:..."` of `<a>` tags (PDFs, documents, any other downloadable file).
- For each match it base64-decodes the payload, uploads the bytes to S3 via `S3Service` using a MIME-derived folder (`images/`, `video/`, `audio/`, or `files/`), and rewrites the attribute to point at the resulting public URL.
- The rewritten HTML is what gets persisted — **the database never stores raw binary payloads**.

Behaviour guarantees (from the Javadoc):

- **Idempotent** — HTML that already contains only S3 URLs is returned unchanged (fast early-out on `!html.contains("data:")`).
- **Null-safe / blank-safe** — `null` and empty strings pass through untouched.
- **Resilient** — a malformed base64 payload or a single failed S3 upload is logged and the original attribute is left in place; the save still succeeds for the rest of the document.

This guards against frontends that forget to upload first: even if the editor pastes a 2 MB inline `data:image/png;base64,...` into the description, the backend will quietly upload it on save and store only the clean URL.
