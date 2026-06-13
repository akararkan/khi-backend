# KHI Backend — Service API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 11 Endpoints · JSON Only · Tiptap HTML · Paginated · Audit Log

Complete documentation for all Service module endpoints — create, update, delete, list, search, and bulk delete — including bilingual content, Tiptap HTML media embedding, audit logging, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Authentication & Notes](#03--authentication--notes)
- [04 · Read](#04--read)
  - `GET /` (getAll — paginated, optional `?type=` filter)
  - `GET /all` (paginated, includes inactive)
  - `GET /{id}`
  - `GET /types`
  - `GET /search`
  - `GET /search/admin`
- [05 · Create](#05--create) — `POST /`
- [06 · Update](#06--update)
  - `PUT /{id}`
  - `PATCH /{id}/active`
- [07 · Delete](#07--delete)
  - `DELETE /{id}`
  - `DELETE /bulk`
- [08 · Media Upload Pipeline (Tiptap)](#08--media-upload-pipeline-tiptap)
- [09 · DTO Reference](#09--dto-reference)
- [10 · Error Responses](#10--error-responses)
- [11 · Change Log — Old vs. New](#11--change-log--old-vs-new)

---

## 01 · Overview

The Service module manages bilingual institute services (Trainings, Events, Programs, Workshops, Conferences, etc.) for the KHI platform. Each service carries:

- **Dynamic service type** — a free-text `serviceType` label (`"Training"`, `"Event"`, `"Workshop"`, …) so admins can define new types without a code change.
- **Bilingual content via a separate table** — each `ServiceContent` row in `service_contents` carries a `languageCode` (`CKB` | `KMR`) and a Tiptap HTML `description`. Adding a third language (e.g. `EN`) requires only a new row — no schema migration.
- **Inline Tiptap media** — all visual media (images, videos, audio, documents) lives **inside** the bilingual `description` as `<img>`, `<video>`, `<audio>`, or `<a href>` tags pointing to S3 URLs. The frontend uploads files first via `POST /api/v1/media/upload`, receives an S3 URL, and bakes it into the Tiptap editor before submitting the service JSON.
- **Active flag** — soft-disable without deleting. `PATCH /{id}/active` toggles visibility.
- **Publish timestamp** — `publishedAt` is set explicitly by the admin when going live.
- **Audit trail** — every CREATE / UPDATE / DELETE / TOGGLE_ACTIVE action is recorded in `service_audit_logs`. Snapshot fields (`serviceId`, `serviceType`) survive after the parent service row is deleted.

### Base URL

```
/api/v1/services
```

### Endpoint Summary

| # | Method | Path | Description |
| --- | --- | --- | --- |
| 1 | `GET` | `/` | Active services, paginated. Optional `?type=` filter |
| 2 | `GET` | `/all` | All services incl. inactive, paginated |
| 3 | `GET` | `/{id}` | Full detail for one service |
| 4 | `GET` | `/types` | Distinct `serviceType` names for dropdowns |
| 5 | `GET` | `/search?q=` | Full-text search — active only, paginated |
| 6 | `GET` | `/search/admin?q=` | Full-text search — all, paginated |
| 7 | `POST` | `/` | Create service — `application/json` |
| 8 | `PUT` | `/{id}` | Full update — `application/json` |
| 9 | `PATCH` | `/{id}/active?value=` | Toggle active flag only |
| 10 | `DELETE` | `/{id}` | Delete service |
| 11 | `DELETE` | `/bulk` | Bulk delete services |

---

## 02 · Data Models

Three JPA entities make up the Service module: `Service` (aggregate root), `ServiceContent` (one row per language), and `ServiceAuditLog` (append-only audit trail).

### Service — `services`

**DB indexes:** `idx_service_type`, `idx_service_active`, `idx_service_published_at`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `serviceType` | `service_type` | VARCHAR(100) | **NOT NULL** (`@NotBlank`) | Free-text type label — e.g. `"Training"`, `"Event"`, `"Workshop"` |
| `location` | `location` | VARCHAR(200) | NULLABLE | Physical or virtual location |
| `active` | `active` | BOOLEAN | (default `true`) | `true` = publicly visible. Default `true` on builder |
| `publishedAt` | `published_at` | TIMESTAMP | NULLABLE | Explicit publish timestamp set by admin. `null` = draft / never formally published |
| `contents` | (one-to-many) | — | — | `Set<ServiceContent>` — one row per language. `@BatchSize(50)`, `cascade=ALL`, `orphanRemoval=true`, LAZY |
| `createdAt` | `created_at` | TIMESTAMP | (auto, NOT NULL on insert) | Set by `@CreationTimestamp`. `LocalDateTime` |
| `updatedAt` | `updated_at` | TIMESTAMP | (auto) | Set by `@UpdateTimestamp` |

**Helper methods on `Service`:**

| Method | Returns | Description |
| --- | --- | --- |
| `addContent(content)` / `removeContent(content)` | `void` | Maintains the bi-directional FK pointer |

> ℹ️ **No standalone media fields.** The previous `service_media_collections` and `service_media_files` tables (and their POJO classes) have been **removed**. All media now lives inline inside the bilingual Tiptap `description`.

### ServiceContent — `service_contents`

One row per language per service. **Unique constraint** `uq_service_content_lang` on `(service_id, language_code)` prevents duplicates.

**DB indexes:** `idx_service_content_service_id`, `idx_service_content_lang`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `languageCode` | `language_code` | VARCHAR(10) | **NOT NULL** (`@NotBlank`, `@Pattern("^[A-Z]{2,5}$")`) | `"CKB"` (Sorani) or `"KMR"` (Kurmanji). Future codes (`"EN"`, `"AR"`) require no schema change — just a new row |
| `title` | `title` | VARCHAR(300) | **NOT NULL** | Localised service title |
| `description` | `description` | TEXT | NULLABLE | **Tiptap HTML** — all media is embedded inline here as `<img>` / `<video>` / `<audio>` / `<a>` tags pointing at S3 URLs |
| `service` | `service_id` | FK → services | **NOT NULL** | Parent service. LAZY. `@ToString.Exclude` + `@EqualsAndHashCode.Exclude` to avoid recursion |

> ℹ️ **Design contrast vs. About module:** Service uses a separate `service_contents` row per language (flexible — adding a third language is just a new row), whereas About uses `@Embeddable` + `@AttributeOverrides` (fixed `_ckb`/`_kmr` columns on the same table).

### ServiceAuditLog — `service_audit_logs`

Append-only audit log. `serviceId` and `serviceType` are stored as snapshot columns so log entries survive even after the parent service is hard-deleted.

**DB indexes:** `idx_sal_service_id`, `idx_sal_action`, `idx_sal_timestamp`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `serviceId` | `service_id` | BIGINT | NOT NULL | ID snapshot — retained after deletion of the parent service |
| `serviceType` | `service_type` | VARCHAR(100) | NULLABLE | Type snapshot at the time of the action |
| `action` | `action` | VARCHAR(30) | NOT NULL | `"CREATE"` \| `"UPDATE"` \| `"DELETE"` \| `"TOGGLE_ACTIVE"` |
| `details` | `details` | TEXT | NULLABLE | Human-readable description of the change |
| `performedBy` | `performed_by` | VARCHAR(150) | NULLABLE | Acting principal (future auth integration) |
| `requestId` | `request_id` | VARCHAR(120) | NULLABLE | Trace/request ID for log correlation |
| `timestamp` | `timestamp` | TIMESTAMP | NOT NULL | Defaults to `LocalDateTime.now()` on `@PrePersist` |

> ⛔ **Removed (vs. old doc):** the `service_media_collections` and `service_media_files` tables and their POJO classes. All media now lives inline in the Tiptap `description`.

---

## 03 · Authentication & Notes

> ℹ️ **Authentication** is enforced by the project's global security configuration (JWT via `Authorization: Bearer …` header or `auth_token` HttpOnly cookie). The `ServiceController` does NOT carry per-endpoint `@PreAuthorize` annotations — there is no explicit admin-vs-public split inside this module, even though `GET /` / `GET /search` and `GET /all` / `GET /search/admin` play those roles by content (active-only vs. all).

> 📝 **Tiptap HTML pipeline:** the `description` field on every `ServiceContent` row stores full Tiptap HTML. On save, the `TiptapHtmlProcessor` rewrites any inline base64 payloads into S3 URLs as a safety net — so the column never holds raw binary data even if the frontend forgets to upload first.

> ℹ️ **Update semantics:** On `PUT /{id}`, a `null` field means "do not change that field." When `contents` is non-null, all existing content rows are **replaced** (via `cascade=ALL` + `orphanRemoval=true`).

> ℹ️ **Default sort:** `GET /` and `GET /all` return services sorted by `publishedAt DESC, createdAt DESC` (service-layer convention).

> ℹ️ **Language codes** must be uppercase, 2–5 letters (`^[A-Z]{2,5}$`). Accepted values today: `"CKB"` (Sorani) and `"KMR"` (Kurmanji). New codes (`"EN"`, `"AR"`, etc.) require no schema change.

> ℹ️ **No `@Cacheable` / `@CacheEvict` layer.** Every read hits the DB directly. Performance budget comes from `@BatchSize(50)` on the `contents` collection + DB indexes.

> ℹ️ **No `ApiResponse<T>` envelope inconsistency:** every Service endpoint returns the `ApiResponse<T>` wrapper (including `DELETE /{id}` → `ApiResponse<Void>` `200 OK`). There is no `204 No Content` shortcut here — that's an About-only convention.

---

## 04 · Read

### `GET /api/v1/services` — getAll

🔒 Auth handled by global security config

Returns active services (`active = true`), paginated. Optional `?type=` filters by service type. The controller branches on the presence of `type`: when present, it calls `getAllActiveByType`; when absent, `getAllActive`.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `type` | String | No | — | Filter by `serviceType` (typically case-insensitive — depends on the service-layer SQL) |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Request

```
GET /api/v1/services?page=0&size=20
GET /api/v1/services?type=Training&page=0&size=10
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Services fetched successfully",
  "data": {
    "content": [
      {
        "id":           5,
        "serviceType":  "Training",
        "location":     "Sulaymaniyah Hall",
        "active":       true,
        "publishedAt":  "2026-03-01T09:00:00",
        "contents": [
          {
            "id":           11,
            "languageCode": "CKB",
            "title":        "پڕۆگرامی ڕاهێنانی زمانی کوردی",
            "description":  "<p>پڕۆگرامێکی تایبەت بۆ فێربوونی زمانی کوردی سۆرانی…</p>"
          },
          {
            "id":           12,
            "languageCode": "KMR",
            "title":        "Bernameya Perwerdehiya Zimanê Kurdî",
            "description":  "<p>Bernameyek taybetî ji bo fêrbûna zimanê Kurdî Kurmancî…</p>"
          }
        ],
        "createdAt": "2026-02-28T14:00:00",
        "updatedAt": "2026-03-01T09:00:00"
      }
    ],
    "pageable":         { "pageNumber": 0, "pageSize": 20 },
    "totalElements":    38,
    "totalPages":       2,
    "last":             false,
    "first":            true,
    "numberOfElements": 20,
    "empty":            false
  }
}
```

---

### `GET /api/v1/services/all`

🔒 Auth handled by global security config

Returns all services including inactive ones. Intended for the admin panel.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Items per page |

### Response · 200 OK

Same shape as `GET /` but includes services with `active = false`.

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/services/{id}`

🔒 Auth handled by global security config

Returns full detail for a single service by primary key.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Service fetched successfully",
  "data": { /* full ServiceResponse — same shape as item in getAll */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | No service with that `id` |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/services/types`

🔒 Auth handled by global security config

Returns all distinct `serviceType` values. Intended for frontend filter dropdowns.

### Response · 200 OK

```json
{
  "success": true,
  "message": "Service types fetched",
  "data": ["Conference", "Event", "Program", "Training", "Workshop"]
}
```

---

### `GET /api/v1/services/search`

🔒 Auth handled by global security config

Full-text search across active services. Searches `serviceType`, `location`, and bilingual `title` / `description`.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `q` | String | **Yes** | — | Search query |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Request

```
GET /api/v1/services/search?q=Training+2026&page=0&size=20
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Search results fetched",
  "data": { /* Page<ServiceResponse> — active only */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `q` is missing (Spring's missing-required-param envelope) |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/services/search/admin`

🔒 Auth handled by global security config

Same as `GET /search` but includes inactive services. Intended for the admin panel.

### Query Parameters

Same as `GET /search`.

### Response · 200 OK

```json
{
  "success": true,
  "message": "Admin search results fetched",
  "data": { /* Page<ServiceResponse> — incl. inactive */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `q` is missing |
| `401` | Missing or expired JWT |

---

## 05 · Create

### `POST /api/v1/services`

🔒 Auth handled by global security config · `Content-Type: application/json`

Create a new service. Media files must be uploaded to S3 first via `POST /api/v1/media/upload` — paste the returned URL directly into the Tiptap `description` HTML before submitting.

### Request Body — Full (Both Languages)

```json
{
  "serviceType": "Training",
  "location":    "Sulaymaniyah Hall",
  "publishedAt": "2026-05-01 09:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title":        "پڕۆگرامی ڕاهێنانی زمانی کوردی",
      "description":  "<p>پڕۆگرامێکی تایبەت بۆ فێربوونی زمانی کوردی سۆرانی.</p><img src=\"https://s3.amazonaws.com/khi-cdn/services/poster.jpg\" alt=\"posterek\" />"
    },
    {
      "languageCode": "KMR",
      "title":        "Bernameya Perwerdehiya Zimanê Kurdî",
      "description":  "<p>Bernameyek taybetî ji bo fêrbûna zimanê Kurdî Kurmancî.</p>"
    }
  ]
}
```

### Request Body — Minimal (Draft, CKB Only)

```json
{
  "serviceType": "Workshop",
  "location":    null,
  "publishedAt": null,
  "contents": [
    {
      "languageCode": "CKB",
      "title":        "وۆرکشۆپی نووسینی دیجیتاڵ",
      "description":  null
    }
  ]
}
```

### Request · curl

```bash
curl -X POST https://api.khi.iq/api/v1/services \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "Training",
    "location":    "Sulaymaniyah Hall",
    "publishedAt": "2026-05-01 09:00:00",
    "contents": [
      { "languageCode": "CKB", "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی" },
      { "languageCode": "KMR", "title": "Bernameya Perwerdehiya Zimanê Kurdî" }
    ]
  }'
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "Service created successfully",
  "data": {
    "id":           5,
    "serviceType":  "Training",
    "location":     "Sulaymaniyah Hall",
    "active":       true,
    "publishedAt":  "2026-05-01T09:00:00",
    "contents": [
      { "id": 11, "languageCode": "CKB", "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی",     "description": null },
      { "id": 12, "languageCode": "KMR", "title": "Bernameya Perwerdehiya Zimanê Kurdî", "description": null }
    ],
    "createdAt": "2026-04-12T10:00:00",
    "updatedAt": "2026-04-12T10:00:00"
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `serviceType` is blank or missing (entity `@NotBlank`) |
| `400` | A `contents` entry is missing `languageCode` (entity `@NotBlank`) |
| `400` | A `contents` entry has a blank `title` (column `nullable=false`) |
| `400` | `languageCode` does not match `^[A-Z]{2,5}$` (entity `@Pattern`) |
| `400` | Duplicate `languageCode` for the same service (DB unique constraint `uq_service_content_lang`) |
| `400` | `publishedAt` format is not `"yyyy-MM-dd HH:mm:ss"` (parse failure in the service) |
| `401` | Missing or expired JWT |

---

## 06 · Update

### `PUT /api/v1/services/{id}`

🔒 Auth handled by global security config · `Content-Type: application/json`

Full update of an existing service. `null` fields are ignored (not applied). When `contents` is non-null, all existing content rows are **replaced entirely** via `cascade=ALL + orphanRemoval=true`.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the service to update |

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `serviceType` | Replaces the value | Existing kept |
| `location` | Replaces the value | Existing kept |
| `publishedAt` | Replaces the timestamp (parsed from `"yyyy-MM-dd HH:mm:ss"`) | Existing kept |
| `contents` (non-null) | **Replaces** the entire bilingual content set | — |
| `contents` (null) | — | Existing content rows kept |
| `active` | (not in request DTO — use `PATCH /{id}/active`) | — |

### Request Body — Update Text Only

```json
{
  "contents": [
    {
      "languageCode": "CKB",
      "title":        "پڕۆگرامی ڕاهێنانی زمانی کوردی — نوێکراوەوە",
      "description":  "<p>وەرشێوەی نوێکراوەی پڕۆگرامی ڕاهێنان بۆ ٢٠٢٦</p>"
    },
    {
      "languageCode": "KMR",
      "title":        "Bernameya Perwerdehiyê — Nûvekirî",
      "description":  "<p>Guhertoyek nûvekirî ya bernameya perwerdehiyê ji bo 2026</p>"
    }
  ]
}
```

### Request Body — Update Location & Publish Time Only

```json
{
  "location":    "Erbil Conference Center",
  "publishedAt": "2026-05-15 08:00:00"
}
```

### Request · curl

```bash
curl -X PUT https://api.khi.iq/api/v1/services/5 \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "location":    "Erbil Conference Center",
    "publishedAt": "2026-05-15 08:00:00"
  }'
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Service updated successfully",
  "data": { /* full ServiceResponse */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Service not found |
| `400` | Validation error (same rules as POST) |
| `401` | Missing or expired JWT |

---

### `PATCH /api/v1/services/{id}/active`

🔒 Auth handled by global security config

Toggle the `active` flag without affecting content. Writes an audit log entry with `action = "TOGGLE_ACTIVE"`.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key |

### Query Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `value` | boolean | **Yes** | `true` to activate, `false` to deactivate |

### Request

```
PATCH /api/v1/services/5/active?value=false
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Service deactivated",
  "data": { /* full ServiceResponse with active=false */ }
}
```

> ℹ️ Message text is `"Service activated"` when `value=true` and `"Service deactivated"` when `value=false`.

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Service not found |
| `400` | `value` parameter missing |
| `401` | Missing or expired JWT |

---

## 07 · Delete

### `DELETE /api/v1/services/{id}`

🔒 Auth handled by global security config

Permanently delete a service and all its `ServiceContent` rows (`cascade=ALL` + `orphanRemoval=true`). Writes an audit log entry with `action = "DELETE"`; the `serviceId` and `serviceType` snapshots survive in `service_audit_logs`.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Service deleted successfully",
  "data":    null
}
```

> ℹ️ Returns `200 OK` with `ApiResponse<Void>`, **not** `204 No Content` — that's an About-only convention.

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Service not found |
| `401` | Missing or expired JWT |

---

### `DELETE /api/v1/services/bulk`

🔒 Auth handled by global security config · `Content-Type: application/json`

Permanently delete multiple services. IDs that do not exist are silently skipped by the service layer.

### Request Body

```json
[5, 6, 7]
```

### Request · curl

```bash
curl -X DELETE https://api.khi.iq/api/v1/services/bulk \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '[5, 6, 7]'
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Services deleted successfully",
  "data":    null
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | Body is not a valid JSON array |
| `401` | Missing or expired JWT |

---

## 08 · Media Upload Pipeline (Tiptap)

The Service module **has no media upload endpoints**. All visual media lives inline inside the bilingual Tiptap `description`.

### How it works

1. **Frontend uploads** each image / video / audio / document first via the shared:
   ```
   POST /api/v1/media/upload
   ```
2. The upload endpoint returns a stored URL (S3 or CDN).
3. The frontend bakes that URL into the Tiptap editor as an `<img src="…">`, `<video src="…">`, `<audio src="…">`, or `<a href="…">` element inside the localized `description`.
4. When the user saves, the entire `contents[*].description` HTML is sent in the JSON request to `POST /api/v1/services` or `PUT /api/v1/services/{id}`.
5. The backend `TiptapHtmlProcessor` sanitizes the HTML and acts as a safety net — if any inline base64 payload slipped through, it hoists that payload up to S3 and rewrites the `src` to the resulting URL before persisting.

### What never existed in this module

- ❌ No `POST /upload` endpoint
- ❌ No `DELETE /media` endpoint
- ❌ No `service_media_collections` table
- ❌ No `service_media_files` table
- ❌ No cover / hero / thumbnail / gallery columns

---

## 09 · DTO Reference

### ServiceRequest

Sent as `application/json` in `POST /` and `PUT /{id}`. **No Bean-Validation annotations** on this DTO — validation is enforced by the JPA entities (`@NotBlank` on `Service.serviceType` and `ServiceContent.languageCode`/`title`, `@Pattern` on `ServiceContent.languageCode`, unique constraint on `(service_id, language_code)`).

| Field | Type | Required (Create) | Description |
| --- | --- | --- | --- |
| `serviceType` | String | **Yes** | Free-text type — e.g. `"Training"`, `"Event"`. Max 100 chars at entity level |
| `location` | String | No | Physical or virtual location. Max 200 chars. `null` when not applicable |
| `publishedAt` | String | No | Format: `"yyyy-MM-dd HH:mm:ss"`. `null` = draft / unpublished |
| `contents` | `List<ServiceContentRequest>` | No | Bilingual content rows. Each entry needs `languageCode` + `title` |

### ServiceContentRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `languageCode` | String | **Yes** | `"CKB"` or `"KMR"`. Must match `^[A-Z]{2,5}$`. Future codes (`"EN"`, `"AR"`) accepted without a schema change |
| `title` | String | **Yes** | Localised service title. Max 300 chars at entity level |
| `description` | String | No | **Tiptap HTML**. All media (images, videos, audio, files) is embedded inline as S3 URLs. `null` is valid |

### ServiceResponse

Returned by all read and write endpoints, wrapped in `ApiResponse<T>`.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `serviceType` | String | Free-text type label |
| `location` | String | Location — may be `null` |
| `active` | boolean | Public visibility flag |
| `publishedAt` | String | ISO-8601 local datetime — may be `null` |
| `contents` | `List<ServiceContentResponse>` | Bilingual content rows |
| `createdAt` | String | ISO-8601 local datetime |
| `updatedAt` | String | ISO-8601 local datetime |

> ℹ️ Timestamps on `ServiceResponse` are serialized as **`String`**, not `LocalDateTime`.

### ServiceContentResponse

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key of the content row |
| `languageCode` | String | `"CKB"` or `"KMR"` |
| `title` | String | Localised title |
| `description` | String | **Tiptap HTML** — may be `null` |

### ApiResponse&lt;T&gt;

All endpoints return this wrapper. `data` is omitted on failure due to `@JsonInclude(NON_NULL)`.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure |

---

## 10 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | Service created |
| `200 OK` | Read, update, delete, toggle, or search succeeded |
| `400 Bad Request` | Validation error, missing required field, or invalid value |
| `401 Unauthorized` | JWT missing, expired, or blacklisted |
| `403 Forbidden` | Insufficient role |
| `404 Not Found` | Service not found |
| `500 Internal Error` | Unexpected server failure |

### Validation Error — `400 Bad Request`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    400,
  "errors": [
    { "field": "serviceType",                "message": "must not be blank" },
    { "field": "contents[0].languageCode",   "message": "language_code must be 2–5 uppercase letters, e.g. CKB or KMR" }
  ]
}
```

### Common Business Error Keys (where surfaced explicitly by the service)

| Error Key / Source | Trigger |
| --- | --- |
| `service.not_found` / generic `404` | No service found for the given `id` |
| `@NotBlank` envelope on `serviceType` | `serviceType` is blank on create |
| `@NotBlank` envelope on `languageCode`/`title` | A `contents` entry is missing one of those fields |
| `@Pattern("^[A-Z]{2,5}$")` envelope | `languageCode` is not 2–5 uppercase letters |
| `uq_service_content_lang` constraint violation | Same `languageCode` appears more than once for the same service |
| `publishedAt` parse failure | `publishedAt` is not `"yyyy-MM-dd HH:mm:ss"` |
| Missing required query param (`q` on `/search`, `value` on `/active`) | Spring's `MissingServletRequestParameterException` envelope |

### Auth Error Body — `401 Unauthorized`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Authentication token is missing or expired"
}
```

### Not Found Error Body — `404`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    404,
  "error":     "Not Found",
  "message":   "Service not found with id: 99"
}
```

> ℹ️ Timestamps are ISO-8601 local datetime. `createdAt` / `updatedAt` on `ServiceResponse` are serialized as `String`.

---

## 11 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `GET /` | Active services, paginated, optional `?type=` | Active services, paginated, optional `?type=` | ⚪ Unchanged |
| `GET /all` | Admin all, paginated | All incl. inactive, paginated | ⚪ Unchanged |
| `GET /{id}` | By id | By id | ⚪ Unchanged |
| `GET /types` | Distinct types | Distinct types | ⚪ Unchanged |
| `GET /search` | Public full-text search | Public full-text search | ⚪ Unchanged |
| `GET /search/admin` | Admin full-text search | Admin full-text search | ⚪ Unchanged |
| `POST /` | Create | Create | ⚪ Unchanged |
| `PUT /{id}` | Full update | Full update — partial-merge semantics formalized | 🟡 Behaviour clarified |
| `PATCH /{id}/active` | Toggle active | Toggle active — message text varies by `value` | ⚪ Unchanged |
| `DELETE /{id}` | Delete | Delete — returns `ApiResponse<Void>` `200 OK` | ⚪ Unchanged |
| `DELETE /bulk` | Bulk delete | Bulk delete — missing IDs silently skipped | ⚪ Unchanged |

**Endpoint count: 11** — matches the old doc exactly.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `Service` entity | Existed | ⚪ Unchanged |
| `Service.contents` collection type | "one-to-many" (unspecified type) | 🟢 **Documented**: `Set<ServiceContent>` (not `List`), `@BatchSize(50)`, `cascade=ALL`, `orphanRemoval=true`, LAZY |
| `ServiceContent` entity | Existed (`uq_service_content_lang` on `(service_id, language_code)`) | ⚪ Unchanged |
| `ServiceContent.languageCode` validation | Documented (`@NotBlank`, `@Pattern("^[A-Z]{2,5}$")`) | ⚪ Unchanged |
| `ServiceAuditLog` entity | Not documented | 🟢 **Added** — append-only audit log with snapshot `serviceId`/`serviceType` columns that survive deletion. Logs `CREATE` / `UPDATE` / `DELETE` / `TOGGLE_ACTIVE` actions |
| `services` DB indexes | Not documented | 🟢 Documented: `idx_service_type`, `idx_service_active`, `idx_service_published_at` |
| `service_contents` DB indexes | Not documented | 🟢 Documented: `idx_service_content_service_id`, `idx_service_content_lang` |
| `service_audit_logs` DB indexes | Not documented | 🟢 Documented: `idx_sal_service_id`, `idx_sal_action`, `idx_sal_timestamp` |
| `Service` helper methods (`addContent`, `removeContent`) | Not documented | 🟢 Documented |
| Removed `service_media_collections` / `service_media_files` tables | Documented as already removed | ⚪ Unchanged |

### C) DTO comparison

| Item | Old | New |
| --- | --- | --- |
| `ServiceRequest` shape | Same | ⚪ Unchanged |
| `ServiceContentRequest` shape | Same | ⚪ Unchanged |
| `ServiceResponse` shape | Same | ⚪ Unchanged |
| `ServiceContentResponse` shape | Same | ⚪ Unchanged |
| DTO Bean-Validation annotations | Implied | 🟡 **Confirmed absent on DTOs** — validation is enforced by the JPA entities (`@NotBlank` on `Service.serviceType`, `ServiceContent.languageCode`, `ServiceContent.title`; `@Pattern` on `languageCode`; unique constraint on `(service_id, language_code)`) |
| `createdAt` / `updatedAt` on `ServiceResponse` | Implied `LocalDateTime` | 🟡 **Confirmed**: serialized as **`String`**, not `LocalDateTime` |
| `publishedAt` on `ServiceResponse` | Implied `LocalDateTime` | 🟡 **Confirmed**: serialized as **`String`** |
| `publishedAt` request format | `"yyyy-MM-dd HH:mm:ss"` | ⚪ Unchanged |

### D) Validation / error-key comparison

| Old error key | New status |
| --- | --- |
| `service.not_found` | 🟡 No dedicated key in code — surfaced as generic `404` |
| `service.field.required` | 🟡 Surfaced as `@NotBlank` Bean-Validation envelope on `Service.serviceType` |
| `service.publishedAt.invalid` | 🟡 Surfaced as a date-parse failure in the service layer |
| `service.content.language.required` / `service.content.title.required` | 🟡 Surfaced as `@NotBlank` envelopes on `ServiceContent.languageCode` / `title` |
| `service.content.language.unsupported` | 🟡 Surfaced as `@Pattern("^[A-Z]{2,5}$")` envelope |
| `service.content.duplicate_language` | 🟡 Surfaced as a DB unique-constraint violation on `uq_service_content_lang` |
| `service.ids.required` | 🟡 No dedicated key in code — controller passes the empty list through, service silently no-ops |
| `service.search.required` | 🟡 Surfaced as Spring's `MissingServletRequestParameterException` on `q` |
| `service.type.required` | 🟡 No dedicated key — `?type=` is optional in the controller; blank values fall through to "no filter" |

### E) Caching & performance

| Item | Old | New |
| --- | --- | --- |
| `@Cacheable` / `@CacheEvict` | Not claimed | 🚧 **Confirmed absent** — every read hits the DB |
| `@BatchSize(50)` on `Service.contents` | Implied | 🟢 Documented |

### F) Auth model

| Item | Old | New |
| --- | --- | --- |
| Per-endpoint admin/public split | Documented (admin vs. public mix) | 🟡 **Controller has no per-method `@PreAuthorize` annotations** — auth is enforced by the global security configuration. The "admin" vs. "public" labels in the old doc were aspirational rather than enforced inside this module |

### G) Summary

- 🟢 **Added (documentation):** the previously undocumented `ServiceAuditLog` entity with all its snapshot fields and indexes; comprehensive DB-index inventory across `services`, `service_contents`, and `service_audit_logs`; explicit type of `Service.contents` (a `Set` with `@BatchSize(50)`, `cascade=ALL`, `orphanRemoval=true`); helper methods on `Service`.
- 🟢 **Added (behaviour notes):** audit-log emission on every write (`CREATE` / `UPDATE` / `DELETE` / `TOGGLE_ACTIVE`); the `PATCH /{id}/active` message text varies (`"activated"` vs `"deactivated"`); bulk delete silently skips missing IDs.
- 🟡 **Clarified:** all the dedicated `service.*` business error keys from the old doc are **not** thrown by the current code — they surface as Bean-Validation envelopes, DB unique-constraint violations, date-parse failures, or generic `404`s instead. `ServiceResponse.createdAt` / `updatedAt` / `publishedAt` are **`String`**, not `LocalDateTime`. The controller has no `@PreAuthorize` annotations — admin/public split is aspirational, not enforced per-method.
- 🚧 **Caching absent:** no `@Cacheable` / `@CacheEvict` in the Service module. Performance budget comes from `@BatchSize(50)` + DB indexes.
- ⚪ **Unchanged:** all 11 endpoints (paths, params, return shapes), the bilingual `service_contents` schema with `UNIQUE(service_id, language_code)`, the language-code regex `^[A-Z]{2,5}$`, the `publishedAt` `"yyyy-MM-dd HH:mm:ss"` format, the `ApiResponse<T>` wrapper on every endpoint, the dynamic free-text `serviceType` design, and the absence of any standalone media columns / tables / DTOs.
