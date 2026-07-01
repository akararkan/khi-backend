# Writing API — Internal (Admin)

**Base URL:** `/api/v1/writings`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Multipart · Paginated
**Note:** Write endpoints use multipart/form-data. Cover images and downloadable book files are uploaded as separate file parts.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/writings` | No | Public | Get all writings/books (paginated) |
| `GET` | `/api/v1/writings/{id}` | No | Public | Get one writing by ID |
| `GET` | `/api/v1/writings/search/writer` | No | Public | Search by author name |
| `GET` | `/api/v1/writings/search/tag` | No | Public | Search by tag |
| `GET` | `/api/v1/writings/search/keyword` | No | Public | Search by keyword |
| `GET` | `/api/v1/writings/series/parents` | No | Public | Get all series parent books |
| `GET` | `/api/v1/writings/series/{seriesId}` | No | Public | Get all books in a series |
| `GET` | `/api/v1/writings/topics` | No | Public | Get WRITING topics |
| `POST` | `/api/v1/writings` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new writing/book (multipart) |
| `PUT` | `/api/v1/writings/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update a writing (multipart) |
| `PATCH` | `/api/v1/writings/{id}/featured` | Yes | `ADMIN` | Set featured status and order |
| `DELETE` | `/api/v1/writings/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a writing |
| `POST` | `/api/v1/writings/series/link` | Yes | `ADMIN` / `SUPER_ADMIN` | Link a book to a series |

---

## `GET /api/v1/writings` — Get All Writings (Paginated, newest first)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `20` | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Writings fetched successfully",
  "data": {
    "content": [
      {
        "id": 15,
        "featured": true,
        "featuredOrder": 6,
        "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg",
        "kmrCoverUrl": "https://cdn.khi.org/books/kmr-cover.jpg",
        "hoverCoverUrl": null,
        "bookGenres": ["NOVEL", "HISTORY"],
        "publishedByInstitute": true,
        "topicId": 6,
        "topicNameCkb": "رۆمان",
        "topicNameKmr": "Roman",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "دەروازەی کوردستان",
          "description": "رۆمانێکی مێژووی کوردی",
          "writer": "ئارام کریم",
          "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf",
          "fileFormat": "PDF",
          "fileSizeBytes": 5242880,
          "pageCount": 320,
          "genre": "مێژووی-رۆمانس"
        },
        "kmrContent": {
          "title": "Deriyê Kurdistanê",
          "description": "Romaneke dîrokî ya Kurdî",
          "writer": "Aram Kerîm",
          "fileUrl": "https://cdn.khi.org/books/kmr/book.pdf",
          "fileFormat": "PDF",
          "fileSizeBytes": 5242880,
          "pageCount": 320,
          "genre": "Dîrokî-Romans"
        },
        "tags": { "ckb": ["رۆمان", "مێژوو"], "kmr": ["Roman", "Dîrok"] },
        "keywords": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] },
        "series": null,
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00"
      }
    ],
    "totalElements": 60,
    "totalPages": 3,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/writings/{id}` — Get Writing by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Writing ID |

**Response `200 OK`:** Single writing object (same shape as array item above).

---

## `GET /api/v1/writings/search/writer` — Search by Author Name

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | **Yes** | Author name to search |
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
      { "id": 20, "ckbCoverUrl": "https://cdn.khi.org/books/cover2.jpg", "bookGenres": ["POETRY"], "contentLanguages": ["CKB"], "ckbContent": { "title": "دیوانی کلاسیک", "description": "...", "writer": "ئەحمەد کریم", "fileUrl": "https://cdn.khi.org/books/diwan.pdf", "fileFormat": "PDF", "pageCount": 150 }, "tags": { "ckb": ["شیعر"], "kmr": [] }, "createdAt": "2026-05-10T10:00:00" }
    ],
    "totalElements": 4,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/writings/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value to match |
| `language` | string | No (`both`) | `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg", "bookGenres": ["NOVEL", "HISTORY"], "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "دەروازەی کوردستان", "writer": "ئارام کریم", "fileFormat": "PDF", "pageCount": 320 }, "tags": { "ckb": ["رۆمان"], "kmr": ["Roman"] }, "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/writings/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword to match |
| `language` | string | No (`both`) | `ckb` \| `kmr` \| `both` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "content": [
      { "id": 15, "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg", "bookGenres": ["NOVEL"], "contentLanguages": ["CKB"], "ckbContent": { "title": "دەروازەی کوردستان", "writer": "ئارام کریم", "fileFormat": "PDF", "pageCount": 320 }, "keywords": { "ckb": ["کوردستان"], "kmr": [] }, "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/writings/series/parents` — Get All Series Parent Books

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index |
| `size` | int | `100` | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Series parents fetched",
  "data": {
    "content": [
      { "id": 10, "ckbCoverUrl": "https://cdn.khi.org/books/series-cover.jpg", "bookGenres": ["NOVEL"], "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "زنجیرەی کوردستان — یەکەم", "writer": "ئارام کریم", "fileFormat": "PDF", "pageCount": 280 }, "series": { "seriesId": "kurdistan-series", "seriesName": "زنجیرەی کوردستان", "seriesOrder": 1.0, "totalBooks": 3, "isParent": true }, "createdAt": "2026-01-01T10:00:00" }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 100
  }
}
```

---

## `GET /api/v1/writings/series/{seriesId}` — Get All Books in a Series

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `seriesId` | string | **Yes** | Series identifier string (e.g. `kurdistan-series`) |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Series books fetched",
  "data": {
    "seriesId": "kurdistan-series",
    "seriesName": "زنجیرەی کوردستان",
    "totalBooks": 3,
    "books": [
      { "id": 10, "titleCkb": "دەروازەی کوردستان — یەکەم", "titleKmr": "Deriyê Kurdistanê — Yekem", "seriesOrder": 1.0, "createdAt": "2026-01-01T10:00:00" },
      { "id": 15, "titleCkb": "دەروازەی کوردستان — دووەم", "titleKmr": "Deriyê Kurdistanê — Duyemîn", "seriesOrder": 2.0, "createdAt": "2026-03-01T10:00:00" }
    ]
  }
}
```

---

## `GET /api/v1/writings/topics` — Get WRITING Topics

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "WRITING topics fetched",
  "data": [
    { "id": 6, "nameCkb": "رۆمان", "nameKmr": "Roman" },
    { "id": 7, "nameCkb": "مێژوو", "nameKmr": "Dîrok" },
    { "id": 8, "nameCkb": "شیعر", "nameKmr": "Helbest" }
  ]
}
```

---

## `POST /api/v1/writings` — Create Writing/Book (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `data` | string (JSON) | **Yes** | Serialized `CreateRequest` JSON (fields below) |
| `ckbCoverImage` | file | No | Sorani cover image — overrides `ckbCoverUrl` in data |
| `kmrCoverImage` | file | No | Kurmanji cover image — overrides `kmrCoverUrl` in data |
| `hoverCoverImage` | file | No | Hover-state cover image — overrides `hoverCoverUrl` in data |
| `ckbBookFile` | file | No | Downloadable book file (Sorani edition) — overrides `ckbContent.fileUrl` |
| `kmrBookFile` | file | No | Downloadable book file (Kurmanji edition) — overrides `kmrContent.fileUrl` |

**`data` JSON Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentLanguages` | array | **Yes** | Languages present: `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `bookGenres` | array | **Yes** | One or more genres: `NOVEL` \| `HISTORY` \| `POETRY` \| `SCIENCE` \| `BIOGRAPHY` \| `CHILDREN` \| `RELIGION` \| `OTHER` etc. |
| `publishedByInstitute` | boolean | No | `true` if KHI published this book |
| `ckbCoverUrl` | string | No | Fallback Sorani cover URL if no file uploaded (max 2000 chars) |
| `kmrCoverUrl` | string | No | Fallback Kurmanji cover URL (max 2000 chars) |
| `hoverCoverUrl` | string | No | Fallback hover cover URL (max 2000 chars) |
| `topicId` | long | No | ID of existing topic to assign |
| `newTopic.nameCkb` | string | No | Inline new topic — creates and assigns (Sorani name, max 300 chars) |
| `newTopic.nameKmr` | string | No | Inline new topic — Kurmanji name (max 300 chars) |
| `ckbContent.title` | string | No | Book title in Sorani (max 300 chars) |
| `ckbContent.description` | string | No | Book synopsis/description in Sorani (max 10000 chars) |
| `ckbContent.writer` | string | No | Author name in Sorani (max 200 chars) |
| `ckbContent.fileUrl` | string | No | Fallback download URL for Sorani edition (max 1000 chars) |
| `ckbContent.fileFormat` | enum | No | File format: `PDF` \| `EPUB` \| `DOCX` \| `TXT` etc. |
| `ckbContent.fileSizeBytes` | long | No | File size in bytes (must be ≥ 0) |
| `ckbContent.pageCount` | int | No | Number of pages (must be ≥ 1) |
| `ckbContent.genre` | string | No | Genre label for this language edition (max 150 chars) |
| `kmrContent` | object | No | Same fields as `ckbContent` for Kurmanji edition |
| `tags.ckb` | array | No | Tag strings in Sorani |
| `tags.kmr` | array | No | Tag strings in Kurmanji |
| `keywords.ckb` | array | No | Keyword strings in Sorani |
| `keywords.kmr` | array | No | Keyword strings in Kurmanji |
| `seriesId` | string | No | Series identifier to place this book in (max 100 chars) |
| `seriesName` | string | No | Human-readable series name (max 300 chars) |
| `seriesOrder` | double | No | Position of this book within the series (must be ≥ 0) |
| `parentBookId` | long | No | ID of the series parent book if this is a child volume |

**`data` JSON Example:**
```json
{
  "contentLanguages": ["CKB", "KMR"],
  "bookGenres": ["NOVEL", "HISTORY"],
  "publishedByInstitute": true,
  "topicId": 6,
  "ckbContent": {
    "title": "دەروازەی کوردستان",
    "description": "رۆمانێکی مێژووی کوردی کە چیرۆکی گەلی کوردستان دەگێڕێتەوە...",
    "writer": "ئارام کریم",
    "fileFormat": "PDF",
    "pageCount": 320
  },
  "kmrContent": {
    "title": "Deriyê Kurdistanê",
    "description": "Romaneke dîrokî ya Kurdî ku çîroka gelê Kurdistanê vedibêje...",
    "writer": "Aram Kerîm",
    "fileFormat": "PDF",
    "pageCount": 320
  },
  "tags": { "ckb": ["رۆمان", "مێژوو", "کوردستان"], "kmr": ["Roman", "Dîrok", "Kurdistan"] },
  "keywords": { "ckb": ["کوردستان", "رزگاری"], "kmr": ["Kurdistan", "rizgarî"] }
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Writing created successfully",
  "data": {
    "id": 15,
    "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg",
    "kmrCoverUrl": "https://cdn.khi.org/books/kmr-cover.jpg",
    "hoverCoverUrl": null,
    "bookGenres": ["NOVEL", "HISTORY"],
    "publishedByInstitute": true,
    "topicId": 6,
    "topicNameCkb": "رۆمان",
    "topicNameKmr": "Roman",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title": "دەروازەی کوردستان",
      "description": "رۆمانێکی مێژووی کوردی...",
      "writer": "ئارام کریم",
      "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf",
      "fileFormat": "PDF",
      "fileSizeBytes": 5242880,
      "pageCount": 320,
      "genre": null
    },
    "kmrContent": { "title": "Deriyê Kurdistanê", "writer": "Aram Kerîm", "fileUrl": "https://cdn.khi.org/books/kmr/book.pdf", "fileFormat": "PDF", "pageCount": 320 },
    "tags": { "ckb": ["رۆمان", "مێژوو"], "kmr": ["Roman", "Dîrok"] },
    "keywords": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] },
    "series": null,
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
}
```

---

## `PUT /api/v1/writings/{id}` — Update Writing (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the writing to update |

**Form Parts:** Same as `POST /`. Uses `UpdateRequest` in `data`.

**Behavior:** Providing `bookGenres` replaces the entire genres set. All other fields do a partial update (only provided fields change).

**Response `200 OK`:** Updated writing object (same shape as create response).

---

## `DELETE /api/v1/writings/{id}` — Delete Writing

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the writing to delete |

**Response `204 No Content`:** Empty body. The operation also succeeds when the ID does not exist or was already deleted.

---

## `POST /api/v1/writings/series/link` — Link Book to a Series

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `bookId` | long | **Yes** | ID of the book to add to the series |
| `parentBookId` | long | **Yes** | ID of the series parent book |

**Request JSON:**
```json
{ "bookId": 15, "parentBookId": 10 }
```

**Response `200 OK`:** Updated writing object for `bookId` with series info populated.

---

## `PATCH /api/v1/writings/{id}/featured` — Set Featured Status

**Auth:** JWT required · Role: `ADMIN`
**Content-Type:** `application/json`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `featured` | boolean | No | `true` to feature, `false` to unfeature; omitted defaults to `true` |
| `featuredOrder` | integer | No | Global featured display order; lower values appear first |

```json
{
  "featured": true,
  "featuredOrder": 6
}
```

Setting `featured` to `false` clears `featuredOrder`.

**Response `204 No Content`:** Empty body.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contentLanguages` or `bookGenres`; field exceeds max length; invalid JSON in `data` part; `fileSizeBytes` < 0 or `pageCount` < 1 |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | Writing, topic, or series parent not found with the given ID |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
