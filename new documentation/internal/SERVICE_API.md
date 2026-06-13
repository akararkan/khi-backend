# Service API — Internal (Admin)

**Base URL:** `/api/v1/services`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** All media is embedded inside the Tiptap `description` field per content row. Upload via `POST /api/v1/media/upload` first.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/services` | No | Public | Get active services, optional type filter (paginated) |
| `GET` | `/api/v1/services/all` | Yes | `ADMIN` / `SUPER_ADMIN` | Get all services including inactive (paginated) |
| `GET` | `/api/v1/services/{id}` | No | Public | Get one service by ID |
| `GET` | `/api/v1/services/types` | No | Public | Get all distinct service type names |
| `GET` | `/api/v1/services/search` | No | Public | Search active services |
| `GET` | `/api/v1/services/search/admin` | Yes | `ADMIN` / `SUPER_ADMIN` | Search all services including inactive |
| `POST` | `/api/v1/services` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new service |
| `PUT` | `/api/v1/services/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update a service |
| `PATCH` | `/api/v1/services/{id}/active` | Yes | `ADMIN` / `SUPER_ADMIN` | Toggle service active/inactive status |
| `DELETE` | `/api/v1/services/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a service |
| `DELETE` | `/api/v1/services/bulk` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete multiple services at once |

---

## `GET /api/v1/services` — Get Active Services (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | string | No | Filter by service type (e.g. `Training`, `Event`, `Workshop`) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Services fetched successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "serviceType": "Training",
        "location": "هەولێر",
        "active": true,
        "publishedAt": "2026-06-15 09:00:00",
        "contents": [
          { "id": 1, "languageCode": "CKB", "title": "پەرەپێدانی کارمەندان", "description": "<p>ناوەرۆکی خزمەتگوزاری...</p>" },
          { "id": 2, "languageCode": "KMR", "title": "Pêşvebirina Karmendan", "description": "<p>Naveroka xizmetê...</p>" }
        ],
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00"
      }
    ],
    "totalElements": 30,
    "totalPages": 2,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/services/all` — Get All Services including Inactive (Paginated)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index |
| `size` | int | `20` | Items per page |

**Response `200 OK`:** Same shape as `GET /services` but includes inactive services.

---

## `GET /api/v1/services/{id}` — Get Service by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Service ID |

**Response `200 OK`:** Single service object (same shape as array item above).

---

## `GET /api/v1/services/types` — Get All Service Type Names

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Service types fetched",
  "data": ["Training", "Event", "Workshop", "Program", "Conference"]
}
```

---

## `GET /api/v1/services/search` — Search Active Services

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Search term — matched against titles and descriptions |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search results fetched",
  "data": {
    "content": [
      { "id": 2, "serviceType": "Workshop", "location": "سلێمانی", "active": true, "publishedAt": "2026-05-10 09:00:00", "contents": [{ "id": 3, "languageCode": "CKB", "title": "وۆرکشۆپی دیزاین", "description": "<p>...</p>" }], "createdAt": "2026-05-01T10:00:00" }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/services/search/admin` — Admin Search (all, including inactive)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Search term |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Admin search results fetched",
  "data": {
    "content": [
      { "id": 3, "serviceType": "Event", "location": "کرکوک", "active": false, "publishedAt": null, "contents": [{ "id": 5, "languageCode": "CKB", "title": "ئیڤێنتی دەستپێنەکراو", "description": "<p>...</p>" }], "createdAt": "2026-04-15T08:00:00" }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `POST /api/v1/services` — Create Service

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serviceType` | string | No | Free-text type label (e.g. `Training`, `Event`, `Workshop`) |
| `location` | string | No | Physical or virtual location of the service |
| `publishedAt` | string | No | Publish timestamp — format: `yyyy-MM-dd HH:mm:ss` — null means draft |
| `contents` | array | **Yes** | Bilingual content list — must have at least one entry |
| `contents[].languageCode` | string | **Yes** | Language: `CKB` (Sorani) or `KMR` (Kurmanji) |
| `contents[].title` | string | **Yes** | Service title for this language |
| `contents[].description` | string | No | Tiptap HTML description — all inline media embedded here |

**Request JSON:**
```json
{
  "serviceType": "Training",
  "location": "هەولێر",
  "publishedAt": "2026-06-15 09:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title": "پەرەپێدانی کارمەندان",
      "description": "<p>ناوەرۆکی خزمەتگوزاریەکە لێرەدایە...</p>"
    },
    {
      "languageCode": "KMR",
      "title": "Pêşvebirina Karmendan",
      "description": "<p>Naveroka xizmetê li vir e...</p>"
    }
  ]
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Service created successfully",
  "data": {
    "id": 1,
    "serviceType": "Training",
    "location": "هەولێر",
    "active": true,
    "publishedAt": "2026-06-15 09:00:00",
    "contents": [
      { "id": 1, "languageCode": "CKB", "title": "پەرەپێدانی کارمەندان", "description": "<p>...</p>" },
      { "id": 2, "languageCode": "KMR", "title": "Pêşvebirina Karmendan", "description": "<p>...</p>" }
    ],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
}
```

---

## `PUT /api/v1/services/{id}` — Update Service

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the service to update |

**Request Body:** Same fields as `POST`. Providing `contents` replaces the entire contents array.

**Response `200 OK`:** Full updated service object.

---

## `PATCH /api/v1/services/{id}/active` — Toggle Active Status

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the service to toggle |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `value` | boolean | **Yes** | `true` to activate, `false` to deactivate |

**Response `200 OK`:**
```json
{ "success": true, "message": "Service activated", "data": { "id": 1, "active": true, "...": "..." } }
```

---

## `DELETE /api/v1/services/{id}` — Delete Service

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the service to delete |

**Response `200 OK`:**
```json
{ "success": true, "message": "Service deleted successfully", "data": null }
```

---

## `DELETE /api/v1/services/bulk` — Bulk Delete Services

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body:** Array of service IDs to delete.
```json
[1, 2, 3]
```

**Response `200 OK`:**
```json
{ "success": true, "message": "Services deleted successfully", "data": null }
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contents` array, missing `languageCode` or `title` in a content entry, invalid JSON |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | No service found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
