# Writing API — External (Public)

**Base URL:** `/api/v1/writings`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Paginated
**Note:** Read-only public endpoints. All file download URLs point to S3/CDN.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/writings` | No | Get all writings/books (paginated, newest first) |
| `GET` | `/api/v1/writings/{id}` | No | Get one writing by ID |
| `GET` | `/api/v1/writings/search/writer` | No | Search by author name |
| `GET` | `/api/v1/writings/search/tag` | No | Search by tag |
| `GET` | `/api/v1/writings/search/keyword` | No | Search by keyword |
| `GET` | `/api/v1/writings/series/parents` | No | Get all series parent books |
| `GET` | `/api/v1/writings/series/{seriesId}` | No | Get all books in a series |
| `GET` | `/api/v1/writings/topics` | No | Get WRITING topics |

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
          "description": "رۆمانێکی مێژووی کوردی...",
          "writer": "ئارام کریم",
          "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf",
          "fileFormat": "PDF",
          "fileSizeBytes": 5242880,
          "pageCount": 320,
          "genre": "مێژووی-رۆمانس"
        },
        "kmrContent": {
          "title": "Deriyê Kurdistanê",
          "description": "Romaneke dîrokî ya Kurdî...",
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

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique writing identifier |
| `featured` | boolean | Whether the writing is featured |
| `featuredOrder` | integer \| null | Global featured display order; lower values appear first |
| `ckbCoverUrl` | string | Sorani cover image URL |
| `kmrCoverUrl` | string | Kurmanji cover image URL (may be null) |
| `hoverCoverUrl` | string | Hover-state cover image URL (may be null) |
| `bookGenres` | array | List of genre enums (e.g. `NOVEL`, `HISTORY`) |
| `publishedByInstitute` | boolean | Whether published by KHI |
| `topicId` | long | Assigned topic ID (may be null) |
| `topicNameCkb` | string | Topic name (Sorani) |
| `topicNameKmr` | string | Topic name (Kurmanji) |
| `contentLanguages` | array | Languages present |
| `ckbContent.title` | string | Book title (Sorani) |
| `ckbContent.description` | string | Synopsis/description (Sorani) |
| `ckbContent.writer` | string | Author name (Sorani) |
| `ckbContent.fileUrl` | string | Download URL for Sorani edition |
| `ckbContent.fileFormat` | enum | `PDF` \| `EPUB` \| `DOCX` etc. |
| `ckbContent.fileSizeBytes` | long | File size in bytes |
| `ckbContent.pageCount` | int | Number of pages |
| `ckbContent.genre` | string | Genre label for this edition |
| `kmrContent` | object | Same fields as `ckbContent` for Kurmanji (may be null) |
| `tags.ckb` / `tags.kmr` | array | Tags per language |
| `keywords.ckb` / `keywords.kmr` | array | Keywords per language |
| `series` | object | Series metadata if part of a series (may be null) |
| `series.seriesId` | string | Series identifier |
| `series.seriesName` | string | Series name |
| `series.seriesOrder` | double | Position in the series |
| `series.parentBookId` | long | Parent book ID |
| `series.totalBooks` | int | Total books in the series |
| `series.isParent` | boolean | Whether this book is the series parent |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

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
      { "id": 15, "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg", "bookGenres": ["NOVEL", "HISTORY"], "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "دەروازەی کوردستان", "writer": "ئارام کریم", "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf", "fileFormat": "PDF", "pageCount": 320 }, "tags": { "ckb": ["رۆمان"], "kmr": ["Roman"] }, "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 3,
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
      { "id": 15, "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg", "bookGenres": ["NOVEL", "HISTORY"], "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "دەروازەی کوردستان", "writer": "ئارام کریم", "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf", "fileFormat": "PDF", "pageCount": 320 }, "tags": { "ckb": ["رۆمان"], "kmr": ["Roman"] }, "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 3,
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
      { "id": 15, "ckbCoverUrl": "https://cdn.khi.org/books/ckb-cover.jpg", "bookGenres": ["NOVEL", "HISTORY"], "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "دەروازەی کوردستان", "writer": "ئارام کریم", "fileUrl": "https://cdn.khi.org/books/ckb/book.pdf", "fileFormat": "PDF", "pageCount": 320 }, "tags": { "ckb": ["رۆمان"], "kmr": ["Roman"] }, "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 3,
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

## `GET /api/v1/writings/series/{seriesId}` — Get Books in a Series

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `seriesId` | string | **Yes** | Series identifier string |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Series books fetched",
  "data": {
    "seriesId": "kurdistan-series",
    "seriesName": "زنجیرەی کوردستان",
    "totalBooks": 2,
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

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required query parameter (`name`, `tag`, or `keyword`) |
| `404 Not Found` | Writing or series not found with the given `id` / `seriesId` |
| `500 Internal Server Error` | Unexpected server-side failure |
