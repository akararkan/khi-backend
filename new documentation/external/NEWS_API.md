# News API — External (Public)

**Base URL:** `/api/v1/news`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** Read-only public endpoints. All media URLs in `description` fields point to S3/CDN.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/news` | No | Get all news articles (paginated) |
| `GET` | `/api/v1/news/{id}` | No | Get one news article by ID |
| `GET` | `/api/v1/news/search` | No | Global search across all news fields |
| `GET` | `/api/v1/news/search/keyword` | No | Search by keyword (CKB / KMR / both) |
| `GET` | `/api/v1/news/search/tag` | No | Search by tag (CKB / KMR / both) |
| `GET` | `/api/v1/news/search/category` | No | Filter by category name |
| `GET` | `/api/v1/news/search/subcategory` | No | Filter by sub-category name |

---

## `GET /api/v1/news` — Get All News (Paginated)

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
  "message": "News fetched successfully",
  "data": {
    "content": [
      {
        "id": 42,
        "coverUrl": "https://cdn.khi.org/news/cover.jpg",
        "coverMediaType": "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery": [],
        "datePublished": "2026-06-12",
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00",
        "contentLanguages": ["CKB", "KMR"],
        "category": { "ckbName": "کەلتووری", "kmrName": "Çandî" },
        "subCategory": { "ckbName": "موزیک", "kmrName": "Muzîk" },
        "ckbContent": { "title": "ناونیشانی هەواڵ", "description": "<p>ناوەرۆکی هەواڵ...</p>" },
        "kmrContent": { "title": "Sernavê Nûçeyê", "description": "<p>Naveroka nûçeyê...</p>" },
        "tags": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] },
        "keywords": { "ckb": ["کەلتوور"], "kmr": ["çand"] }
      }
    ],
    "totalElements": 120,
    "totalPages": 6,
    "number": 0,
    "size": 20
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique news article identifier |
| `coverUrl` | string | Cover asset URL |
| `coverMediaType` | enum | `IMAGE` \| `VIDEO` \| `AUDIO` |
| `coverThumbnailUrl` | string | Poster/thumbnail for video or audio cover (may be null) |
| `mediaGallery` | array | Additional media items beside the cover |
| `datePublished` | string | Publish date (`yyyy-MM-dd`) |
| `contentLanguages` | array | Languages present in this article |
| `category.ckbName` | string | Category name (Sorani) |
| `category.kmrName` | string | Category name (Kurmanji) |
| `subCategory.ckbName` | string | Sub-category name (Sorani) |
| `subCategory.kmrName` | string | Sub-category name (Kurmanji) |
| `ckbContent.title` | string | Article title in Sorani Kurdish |
| `ckbContent.description` | string | Tiptap HTML body — all inline media here |
| `kmrContent.title` | string | Article title in Kurmanji Kurdish |
| `kmrContent.description` | string | Tiptap HTML body (Kurmanji) |
| `tags.ckb` | array | Tag strings in Sorani |
| `tags.kmr` | array | Tag strings in Kurmanji |
| `keywords.ckb` | array | Keyword strings in Sorani |
| `keywords.kmr` | array | Keyword strings in Kurmanji |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/news/{id}` — Get News Article by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | News article ID |

**Response `200 OK`:** Single news article object (same shape as array item above).

---

## `GET /api/v1/news/search` — Global Search

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Search term — matched against titles, descriptions, tags, keywords |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Global search completed",
  "data": {
    "content": [
      { "id": 42, "coverUrl": "https://cdn.khi.org/news/cover.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "ناونیشانی هەواڵ", "description": "<p>...</p>" }, "kmrContent": { "title": "Sernavê Nûçeyê", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] }, "datePublished": "2026-06-12" }
    ],
    "totalElements": 8,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/news/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword to match |
| `language` | string | No (`both`) | Search scope: `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "coverUrl": "https://cdn.khi.org/news/cover2.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی کوردستان", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": [] }, "keywords": { "ckb": ["کەلتوور"], "kmr": [] }, "datePublished": "2026-05-20" }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/news/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value to match |
| `language` | string | No (`both`) | Search scope: `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "coverUrl": "https://cdn.khi.org/news/cover2.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی کوردستان", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": [] }, "keywords": { "ckb": ["کەلتوور"], "kmr": [] }, "datePublished": "2026-05-20" }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/news/search/category` — Search by Category

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | **Yes** | Category name — can be CKB or KMR value |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "coverUrl": "https://cdn.khi.org/news/cover2.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی کوردستان", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": [] }, "keywords": { "ckb": ["کەلتوور"], "kmr": [] }, "datePublished": "2026-05-20" }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/news/search/subcategory` — Search by Sub-Category

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | **Yes** | Sub-category name |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "coverUrl": "https://cdn.khi.org/news/cover2.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی کوردستان", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": [] }, "keywords": { "ckb": ["کەلتوور"], "kmr": [] }, "datePublished": "2026-05-20" }
    ],
    "totalElements": 5,
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
| `400 Bad Request` | Missing required query parameter (e.g. `q`, `tag`, `keyword`, `name`) |
| `404 Not Found` | News article not found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
