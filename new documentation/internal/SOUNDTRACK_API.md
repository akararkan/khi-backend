# SoundTrack API — Internal (Admin)

**Base URL:** `/api/v1/sound-tracks`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Multipart · Paginated
**Note:** Write endpoints use multipart/form-data. `audioFiles[i]` is index-matched to `data.files[i]`. `brochureFiles` is a flat list consumed across all files in order. `attachmentFiles[i]` is index-matched to `data.attachments[i]` (MULTI only).

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/sound-tracks` | No | Public | Get all soundtracks (paginated) |
| `GET` | `/api/v1/sound-tracks/{id}` | No | Public | Get one soundtrack by ID |
| `GET` | `/api/v1/sound-tracks/by-state` | No | Public | Filter by SINGLE or MULTI |
| `GET` | `/api/v1/sound-tracks/by-sound-type` | No | Public | Filter by sound type |
| `GET` | `/api/v1/sound-tracks/by-topic` | No | Public | Filter by topic ID |
| `GET` | `/api/v1/sound-tracks/album-of-memories` | No | Public | Get album-of-memories soundtracks |
| `GET` | `/api/v1/sound-tracks/search/tag` | No | Public | Search by tag |
| `GET` | `/api/v1/sound-tracks/search/keyword` | No | Public | Search by keyword |
| `GET` | `/api/v1/sound-tracks/search` | No | Public | Global search |
| `GET` | `/api/v1/sound-tracks/topics` | No | Public | Get SOUND topics for autocomplete |
| `POST` | `/api/v1/sound-tracks` | Yes | `ADMIN` / `SUPER_ADMIN` | Create soundtrack (multipart) |
| `PUT` | `/api/v1/sound-tracks/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update soundtrack (multipart) |
| `PATCH` | `/api/v1/sound-tracks/{id}/featured` | Yes | `ADMIN` | Set featured status and order |
| `DELETE` | `/api/v1/sound-tracks/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete soundtrack |

---

## `GET /api/v1/sound-tracks` — Get All SoundTracks (Paginated)

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
  "message": "SoundTracks fetched successfully",
  "data": {
    "content": [
      {
        "id": 7,
        "featured": true,
        "featuredOrder": 3,
        "soundType": "poem",
        "trackState": "SINGLE",
        "ckbCoverUrl": "https://cdn.khi.org/sound/cover.jpg",
        "kmrCoverUrl": null,
        "hoverCoverUrl": null,
        "topicId": 2,
        "topicNameCkb": "شیعر",
        "topicNameKmr": "Helbest",
        "contentLanguages": ["CKB"],
        "ckbContent": { "title": "هاوار", "description": "شیعری کلاسیکی کوردی" },
        "kmrContent": null,
        "tags": { "ckb": ["شیعر", "کلاسیک"], "kmr": [] },
        "keywords": { "ckb": ["هاوار"], "kmr": [] },
        "files": [
          { "id": 1, "fileUrl": "https://cdn.khi.org/audio/hawar.mp3", "fileType": "AUDIO", "title": "هاوار — دەنگی یەکەم", "durationSeconds": 185, "bitRate": "320kbps", "sampleRate": "44100Hz", "audioChannel": "STEREO", "sortOrder": 0, "brochures": [] }
        ],
        "attachments": [],
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00"
      }
    ],
    "totalElements": 80,
    "totalPages": 4,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/sound-tracks/{id}` — Get SoundTrack by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | SoundTrack ID |

**Response `200 OK`:** Single soundtrack object (same shape as array item above).

---

## `GET /api/v1/sound-tracks/by-state` — Filter by Track State

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `state` | enum | **Yes** | `SINGLE` (one audio file) or `MULTI` (album with multiple files) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/by-sound-type` — Filter by Sound Type

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `soundType` | string | **Yes** | Free-text sound type (e.g. `poem`, `song`, `lecture`) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/by-topic` — Filter by Topic

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `topicId` | long | **Yes** | Topic ID to filter by |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/album-of-memories` — Get Album of Memories

**Auth:** None — public

**Query Parameters:** `page` (default 0), `size` (default 20)

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Album of memories fetched successfully",
  "data": {
    "content": [
      { "id": 12, "soundType": "song", "trackState": "MULTI", "ckbCoverUrl": "https://cdn.khi.org/sound/memories-cover.jpg", "topicNameCkb": "یادی مێژوو", "contentLanguages": ["CKB"], "ckbContent": { "title": "یادەکانی کوردستان", "description": "ئەلبوومی یادی کوردستان" }, "files": [{ "id": 5, "fileUrl": "https://cdn.khi.org/audio/mem1.mp3", "fileType": "AUDIO", "title": "گۆرانی یەکەم", "durationSeconds": 210, "sortOrder": 0 }], "createdAt": "2026-03-01T10:00:00" }
    ],
    "totalElements": 6,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/sound-tracks/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value to match (must not be blank) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword value to match (must not be blank) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/search` — Global Search

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Search term — matched across titles, descriptions, tags, keywords (must not be blank) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/topics` — Get SOUND Topics

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "SOUND topics fetched successfully",
  "data": [
    { "id": 1, "nameCkb": "شیعر", "nameKmr": "Helbest" },
    { "id": 2, "nameCkb": "سەمایی", "nameKmr": "Semayî" }
  ]
}
```

---

## `POST /api/v1/sound-tracks` — Create SoundTrack (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `data` | string (JSON) | **Yes** | Serialized `CreateRequest` JSON |
| `ckbCoverImage` | file | No | Sorani cover image — overrides `ckbCoverUrl` in data |
| `kmrCoverImage` | file | No | Kurmanji cover image — overrides `kmrCoverUrl` in data |
| `hoverCoverImage` | file | No | Hover-state cover image — overrides `hoverCoverUrl` in data |
| `audioFiles` | file[] | No | Audio binaries — `audioFiles[i]` maps to `data.files[i]` |
| `brochureFiles` | file[] | No | Brochure image binaries — flat list consumed sequentially across all files |
| `attachmentFiles` | file[] | No | Attachment binaries — `attachmentFiles[i]` maps to `data.attachments[i]` (MULTI only) |

**`data` JSON Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentLanguages` | array | **Yes** | `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `soundType` | string | **Yes** | Free-text type (e.g. `poem`, `song`, `lecture`, `nasheed`) |
| `trackState` | enum | **Yes** | `SINGLE` (one track) or `MULTI` (album) |
| `topicId` | long | No | ID of existing topic to assign |
| `newTopic.nameCkb` | string | No | Inline new topic name (Sorani) — creates and assigns a new topic |
| `newTopic.nameKmr` | string | No | Inline new topic name (Kurmanji) |
| `ckbCoverUrl` | string | No | Fallback Sorani cover URL if no file uploaded |
| `kmrCoverUrl` | string | No | Fallback Kurmanji cover URL |
| `hoverCoverUrl` | string | No | Fallback hover cover URL |
| `ckbContent.title` | string | No | Track/album title in Sorani (max 200 chars) |
| `ckbContent.description` | string | No | Description in Sorani (max 4000 chars) |
| `kmrContent` | object | No | Same fields as `ckbContent` for Kurmanji |
| `tags.ckb` | array | No | Tag strings in Sorani |
| `tags.kmr` | array | No | Tag strings in Kurmanji |
| `keywords.ckb` | array | No | Keyword strings in Sorani |
| `keywords.kmr` | array | No | Keyword strings in Kurmanji |
| `files` | array | No | Audio file descriptor list (one per track) |
| `files[].fileUrl` | string | No | Direct S3 URL (used when no binary is uploaded) |
| `files[].externalUrl` | string | No | External streaming URL |
| `files[].embedUrl` | string | No | Embed/iframe URL |
| `files[].title` | string | No | Track title (max 300 chars) |
| `files[].fileType` | enum | **Yes** | File kind: `AUDIO` \| `VIDEO` \| `DOCUMENT` etc. |
| `files[].publishmentYear` | int | No | Year of recording or publication |
| `files[].durationSeconds` | long | No | Audio duration in seconds |
| `files[].bitRate` | string | No | Bit rate (e.g. `320kbps`, max 50 chars) |
| `files[].sampleRate` | string | No | Sample rate (e.g. `44100Hz`, max 50 chars) |
| `files[].audioChannel` | enum | No | `MONO` \| `STEREO` |
| `files[].form` | string | No | Musical form (e.g. `sonata`, max 150 chars) |
| `files[].genre` | string | No | Genre (e.g. `classical`, max 100 chars) |
| `files[].recordingVenue` | string | No | Where recording took place (max 500 chars) |
| `files[].sortOrder` | int | No | Display order within the album (0-based) |
| `files[].brochures` | array | No | Brochure images for this file |
| `files[].brochures[].imageUrl` | string | No | Brochure image URL |
| `files[].brochures[].caption` | string | No | Brochure caption (max 300 chars) |

**`data` JSON Example (SINGLE track):**
```json
{
  "contentLanguages": ["CKB"],
  "soundType": "poem",
  "trackState": "SINGLE",
  "topicId": 2,
  "ckbContent": {
    "title": "هاوار",
    "description": "شیعری کلاسیکی کوردی لەلایەن ئەحمەد مۆختار"
  },
  "tags": { "ckb": ["شیعر", "کلاسیک"], "kmr": [] },
  "keywords": { "ckb": ["هاوار", "کوردی"], "kmr": [] },
  "files": [
    {
      "fileType": "AUDIO",
      "title": "هاوار — دەنگی یەکەم",
      "durationSeconds": 185,
      "bitRate": "320kbps",
      "sampleRate": "44100Hz",
      "audioChannel": "STEREO",
      "sortOrder": 0
    }
  ]
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "SoundTrack created successfully",
  "data": {
    "id": 7,
    "soundType": "poem",
    "trackState": "SINGLE",
    "ckbCoverUrl": "https://cdn.khi.org/sound/cover.jpg",
    "kmrCoverUrl": null,
    "hoverCoverUrl": null,
    "topicId": 2,
    "topicNameCkb": "شیعر",
    "topicNameKmr": "Helbest",
    "contentLanguages": ["CKB"],
    "ckbContent": { "title": "هاوار", "description": "شیعری کلاسیکی کوردی" },
    "kmrContent": null,
    "tags": { "ckb": ["شیعر", "کلاسیک"], "kmr": [] },
    "keywords": { "ckb": ["هاوار"], "kmr": [] },
    "files": [
      { "id": 1, "fileUrl": "https://cdn.khi.org/audio/hawar.mp3", "fileType": "AUDIO", "title": "هاوار — دەنگی یەکەم", "durationSeconds": 185, "bitRate": "320kbps", "audioChannel": "STEREO", "sortOrder": 0, "brochures": [] }
    ],
    "attachments": [],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
}
```

---

## `PUT /api/v1/sound-tracks/{id}` — Update SoundTrack (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the soundtrack to update |

**Form Parts:** Same as `POST /`. Uses `UpdateRequest` in `data`.

**Extra field in `data`:**

| Field | Type | Description |
|-------|------|-------------|
| `clearTopic` | boolean | Set `true` to remove the existing topic association |

**Behavior:** Only fields present in `data` are changed. To replace all files, send a new `files` array plus matching `audioFiles` parts. To leave files unchanged, omit both.

**Response `200 OK`:** Updated soundtrack object (same shape as create response).

---

## `DELETE /api/v1/sound-tracks/{id}` — Delete SoundTrack

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the soundtrack to delete |

**Response `204 No Content`:** Empty body. The operation also succeeds when the ID does not exist or was already deleted.

---

## `PATCH /api/v1/sound-tracks/{id}/featured` — Set Featured Status

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
  "featuredOrder": 3
}
```

Setting `featured` to `false` clears `featuredOrder`.

**Response `204 No Content`:** Empty body.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contentLanguages`, `soundType`, or `trackState`; blank `tag`, `keyword`, or `q`; invalid JSON in `data` part |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | SoundTrack or topic not found with the given ID |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
