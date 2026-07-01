# Video API — Internal (Admin)

**Base URL:** `/api/v1/videos`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Multipart · Paginated
**Note:** Write endpoints use multipart/form-data. Upload cover images or video file as separate parts alongside the serialized JSON `data` part.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/videos` | No | Public | Get all videos (paginated) |
| `GET` | `/api/v1/videos/{id}` | No | Public | Get one video by ID |
| `GET` | `/api/v1/videos/search/tag` | No | Public | Search videos by tag |
| `GET` | `/api/v1/videos/search/keyword` | No | Public | Search videos by keyword |
| `GET` | `/api/v1/videos/topics` | No | Public | Get VIDEO topics list |
| `POST` | `/api/v1/videos` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new video (multipart) |
| `PUT` | `/api/v1/videos/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update a video (multipart) |
| `PATCH` | `/api/v1/videos/{id}/featured` | Yes | `ADMIN` | Set featured status and order |
| `DELETE` | `/api/v1/videos/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a video |
| `POST` | `/api/v1/videos/topics` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new VIDEO topic |
| `DELETE` | `/api/v1/videos/topics/{topicId}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a VIDEO topic |

---

## `GET /api/v1/videos` — Get All Videos (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
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
    { "id": 5, "videoType": "FILM", "ckbCoverUrl": "https://cdn.khi.org/video/cover2.jpg", "topicNameCkb": "بەلگەنامە", "contentLanguages": ["CKB"], "ckbContent": { "title": "بەلگەنامەی کوردستان", "description": "...", "director": "کاوە ئیبراهیم" }, "sourceUrl": "https://cdn.khi.org/videos/doc.mp4", "durationSeconds": 3600, "publishmentDate": "2026-04-10", "tagsCkb": ["کوردستان"], "tagsKmr": [] },
    { "id": 7, "videoType": "VIDEO_CLIP", "ckbCoverUrl": "https://cdn.khi.org/video/cover3.jpg", "topicNameCkb": "کلیپ", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "کلیپەکانی نەورۆز", "description": "..." }, "videoClipItems": [{ "clipNumber": 1, "url": "https://cdn.khi.org/clip1.mp4", "titleCkb": "کلیپی یەکەم", "durationSeconds": 120 }], "tagsCkb": ["نەورۆز"], "tagsKmr": ["Newroz"] }
  ],
  "totalElements": 4,
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
| `value` | string | **Yes** | Keyword value to match |
| `page` | int | No (0) | Page index |
| `size` | int | No (10) | Items per page |

**Response `200 OK`:**
```json
{
  "content": [
    { "id": 5, "videoType": "FILM", "ckbCoverUrl": "https://cdn.khi.org/video/cover2.jpg", "topicNameCkb": "بەلگەنامە", "contentLanguages": ["CKB"], "ckbContent": { "title": "بەلگەنامەی کوردستان", "description": "...", "director": "کاوە ئیبراهیم" }, "sourceUrl": "https://cdn.khi.org/videos/doc.mp4", "durationSeconds": 3600, "publishmentDate": "2026-04-10", "tagsCkb": ["کوردستان"], "tagsKmr": [] },
    { "id": 7, "videoType": "VIDEO_CLIP", "ckbCoverUrl": "https://cdn.khi.org/video/cover3.jpg", "topicNameCkb": "کلیپ", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "کلیپەکانی نەورۆز", "description": "..." }, "videoClipItems": [{ "clipNumber": 1, "url": "https://cdn.khi.org/clip1.mp4", "titleCkb": "کلیپی یەکەم", "durationSeconds": 120 }], "tagsCkb": ["نەورۆز"], "tagsKmr": ["Newroz"] }
  ],
  "totalElements": 4,
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

## `POST /api/v1/videos` — Create Video (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `data` | string (JSON) | **Yes** | Serialized `VideoDTO` JSON (fields below) |
| `ckbCoverImage` | file | No | Sorani cover image — overrides `ckbCoverUrl` in data |
| `kmrCoverImage` | file | No | Kurmanji cover image — overrides `kmrCoverUrl` in data |
| `hoverImage` | file | No | Hover-state image — overrides `hoverCoverUrl` in data |
| `videoFile` | file | No | Video binary (FILM type only) — overrides `sourceUrl` in data |

**`data` JSON Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentLanguages` | array | **Yes** | Languages present: `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `videoType` | enum | **Yes** | `FILM` (single video source) or `VIDEO_CLIP` (list of clips) |
| `albumOfMemories` | boolean | No | Set `true` to tag this video as album-of-memories |
| `topicId` | long | No | ID of an existing topic to assign |
| `newTopic.nameCkb` | string | No | Inline new topic — creates and assigns (Sorani name) |
| `newTopic.nameKmr` | string | No | Inline new topic — Kurmanji name |
| `ckbCoverUrl` | string | No | Fallback Sorani cover URL if no file uploaded |
| `kmrCoverUrl` | string | No | Fallback Kurmanji cover URL |
| `hoverCoverUrl` | string | No | Fallback hover cover URL |
| `ckbContent.title` | string | No | Video title in Sorani |
| `ckbContent.description` | string | No | Description in Sorani |
| `ckbContent.location` | string | No | Filming location (Sorani) |
| `ckbContent.director` | string | No | Director name (Sorani) |
| `ckbContent.producer` | string | No | Producer name (Sorani) |
| `kmrContent` | object | No | Same fields as `ckbContent` for Kurmanji |
| `sourceUrl` | string | No | Direct video URL (FILM only) — used when no `videoFile` uploaded |
| `sourceExternalUrl` | string | No | External streaming URL (FILM only, e.g. YouTube) |
| `sourceEmbedUrl` | string | No | Embed/iframe URL (FILM only) |
| `videoClipItems` | array | No | List of clip items (VIDEO_CLIP type only) |
| `videoClipItems[].url` | string | No | Direct clip URL |
| `videoClipItems[].externalUrl` | string | No | External clip URL |
| `videoClipItems[].embedUrl` | string | No | Clip embed URL |
| `videoClipItems[].clipNumber` | int | No | Clip sequence number |
| `videoClipItems[].durationSeconds` | int | No | Clip duration in seconds |
| `videoClipItems[].resolution` | string | No | Clip resolution (e.g. `1280x720`) |
| `videoClipItems[].fileFormat` | string | No | Clip file format (e.g. `mp4`) |
| `videoClipItems[].fileSizeMb` | double | No | Clip file size in MB |
| `videoClipItems[].titleCkb` | string | No | Clip title in Sorani |
| `videoClipItems[].titleKmr` | string | No | Clip title in Kurmanji |
| `videoClipItems[].descriptionCkb` | string | No | Clip description (Sorani) |
| `videoClipItems[].descriptionKmr` | string | No | Clip description (Kurmanji) |
| `fileFormat` | string | No | Overall file format (e.g. `mp4`) |
| `durationSeconds` | int | No | Total duration in seconds |
| `publishmentDate` | string | No | Publication date (`yyyy-MM-dd`) |
| `resolution` | string | No | Video resolution (e.g. `1920x1080`) |
| `fileSizeMb` | double | No | File size in MB |
| `tagsCkb` | array | No | Tag strings in Sorani |
| `tagsKmr` | array | No | Tag strings in Kurmanji |
| `keywordsCkb` | array | No | Keyword strings in Sorani |
| `keywordsKmr` | array | No | Keyword strings in Kurmanji |

**`data` JSON Example (FILM):**
```json
{
  "contentLanguages": ["CKB", "KMR"],
  "videoType": "FILM",
  "albumOfMemories": false,
  "topicId": 4,
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
  "fileFormat": "mp4",
  "durationSeconds": 5400,
  "publishmentDate": "2026-06-12",
  "resolution": "1920x1080",
  "fileSizeMb": 2048.5,
  "tagsCkb": ["کوردستان", "دۆکیومێنتاری"],
  "tagsKmr": ["Kurdistan", "Belgefîlm"],
  "keywordsCkb": ["مێژوو"],
  "keywordsKmr": ["Dîrok"]
}
```

**`data` JSON Example (VIDEO_CLIP):**
```json
{
  "contentLanguages": ["CKB"],
  "videoType": "VIDEO_CLIP",
  "ckbContent": { "title": "کلیپەکانی نەورۆز", "description": "کلیپی جەژنی نەورۆز" },
  "videoClipItems": [
    { "clipNumber": 1, "titleCkb": "کلیپی یەکەم", "url": "https://cdn.khi.org/clip1.mp4", "durationSeconds": 120 },
    { "clipNumber": 2, "titleCkb": "کلیپی دووەم", "url": "https://cdn.khi.org/clip2.mp4", "durationSeconds": 95 }
  ],
  "tagsCkb": ["نەورۆز", "کلیپ"]
}
```

**Response `201 Created`:**
```json
{
  "id": 3,
  "videoType": "FILM",
  "albumOfMemories": false,
  "ckbCoverUrl": "https://cdn.khi.org/video/ckb-cover.jpg",
  "kmrCoverUrl": "https://cdn.khi.org/video/kmr-cover.jpg",
  "hoverCoverUrl": null,
  "topicId": 4,
  "topicNameCkb": "فیلم",
  "topicNameKmr": "Fîlm",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": { "title": "فیلمی کوردستان", "description": "دۆکیومێنتاری کوردستان", "director": "ئەحمەد کریم", "producer": "KHI پڕۆداکشن", "location": "هەولێر" },
  "kmrContent": { "title": "Filma Kurdistanê", "description": "Belgefîlma Kurdistanê", "director": "Ehmed Kerîm", "producer": "KHI Production", "location": "Hewlêr" },
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
```

---

## `PUT /api/v1/videos/{id}` — Update Video (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the video to update |

**Form Parts:** Same as `POST /`. Uses the same `VideoDTO` in `data`.

**Extra field in `data`:**

| Field | Type | Description |
|-------|------|-------------|
| `clearTopic` | boolean | Set `true` to remove the existing topic association from this video |

**Response `200 OK`:** Updated video object (same shape as create response).

---

## `DELETE /api/v1/videos/{id}` — Delete Video

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the video to delete |

**Response `204 No Content`:** Empty body — deletion successful.

---

## `POST /api/v1/videos/topics` — Create VIDEO Topic

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `nameCkb` | string | No | Topic name in Sorani Kurdish |
| `nameKmr` | string | No | Topic name in Kurmanji Kurdish |

> At least one of `nameCkb` or `nameKmr` should be provided.

**Response `201 Created`:**
```json
{ "id": 7, "nameCkb": "كلیپ", "nameKmr": "Klîp", "createdAt": "2026-06-12T10:00:00" }
```

---

## `DELETE /api/v1/videos/topics/{topicId}` — Delete VIDEO Topic

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `topicId` | long | **Yes** | ID of the topic to delete |

**Response `204 No Content`:** Empty body — deletion successful.

---

## `PATCH /api/v1/videos/{id}/featured` — Set Featured Status

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
  "featuredOrder": 4
}
```

Setting `featured` to `false` clears `featuredOrder`.

**Response `204 No Content`:** Empty body.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contentLanguages` or `videoType`; invalid JSON in `data` part |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | Video or topic not found with the given ID |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
