# Project API — External (Public)

**Base URL:** `/api/v1/projects`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** Read-only public endpoints. Media URLs inside `description` fields point to S3/CDN.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/projects/getAll` | No | Get all projects (paginated) |
| `GET` | `/api/v1/projects/search/tag` | No | Search projects by tag |
| `GET` | `/api/v1/projects/search/keyword` | No | Search projects by keyword |

---

## `GET /api/v1/projects/getAll` — Get All Projects (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `20` | Number of items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Projects fetched successfully",
  "data": {
    "content": [
      {
        "id": 10,
        "coverUrl": "https://cdn.khi.org/projects/cover.jpg",
        "coverMediaType": "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery": [],
        "projectTypeCkb": "پەروەردەیی",
        "projectTypeKmr": "Perwerdehî",
        "status": "ACTIVE",
        "projectDate": "2026-01-15",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "پڕۆژەی پەرەپێدانی زمانی کوردی",
          "description": "<p>ناوەرۆکی پڕۆژەکە...</p>",
          "location": "هەولێر"
        },
        "kmrContent": {
          "title": "Projeya Pêşveçûna Zimanê Kurdî",
          "description": "<p>Naveroka projeyê...</p>",
          "location": "Hewlêr"
        },
        "tagsCkb": ["زمان", "کوردستان"],
        "tagsKmr": ["Ziman", "Kurdistan"],
        "keywordsCkb": ["زمانناسی"],
        "keywordsKmr": ["zimannasî"],
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "number": 0,
    "size": 20
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique project identifier |
| `coverUrl` | string | Cover asset URL |
| `coverMediaType` | enum | `IMAGE` \| `VIDEO` \| `AUDIO` |
| `coverThumbnailUrl` | string | Thumbnail for video/audio cover (may be null) |
| `mediaGallery` | array | Additional media items beside the cover |
| `projectTypeCkb` | string | Project type label in Sorani |
| `projectTypeKmr` | string | Project type label in Kurmanji |
| `status` | enum | `ACTIVE` \| `COMPLETED` \| `ARCHIVED` |
| `projectDate` | string | Project date (`yyyy-MM-dd`) |
| `contentLanguages` | array | Languages present in this project |
| `ckbContent.title` | string | Project title in Sorani |
| `ckbContent.description` | string | Tiptap HTML body with inline media (Sorani) |
| `ckbContent.location` | string | Project location (Sorani) |
| `kmrContent` | object | Same structure as `ckbContent` for Kurmanji |
| `tagsCkb` | array | Tags in Sorani |
| `tagsKmr` | array | Tags in Kurmanji |
| `keywordsCkb` | array | Keywords in Sorani |
| `keywordsKmr` | array | Keywords in Kurmanji |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/projects/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value to match (CKB or KMR) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by tag completed",
  "data": {
    "content": [
      { "id": 10, "coverUrl": "https://cdn.khi.org/projects/cover.jpg", "coverMediaType": "IMAGE", "projectTypeCkb": "پەروەردەیی", "projectTypeKmr": "Perwerdehî", "status": "ACTIVE", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "پڕۆژەی پەرەپێدانی", "description": "<p>...</p>", "location": "هەولێر" }, "tagsCkb": ["زمان"], "tagsKmr": ["Ziman"], "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/projects/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword to match (CKB or KMR) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by keyword completed",
  "data": {
    "content": [
      { "id": 8, "coverUrl": "https://cdn.khi.org/projects/cover2.jpg", "coverMediaType": "IMAGE", "projectTypeCkb": "کەلتووری", "status": "COMPLETED", "contentLanguages": ["CKB"], "ckbContent": { "title": "پڕۆژەی زمانناسی", "description": "<p>...</p>", "location": "سلێمانی" }, "keywordsCkb": ["زمانناسی"], "createdAt": "2026-04-01T08:00:00" }
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
| `400 Bad Request` | Missing required query parameter (`tag` or `keyword`) |
| `404 Not Found` | No project found (only when fetching by specific ID if added later) |
| `500 Internal Server Error` | Unexpected server-side failure |
