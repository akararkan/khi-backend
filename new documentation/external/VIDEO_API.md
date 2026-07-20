# Video API — External (Public)

**Base URL:** `/api/v1/videos`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Paginated
**Note:** Read-only public endpoints. All video/image URLs point to S3/CDN or external streaming services.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/videos` | No | Get all videos (paginated) |
| `GET` | `/api/v1/videos/{id}` | No | Get one video by ID |
| `GET` | `/api/v1/videos/search/tag` | No | Search videos by tag |
| `GET` | `/api/v1/videos/search/keyword` | No | Search videos by keyword |
| `GET` | `/api/v1/videos/topics` | No | Get VIDEO topics list |

---

## `GET /api/v1/videos` — Get All Videos (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `videoType` | enum | — | Filter by type: `FILM` or `VIDEO_CLIP` |
| `memories` | boolean | — | Filter VIDEO_CLIP by album-of-memories flag (only meaningful when `videoType=VIDEO_CLIP`) |
| `topicId` | long | — | Filter by topic ID |
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `10` | Items per page |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": 3,
      "featured": true,
      "featuredOrder": 4,
      "videoType": "FILM",
      "albumOfMemories": false,
      "ckbCoverUrl": "https://cdn.khi.org/video/ckb-cover.jpg",
      "kmrCoverUrl": "https://cdn.khi.org/video/kmr-cover.jpg",
      "hoverCoverUrl": null,
      "topicId": 4,
      "topicNameCkb": "فیلم",
      "topicNameKmr": "Fîlm",
      "contentLanguages": ["CKB", "KMR"],
      "ckbContent": {
        "title": "فیلمی کوردستان",
        "description": "دۆکیومێنتاری کوردستان",
        "location": "هەولێر",
        "director": "ئەحمەد کریم",
        "producer": "KHI پڕۆداکشن"
      },
      "kmrContent": {
        "title": "Filma Kurdistanê",
        "description": "Belgefîlma Kurdistanê",
        "location": "Hewlêr",
        "director": "Ehmed Kerîm",
        "producer": "KHI Production"
      },
      "sourceUrl": "https://cdn.khi.org/videos/kurdistan.mp4",
      "sourceExternalUrl": null,
      "sourceEmbedUrl": null,
      "videoClipItems": null,
      "fileFormat": "mp4",
      "durationSeconds": 5400,
      "publishmentDate": "2026-06-12",
      "resolution": "1920x1080",
      "fileSizeMb": 2048.5,
      "tagsCkb": ["کوردستان", "دۆکیومێنتاری"],
      "tagsKmr": ["Kurdistan", "Belgefîlm"],
      "keywordsCkb": ["مێژوو"],
      "keywordsKmr": ["Dîrok"],
      "createdAt": "2026-06-12T10:00:00",
      "updatedAt": "2026-06-12T10:00:00"
    }
  ],
  "totalElements": 20,
  "totalPages": 2,
  "number": 0,
  "size": 10
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique video identifier |
| `featured` | boolean | Whether the video is featured |
| `featuredOrder` | integer \| null | Global featured display order; lower values appear first |
| `videoType` | enum | `FILM` (single source) or `VIDEO_CLIP` (multiple clips) |
| `albumOfMemories` | boolean | Whether this is tagged as album-of-memories |
| `ckbCoverUrl` | string | Sorani cover image URL |
| `kmrCoverUrl` | string | Kurmanji cover image URL (may be null) |
| `hoverCoverUrl` | string | Hover-state cover image URL (may be null) |
| `topicId` | long | Assigned topic ID (may be null) |
| `topicNameCkb` | string | Topic name in Sorani |
| `topicNameKmr` | string | Topic name in Kurmanji |
| `contentLanguages` | array | Languages present in this video |
| `ckbContent.title` | string | Video title (Sorani) |
| `ckbContent.description` | string | Description (Sorani) |
| `ckbContent.location` | string | Filming location (Sorani) |
| `ckbContent.director` | string | Director name |
| `ckbContent.producer` | string | Producer name |
| `kmrContent` | object | Same structure as `ckbContent` for Kurmanji (may be null) |
| `sourceUrl` | string | Direct video URL (FILM only, may be null) |
| `sourceExternalUrl` | string | External streaming URL (FILM only, may be null) |
| `sourceEmbedUrl` | string | Embed/iframe URL (FILM only, may be null) |
| `videoClipItems` | array | Clip list (VIDEO_CLIP only, null for FILM) |
| `videoClipItems[].clipNumber` | int | Clip sequence number |
| `videoClipItems[].url` | string | Direct clip URL (set automatically when clip file uploaded via `videoFiles` part — see write endpoints) |
| `videoClipItems[].durationSeconds` | int | Clip duration |
| `videoClipItems[].titleCkb` | string | Clip title (Sorani) |
| `videoClipItems[].titleKmr` | string | Clip title (Kurmanji) |
| `fileFormat` | string | File format (e.g. `mp4`) |
| `durationSeconds` | int | Total duration in seconds |
| `publishmentDate` | string | Publication date (`yyyy-MM-dd`) |
| `resolution` | string | Video resolution (e.g. `1920x1080`) |
| `fileSizeMb` | double | File size in MB |
| `tagsCkb` / `tagsKmr` | array | Tags per language |
| `keywordsCkb` / `keywordsKmr` | array | Keywords per language |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/videos/{id}` — Get Video by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Video ID |

**Response `200 OK`:** Single video object (same shape as array item above).

---

## `GET /api/v1/videos/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `value` | string | **Yes** | Tag value to match (CKB or KMR) |
| `page` | int | No (0) | Page index |
| `size` | int | No (10) | Items per page |

**Response `200 OK`:**
```json
{
  "content": [
    { "id": 5, "videoType": "FILM", "ckbCoverUrl": "https://cdn.khi.org/video/cover2.jpg", "topicNameCkb": "بەلگەنامە", "topicNameKmr": "Belgefîlm", "contentLanguages": ["CKB"], "ckbContent": { "title": "بەلگەنامەی کوردستان", "description": "..." }, "sourceUrl": "https://cdn.khi.org/videos/doc.mp4", "durationSeconds": 3600, "publishmentDate": "2026-04-10", "tagsCkb": ["کوردستان"], "tagsKmr": [] }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

---

## `GET /api/v1/videos/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `value` | string | **Yes** | Keyword to match |
| `page` | int | No (0) | Page index |
| `size` | int | No (10) | Items per page |

**Response `200 OK`:**
```json
{
  "content": [
    { "id": 9, "videoType": "FILM", "ckbCoverUrl": "https://cdn.khi.org/video/cover4.jpg", "topicNameCkb": "فیلم", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "فیلمی مێژووی", "description": "..." }, "sourceUrl": "https://cdn.khi.org/videos/history.mp4", "durationSeconds": 7200, "keywordsCkb": ["مێژوو"], "keywordsKmr": ["Dîrok"] }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

---

## `GET /api/v1/videos/topics` — Get VIDEO Topics

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
[
  { "id": 4, "nameCkb": "فیلم", "nameKmr": "Fîlm", "createdAt": "2026-06-12T10:00:00" },
  { "id": 5, "nameCkb": "کلیپ", "nameKmr": "Klîp", "createdAt": "2026-06-12T10:00:00" },
  { "id": 6, "nameCkb": "بەلگەنامە", "nameKmr": "Belgefîlm", "createdAt": "2026-06-12T10:00:00" }
]
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required query parameter (`value`) |
| `404 Not Found` | No video found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
