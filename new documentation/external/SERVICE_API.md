# Service API — External (Public)

**Base URL:** `/api/v1/services`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** Read-only public endpoints. Returns only active services.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/services` | No | Get active services, optional type filter (paginated) |
| `GET` | `/api/v1/services/{id}` | No | Get one service by ID |
| `GET` | `/api/v1/services/types` | No | Get all distinct service type names |
| `GET` | `/api/v1/services/search` | No | Search active services |

---

## `GET /api/v1/services` — Get Active Services (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | string | No | Filter by service type (e.g. `Training`, `Event`, `Workshop`) |
| `page` | int | No (0) | Page index (0-based) |
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

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique service identifier |
| `serviceType` | string | Free-text type label |
| `location` | string | Physical or virtual service location |
| `active` | boolean | Always `true` for public responses |
| `publishedAt` | string | Publish timestamp (`yyyy-MM-dd HH:mm:ss`) |
| `contents` | array | Bilingual content entries |
| `contents[].id` | long | Content row ID |
| `contents[].languageCode` | string | `CKB` or `KMR` |
| `contents[].title` | string | Service title for this language |
| `contents[].description` | string | Tiptap HTML description with inline media |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/services/{id}` — Get Service by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Service ID |

**Response `200 OK`:** Single service object (same shape as array item above).

---

## `GET /api/v1/services/types` — Get Service Type Names

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
| `q` | string | **Yes** | Search term matched against titles and descriptions |
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

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required query parameter `q` for search |
| `404 Not Found` | No service found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
