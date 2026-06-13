# News API — Internal (Admin)

**Base URL:** `/api/v1/news`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** All inline media lives inside the Tiptap `description` field. Upload cover/media via `POST /api/v1/media/upload` first, then send the returned URLs in the request body.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/news` | No | Public | Get all news articles (paginated) |
| `GET` | `/api/v1/news/{id}` | No | Public | Get one news article by ID |
| `GET` | `/api/v1/news/search` | No | Public | Global search across all news fields |
| `GET` | `/api/v1/news/search/keyword` | No | Public | Search by keyword (CKB / KMR / both) |
| `GET` | `/api/v1/news/search/tag` | No | Public | Search by tag (CKB / KMR / both) |
| `GET` | `/api/v1/news/search/category` | No | Public | Filter by category name |
| `GET` | `/api/v1/news/search/subcategory` | No | Public | Filter by sub-category name |
| `POST` | `/api/v1/news` | Yes | `EMPLOYEE` / `ADMIN` / `SUPER_ADMIN` | Create a single news article |
| `POST` | `/api/v1/news/bulk` | Yes | `EMPLOYEE` / `ADMIN` / `SUPER_ADMIN` | Create multiple news articles at once |
| `PUT` | `/api/v1/news/{id}` | Yes | `EMPLOYEE` / `ADMIN` / `SUPER_ADMIN` | Update a news article |
| `DELETE` | `/api/v1/news/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a news article |
| `DELETE` | `/api/v1/news/delete/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a news article (alias path) |
| `DELETE` | `/api/v1/news/bulk` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete multiple news articles at once |

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

---

## `GET /api/v1/news/{id}` — Get News by ID

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
      { "id": 42, "coverUrl": "https://cdn.khi.org/news/cover.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB", "KMR"], "category": { "ckbName": "کەلتووری", "kmrName": "Çandî" }, "ckbContent": { "title": "ناونیشانی هەواڵ", "description": "<p>...</p>" }, "kmrContent": { "title": "Sernavê Nûçeyê", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] }, "datePublished": "2026-06-12", "createdAt": "2026-06-12T10:00:00" }
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
| `keyword` | string | **Yes** | Keyword value to match |
| `language` | string | No (`both`) | Language scope: `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by keyword completed",
  "data": {
    "content": [
      { "id": 15, "coverUrl": "https://cdn.khi.org/news/cover2.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی کوردستان", "description": "<p>...</p>" }, "keywords": { "ckb": ["کوردستان"], "kmr": [] }, "datePublished": "2026-05-20", "createdAt": "2026-05-20T09:00:00" }
    ],
    "totalElements": 3,
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
| `language` | string | No (`both`) | Language scope: `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by tag completed",
  "data": {
    "content": [
      { "id": 22, "coverUrl": "https://cdn.khi.org/news/cover3.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "هەواڵی نەورۆز", "description": "<p>...</p>" }, "tags": { "ckb": ["کوردستان", "نەورۆز"], "kmr": ["Kurdistan", "Newroz"] }, "datePublished": "2026-03-21", "createdAt": "2026-03-21T08:00:00" }
    ],
    "totalElements": 7,
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
| `name` | string | **Yes** | Category name (can be CKB or KMR value) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by category completed",
  "data": {
    "content": [
      { "id": 31, "coverUrl": "https://cdn.khi.org/news/cover4.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB", "KMR"], "category": { "ckbName": "کەلتووری", "kmrName": "Çandî" }, "ckbContent": { "title": "هەواڵی کەلتووری", "description": "<p>...</p>" }, "datePublished": "2026-04-10", "createdAt": "2026-04-10T10:00:00" }
    ],
    "totalElements": 12,
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
  "message": "Search by subcategory completed",
  "data": {
    "content": [
      { "id": 38, "coverUrl": "https://cdn.khi.org/news/cover5.jpg", "coverMediaType": "IMAGE", "contentLanguages": ["CKB"], "subCategory": { "ckbName": "موزیک", "kmrName": "Muzîk" }, "ckbContent": { "title": "هەواڵی موزیک", "description": "<p>...</p>" }, "datePublished": "2026-05-01", "createdAt": "2026-05-01T09:00:00" }
    ],
    "totalElements": 4,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `POST /api/v1/news` — Create News Article

**Auth:** JWT required · Role: `EMPLOYEE`, `ADMIN`, or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentLanguages` | array | **Yes** | Languages present — `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `coverUrl` | string | No | URL of the cover asset (uploaded via `/api/v1/media/upload`) |
| `coverMediaType` | enum | No | Type of cover: `IMAGE` \| `VIDEO` \| `AUDIO` — defaults to `IMAGE` |
| `coverThumbnailUrl` | string | No | Thumbnail/poster URL for video or audio cover |
| `mediaGallery` | array | No | List of additional media items displayed beside the cover |
| `datePublished` | string | No | Publish date in `yyyy-MM-dd` format |
| `category.ckbName` | string | No | Category name in Sorani |
| `category.kmrName` | string | No | Category name in Kurmanji |
| `subCategory.ckbName` | string | No | Sub-category name in Sorani |
| `subCategory.kmrName` | string | No | Sub-category name in Kurmanji |
| `ckbContent.title` | string | No | Article title in Sorani |
| `ckbContent.description` | string | No | Tiptap HTML body for Sorani — all inline media embedded here |
| `kmrContent.title` | string | No | Article title in Kurmanji |
| `kmrContent.description` | string | No | Tiptap HTML body for Kurmanji |
| `tags.ckb` | array | No | List of tag strings in Sorani |
| `tags.kmr` | array | No | List of tag strings in Kurmanji |
| `keywords.ckb` | array | No | List of keyword strings in Sorani |
| `keywords.kmr` | array | No | List of keyword strings in Kurmanji |

**Request JSON:**
```json
{
  "contentLanguages": ["CKB", "KMR"],
  "coverUrl": "https://cdn.khi.org/news/cover.jpg",
  "coverMediaType": "IMAGE",
  "datePublished": "2026-06-12",
  "category": { "ckbName": "کەلتووری", "kmrName": "Çandî" },
  "subCategory": { "ckbName": "موزیک", "kmrName": "Muzîk" },
  "ckbContent": {
    "title": "ناونیشانی هەواڵ",
    "description": "<p>ناوەرۆکی هەواڵەکە لێرەدایە...</p>"
  },
  "kmrContent": {
    "title": "Sernavê Nûçeyê",
    "description": "<p>Naveroka nûçeyê li vir e...</p>"
  },
  "tags": { "ckb": ["کوردستان", "کەلتوور"], "kmr": ["Kurdistan", "Çand"] },
  "keywords": { "ckb": ["موزیک", "هونەر"], "kmr": ["Muzîk", "Huner"] }
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "News created successfully",
  "data": {
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
    "ckbContent": { "title": "ناونیشانی هەواڵ", "description": "<p>...</p>" },
    "kmrContent": { "title": "Sernavê Nûçeyê", "description": "<p>...</p>" },
    "tags": { "ckb": ["کوردستان", "کەلتوور"], "kmr": ["Kurdistan", "Çand"] },
    "keywords": { "ckb": ["موزیک"], "kmr": ["Muzîk"] }
  }
}
```

---

## `POST /api/v1/news/bulk` — Bulk Create News Articles

**Auth:** JWT required · Role: `EMPLOYEE`, `ADMIN`, or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body:** Array of news objects — each item has the same fields as the single `POST` body above.

```json
[
  { "contentLanguages": ["CKB"], "ckbContent": { "title": "هەواڵی یەکەم", "description": "<p>...</p>" } },
  { "contentLanguages": ["KMR"], "kmrContent": { "title": "Nûçeya Duyemîn", "description": "<p>...</p>" } }
]
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "News created successfully (bulk)",
  "data": [ { "id": 43, "contentLanguages": ["CKB"], "..." : "..." }, { "id": 44, "..." : "..." } ]
}
```

---

## `PUT /api/v1/news/{id}` — Update News Article

**Auth:** JWT required · Role: `EMPLOYEE`, `ADMIN`, or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the news article to update |

**Request Body:** Same fields as `POST`. All fields are optional (partial merge update).

**Response `200 OK`:**
```json
{ "success": true, "message": "News updated successfully", "data": { "id": 42, "...": "..." } }
```

---

## `DELETE /api/v1/news/{id}` — Delete News Article

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the news article to delete |

**Alias:** `DELETE /api/v1/news/delete/{id}` — identical behavior, alternative path.

**Response `200 OK`:**
```json
{ "success": true, "message": "News deleted successfully", "data": null }
```

---

## `DELETE /api/v1/news/bulk` — Bulk Delete News Articles

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body:** Array of news article IDs to delete.
```json
[1, 2, 3, 42]
```

**Response `200 OK`:**
```json
{ "success": true, "message": "News deleted successfully (bulk)", "data": null }
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contentLanguages`, invalid JSON, or constraint violation |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user lacks the required role (e.g. `USER` trying to write) |
| `404 Not Found` | News article not found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
