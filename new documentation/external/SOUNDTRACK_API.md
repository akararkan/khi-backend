# SoundTrack API — External (Public)

**Base URL:** `/api/v1/sound-tracks`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Paginated
**Note:** Read-only public endpoints. All audio URLs point to S3/CDN or external streaming services.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/sound-tracks` | No | Get all soundtracks (paginated) |
| `GET` | `/api/v1/sound-tracks/{id}` | No | Get one soundtrack by ID |
| `GET` | `/api/v1/sound-tracks/by-state` | No | Filter by SINGLE or MULTI |
| `GET` | `/api/v1/sound-tracks/by-sound-type` | No | Filter by sound type |
| `GET` | `/api/v1/sound-tracks/by-topic` | No | Filter by topic ID |
| `GET` | `/api/v1/sound-tracks/album-of-memories` | No | Get album-of-memories soundtracks |
| `GET` | `/api/v1/sound-tracks/search/tag` | No | Search by tag |
| `GET` | `/api/v1/sound-tracks/search/keyword` | No | Search by keyword |
| `GET` | `/api/v1/sound-tracks/search` | No | Global search |
| `GET` | `/api/v1/sound-tracks/topics` | No | Get SOUND topics |

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
          {
            "id": 1,
            "fileUrl": "https://cdn.khi.org/audio/hawar.mp3",
            "fileType": "AUDIO",
            "title": "هاوار — دەنگی یەکەم",
            "durationSeconds": 185,
            "bitRate": "320kbps",
            "sampleRate": "44100Hz",
            "audioChannel": "STEREO",
            "sortOrder": 0,
            "brochures": []
          }
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

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique soundtrack identifier |
| `soundType` | string | Free-text type label (e.g. `poem`, `song`) |
| `trackState` | enum | `SINGLE` (one file) or `MULTI` (album) |
| `ckbCoverUrl` | string | Sorani cover image URL |
| `kmrCoverUrl` | string | Kurmanji cover image URL (may be null) |
| `hoverCoverUrl` | string | Hover-state cover image URL (may be null) |
| `topicId` | long | Assigned topic ID (may be null) |
| `topicNameCkb` | string | Topic name in Sorani |
| `topicNameKmr` | string | Topic name in Kurmanji |
| `contentLanguages` | array | Languages present |
| `ckbContent.title` | string | Title in Sorani |
| `ckbContent.description` | string | Description in Sorani |
| `kmrContent` | object | Same fields for Kurmanji (may be null) |
| `tags.ckb` / `tags.kmr` | array | Tags per language |
| `keywords.ckb` / `keywords.kmr` | array | Keywords per language |
| `files[].id` | long | File row ID |
| `files[].fileUrl` | string | Audio file URL |
| `files[].fileType` | enum | `AUDIO` \| `VIDEO` \| `DOCUMENT` etc. |
| `files[].title` | string | Individual track title |
| `files[].durationSeconds` | long | Duration in seconds |
| `files[].bitRate` | string | Bit rate string |
| `files[].audioChannel` | enum | `MONO` or `STEREO` |
| `files[].sortOrder` | int | Order in album |
| `files[].brochures` | array | Brochure images for this file |
| `attachments` | array | Supplementary files (MULTI type only) |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

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
| `state` | enum | **Yes** | `SINGLE` or `MULTI` |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/by-sound-type` — Filter by Sound Type

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `soundType` | string | **Yes** | Free-text sound type (e.g. `poem`, `song`) — must not be blank |
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

---

## `GET /api/v1/sound-tracks/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value — must not be blank |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword value — must not be blank |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/sound-tracks/search` — Global Search

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Search term — must not be blank |
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

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Blank or missing `tag`, `keyword`, `q`, or `soundType`; null `topicId` or `state` |
| `404 Not Found` | SoundTrack not found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
