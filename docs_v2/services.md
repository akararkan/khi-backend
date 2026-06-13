# Services Module

> Bilingual (CKB / KMR) service catalogue with Tiptap descriptions, soft-active toggle, and type filtering. Public reads, admin writes.

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — Service](#02--data-model--service)
- [03 · Data Model — ServiceContent](#03--data-model--servicecontent)
- [04 · Data Model — ServiceAuditLog](#04--data-model--serviceauditlog)
- [05 · Authentication & Roles](#05--authentication--roles)
- [06 · Public API](#06--public-api)
- [07 · Internal API (Admin)](#07--internal-api-admin)
- [08 · DTO Reference](#08--dto-reference)
- [09 · Response Envelope](#09--response-envelope)
- [10 · Error Responses](#10--error-responses)
- [11 · Notes](#11--notes)

---

## 01 · Module Overview

The Services module manages the bilingual catalogue of institute services (training, events, programs, workshops, etc.). Each service is a small parent row that carries dynamic `serviceType`, optional `location`, a publish timestamp, a soft-active toggle, plus a one-to-many set of bilingual `ServiceContent` rows (one per language). All rich text and inline media (image / video / voice / document / other) is embedded inside the Tiptap HTML `description` on each content row — there is no separate cover, hero, gallery, or per-file media model on Service. Files are uploaded once via the shared `POST /api/v1/media/upload`, and the returned S3 URL is baked into the editor before submitting the JSON body here. The `TiptapHtmlProcessor` additionally hoists any inline base64 payloads to S3 at save time.

| Attribute      | Value                                                                                          |
|----------------|------------------------------------------------------------------------------------------------|
| Base path      | `/api/v1/services`                                                                             |
| Controller     | `ServiceController`                                                                            |
| Service class  | `ServiceService`                                                                               |
| Supported langs| `CKB` (Sorani), `KMR` (Kurmanji) — enforced server-side                                        |
| Read access    | Public (all `GET` routes)                                                                      |
| Write access   | `ADMIN` or `SUPER_ADMIN`                                                                       |
| Caching        | Redis cache name `services`, evicted on every CUD operation                                    |
| Audit          | `ServiceAuditLog` rows for `CREATE`, `UPDATE`, `DELETE`, `TOGGLE_ACTIVE`                       |
| Pagination     | Two-phase: ID scan → batch hydration (avoids N+1)                                              |

### Endpoint Summary

| # | Method | Path                                  | Description                                          | Auth                      |
|---|--------|---------------------------------------|------------------------------------------------------|---------------------------|
| 1 | GET    | `/api/v1/services`                    | List active services (paginated); optional `?type=`  | Public                    |
| 2 | GET    | `/api/v1/services/all`                | List all services incl. inactive (paginated)         | Public per SecurityConfig*|
| 3 | GET    | `/api/v1/services/{id}`               | Fetch a single service with all contents             | Public                    |
| 4 | GET    | `/api/v1/services/types`              | Distinct service-type strings                        | Public                    |
| 5 | GET    | `/api/v1/services/search?q=`          | Global search (active only, paginated)               | Public                    |
| 6 | GET    | `/api/v1/services/search/admin?q=`    | Admin search (all rows, paginated)                   | Public per SecurityConfig*|
| 7 | POST   | `/api/v1/services`                    | Create a service                                     | `ADMIN` or `SUPER_ADMIN`  |
| 8 | PUT    | `/api/v1/services/{id}`               | Full update (replaces contents)                      | `ADMIN` or `SUPER_ADMIN`  |
| 9 | PATCH  | `/api/v1/services/{id}/active?value=` | Soft-toggle active flag                              | `ADMIN` or `SUPER_ADMIN`  |
|10 | DELETE | `/api/v1/services/{id}`               | Hard-delete single service                           | `ADMIN` or `SUPER_ADMIN`  |
|11 | DELETE | `/api/v1/services/bulk`               | Hard-delete multiple by ID list                      | `ADMIN` or `SUPER_ADMIN`  |

\* `SecurityConfig` rule `GET /api/v1/services/**` → public applies to **every** `GET`, including the "admin convenience" endpoints `/all` and `/search/admin`. See [Section 07](#07--internal-api-admin).

---

## 02 · Data Model — Service

JPA entity mapped to table `services`.

| Field         | Java Type       | Column          | Constraints / Annotations                                               | Notes                                            |
|---------------|-----------------|-----------------|-------------------------------------------------------------------------|--------------------------------------------------|
| `id`          | `Long`          | `id`            | `@Id`, `@GeneratedValue(IDENTITY)`                                      | Primary key                                      |
| `serviceType` | `String`        | `service_type`  | `@NotBlank`, `@Column(nullable=false, length=100)`                      | Dynamic free-text label                          |
| `location`    | `String`        | `location`      | `@Column(length=200)`                                                   | Nullable; physical or virtual                    |
| `active`      | `boolean`       | `active`        | `@Column`, `@Builder.Default = true`                                    | Soft visibility toggle                           |
| `publishedAt` | `LocalDateTime` | `published_at`  | `@Column`                                                               | Null = draft / unpublished                       |
| `contents`    | `Set<ServiceContent>` | (FK on child) | `@OneToMany(mappedBy="service", cascade=ALL, orphanRemoval=true, fetch=LAZY)`, `@BatchSize(50)`, default `new HashSet<>()` | Bilingual rows |
| `createdAt`   | `LocalDateTime` | `created_at`    | `@CreationTimestamp`, `@Column(updatable=false)`                        | Auto-populated on insert                         |
| `updatedAt`   | `LocalDateTime` | `updated_at`    | `@UpdateTimestamp`, `@Column`                                           | Auto-populated on every update                   |

### Indexes

| Index name                   | Columns         |
|------------------------------|-----------------|
| `idx_service_type`           | `service_type`  |
| `idx_service_active`         | `active`        |
| `idx_service_published_at`   | `published_at`  |

### Helper methods

- `addContent(ServiceContent)` — adds to set and back-references the parent.
- `removeContent(ServiceContent)` — removes and unlinks.

---

## 03 · Data Model — ServiceContent

One row per language per service, mapped to table `service_contents`. This is a separate child table (not `@Embedded`) so additional languages can be added without a schema change.

| Field          | Java Type   | Column           | Constraints / Annotations                                                                                          | Notes                                |
|----------------|-------------|------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `id`           | `Long`      | `id`             | `@Id`, `@GeneratedValue(IDENTITY)`                                                                                 | Primary key                          |
| `languageCode` | `String`    | `language_code`  | `@NotBlank`, `@Pattern(regexp="^[A-Z]{2,5}$", message="language_code must be 2–5 uppercase letters, e.g. CKB or KMR")`, `@Column(nullable=false, length=10)` | `CKB` or `KMR` (service-layer check) |
| `title`        | `String`    | `title`          | `@Column(nullable=false, length=300)`                                                                              | Localised service title              |
| `description`  | `String`    | `description`    | `@Column(columnDefinition="TEXT")`                                                                                 | Tiptap HTML; inline media URLs       |
| `service`      | `Service`   | `service_id` FK  | `@ManyToOne(fetch=LAZY)`, `@JoinColumn(name="service_id", nullable=false)`, `@ToString.Exclude`, `@EqualsAndHashCode.Exclude` | Owning parent                        |

### Unique Constraints

| Constraint name              | Columns                          | Purpose                                  |
|------------------------------|----------------------------------|------------------------------------------|
| `uq_service_content_lang`    | `service_id, language_code`      | One row per language per service         |

### Indexes

| Index name                            | Columns         |
|---------------------------------------|-----------------|
| `idx_service_content_service_id`      | `service_id`    |
| `idx_service_content_lang`            | `language_code` |

### Service-layer validation (from `ServiceService.validateContents`)

- Duplicate `languageCode` values across the contents list → `service.content.duplicate_language`.
- `languageCode` must be non-blank → `service.content.language.required`.
- Allowed languages: `{CKB, KMR}` (uppercased) → `service.content.language.unsupported` otherwise.
- `title` must be non-blank → `service.content.title.required`.

---

## 04 · Data Model — ServiceAuditLog

Append-only audit trail mapped to table `service_audit_logs`. Snapshots `serviceId` and `serviceType` so records survive after hard-deletion of the parent service.

| Field          | Java Type       | Column         | Constraints / Annotations                          | Notes                                          |
|----------------|-----------------|----------------|----------------------------------------------------|------------------------------------------------|
| `id`           | `Long`          | `id`           | `@Id`, `@GeneratedValue(IDENTITY)`                 | Primary key                                    |
| `serviceId`    | `Long`          | `service_id`   | `@Column(nullable=false)`                          | Reference to the service (no FK constraint)    |
| `serviceType`  | `String`        | `service_type` | `@Column(length=100)`                              | Snapshot of type at action time                |
| `action`       | `String`        | `action`       | `@Column(nullable=false, length=30)`               | `CREATE` \| `UPDATE` \| `DELETE` \| `TOGGLE_ACTIVE` |
| `details`      | `String`        | `details`      | `@Column(columnDefinition="TEXT")`                 | Human-readable detail message                  |
| `performedBy`  | `String`        | `performed_by` | `@Column(length=150)`                              | Currently stored as `"system"`                 |
| `requestId`    | `String`        | `request_id`   | `@Column(length=120)`                              | Trace ID for log correlation (from MDC)        |
| `timestamp`    | `LocalDateTime` | `timestamp`    | `@Column(nullable=false)`, `@PrePersist` sets `now()` if null | Action time                          |

### Indexes

| Index name             | Columns        |
|------------------------|----------------|
| `idx_sal_service_id`   | `service_id`   |
| `idx_sal_action`       | `action`       |
| `idx_sal_timestamp`    | `timestamp`    |

Audit rows are written by `ServiceService` for every CUD operation, including each individual service in a bulk-delete.

---

## 05 · Authentication & Roles

| Method | Path                                  | Auth   | Required Roles              |
|--------|---------------------------------------|--------|-----------------------------|
| GET    | `/api/v1/services`                    | None   | —                           |
| GET    | `/api/v1/services/all`                | None*  | —                           |
| GET    | `/api/v1/services/{id}`               | None   | —                           |
| GET    | `/api/v1/services/types`              | None   | —                           |
| GET    | `/api/v1/services/search`             | None   | —                           |
| GET    | `/api/v1/services/search/admin`       | None*  | —                           |
| POST   | `/api/v1/services`                    | Bearer | `ADMIN` or `SUPER_ADMIN`    |
| PUT    | `/api/v1/services/{id}`               | Bearer | `ADMIN` or `SUPER_ADMIN`    |
| PATCH  | `/api/v1/services/{id}/active`        | Bearer | `ADMIN` or `SUPER_ADMIN`    |
| DELETE | `/api/v1/services/{id}`               | Bearer | `ADMIN` or `SUPER_ADMIN`    |
| DELETE | `/api/v1/services/bulk`               | Bearer | `ADMIN` or `SUPER_ADMIN`    |

\* `SecurityConfig` whitelists every `GET /api/v1/services/**` as public. The `/all` and `/search/admin` endpoints are therefore reachable without authentication even though they are intended for the admin UI. Treat the data they expose as public.

---

## 06 · Public API

### 06.1 · `GET /api/v1/services`

List active services with pagination. Optional `type` filter narrows results to one `service_type`.

| Query Param | Required | Default | Description                                              |
|-------------|----------|---------|----------------------------------------------------------|
| `type`      | No       | —       | Filter by `Service.service_type` (case-insensitive trim) |
| `page`      | No       | `0`     | Zero-based page index                                    |
| `size`      | No       | `20`    | Page size                                                |

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services?type=Training&page=0&size=20"
```

**Response 200**

```json
{
  "success": true,
  "message": "Services fetched successfully",
  "data": {
    "content": [
      {
        "id": 12,
        "serviceType": "Training",
        "location": "هەولێر — کۆلێژی زانست",
        "active": true,
        "publishedAt": "2026-04-12 09:00:00",
        "contents": [
          {
            "id": 41,
            "languageCode": "CKB",
            "title": "خولی فێرکردنی زمانی ئینگلیزی",
            "description": "<p>ئەم خولە بۆ فێرکردنی زمانی ئینگلیزی لە ئاستی بنەڕەتییەوە دەستپێدەکات.</p><img src=\"https://s3.example.com/khi/services/12/banner.webp\"/>"
          },
          {
            "id": 42,
            "languageCode": "KMR",
            "title": "Kursa Hînkirina Zimanê Îngilîzî",
            "description": "<p>Ev kurs ji asta bingehîn ve dest pê dike.</p>"
          }
        ],
        "createdAt": "2026-03-30 14:21:11",
        "updatedAt": "2026-04-12 09:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

### 06.2 · `GET /api/v1/services/{id}`

Fetch a single service with all bilingual content rows.

| Path Param | Required | Description       |
|------------|----------|-------------------|
| `id`       | Yes      | Service ID (Long) |

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services/12"
```

**Response 200**

```json
{
  "success": true,
  "message": "Service fetched successfully",
  "data": {
    "id": 12,
    "serviceType": "Training",
    "location": "هەولێر — کۆلێژی زانست",
    "active": true,
    "publishedAt": "2026-04-12 09:00:00",
    "contents": [
      {
        "id": 41,
        "languageCode": "CKB",
        "title": "خولی فێرکردنی زمانی ئینگلیزی",
        "description": "<p>پێشکەشکراوە لەلایەن دامەزراوەی خانی.</p>"
      },
      {
        "id": 42,
        "languageCode": "KMR",
        "title": "Kursa Hînkirina Zimanê Îngilîzî",
        "description": "<p>Ji aliyê Saziya Xanî ve tê pêşkêşkirin.</p>"
      }
    ],
    "createdAt": "2026-03-30 14:21:11",
    "updatedAt": "2026-04-12 09:00:00"
  }
}
```

If the ID is unknown, the global handler returns a `NOT_FOUND` envelope (see [Section 10](#10--error-responses)).

---

### 06.3 · `GET /api/v1/services/types`

Returns the distinct list of `service_type` strings currently used in the DB. Useful for populating a filter dropdown in the public site.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services/types"
```

**Response 200**

```json
{
  "success": true,
  "message": "Service types fetched",
  "data": ["Training", "Event", "Program", "Workshop"]
}
```

---

### 06.4 · `GET /api/v1/services/search?q=`

Global search across `service_type`, `location`, and all bilingual content (title + description). Only active services are returned.

| Query Param | Required | Default | Description                |
|-------------|----------|---------|----------------------------|
| `q`         | Yes      | —       | Search term (non-blank)    |
| `page`      | No       | `0`     | Zero-based page index      |
| `size`      | No       | `20`    | Page size                  |

A blank `q` yields `BadRequest service.search.required` (HTTP 400).

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services/search?q=%D8%AE%D9%88%D9%84&page=0&size=20"
```

**Response 200**

```json
{
  "success": true,
  "message": "Search results fetched",
  "data": {
    "content": [
      {
        "id": 12,
        "serviceType": "Training",
        "location": "هەولێر — کۆلێژی زانست",
        "active": true,
        "publishedAt": "2026-04-12 09:00:00",
        "contents": [
          {
            "id": 41,
            "languageCode": "CKB",
            "title": "خولی فێرکردنی زمانی ئینگلیزی",
            "description": "<p>پێشکەشکراوە لەلایەن دامەزراوەی خانی.</p>"
          }
        ],
        "createdAt": "2026-03-30 14:21:11",
        "updatedAt": "2026-04-12 09:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## 07 · Internal API (Admin)

> The two `GET` endpoints below are designed for the admin console and return inactive rows, but `SecurityConfig` whitelists all `GET /api/v1/services/**` as public — they are **technically reachable without authentication**. All mutating endpoints (`POST` / `PUT` / `PATCH` / `DELETE`) require a Bearer JWT with `ADMIN` or `SUPER_ADMIN`.

### 07.1 · `GET /api/v1/services/all`

List all services including inactive ones, paginated. Intended for the admin table view.

| Query Param | Required | Default | Description           |
|-------------|----------|---------|-----------------------|
| `page`      | No       | `0`     | Zero-based page index |
| `size`      | No       | `20`    | Page size             |

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services/all?page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOi..."
```

**Response 200**

```json
{
  "success": true,
  "message": "All services fetched successfully",
  "data": {
    "content": [
      {
        "id": 13,
        "serviceType": "Workshop",
        "location": null,
        "active": false,
        "publishedAt": null,
        "contents": [
          {
            "id": 50,
            "languageCode": "CKB",
            "title": "وۆرکشۆپی هونەری",
            "description": "<p>دەرگاکانمان بۆ هونەرمەندانی نوێ کراوەن.</p>"
          }
        ],
        "createdAt": "2026-05-04 10:11:00",
        "updatedAt": "2026-05-20 12:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

### 07.2 · `GET /api/v1/services/search/admin?q=`

Admin search across `service_type`, `location`, and bilingual content — includes inactive rows. Same `SecurityConfig` caveat as `/all`.

| Query Param | Required | Default | Description             |
|-------------|----------|---------|-------------------------|
| `q`         | Yes      | —       | Search term (non-blank) |
| `page`      | No       | `0`     | Zero-based page index   |
| `size`      | No       | `20`    | Page size               |

**cURL**

```bash
curl -s "https://api.example.com/api/v1/services/search/admin?q=Training&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOi..."
```

**Response 200** — identical envelope shape to 07.1 with the matched, possibly inactive, rows.

---

### 07.3 · `POST /api/v1/services`

Create a new service. Content-Type must be `application/json` (enforced via `consumes = APPLICATION_JSON_VALUE`).

**Headers**

| Header          | Value                                |
|-----------------|--------------------------------------|
| `Authorization` | `Bearer <token>` (ADMIN/SUPER_ADMIN) |
| `Content-Type`  | `application/json`                   |

**Body — `ServiceRequest`**

```json
{
  "serviceType": "Training",
  "location": "هەولێر — کۆلێژی زانست",
  "publishedAt": "2026-06-01 09:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title": "خولی فێرکردنی زمانی ئینگلیزی",
      "description": "<p>ئەم خولە بۆ فێرکردنی زمانی ئینگلیزی لە ئاستی بنەڕەتییەوە دەستپێدەکات.</p><img src=\"https://s3.example.com/khi/services/12/banner.webp\"/>"
    },
    {
      "languageCode": "KMR",
      "title": "Kursa Hînkirina Zimanê Îngilîzî",
      "description": "<p>Ev kurs ji asta bingehîn ve dest pê dike.</p>"
    }
  ]
}
```

**cURL**

```bash
curl -s -X POST "https://api.example.com/api/v1/services" \
  -H "Authorization: Bearer eyJhbGciOi..." \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "Training",
    "location": "هەولێر — کۆلێژی زانست",
    "publishedAt": "2026-06-01 09:00:00",
    "contents": [
      {
        "languageCode": "CKB",
        "title": "خولی فێرکردنی زمانی ئینگلیزی",
        "description": "<p>ئەم خولە بۆ فێرکردنی زمانی ئینگلیزی لە ئاستی بنەڕەتییەوە دەستپێدەکات.</p>"
      },
      {
        "languageCode": "KMR",
        "title": "Kursa Hînkirina Zimanê Îngilîzî",
        "description": "<p>Ev kurs ji asta bingehîn ve dest pê dike.</p>"
      }
    ]
  }'
```

**Response 201**

```json
{
  "success": true,
  "message": "Service created successfully",
  "data": {
    "id": 14,
    "serviceType": "Training",
    "location": "هەولێر — کۆلێژی زانست",
    "active": true,
    "publishedAt": "2026-06-01 09:00:00",
    "contents": [
      { "id": 60, "languageCode": "CKB", "title": "خولی فێرکردنی زمانی ئینگلیزی", "description": "<p>...</p>" },
      { "id": 61, "languageCode": "KMR", "title": "Kursa Hînkirina Zimanê Îngilîzî", "description": "<p>...</p>" }
    ],
    "createdAt": "2026-05-31 11:42:00",
    "updatedAt": "2026-05-31 11:42:00"
  }
}
```

Server behaviour:

- `serviceType` is trimmed and required (else `service.field.required`).
- `location` is trimmed; blank → null.
- `publishedAt` is parsed with `yyyy-MM-dd HH:mm:ss` (else `service.publishedAt.invalid`).
- `active` is forced to `true` on create.
- Each content row is uppercased to its `languageCode`, trimmed, and the `description` is passed through `TiptapHtmlProcessor` which hoists inline base64 media to S3.
- An audit row `action=CREATE` is written.
- The Redis cache `services` is fully evicted (`allEntries=true`).

---

### 07.4 · `PUT /api/v1/services/{id}`

Full update. Replaces all content rows by clearing the existing set, flushing, then re-inserting from the request body. Content-Type must be `application/json`.

**Path / Headers** — same as `POST` plus `{id}`.

**Body** — identical shape to `ServiceRequest` above.

**cURL**

```bash
curl -s -X PUT "https://api.example.com/api/v1/services/14" \
  -H "Authorization: Bearer eyJhbGciOi..." \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "Training",
    "location": "سلێمانی — ناوەندی فێرکاری",
    "publishedAt": "2026-06-05 09:00:00",
    "contents": [
      {
        "languageCode": "CKB",
        "title": "خولی فێرکردنی زمانی ئینگلیزی — وەشانی نوێ",
        "description": "<p>وەشانی نوێ کراوەی خولەکە.</p>"
      },
      {
        "languageCode": "KMR",
        "title": "Kursa Îngilîzî — Versiyona Nû",
        "description": "<p>Versiyona nû ya kursê.</p>"
      }
    ]
  }'
```

**Response 200**

```json
{
  "success": true,
  "message": "Service updated successfully",
  "data": {
    "id": 14,
    "serviceType": "Training",
    "location": "سلێمانی — ناوەندی فێرکاری",
    "active": true,
    "publishedAt": "2026-06-05 09:00:00",
    "contents": [
      { "id": 72, "languageCode": "CKB", "title": "خولی فێرکردنی زمانی ئینگلیزی — وەشانی نوێ", "description": "<p>...</p>" },
      { "id": 73, "languageCode": "KMR", "title": "Kursa Îngilîzî — Versiyona Nû", "description": "<p>...</p>" }
    ],
    "createdAt": "2026-05-31 11:42:00",
    "updatedAt": "2026-05-31 12:01:55"
  }
}
```

Server behaviour:

- Looks up the service via `findByIdWithAll`; missing → `NotFoundException service.not_found`.
- Re-runs `validateContents` (duplicate-language, allowed codes, title required).
- Clears `contents` and calls `saveAndFlush` so DELETEs run before re-INSERTs (avoids `uq_service_content_lang` violations).
- Rebuilds content rows (uppercased, trimmed, Tiptap-processed).
- Writes audit row `action=UPDATE`.
- Evicts the `services` cache.

---

### 07.5 · `PATCH /api/v1/services/{id}/active?value={true|false}`

Soft toggle the `active` flag without touching contents.

| Path / Query | Required | Description                              |
|--------------|----------|------------------------------------------|
| `id`         | Yes      | Service ID                               |
| `value`      | Yes      | `true` to activate, `false` to deactivate |

**cURL**

```bash
curl -s -X PATCH "https://api.example.com/api/v1/services/14/active?value=false" \
  -H "Authorization: Bearer eyJhbGciOi..."
```

**Response 200**

```json
{
  "success": true,
  "message": "Service deactivated",
  "data": {
    "id": 14,
    "serviceType": "Training",
    "location": "سلێمانی — ناوەندی فێرکاری",
    "active": false,
    "publishedAt": "2026-06-05 09:00:00",
    "contents": [ /* ... */ ],
    "createdAt": "2026-05-31 11:42:00",
    "updatedAt": "2026-05-31 12:15:00"
  }
}
```

The response message is `"Service activated"` when `value=true`. Audit row `action=TOGGLE_ACTIVE` is written. Cache is evicted.

---

### 07.6 · `DELETE /api/v1/services/{id}`

Hard-deletes the service. `OneToMany(cascade=ALL, orphanRemoval=true)` cascades to its `ServiceContent` rows.

**cURL**

```bash
curl -s -X DELETE "https://api.example.com/api/v1/services/14" \
  -H "Authorization: Bearer eyJhbGciOi..."
```

**Response 200**

```json
{
  "success": true,
  "message": "Service deleted successfully",
  "data": null
}
```

Behaviour:

- `id` must be non-null (else `BadRequestException service.id.required`).
- Service must exist (else `NotFoundException service.not_found`).
- Audit row `action=DELETE` is written **before** the row is removed (snapshot survives).
- Cache is evicted.

---

### 07.7 · `DELETE /api/v1/services/bulk`

Hard-deletes multiple services by ID. Body is a raw JSON array of `Long` IDs (not wrapped).

**Headers**

| Header          | Value                                |
|-----------------|--------------------------------------|
| `Authorization` | `Bearer <token>` (ADMIN/SUPER_ADMIN) |
| `Content-Type`  | `application/json`                   |

**Body**

```json
[14, 15, 17]
```

**cURL**

```bash
curl -s -X DELETE "https://api.example.com/api/v1/services/bulk" \
  -H "Authorization: Bearer eyJhbGciOi..." \
  -H "Content-Type: application/json" \
  -d '[14, 15, 17]'
```

**Response 200**

```json
{
  "success": true,
  "message": "Services deleted successfully",
  "data": null
}
```

Behaviour:

- Empty / null list → `BadRequestException service.ids.required`.
- If no IDs in the list match any row → `NotFoundException service.not_found`.
- An audit row `action=DELETE` is saved for each matched service before deletion.
- Cache is evicted in one call.

---

## 08 · DTO Reference

All DTOs live in `ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs`. Each is annotated `@Data @NoArgsConstructor @AllArgsConstructor @Builder`.

### 08.1 · `ServiceDTOs.ServiceRequest`

| Field         | Type                            | Validation / Notes                                                                                 |
|---------------|---------------------------------|----------------------------------------------------------------------------------------------------|
| `serviceType` | `String`                        | Required (service-layer `trimRequired`); free-text label                                           |
| `location`    | `String`                        | Optional; trimmed; blank becomes `null`                                                            |
| `publishedAt` | `String`                        | Optional; pattern `yyyy-MM-dd HH:mm:ss`; null = draft                                              |
| `contents`    | `List<ServiceContentRequest>`   | Optional; duplicate language codes rejected; each entry validated below                            |

No bean-validation annotations are declared on the DTO itself — validation is performed in `ServiceService.validateContents` and helper methods.

### 08.2 · `ServiceDTOs.ServiceContentRequest`

| Field          | Type     | Validation / Notes                                                                  |
|----------------|----------|-------------------------------------------------------------------------------------|
| `languageCode` | `String` | Required, non-blank, must be `"CKB"` or `"KMR"` (uppercased server-side)            |
| `title`        | `String` | Required, non-blank, max 300 chars (DB column)                                      |
| `description`  | `String` | Optional; Tiptap HTML; processed via `TiptapHtmlProcessor` (base64 → S3 rewrite)    |

### 08.3 · `ServiceDTOs.ServiceResponse`

| Field         | Type                            | Notes                                                              |
|---------------|---------------------------------|--------------------------------------------------------------------|
| `id`          | `Long`                          | Service ID                                                         |
| `serviceType` | `String`                        | Same as entity                                                     |
| `location`    | `String`                        | May be null                                                        |
| `active`      | `boolean`                       | Soft toggle                                                        |
| `publishedAt` | `String`                        | Formatted `yyyy-MM-dd HH:mm:ss`; null when unpublished             |
| `contents`    | `List<ServiceContentResponse>`  | All language rows                                                  |
| `createdAt`   | `String`                        | Formatted `yyyy-MM-dd HH:mm:ss`                                    |
| `updatedAt`   | `String`                        | Formatted `yyyy-MM-dd HH:mm:ss`                                    |

### 08.4 · `ServiceDTOs.ServiceContentResponse`

| Field          | Type     | Notes                                  |
|----------------|----------|----------------------------------------|
| `id`           | `Long`   | Content row PK                         |
| `languageCode` | `String` | `CKB` or `KMR`                         |
| `title`        | `String` | Localised title                        |
| `description`  | `String` | Tiptap HTML (S3 URLs in src/href)      |

---

## 09 · Response Envelope

### `ApiResponse<T>`

Every controller method returns `ResponseEntity<ApiResponse<T>>`:

```json
{
  "success": true,
  "message": "Human-readable status",
  "data": { /* payload of type T, or null */ }
}
```

| Field     | Type     | Notes                                          |
|-----------|----------|------------------------------------------------|
| `success` | boolean  | `true` for 2xx successes                       |
| `message` | string   | Endpoint-specific message                      |
| `data`    | T / null | Payload — entity, Page, list, string, or null  |

### `Page<T>` (Spring Data) — used by all paginated endpoints

```json
{
  "content": [ /* T[] */ ],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20
}
```

| Field           | Type    | Notes                                |
|-----------------|---------|--------------------------------------|
| `content`       | T[]     | Rows for the current page            |
| `totalElements` | number  | Total matching rows across all pages |
| `totalPages`    | number  | Total page count                     |
| `number`        | number  | Zero-based current page index        |
| `size`          | number  | Page size used                       |

Spring serialises additional `Pageable` metadata (e.g. `pageable`, `sort`, `first`, `last`, `numberOfElements`, `empty`) — clients should ignore unknown fields.

---

## 10 · Error Responses

Errors are emitted by the global exception handler as a bilingual `ApiErrorResponse` envelope:

```json
{
  "timestamp": "2026-05-31T11:42:33.512Z",
  "status": 404,
  "path": "/api/v1/services/9999",
  "method": "GET",
  "traceId": "a1b2c3d4",
  "code": "NOT_FOUND",
  "message": "Service not found",
  "messageEn": "Service not found",
  "messageKu": "خزمەتگوزاری نەدۆزرایەوە",
  "fieldErrors": null,
  "details": {
    "id": 9999
  }
}
```

| Field         | Type            | Notes                                                                 |
|---------------|-----------------|-----------------------------------------------------------------------|
| `timestamp`   | ISO-8601 string | Server time of the error                                              |
| `status`      | number          | HTTP status code                                                      |
| `path`        | string          | Request path                                                          |
| `method`      | string          | HTTP method                                                           |
| `traceId`     | string          | MDC trace ID for correlating with logs (also used as audit `requestId`) |
| `code`        | string          | Stable machine code (e.g. `NOT_FOUND`, `VALIDATION_ERROR`)            |
| `message`     | string          | Default-locale message                                                |
| `messageEn`   | string          | English message                                                       |
| `messageKu`   | string          | Kurdish message                                                       |
| `fieldErrors` | object \| null  | Map of field → error message (validation errors only)                 |
| `details`     | object \| null  | Structured context (e.g. `{ "id": 9999 }`, `{ "field": "serviceType" }`) |

### Common codes for this module

| HTTP | code              | Trigger                                                                                         |
|------|-------------------|-------------------------------------------------------------------------------------------------|
| 400  | `VALIDATION_ERROR`| Missing `serviceType`, blank `title`, blank `q`, blank `id`, blank `ids`                        |
| 400  | `VALIDATION_ERROR`| `publishedAt` not matching `yyyy-MM-dd HH:mm:ss`                                                |
| 400  | `VALIDATION_ERROR`| Unsupported `languageCode` (not in `{CKB, KMR}`) or duplicate language codes in contents        |
| 401  | `UNAUTHORIZED`    | Missing / invalid JWT on `POST` / `PUT` / `PATCH` / `DELETE`                                    |
| 403  | `FORBIDDEN`       | Authenticated but lacks `ADMIN` / `SUPER_ADMIN`                                                 |
| 404  | `NOT_FOUND`       | `getById`, `update`, `setActive`, `delete`, `deleteBulk` against unknown ID(s)                  |
| 409  | `CONFLICT`        | DB-level violation (e.g. simultaneous insert hitting `uq_service_content_lang`)                 |

Internal service error keys (returned via `details.code` or as the message key, depending on the global handler):

| Key                                       | Meaning                                              |
|-------------------------------------------|------------------------------------------------------|
| `service.not_found`                       | Service ID(s) not found                              |
| `service.id.required`                     | `id` was null on delete                              |
| `service.ids.required`                    | `ids` list was null or empty on bulk delete          |
| `service.type.required`                   | `serviceType` filter blank in `getAllActiveByType`   |
| `service.field.required`                  | Generic missing field (with `field` detail)          |
| `service.search.required`                 | `q` blank in search / admin search                   |
| `service.publishedAt.invalid`             | Bad `publishedAt` format                             |
| `service.content.language.required`       | Content row missing `languageCode`                   |
| `service.content.language.unsupported`    | `languageCode` not in `{CKB, KMR}`                   |
| `service.content.title.required`          | Content row missing `title`                          |
| `service.content.duplicate_language`      | Two content rows with the same language code         |

---

## 11 · Notes

- **Soft-active vs hard-delete.** `PATCH /{id}/active?value=false` is the recommended way to hide a service from public listings while preserving its rows and audit history. `DELETE /{id}` and `DELETE /bulk` are hard deletes — the rows are removed and child `ServiceContent` rows cascade-delete via `orphanRemoval=true`. `ServiceAuditLog` rows persist after deletion because the audit table has no FK to `services`; the snapshot `serviceId` + `serviceType` are kept on the log row.
- **Tiptap inline media.** Service has no separate cover, hero, gallery, or per-file metadata model. All images / videos / voice / documents live inline inside the bilingual Tiptap HTML `description` (`<img>`, `<video>`, `<audio>`, `<a href>`). The frontend uploads each file once via the shared `POST /api/v1/media/upload` and bakes the returned S3 URL into the editor before submitting. On save, `TiptapHtmlProcessor.process(description)` also hoists any inline base64 payloads to S3.
- **Bulk delete payload.** The body for `DELETE /api/v1/services/bulk` is a JSON array of IDs (`[14, 15, 17]`), not an object wrapper. Empty / null arrays return a `VALIDATION_ERROR`.
- **`type` filter operates on `Service.serviceType`** (`Service.service_type` column on the `services` table), not on `ServiceContent`. Repository call: `serviceRepository.findActiveIdsByType(serviceType.trim(), pageable)`. There is no localized type — all language rows share the parent's `serviceType`.
- **Caching.** Reads are cached under cache name `services` with composite keys (`active:p{page}:s{size}`, `all:p{page}:s{size}`, `type:{lower}:p{page}:s{size}`, `search:{lower}:p{page}:s{size}`, `adminSearch:{lower}:p{page}:s{size}`, `types`). `getById` is not cached. Every CUD operation triggers `@CacheEvict(value="services", allEntries=true)`.
- **Two-phase pagination.** List endpoints first fetch a `Page<Long>` of IDs, then hydrate via `findAllByIds` to avoid N+1 on `@OneToMany contents`. Original ID order is preserved by re-sorting the hydrated set against the ID list.
- **`GET /all` and `GET /search/admin` are publicly reachable** despite naming, because `SecurityConfig` permits all `GET /api/v1/services/**`. Treat the rows they return (including inactive ones) as public information, or tighten `SecurityConfig` if that is not desired.
- **Audit `performedBy`** is currently hard-coded to `"system"` in `ServiceService.buildAuditLog`. Future auth integration is expected to replace this with the authenticated principal.
