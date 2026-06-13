# KHI Backend — Service API Reference

> **ak.dev · KHI Platform** · Spring Boot 4 · JWT Auth Required (Admin) · Bilingual (CKB / KMR) · 11 Endpoints · JSON · Paginated · Tiptap Inline Media

Complete documentation for all Service module endpoints — create, update, delete, list, and search — including bilingual content, Tiptap HTML media embedding, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Authentication & Notes](#03--authentication--notes)
- [04 · Read](#04--read)
  - [GET / (getAll — Public, Paginated)](#get-apiv1services--getall--public-paginated)
  - [GET /all (Admin, Paginated)](#get-apiv1servicesall--admin-paginated)
  - [GET /{id}](#get-apiv1servicesid)
  - [GET /types](#get-apiv1servicestypes)
  - [GET /search](#get-apiv1servicessearch)
  - [GET /search/admin](#get-apiv1servicessearchadmin)
- [05 · Create](#05--create)
  - [POST /](#post-apiv1services)
- [06 · Update](#06--update)
  - [PUT /{id}](#put-apiv1servicesid)
  - [PATCH /{id}/active](#patch-apiv1servicesidactive)
- [07 · Delete](#07--delete)
  - [DELETE /{id}](#delete-apiv1servicesid)
  - [DELETE /bulk](#delete-apiv1servicesbulk)
- [08 · DTO Reference](#08--dto-reference)
- [09 · Error Responses](#09--error-responses)

---

## 01 · Overview

The Service module manages bilingual institute services (Trainings, Events, Programs, Workshops, Conferences, etc.) for the KHI platform. Each service carries:

- **Dynamic service type** — a free-text `serviceType` label (`"Training"`, `"Event"`, `"Workshop"`) so admins can define new types without a code change.
- **Bilingual content via a separate table** — each `ServiceContent` row in `service_contents` carries a `languageCode` (`CKB` | `KMR`) and a Tiptap HTML `description`. Adding a third language (e.g. `EN`) requires only a new row — no schema migration.
- **Inline Tiptap media** — all visual media (images, videos, audio, documents) lives **inside** the bilingual `description` as `<img>`, `<video>`, `<audio>`, or `<a href>` tags pointing to S3 URLs. The frontend uploads files first via `POST /api/v1/media/upload`, receives an S3 URL, and bakes it into the Tiptap editor before submitting the service JSON.
- **Active flag** — soft-disable without deleting. `PATCH /{id}/active` toggles visibility.
- **Publish timestamp** — `publishedAt` is set explicitly by the admin when going live.

### Base URL

```
/api/v1/services
```

### Endpoint Summary

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 1 | `GET` | `/` | Public | Active services, paginated. Optional `?type=` filter |
| 2 | `GET` | `/all` | 🔒 Admin | All services incl. inactive, paginated |
| 3 | `GET` | `/{id}` | Public | Full detail for one service |
| 4 | `GET` | `/types` | Public | Distinct `serviceType` names for dropdowns |
| 5 | `GET` | `/search?q=` | Public | Full-text search — active only, paginated |
| 6 | `GET` | `/search/admin?q=` | 🔒 Admin | Full-text search — all, paginated |
| 7 | `POST` | `/` | 🔒 Admin | Create service — `application/json` |
| 8 | `PUT` | `/{id}` | 🔒 Admin | Full update — `application/json` |
| 9 | `PATCH` | `/{id}/active` | 🔒 Admin | Toggle active flag only |
| 10 | `DELETE` | `/{id}` | 🔒 Admin | Delete service |
| 11 | `DELETE` | `/bulk` | 🔒 Admin | Bulk delete services |

---

## 02 · Data Models

### Service — `services`

| Field | DB Column | DB Type | Constraint | Description |
|-------|-----------|---------|------------|-------------|
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `serviceType` | `service_type` | VARCHAR(100) | NOT NULL | Free-text type label — e.g. `"Training"`, `"Event"`, `"Workshop"` |
| `location` | `location` | VARCHAR(200) | NULLABLE | Physical or virtual location |
| `active` | `active` | BOOLEAN | NOT NULL | `true` = publicly visible. Default `true` |
| `publishedAt` | `published_at` | TIMESTAMP | NULLABLE | Explicit publish timestamp set by admin. `null` = draft |
| `contents` | (one-to-many) | — | — | `ServiceContent` rows — one per language |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Set by `@CreationTimestamp` |
| `updatedAt` | `updated_at` | TIMESTAMP | NOT NULL | Set by `@UpdateTimestamp` |

### ServiceContent — `service_contents`

One row per language per service. UNIQUE constraint on `(service_id, language_code)` prevents duplicates.

| Field | DB Column | DB Type | Constraint | Description |
|-------|-----------|---------|------------|-------------|
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `languageCode` | `language_code` | VARCHAR(10) | NOT NULL | `"CKB"` (Sorani) or `"KMR"` (Kurmanji). Pattern: `^[A-Z]{2,5}$` |
| `title` | `title` | VARCHAR(300) | NOT NULL | Localised service title |
| `description` | `description` | TEXT | NULLABLE | Tiptap HTML — all media is embedded inline here as S3 URLs |
| `service_id` | `service_id` | FK → services | NOT NULL | Parent service |

> **Media design:** There are no separate media collection or file tables. All images, videos, audio, and documents are embedded inline inside the Tiptap `description` HTML. The frontend editor uploads each file to S3 via `POST /api/v1/media/upload`, receives a public URL, and inserts it as an inline `<img>`, `<video>`, `<audio>`, or `<a>` tag before submitting.

---

## 03 · Authentication & Notes

> **Admin endpoints** (all `POST`, `PUT`, `PATCH`, `DELETE`, and `GET /all`, `GET /search/admin`) require a valid JWT via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> **Public endpoints** (`GET /`, `GET /{id}`, `GET /types`, `GET /search`) do not require authentication.

> **Update semantics:** On `PUT /{id}`, a `null` field means "do not change that field." When `contents` is non-null, all existing content rows are **replaced**.

> **Default sort:** `GET /` and `GET /all` return services sorted by `publishedAt DESC, createdAt DESC`.

> **Language codes** must be uppercase, 2–5 letters (`^[A-Z]{2,5}$`). Accepted values: `"CKB"` (Sorani) and `"KMR"` (Kurmanji).

---

## 04 · Read

### `GET /api/v1/services` — getAll (Public, Paginated)

Returns all active services (`active = true`), paginated. Optional `?type=` filters by service type.

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `type` | String | No | — | Filter by `serviceType` (case-insensitive) |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

#### Request

```
GET /api/v1/services?page=0&size=20
GET /api/v1/services?type=Training&page=0&size=10
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Services fetched successfully",
  "data": {
    "content": [
      {
        "id": 5,
        "serviceType": "Training",
        "location": "Sulaymaniyah Hall",
        "active": true,
        "publishedAt": "2025-03-01T09:00:00",
        "contents": [
          {
            "id": 11,
            "languageCode": "CKB",
            "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی",
            "description": "<p>پڕۆگرامێکی تایبەت بۆ فێربوونی زمانی کوردی سۆرانی...</p>"
          },
          {
            "id": 12,
            "languageCode": "KMR",
            "title": "Bernameya Perwerdehiya Zimanê Kurdî",
            "description": "<p>Bernameyek taybetî ji bo fêrbûna zimanê Kurdî Kurmancî...</p>"
          }
        ],
        "createdAt": "2025-02-28T14:00:00",
        "updatedAt": "2025-03-01T09:00:00"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 38,
    "totalPages": 2,
    "last": false,
    "first": true,
    "numberOfElements": 20,
    "empty": false
  }
}
```

---

### `GET /api/v1/services/all` — Admin (Paginated)

🔒 **Auth Required**

Returns all services including inactive ones. Intended for the admin panel.

#### Query Parameters

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `page` | int | No | `0` |
| `size` | int | No | `20` |

#### Request

```
GET /api/v1/services/all?page=0&size=20
Authorization: Bearer eyJhbGci...
```

#### Response · 200 OK

Same shape as `GET /` but includes services with `active = false`.

#### Error Responses

| Status | Description |
|--------|-------------|
| `401` | Missing or expired JWT |

---

### `GET /api/v1/services/{id}`

Returns full detail for a single service by primary key.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | **Yes** | Primary key |

#### Request

```
GET /api/v1/services/5
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Service fetched successfully",
  "data": { /* full ServiceResponse — same shape as item in getAll */ }
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `404` | No service with that `id` |

---

### `GET /api/v1/services/types`

Returns all distinct `serviceType` values. Intended for frontend filter dropdowns.

#### Request

```
GET /api/v1/services/types
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Service types fetched",
  "data": ["Conference", "Event", "Program", "Training", "Workshop"]
}
```

---

### `GET /api/v1/services/search`

Full-text search across active services. Searches `serviceType`, `location`, and bilingual `title` / `description`.

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `q` | String | **Yes** | — | Search query |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

#### Request

```
GET /api/v1/services/search?q=Training+2025&page=0&size=20
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Search results fetched",
  "data": { /* Page<ServiceResponse> — active only */ }
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `400` | `q` is missing or blank |

---

### `GET /api/v1/services/search/admin`

🔒 **Auth Required**

Same as `GET /search` but includes inactive services.

#### Request

```
GET /api/v1/services/search/admin?q=Workshop&page=0&size=20
Authorization: Bearer eyJhbGci...
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `400` | `q` is missing or blank |
| `401` | Missing or expired JWT |

---

## 05 · Create

### `POST /api/v1/services`

🔒 **Auth Required**

Create a new service. Media files must be uploaded to S3 first via `POST /api/v1/media/upload` — paste the returned URL directly into the Tiptap `description` HTML before submitting.

#### Content-Type

```
Content-Type: application/json
```

---

#### Request Body — Full (Both Languages)

```json
{
  "serviceType": "Training",
  "location": "Sulaymaniyah Hall",
  "publishedAt": "2025-05-01 09:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی",
      "description": "<p>پڕۆگرامێکی تایبەت بۆ فێربوونی زمانی کوردی سۆرانی.</p><img src=\"https://s3.amazonaws.com/bucket/img1.jpg\" />"
    },
    {
      "languageCode": "KMR",
      "title": "Bernameya Perwerdehiya Zimanê Kurdî",
      "description": "<p>Bernameyek taybetî ji bo fêrbûna zimanê Kurdî Kurmancî.</p>"
    }
  ]
}
```

---

#### Request Body — Minimal (Draft, CKB Only)

```json
{
  "serviceType": "Workshop",
  "location": null,
  "publishedAt": null,
  "contents": [
    {
      "languageCode": "CKB",
      "title": "وۆرکشۆپی نووسینی دیجیتاڵ",
      "description": null
    }
  ]
}
```

---

#### Request · curl

```bash
curl -X POST https://api.khi.iq/api/v1/services \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "Training",
    "location": "Sulaymaniyah Hall",
    "publishedAt": "2025-05-01 09:00:00",
    "contents": [
      { "languageCode": "CKB", "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی" },
      { "languageCode": "KMR", "title": "Bernameya Perwerdehiya Zimanê Kurdî" }
    ]
  }'
```

#### Response · 201 Created

```json
{
  "success": true,
  "message": "Service created successfully",
  "data": {
    "id": 5,
    "serviceType": "Training",
    "location": "Sulaymaniyah Hall",
    "active": true,
    "publishedAt": "2025-05-01T09:00:00",
    "contents": [
      { "id": 11, "languageCode": "CKB", "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی", "description": null },
      { "id": 12, "languageCode": "KMR", "title": "Bernameya Perwerdehiya Zimanê Kurdî", "description": null }
    ],
    "createdAt": "2025-04-12T10:00:00",
    "updatedAt": "2025-04-12T10:00:00"
  }
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `400` | `serviceType` is blank or missing |
| `400` | A `contents` entry is missing `languageCode` or `title` |
| `400` | `languageCode` does not match `^[A-Z]{2,5}$` |
| `400` | Duplicate `languageCode` in the same `contents` list |
| `400` | `publishedAt` format is not `"yyyy-MM-dd HH:mm:ss"` |
| `401` | Missing or expired JWT |

---

## 06 · Update

### `PUT /api/v1/services/{id}`

🔒 **Auth Required**

Full update of an existing service. `null` fields are ignored (not applied). When `contents` is non-null, all existing content rows are replaced entirely.

#### Content-Type

```
Content-Type: application/json
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | **Yes** | Primary key of the service to update |

---

#### Request Body — Update Text Only

```json
{
  "contents": [
    {
      "languageCode": "CKB",
      "title": "پڕۆگرامی ڕاهێنانی زمانی کوردی — نوێکراوەوە",
      "description": "<p>وەرشێوەی نوێکراوەی پڕۆگرامی ڕاهێنان بۆ ٢٠٢٥</p>"
    },
    {
      "languageCode": "KMR",
      "title": "Bernameya Perwerdehiyê — Nûvekirî",
      "description": "<p>Guhertoyek nûvekirî ya bernameya perwerdehiyê ji bo 2025</p>"
    }
  ]
}
```

---

#### Request Body — Update Location & Publish Time Only

```json
{
  "location": "Erbil Conference Center",
  "publishedAt": "2025-05-15 08:00:00"
}
```

---

#### Request · curl

```bash
curl -X PUT https://api.khi.iq/api/v1/services/5 \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "location": "Erbil Conference Center",
    "publishedAt": "2025-05-15 08:00:00"
  }'
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Service updated successfully",
  "data": { /* full ServiceResponse */ }
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `404` | Service not found |
| `400` | Validation error (same rules as POST) |
| `401` | Missing or expired JWT |

---

### `PATCH /api/v1/services/{id}/active`

🔒 **Auth Required**

Toggle the `active` flag without affecting content.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | **Yes** | Primary key |

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `value` | boolean | **Yes** | `true` to activate, `false` to deactivate |

#### Request

```
PATCH /api/v1/services/5/active?value=false
Authorization: Bearer eyJhbGci...
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Service deactivated",
  "data": { /* full ServiceResponse with active=false */ }
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `404` | Service not found |
| `400` | `value` parameter missing |
| `401` | Missing or expired JWT |

---

## 07 · Delete

### `DELETE /api/v1/services/{id}`

🔒 **Auth Required**

Permanently delete a service and all its `ServiceContent` rows.

#### Request

```
DELETE /api/v1/services/5
Authorization: Bearer eyJhbGci...
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Service deleted successfully",
  "data": null
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `404` | Service not found |
| `401` | Missing or expired JWT |

---

### `DELETE /api/v1/services/bulk`

🔒 **Auth Required**

Permanently delete multiple services. IDs that do not exist are silently skipped.

#### Content-Type

```
Content-Type: application/json
```

#### Request Body

```json
[5, 6, 7]
```

#### Request · curl

```bash
curl -X DELETE https://api.khi.iq/api/v1/services/bulk \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '[5, 6, 7]'
```

#### Response · 200 OK

```json
{
  "success": true,
  "message": "Services deleted successfully",
  "data": null
}
```

#### Error Responses

| Status | Description |
|--------|-------------|
| `400` | Body is not a valid JSON array or is empty |
| `401` | Missing or expired JWT |

---

## 08 · DTO Reference

### ServiceRequest

Sent as `application/json` in `POST /` and `PUT /{id}`.

| Field | Type | Required (Create) | Description |
|-------|------|-------------------|-------------|
| `serviceType` | String | **Yes** | Free-text type — e.g. `"Training"`, `"Event"`. Max 100 chars |
| `location` | String | No | Physical or virtual location. Max 200 chars. `null` when not applicable |
| `publishedAt` | String | No | Format: `"yyyy-MM-dd HH:mm:ss"`. `null` = draft/unpublished |
| `contents` | `List<ServiceContentRequest>` | No | Bilingual content rows. Each entry needs `languageCode` + `title` |

### ServiceContentRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `languageCode` | String | **Yes** | `"CKB"` or `"KMR"`. Must match `^[A-Z]{2,5}$` |
| `title` | String | **Yes** | Localised service title. Max 300 chars |
| `description` | String | No | Tiptap HTML. All media (images, videos, audio, files) is embedded inline as S3 URLs. `null` is valid |

### ServiceResponse

Returned by all read and write endpoints.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | DB primary key |
| `serviceType` | String | Free-text type label |
| `location` | String | Location — may be `null` |
| `active` | boolean | Public visibility flag |
| `publishedAt` | String | ISO-8601 datetime — may be `null` |
| `contents` | `List<ServiceContentResponse>` | Bilingual content rows |
| `createdAt` | String | ISO-8601 local datetime |
| `updatedAt` | String | ISO-8601 local datetime |

### ServiceContentResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | DB primary key of the content row |
| `languageCode` | String | `"CKB"` or `"KMR"` |
| `title` | String | Localised title |
| `description` | String | Tiptap HTML — may be `null` |

### ApiResponse\<T\>

All endpoints return this wrapper.

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure (`@JsonInclude(NON_NULL)`) |

---

## 09 · Error Responses

### HTTP Status Code Reference

| Status | Description |
|--------|-------------|
| `201 Created` | Service created |
| `200 OK` | Read, update, delete, toggle, or search succeeded |
| `400 Bad Request` | Validation error, missing required field, or invalid value |
| `401 Unauthorized` | JWT missing, expired, or blacklisted |
| `403 Forbidden` | Insufficient role |
| `404 Not Found` | Service not found |
| `500 Internal Error` | Unexpected server failure |

### Validation Error — 400

```json
{
  "timestamp": "2025-04-12T10:00:00",
  "status": 400,
  "errors": [
    { "field": "serviceType", "message": "must not be blank" },
    { "field": "contents[0].languageCode", "message": "language_code must be 2–5 uppercase letters, e.g. CKB or KMR" }
  ]
}
```

### Business Error Keys

| Error Key | Trigger |
|-----------|---------|
| `service.not_found` | No service found for the given `id` |
| `service.field.required` | `serviceType` is blank on create |
| `service.publishedAt.invalid` | `publishedAt` is not `"yyyy-MM-dd HH:mm:ss"` |
| `service.content.language.required` | A `contents` entry has no `languageCode` |
| `service.content.title.required` | A `contents` entry has no `title` |
| `service.content.language.unsupported` | `languageCode` does not match `^[A-Z]{2,5}$` |
| `service.content.duplicate_language` | Same `languageCode` appears more than once in `contents` |
| `service.ids.required` | Bulk delete called with empty or null list |
| `service.search.required` | `/search` called with blank `q` |
| `service.type.required` | `?type=` filter called with blank value |
