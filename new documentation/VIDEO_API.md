# KHI Backend — Video API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 10 Endpoints · Multipart + JSON · Paginated · Tiptap-aware Descriptions

Complete documentation for all video management endpoints — create, update, delete, list, search, and topic management — including bilingual content, multi-slot cover images, FILM vs VIDEO_CLIP type handling, clip item collections, common video metadata, enums, DTOs, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Enums](#03--enums)
- [04 · Authentication, Performance & Tiptap Notes](#04--authentication-performance--tiptap-notes)
- [05 · Create Video](#05--create-video) — `POST /` (multipart)
- [06 · Update Video](#06--update-video) — `PUT /{id}` (multipart, partial-merge)
- [07 · Read](#07--read)
  - `GET /` (getAllVideos)
  - `GET /{id}`
- [08 · Delete](#08--delete) — `DELETE /{id}`
- [09 · Search Endpoints](#09--search-endpoints)
  - `GET /search/tag`
  - `GET /search/keyword`
- [10 · Topic Management](#10--topic-management)
  - `GET /topics`
  - `POST /topics`
  - `DELETE /topics/{topicId}`
- [11 · DTO Reference](#11--dto-reference)
- [12 · Error Responses](#12--error-responses)
- [13 · Change Log — Old vs. New](#13--change-log--old-vs-new)

---

## 01 · Overview

The Video module manages bilingual video publishments for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each video has one of two types:

- **`FILM`** — A traditional film or documentary. Has a single video source (`sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl`). The `videoClipItems` list is empty and unused.
- **`VIDEO_CLIP`** — A collection of short video clips. Each clip lives in an ordered `VideoClipItem` child record with its own URLs, title, description, and metadata. The top-level `sourceUrl` fields are unused. Can optionally be flagged as an **Album of Memories** (`albumOfMemories = true`).

The service automatically enforces the type-vs-fields rules on every write — `albumOfMemories` is forced to `false` for `FILM`; `sourceUrl` fields are cleared for `VIDEO_CLIP`; the opposite type's collection is reset.

Each video additionally carries:

- **Three cover image slots** — CKB cover, KMR cover, and a hover overlay
- **Bilingual embedded content** per language (title, description as **Tiptap HTML**, location, director, producer)
- **Common metadata**: file format, duration, resolution, file size, publishment date
- **Bilingual tags and keywords**
- An optional **topic** — a `@ManyToOne` relation to `PublishmentTopic` (looked up by ID or created inline). The Video module also exposes full topic CRUD directly on the `/topics` sub-resource.
- A full **audit trail** via `VideoLog`

### Base URL

```
/api/v1/videos

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create video — `multipart/form-data` |
| `PUT` | `/{id}` | Update video — `multipart/form-data` (partial-merge) |
| `GET` | `/` | Paginated list of all videos |
| `GET` | `/{id}` | Fetch a single video by ID |
| `DELETE` | `/{id}` | Delete a single video (returns `204 No Content`) |
| `GET` | `/search/tag` | Search by tag value (bilingual) |
| `GET` | `/search/keyword` | Search by keyword value (bilingual) |
| `GET` | `/topics` | List all `VIDEO` topics |
| `POST` | `/topics` | Create a new `VIDEO` topic |
| `DELETE` | `/topics/{topicId}` | Delete a `VIDEO` topic by ID (returns `204 No Content`) |

> ℹ️ **Endpoint count:** Old doc header advertised "12 Endpoints" and its summary listed 10; the controller exposes **10**. The new doc reflects the actual 10.

---

## 02 · Data Models

Four JPA entities and one embeddable make up the Video module. `Video` is the aggregate root.

### Video — `videos`

Aggregate root. Manages `@PrePersist` / `@PreUpdate` lifecycle hooks for `createdAt` and `updatedAt`. Uses `@BatchSize(25)` on all `@ElementCollection` fields.

**DB indexes:** `idx_video_type`, `idx_video_album`, `idx_video_pub_date`, `idx_video_topic`, `idx_video_title_ckb`, `idx_video_title_kmr`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `ckbCoverUrl` | `ckb_cover_url` | VARCHAR(1000) | NULLABLE | Sorani cover / thumbnail |
| `kmrCoverUrl` | `kmr_cover_url` | VARCHAR(1000) | NULLABLE | Kurmanji cover / thumbnail |
| `hoverCoverUrl` | `hover_cover_url` | VARCHAR(1000) | NULLABLE | Hover overlay image |
| `videoType` | `video_type` | VARCHAR(20) | NOT NULL | `FILM` \| `VIDEO_CLIP` |
| `albumOfMemories` | `is_album_of_memories` | BOOLEAN | NOT NULL | Only meaningful when `videoType = VIDEO_CLIP`. **Service forces `false` for FILM.** Default `false` |
| `topic` | `topic_id` | FK → publishment_topics | NULLABLE | Associated topic (`entityType = "VIDEO"`). LAZY |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani: title, description (Tiptap HTML), location, director, producer |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji: title, description (Tiptap HTML), location, director, producer |
| `sourceUrl` | `source_url` | TEXT | NULLABLE | Direct hosted file URL. **FILM type only** — service clears this on VIDEO_CLIP |
| `sourceExternalUrl` | `source_external_url` | TEXT | NULLABLE | External watch page (YouTube, Vimeo). **FILM type only** |
| `sourceEmbedUrl` | `source_embed_url` | TEXT | NULLABLE | iframe-embeddable URL. **FILM type only** |
| `videoClipItems` | — | @OneToMany (LAZY) | NULLABLE | Ordered clip list. **VIDEO_CLIP type only**. Cascade ALL + orphanRemoval. `@OrderBy("clipNumber ASC")` |
| `fileFormat` | `file_format` | VARCHAR(20) | NULLABLE | Container format: `"mp4"`, `"webm"`, `"mov"` |
| `durationSeconds` | `duration_seconds` | INT | NULLABLE | Total duration in seconds |
| `publishmentDate` | `publishment_date` | DATE | NULLABLE | Publication date. ISO-8601 |
| `resolution` | `resolution` | VARCHAR(20) | NULLABLE | e.g. `"1080p"`, `"4K"`, `"720p"` |
| `fileSizeMb` | `file_size_mb` | DOUBLE | NULLABLE | File size in megabytes |
| `contentLanguages` | video_content_languages | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | `Set<Language>` |
| `tagsCkb` | video_tags_ckb | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | CKB tag strings (100 chars each) |
| `tagsKmr` | video_tags_kmr | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | KMR tag strings (100 chars each) |
| `keywordsCkb` | video_keywords_ckb | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | CKB keyword strings (150 chars each) |
| `keywordsKmr` | video_keywords_kmr | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | KMR keyword strings (150 chars each) |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` (`LocalDateTime`) |
| `updatedAt` | `updated_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` and `@PreUpdate` |

> ℹ️ **Type-specific field usage:**
>
> | Field | FILM | VIDEO_CLIP |
> | --- | --- | --- |
> | `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl` | ✅ Used | ✗ Cleared by service |
> | `videoClipItems` | ✗ Cleared by service | ✅ Used |
> | `albumOfMemories` | Always `false` (forced) | `true` or `false` |

**Helper methods on `Video`:**

| Method | Returns | Description |
| --- | --- | --- |
| `addClipItem(item)` / `removeClipItem(item)` | `void` | Maintains the bi-directional FK pointer |
| `isVideoClipAlbumOfMemories()` | `boolean` | `true` only when `videoType == VIDEO_CLIP && albumOfMemories == true` |

### VideoContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `Video`.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(300) | Video title |
| `description` | `description_ckb` | `description_kmr` | TEXT | **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| `location` | `location_ckb` | `location_kmr` | VARCHAR(250) | Filming location |
| `director` | `director_ckb` | `director_kmr` | VARCHAR(250) | Director name in this language |
| `producer` | `producer_ckb` | `producer_kmr` | VARCHAR(250) | Producer name in this language |

> ℹ️ **Mapper null-guard:** If all five fields in a `VideoContentDTO` are blank, `VideoMapper.toContentEntity()` returns `null` rather than persisting an empty embedded object.

### VideoClipItem — `video_clip_items`

Each row is one individual clip inside a `VIDEO_CLIP` video. Ordered by `clipNumber ASC` on the parent. **At least one of `url`, `externalUrl`, or `embedUrl` must be provided** — otherwise the server returns `400 video.clip.source.required`.

**DB indexes:** `idx_clip_video_id`, `idx_clip_clip_number`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `video` | `video_id` | FK → videos | NOT NULL | Parent video — LAZY |
| `url` | `url` | TEXT | NULLABLE | Direct hosted file URL (S3 / CDN) |
| `externalUrl` | `external_url` | TEXT | NULLABLE | External watch page |
| `embedUrl` | `embed_url` | TEXT | NULLABLE | iframe-embeddable URL |
| `clipNumber` | `clip_number` | INT | NULLABLE | Sequence order within the clip collection |
| `durationSeconds` | `duration_seconds` | INT | NULLABLE | Duration of this clip in seconds |
| `resolution` | `resolution` | VARCHAR(20) | NULLABLE | e.g. `"1080p"`, `"720p"` |
| `fileFormat` | `file_format` | VARCHAR(20) | NULLABLE | Container format |
| `fileSizeMb` | `file_size_mb` | DOUBLE | NULLABLE | File size in megabytes |
| `titleCkb` | `title_ckb` | VARCHAR(300) | NULLABLE | Clip title in Sorani |
| `titleKmr` | `title_kmr` | VARCHAR(300) | NULLABLE | Clip title in Kurmanji |
| `descriptionCkb` | `description_ckb` | TEXT | NULLABLE | Clip description in Sorani |
| `descriptionKmr` | `description_kmr` | TEXT | NULLABLE | Clip description in Kurmanji |

### VideoLog — `video_logs`

Append-only audit log. `videoId` and `videoTitle` are stored as snapshot columns so log entries survive hard deletion of the video.

**DB indexes:** `idx_vlog_video_id`, `idx_vlog_action`, `idx_vlog_timestamp`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `videoId` | `video_id` | BIGINT (column) | NULLABLE | ID snapshot — retained after deletion |
| `videoTitle` | `video_title` | VARCHAR(300) | NULLABLE | Title snapshot at time of action |
| `action` | `action` | VARCHAR(30) | NOT NULL | `"CREATED"` \| `"UPDATED"` \| `"DELETED"` |
| `details` | `details` | TEXT | NULLABLE | Human-readable description |
| `performedBy` | `performed_by` | VARCHAR(150) | NULLABLE | Acting principal |
| `timestamp` | `timestamp` | TIMESTAMP | NOT NULL | Defaults to `LocalDateTime.now()` on `@PrePersist` |

---

## 03 · Enums

### VideoType

| Value | Description |
| --- | --- |
| `FILM` | A single film or documentary. Uses `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl`. `videoClipItems` is empty |
| `VIDEO_CLIP` | An ordered collection of short clips. Each clip is a `VideoClipItem` child. `sourceUrl` fields are unused. `albumOfMemories` may be `true` |

### Language

| Value | Description |
| --- | --- |
| `CKB` | Sorani Kurdish |
| `KMR` | Kurmanji Kurdish |

---

## 04 · Authentication, Performance & Tiptap Notes

> ℹ️ **All video endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> 🚧 **No `@Cacheable` / `@CacheEvict` layer here.** Unlike News / Image Collection / SoundTrack, the Video module's service has **no caching annotations**. Every read hits the DB directly. The performance budget comes from `@BatchSize(25)` and DB indexes alone.

> ⚡ **N+1 Protection — `@BatchSize(25)` strategy:** All five `@ElementCollection` fields use `@BatchSize(25)`. For a page of 20 videos, Hibernate fires 5 focused `IN`-queries (one per collection) instead of 100 individual SELECTs — a 20× query reduction.
>
> | Query | Target |
> | --- | --- |
> | Q1 | `videos` — base rows |
> | Q2 | `video_content_languages` |
> | Q3 | `video_tags_ckb` |
> | Q4 | `video_tags_kmr` |
> | Q5 | `video_keywords_ckb` |
> | Q6 | `video_keywords_kmr` |

> 📝 **Tiptap HTML pipeline:** the `description` fields of both `ckbContent` and `kmrContent` are processed through `TiptapHtmlProcessor.process(...)` on every save. Inline media uploaded via `POST /api/v1/media/upload` can be baked into the description body.

> ℹ️ **Update semantics (mapper rule):** On `PUT /{id}`, a `null` field in the `data` JSON means "do not change that field." Collections (tags, keywords, languages, clip items) are replaced only when their value is **non-null** in the DTO.

---

## 05 · Create Video

### `POST /api/v1/videos` — Multipart

🔒 **Auth Required** · `Content-Type: multipart/form-data`

Create a new video. The JSON payload goes in the `data` part. Cover images and an optional video file are uploaded as additional multipart parts. The service applies business rules: `albumOfMemories` is forced to `false` for `FILM`; `sourceUrl` fields are cleared for `VIDEO_CLIP`.

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON String | **Yes** | Full `VideoDTO` as a JSON string |
| `ckbCoverImage` | File (image/*) | No | Sorani cover image. Uploaded to S3; URL set on the video |
| `kmrCoverImage` | File (image/*) | No | Kurmanji cover image |
| `hoverImage` | File (image/*) | No | Hover overlay image |
| `videoFiles` | File (video/*, repeat) | No | Video file(s) uploaded directly alongside the JSON. **FILM:** `videoFiles[0]` → `sourceUrl`. **VIDEO_CLIP:** `videoFiles[i]` → `videoClipItems[i].url` (zero-based index). Repeat this part once per clip. Uploaded files override any URL in the JSON `data` part. Omit to supply URLs in the JSON instead. |

> ℹ️ All covers are **optional**. The service does not enforce any cover-required rule on create — videos can be saved with zero cover images and added later.

> ℹ️ **Two approaches for video files:** (A) Pre-upload via `POST /api/v1/media/upload` and set `sourceUrl` / `videoClipItems[].url` in the JSON — still fully supported. (B) Send files directly as `videoFiles` parts in this same request — no URL needed in the JSON for those clips. Index `i` of `videoFiles` maps to index `i` of `videoClipItems`.

---

### `data` JSON Part — FILM Type (Both Languages, File Upload)

```json
{
  "videoType":       "FILM",
  "albumOfMemories": false,
  "topicId":         2,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "مێژووی کوردستان",
    "description": "<p>فیلمێکی بەلگەنامەیی دەربارەی مێژووی کوردستان</p>",
    "location":    "سلێمانی، کوردستان",
    "director":    "ئەحمەد کەریم",
    "producer":    "ستودیۆی کهی"
  },
  "kmrContent": {
    "title":       "Dîroka Kurdistanê",
    "description": "<p>Belgefîlmek li ser dîroka Kurdistanê</p>",
    "location":    "Silêmanî, Kurdistanê",
    "director":    "Ehmed Kerîm",
    "producer":    "Stûdyoya KHI"
  },
  "sourceUrl":         null,
  "sourceExternalUrl": null,
  "sourceEmbedUrl":    null,
  "fileFormat":        "mp4",
  "durationSeconds":   5400,
  "publishmentDate":   "2026-04-11",
  "resolution":        "1080p",
  "fileSizeMb":        2048.5,
  "tagsCkb":     ["مێژوو", "بەلگەنامە", "کوردستان"],
  "tagsKmr":     ["dîrok", "belgefîlm", "Kurdistan"],
  "keywordsCkb": ["فیلمی کوردی", "ئەرشیف"],
  "keywordsKmr": ["fîlma kurdî", "arşîv"]
}
```

> ℹ️ `sourceUrl: null` above means the video binary will be supplied via the `videoFile` multipart part. The service fills in the S3 URL after upload.

---

### `data` JSON Part — FILM Type (External/Embed URL, No File Upload)

```json
{
  "videoType":         "FILM",
  "topicId":           3,
  "contentLanguages":  ["CKB"],
  "ckbContent": {
    "title":       "ژیانی شاعیری کوردی",
    "description": "<p>فیلمێکی دەربارەی ژیانی شاعیری کلاسیکی کوردی</p>",
    "location":    "هەولێر",
    "director":    "سالار حەمە",
    "producer":    "KHI Production"
  },
  "sourceUrl":         null,
  "sourceExternalUrl": "https://youtube.com/watch?v=abc123",
  "sourceEmbedUrl":    "https://www.youtube.com/embed/abc123",
  "fileFormat":        "mp4",
  "durationSeconds":   3600,
  "publishmentDate":   "2026-03-20",
  "resolution":        "720p",
  "tagsCkb":     ["شاعیر", "کلاسیک"],
  "tagsKmr":     [],
  "keywordsCkb": ["ئەدەبیات", "کوردی"],
  "keywordsKmr": []
}
```

---

### `data` JSON Part — VIDEO_CLIP Type (Album of Memories)

```json
{
  "videoType":       "VIDEO_CLIP",
  "albumOfMemories": true,
  "topicId":         5,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "بیرەوەرییەکانی بەهار",
    "description": "<p>کۆمەڵێک ڤیدیۆی کلیپی بیرەوەری لە دەمی بەهار</p>",
    "location":    "سلێمانی",
    "director":    null,
    "producer":    "ئەرشیفی کهی"
  },
  "kmrContent": {
    "title":       "Bîranînên Biharê",
    "description": "<p>Komek klîpên bîranînê ji demsala biharê</p>",
    "location":    "Silêmanî",
    "director":    null,
    "producer":    "Arşîva KHI"
  },
  "publishmentDate": "2026-04-11",
  "tagsCkb":         ["بیرەوەری", "بەهار", "کلیپ"],
  "tagsKmr":         ["bîranîn", "bihar", "klîp"],
  "keywordsCkb":     ["ئەرشیف", "کوردستان"],
  "keywordsKmr":     ["arşîv", "Kurdistan"],
  "videoClipItems": [
    {
      "url":             "https://cdn.khi.iq/videos/clips/clip-001.mp4",
      "externalUrl":     null,
      "embedUrl":        null,
      "clipNumber":      1,
      "durationSeconds": 120,
      "resolution":      "1080p",
      "fileFormat":      "mp4",
      "fileSizeMb":      85.5,
      "titleCkb":        "بەشی یەکەم — سەرەتای بەهار",
      "titleKmr":        "Beşa Yekem — Destpêka Biharê",
      "descriptionCkb":  "دیمەنی سروشتی لە سەرەتای وەرزی بەهار",
      "descriptionKmr":  "Dîmenên xwezayê ji destpêka demsala biharê"
    },
    {
      "url":             null,
      "externalUrl":     "https://youtube.com/watch?v=clip002",
      "embedUrl":        "https://www.youtube.com/embed/clip002",
      "clipNumber":      2,
      "durationSeconds": 95,
      "resolution":      "720p",
      "titleCkb":        "بەشی دووەم — ئاوی ڕووبار",
      "titleKmr":        "Beşa Duyem — Ava Çem"
    }
  ]
}
```

> ℹ️ The `url` fields above use Approach A (pre-uploaded URLs). With Approach B, omit `url` / `externalUrl` / `embedUrl` from each clip item and send the files as `videoFiles[0]`, `videoFiles[1]`, etc. parts instead.

---

### `data` JSON Part — VIDEO_CLIP Type (Inline Topic Creation)

```json
{
  "videoType":       "VIDEO_CLIP",
  "albumOfMemories": false,
  "newTopic": { "nameCkb": "چالاکی کۆمەڵایەتی", "nameKmr": "Çalakiya Civakî" },
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title":       "کلیپەکانی کۆبوونەوە",
    "description": "<p>ڤیدیۆکانی کۆبوونەوەی ساڵانەی کهی</p>",
    "location":    "سلێمانی",
    "producer":    "KHI Media"
  },
  "publishmentDate": "2026-01-15",
  "tagsCkb":         ["کۆبوونەوە", "کهی"],
  "tagsKmr":         [],
  "keywordsCkb":     ["ئەنجوومەن", "راپۆرت"],
  "keywordsKmr":     [],
  "videoClipItems": [
    {
      "url":             "https://cdn.khi.iq/videos/events/event-clip-01.mp4",
      "clipNumber":      1,
      "durationSeconds": 600,
      "resolution":      "1080p",
      "fileFormat":      "mp4",
      "fileSizeMb":      450.0,
      "titleCkb":        "کلیپی کۆبوونەوەی یەکەم"
    }
  ]
}
```

---

### Request · curl Example (FILM — File Upload)

```bash
curl -X POST https://api.khi.iq/api/v1/videos \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "videoType":"FILM",
    "topicId":2,
    "contentLanguages":["CKB","KMR"],
    "ckbContent":{"title":"مێژووی کوردستان","description":"<p>فیلمی بەلگەنامەیی</p>","location":"سلێمانی","director":"ئەحمەد کەریم","producer":"ستودیۆی کهی"},
    "kmrContent":{"title":"Dîroka Kurdistanê","description":"<p>Belgefîlm</p>","location":"Silêmanî","director":"Ehmed Kerîm","producer":"Stûdyoya KHI"},
    "fileFormat":"mp4",
    "durationSeconds":5400,
    "publishmentDate":"2026-04-11",
    "resolution":"1080p",
    "fileSizeMb":2048.5,
    "tagsCkb":["مێژوو","بەلگەنامە"],
    "tagsKmr":["dîrok","belgefîlm"],
    "keywordsCkb":["فیلمی کوردی"],
    "keywordsKmr":["fîlma kurdî"]
  };type=application/json' \
  -F "ckbCoverImage=@ckb-cover.jpg;type=image/jpeg" \
  -F "kmrCoverImage=@kmr-cover.jpg;type=image/jpeg" \
  -F "hoverImage=@hover.jpg;type=image/jpeg" \
  -F "videoFiles=@documentary.mp4;type=video/mp4"
```

---

### Response · 201 Created

```json
{
  "id":             55,
  "ckbCoverUrl":    "https://cdn.khi.iq/videos/55/ckb-cover.jpg",
  "kmrCoverUrl":    "https://cdn.khi.iq/videos/55/kmr-cover.jpg",
  "hoverCoverUrl":  "https://cdn.khi.iq/videos/55/hover.jpg",
  "videoType":      "FILM",
  "albumOfMemories": false,
  "topicId":        2,
  "topicNameCkb":   "مێژوو",
  "topicNameKmr":   "Dîrok",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "مێژووی کوردستان",
    "description": "<p>فیلمێکی بەلگەنامەیی دەربارەی مێژووی کوردستان</p>",
    "location":    "سلێمانی، کوردستان",
    "director":    "ئەحمەد کەریم",
    "producer":    "ستودیۆی کهی"
  },
  "kmrContent": {
    "title":       "Dîroka Kurdistanê",
    "description": "<p>Belgefîlmek li ser dîroka Kurdistanê</p>",
    "location":    "Silêmanî, Kurdistanê",
    "director":    "Ehmed Kerîm",
    "producer":    "Stûdyoya KHI"
  },
  "sourceUrl":        "https://cdn.khi.iq/videos/55/documentary.mp4",
  "fileFormat":       "mp4",
  "durationSeconds":  5400,
  "publishmentDate":  "2026-04-11",
  "resolution":       "1080p",
  "fileSizeMb":       2048.5,
  "tagsCkb":     ["مێژوو", "بەلگەنامە", "کوردستان"],
  "tagsKmr":     ["dîrok", "belgefîlm", "Kurdistan"],
  "keywordsCkb": ["فیلمی کوردی", "ئەرشیف"],
  "keywordsKmr": ["fîlma kurdî", "arşîv"],
  "createdAt": "2026-04-11T21:30:00",
  "updatedAt": "2026-04-11T21:30:00"
}
```

> ℹ️ Response is **not wrapped** in `ApiResponse<T>` — the Video module returns `VideoDTO` directly. All `null` fields are omitted from the JSON via `@JsonInclude(NON_NULL)`. For `FILM`, `videoClipItems` is omitted; for `VIDEO_CLIP`, the three `source*Url` fields are omitted.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `video.dto.required` | Request `data` body is null |
| `400` | `video.type.required` | `videoType` is null |
| `400` | `video.clip.source.required` | A `VIDEO_CLIP` clip item has none of `url` / `externalUrl` / `embedUrl` |
| `400` | `video.topic.names.required` | `newTopic` provided but both `nameCkb` and `nameKmr` are blank |
| `400` | `topic.type.mismatch` | `topicId` points to a topic that is not a `VIDEO` topic |
| `401` | — | Missing or expired JWT |
| `500` | (storage failure) | S3/disk upload failed |

---

## 06 · Update Video

### `PUT /api/v1/videos/{id}`

🔒 **Auth Required** · `Content-Type: multipart/form-data`

**Partial-merge** update. Only non-null fields in the `data` JSON are applied. Collections (tags, keywords, languages, clip items) are replaced entirely when **non-null**; `null` means "leave unchanged." To clear the topic, send `"clearTopic": true`. To change from `FILM` to `VIDEO_CLIP`, send the new `videoType` — the service enforces the `albumOfMemories` and `sourceUrl` business rules automatically.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the video to update |

### Form Parts

Same as `POST /` — `data`, `ckbCoverImage`, `kmrCoverImage`, `hoverImage`, `videoFiles`. All parts except `data` are optional. Supply `videoFiles[i]` to replace clip i's source; omit `videoFiles` entirely to keep all existing clip sources.

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `videoType` | Replaces the value; service re-applies `albumOfMemories` + `sourceUrl`/`videoClipItems` rules | Existing kept |
| `albumOfMemories` | Replaces the flag (forced to `false` for FILM) | Existing kept |
| `topicId` | Assigns existing VIDEO topic by ID | Existing kept |
| `newTopic` | Creates and assigns a new inline topic | Existing kept |
| `clearTopic: true` | Removes the topic | No-op |
| `contentLanguages` | Replaces the language set | Existing kept |
| `ckbContent` / `kmrContent` | Replaces the block; description re-processed as Tiptap HTML | Existing kept |
| `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl` | Replaces (FILM only; cleared for VIDEO_CLIP) | Existing kept |
| `videoClipItems` (non-null) | **Replaces** the entire clip list (VIDEO_CLIP only) | — |
| `videoClipItems` (null) | — | Existing clips kept |
| `fileFormat` / `durationSeconds` / `publishmentDate` / `resolution` / `fileSizeMb` | Replaces | Existing kept |
| `tagsCkb` / `tagsKmr` / `keywordsCkb` / `keywordsKmr` (non-null) | Replaces the whole set | Existing kept |

### `data` JSON Part — Update Metadata and Replace Tags

```json
{
  "fileFormat":      "mkv",
  "resolution":      "4K",
  "durationSeconds": 5520,
  "fileSizeMb":      3200.0,
  "publishmentDate": "2026-04-12",
  "tagsCkb":         ["مێژوو", "بەلگەنامە", "کوردستان", "نوێ"],
  "tagsKmr":         ["dîrok", "belgefîlm", "Kurdistan", "nû"],
  "ckbContent": {
    "title":       "مێژووی کوردستان — ویرایشکراوە",
    "description": "<p>نوێکراوەوەی فیلمی بەلگەنامەیی</p>",
    "location":    "سلێمانی، کوردستان",
    "director":    "ئەحمەد کەریم",
    "producer":    "ستودیۆی کهی"
  }
}
```

### `data` JSON Part — Replace All Clips (VIDEO_CLIP)

```json
{
  "videoClipItems": [
    {
      "url":             "https://cdn.khi.iq/videos/clips/v2-clip-001.mp4",
      "clipNumber":      1,
      "durationSeconds": 130,
      "resolution":      "1080p",
      "fileFormat":      "mp4",
      "fileSizeMb":      90.0,
      "titleCkb":        "بەشی یەکەم — ویرایشکراوە",
      "titleKmr":        "Beşa Yekem — Nûvekirî"
    },
    {
      "url":             "https://cdn.khi.iq/videos/clips/v2-clip-002.mp4",
      "clipNumber":      2,
      "durationSeconds": 105,
      "resolution":      "1080p",
      "fileFormat":      "mp4",
      "fileSizeMb":      75.0,
      "titleCkb":        "بەشی دووەم — ویرایشکراوە",
      "titleKmr":        "Beşa Duyem — Nûvekirî"
    }
  ]
}
```

### `data` JSON Part — Topic actions

```json
{ "clearTopic": true }
```
```json
{ "topicId": 7 }
```
```json
{ "newTopic": { "nameCkb": "کەلتوور", "nameKmr": "Çand" } }
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/videos/55 \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "resolution":"4K",
    "durationSeconds":5520,
    "tagsCkb":["مێژوو","نوێ"],
    "tagsKmr":["dîrok","nû"]
  };type=application/json' \
  -F "ckbCoverImage=@new-cover.jpg;type=image/jpeg"
```

### Response · 200 OK

Same shape as `POST /` — returns the full `VideoDTO` reflecting the merged state.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `video.id.required` | Path `id` is null |
| `400` | `video.dto.required` | Request `data` is null |
| `400` | various | Same as `POST /` (`video.clip.source.required`, `topic.type.mismatch`, etc.) |
| `401` | — | Missing or expired JWT |
| `404` | `video.not_found` | Video with given `id` does not exist |

---

## 07 · Read

### `GET /api/v1/videos` — getAllVideos

🔒 **Auth Required**

Returns a paginated list of all videos. Default page size is `10`. Each item includes the full response shape: bilingual content, cover URLs, topic, clip items (for `VIDEO_CLIP` type), tags, and keywords.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `videoType` | `VideoType` | — | Filter by video type: `FILM` \| `VIDEO_CLIP` |
| `memories` | boolean | — | Filter `VIDEO_CLIP` by album-of-memories flag (only meaningful when `videoType=VIDEO_CLIP`) |
| `topicId` | Long | — | Filter by topic FK ID |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `10` | Items per page |

### Request

```
GET /api/v1/videos?page=0&size=10
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "content": [
    {
      "id":             55,
      "ckbCoverUrl":    "https://cdn.khi.iq/videos/55/ckb-cover.jpg",
      "kmrCoverUrl":    "https://cdn.khi.iq/videos/55/kmr-cover.jpg",
      "hoverCoverUrl":  "https://cdn.khi.iq/videos/55/hover.jpg",
      "videoType":      "FILM",
      "albumOfMemories": false,
      "topicId":        2,
      "topicNameCkb":   "مێژوو",
      "topicNameKmr":   "Dîrok",
      "contentLanguages": ["CKB", "KMR"],
      "ckbContent": { "title": "مێژووی کوردستان", "description": "<p>…</p>", "location": "سلێمانی", "director": "ئەحمەد کەریم", "producer": "ستودیۆی کهی" },
      "kmrContent": { "title": "Dîroka Kurdistanê", "description": "<p>…</p>", "location": "Silêmanî", "director": "Ehmed Kerîm", "producer": "Stûdyoya KHI" },
      "sourceUrl":      "https://cdn.khi.iq/videos/55/documentary.mp4",
      "fileFormat":     "mp4",
      "durationSeconds": 5400,
      "publishmentDate": "2026-04-11",
      "resolution":     "1080p",
      "fileSizeMb":     2048.5,
      "tagsCkb":     ["مێژوو", "بەلگەنامە"],
      "tagsKmr":     ["dîrok", "belgefîlm"],
      "keywordsCkb": ["فیلمی کوردی"],
      "keywordsKmr": ["fîlma kurdî"],
      "createdAt":   "2026-04-11T21:30:00",
      "updatedAt":   "2026-04-11T21:30:00"
    },
    {
      "id":             56,
      "videoType":      "VIDEO_CLIP",
      "albumOfMemories": true,
      "ckbContent": { "title": "بیرەوەرییەکانی بەهار", "description": "<p>…</p>" },
      "videoClipItems": [
        {
          "id":             201,
          "url":            "https://cdn.khi.iq/videos/clips/clip-001.mp4",
          "clipNumber":     1,
          "durationSeconds": 120,
          "resolution":     "1080p",
          "fileFormat":     "mp4",
          "fileSizeMb":     85.5,
          "titleCkb":       "بەشی یەکەم",
          "titleKmr":       "Beşa Yekem"
        }
      ]
    }
  ],
  "pageable":         { "pageNumber": 0, "pageSize": 10 },
  "totalElements":    62,
  "totalPages":       7,
  "last":             false,
  "first":            true,
  "numberOfElements": 10,
  "empty":            false
}
```

> ℹ️ The `getAllVideos` response wraps directly in Spring's `Page<VideoDTO>` — there is **no outer `ApiResponse` wrapper** on this endpoint (unlike the News / Image / SoundTrack modules). `VideoDTO` fields use `@JsonInclude(NON_NULL)` so absent values are omitted from the JSON.

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/videos/{id}`

🔒 **Auth Required**

Fetch a single video by primary key. Returns the complete `VideoDTO` shape.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the video |

### Response · 200 OK

Same shape as one element from `getAllVideos`.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `video.not_found` | Video with given `id` does not exist |

---

## 08 · Delete

### `DELETE /api/v1/videos/{id}`

🔒 **Auth Required**

Permanently deletes a video and all its cascaded `VideoClipItem` children. A `VideoLog` record is created with `action = "DELETED"` and a `videoTitle` snapshot before deletion.

### Response · 204 No Content

```
HTTP/1.1 204 No Content
```

> ℹ️ The delete endpoint returns `204 No Content` with an empty body — unlike the News / Image / SoundTrack modules which return `200 OK` with an `ApiResponse` wrapper.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `video.id.required` | Path `id` is null |
| `401` | — | Missing or expired JWT |
| `404` | `video.not_found` | Video with given `id` does not exist |

---

## 09 · Search Endpoints

### `GET /api/v1/videos/search/tag`

🔒 **Auth Required**

Search videos by tag value across both `tagsCkb` and `tagsKmr` collections. URL-encode non-ASCII (Kurdish) tag values.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `value` | String | **Yes** | — | Tag value to search |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `10` | Items per page |

### Request

```
GET /api/v1/videos/search/tag?value=belgefîlm&page=0&size=10
GET /api/v1/videos/search/tag?value=%D8%A8%DB%95%D9%84%DA%AF%DB%95%D9%86%D8%A7%D9%85%DB%95
```

### Response · 200 OK

Spring `Page<VideoDTO>` — no `ApiResponse` wrapper.

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `value` is missing or blank |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/videos/search/keyword`

🔒 **Auth Required**

Search videos by keyword across both `keywordsCkb` and `keywordsKmr`.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `value` | String | **Yes** | — | Keyword value |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `10` | Items per page |

### Request

```
GET /api/v1/videos/search/keyword?value=arşîv&page=0&size=10
```

### Response · 200 OK

Spring `Page<VideoDTO>`.

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `value` is missing or blank |
| `401` | Missing or expired JWT |

---

## 10 · Topic Management

The Video module exposes full topic CRUD directly under `/api/v1/videos/topics`. Topics are `PublishmentTopic` records with `entityType = "VIDEO"`.

### `GET /api/v1/videos/topics`

🔒 **Auth Required**

Returns all `VIDEO` topics — a plain `List<TopicView>`, not wrapped in `Page` or `ApiResponse`. Each item includes `createdAt`.

### Response · 200 OK

```json
[
  { "id": 1, "nameCkb": "مێژوو",   "nameKmr": "Dîrok",  "createdAt": "2026-01-10T09:00:00" },
  { "id": 2, "nameCkb": "کەلتوور", "nameKmr": "Çand",   "createdAt": "2026-01-12T14:30:00" },
  { "id": 3, "nameCkb": "سیاسی",   "nameKmr": "Siyasî", "createdAt": "2026-02-01T08:00:00" }
]
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `POST /api/v1/videos/topics`

🔒 **Auth Required**

Create a new `VIDEO` topic. Both `nameCkb` and `nameKmr` are **query parameters** (not a JSON body). Provide at least one — both blank returns `400 video.topic.names.required`.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `nameCkb` | String | No* | Sorani topic name |
| `nameKmr` | String | No* | Kurmanji topic name |

> *At least one of `nameCkb` or `nameKmr` must be non-blank.

### Request

```
POST /api/v1/videos/topics?nameCkb=سنووری&nameKmr=Sînor
Authorization: Bearer eyJhbGci...
```

### Request · curl Example

```bash
curl -X POST "https://api.khi.iq/api/v1/videos/topics?nameCkb=%D8%B3%D9%86%D9%88%D9%88%D8%B1%DB%8C&nameKmr=S%C3%AEnor" \
  -H "Authorization: Bearer eyJhbGci..."
```

### Response · 201 Created

```json
{
  "id":        8,
  "nameCkb":   "سنووری",
  "nameKmr":   "Sînor",
  "createdAt": "2026-04-12T10:00:00"
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `video.topic.names.required` | Both `nameCkb` and `nameKmr` are blank |
| `401` | — | Missing or expired JWT |

---

### `DELETE /api/v1/videos/topics/{topicId}`

🔒 **Auth Required**

Delete a `VIDEO` topic by ID. Videos that reference this topic will have their `topic_id` set to `NULL` (enforced at the DB level by `ON DELETE SET NULL` on the FK).

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `topicId` | Long | **Yes** | ID of the topic to delete |

### Response · 204 No Content

```
HTTP/1.1 204 No Content
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |
| `404` | Topic with given `topicId` does not exist |

---

## 11 · DTO Reference

### VideoDTO

`VideoDTO` is the single unified DTO used for both requests and responses. All fields are annotated `@JsonInclude(NON_NULL)` — absent or null values are omitted from the JSON output.

| Field | Type | Request | Response | Description |
| --- | --- | --- | --- | --- |
| `id` | Long | — | ✅ | DB primary key |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | String | Optional | ✅ | Cover URLs. Overridden by file parts if uploaded |
| `videoType` | `VideoType` | **Required** | ✅ | `FILM` \| `VIDEO_CLIP` |
| `albumOfMemories` | Boolean | Optional | ✅ | Service forces `false` for `FILM`. Default `false` |
| `topicId` | Long | Optional | ✅ | ID of an existing `VIDEO` topic. **Precedence over `newTopic`** |
| `newTopic` | `InlineTopicRequest` | Optional | — | Creates and assigns a new topic inline |
| `clearTopic` | boolean | Optional (update) | — | `true` removes the topic relation. Default `false` |
| `topicNameCkb` / `topicNameKmr` | String | — | ✅ | Topic names of the assigned topic |
| `contentLanguages` | `Set<Language>` | Optional | ✅ | `CKB`, `KMR`, or both |
| `ckbContent` / `kmrContent` | `VideoContentDTO` | Optional | ✅ | Bilingual content blocks |
| `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl` | String | Optional | ✅ | **FILM type only** |
| `videoClipItems` | `List<VideoClipItemDTO>` | Optional | ✅ | **VIDEO_CLIP type only** |
| `fileFormat` | String | Optional | ✅ | Container format |
| `durationSeconds` | Integer | Optional | ✅ | Total duration in seconds |
| `publishmentDate` | `LocalDate` | Optional | ✅ | ISO-8601 |
| `resolution` | String | Optional | ✅ | e.g. `"1080p"`, `"4K"` |
| `fileSizeMb` | Double | Optional | ✅ | File size in megabytes |
| `tagsCkb` / `tagsKmr` | `Set<String>` | Optional | ✅ | Max 100 chars each |
| `keywordsCkb` / `keywordsKmr` | `Set<String>` | Optional | ✅ | Max 150 chars each |
| `createdAt` / `updatedAt` | `LocalDateTime` | — | ✅ | ISO-8601 local datetime |

### VideoContentDTO

Used inside `ckbContent` / `kmrContent` on both request and response. If all five fields are blank, the mapper stores `null` (no embedded content for that language).

| Field | Type | Max Length | Description |
| --- | --- | --- | --- |
| `title` | String | 300 | Video title in this language |
| `description` | String | TEXT | **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| `location` | String | 250 | Filming location |
| `director` | String | 250 | Director name in this language |
| `producer` | String | 250 | Producer name in this language |

### VideoClipItemDTO

Used in `videoClipItems` on both request and response. **At least one of `url`, `externalUrl`, or `embedUrl` must be provided** (enforced by the service with `video.clip.source.required`).

| Field | Type | Request | Response | Description |
| --- | --- | --- | --- | --- |
| `id` | Long | — | ✅ | DB primary key |
| `url` / `externalUrl` / `embedUrl` | String | At least one required **unless** a file is uploaded via the corresponding `videoFiles[i]` multipart part — the uploaded file sets `url` automatically. | ✅ | Clip source URLs |
| `clipNumber` | Integer | Optional | ✅ | Sequence order. Collection ordered `ASC` |
| `durationSeconds` | Integer | Optional | ✅ | Clip duration in seconds |
| `resolution` | String | Optional | ✅ | e.g. `"1080p"` |
| `fileFormat` | String | Optional | ✅ | Container format |
| `fileSizeMb` | Double | Optional | ✅ | File size in MB |
| `titleCkb` / `titleKmr` | String | Optional | ✅ | Clip titles (max 300) |
| `descriptionCkb` / `descriptionKmr` | String | Optional | ✅ | Clip descriptions |

### InlineTopicRequest

Used in `VideoDTO.newTopic`.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `nameCkb` | String | No* | Sorani name for the new topic |
| `nameKmr` | String | No* | Kurmanji name for the new topic |

> *At least one of `nameCkb` or `nameKmr` must be non-blank.

### TopicView

Returned by `GET /topics` and `POST /topics`.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key of the topic |
| `nameCkb` | String | Sorani topic name |
| `nameKmr` | String | Kurmanji topic name |
| `createdAt` | `LocalDateTime` | When the topic was created |

### VideoLogDTO

Internal audit log shape. Not currently exposed via a public endpoint.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `videoId` | Long | ID snapshot of the affected video |
| `videoTitle` | String | Title snapshot |
| `action` | String | `"CREATED"` \| `"UPDATED"` \| `"DELETED"` |
| `details` | String | Human-readable description |
| `performedBy` | String | Acting principal |
| `timestamp` | `LocalDateTime` | When the action occurred |

---

## 12 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New video or topic saved successfully |
| `200 OK` | Update or read succeeded |
| `204 No Content` | Delete succeeded (video or topic) |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | Video or topic with given id does not exist |
| `500 Internal Error` | Unexpected server failure — check logs |

> ⚠️ **Response envelope difference:** The Video module does **not** wrap responses in `ApiResponse<T>`. Write endpoints return `VideoDTO` directly; list/search endpoints return Spring's `Page<VideoDTO>` directly; delete endpoints return `204 No Content`; topic list/create return `List<TopicView>` / `TopicView` directly.

### Common Business Error Keys

| Error Key | Trigger |
| --- | --- |
| `video.dto.required` | Request `data` body is null |
| `video.type.required` | `videoType` is null on create |
| `video.id.required` | Path `id` is null on update / delete |
| `video.not_found` | No video found for the given `id` |
| `video.clip.source.required` | A `VIDEO_CLIP` clip item has none of `url` / `externalUrl` / `embedUrl` |
| `video.topic.names.required` | `POST /topics` (or `newTopic`) with both names blank |
| `topic.type.mismatch` | `topicId` provided but topic is not a `VIDEO` topic |
| `video.clip.id.invalid` | On `PUT /{id}`, a clip item DTO carries an `id` that does not belong to this video |
| `video.clip.id.duplicate` | On `PUT /{id}`, the same clip item `id` appears more than once in `videoClipItems` |

> ℹ️ The keys `video.cover.ckb.required`, `video.cover.kmr.required`, and `video.cover.hover.required` are **mentioned in the service JavaDoc** but **not enforced** by the current code — all covers are optional on create and update. Treat the JavaDoc references as historical/planning notes only.

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "error":     "Bad Request",
  "message":   "video.type.required"
}
```

### Auth Error Body — `401 Unauthorized`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Authentication token is missing or expired"
}
```

### Not Found Error Body — `404`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    404,
  "error":     "Not Found",
  "message":   "video.not_found"
}
```

> ℹ️ All `createdAt` and `updatedAt` fields in `VideoDTO` are `LocalDateTime`.

---

## 13 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /` | Multipart create | Multipart create | ⚪ Unchanged |
| `POST /` and `PUT /{id}` | `videoFile` — single file part | `videoFiles` — list of files; `videoFiles[i]` maps to clip i for VIDEO_CLIP, `videoFiles[0]` for FILM | 🟢 Extended |
| `PUT /{id}` | Multipart update | Multipart update — **partial-merge** semantics formalized | 🟡 Behaviour clarified |
| `GET /` | Paginated list | Paginated list (`Page<VideoDTO>`, **no ApiResponse**) | 🟡 Wrapper clarified |
| `GET /{id}` | Get by id | Get by id (returns `VideoDTO` directly) | ⚪ Unchanged |
| `DELETE /{id}` | Delete | Delete — returns **`204 No Content`** | ⚪ Unchanged |
| `GET /search/tag` | Tag search | Tag search (`Page<VideoDTO>`, no wrapper) | ⚪ Unchanged |
| `GET /search/keyword` | Keyword search | Keyword search (`Page<VideoDTO>`, no wrapper) | ⚪ Unchanged |
| `GET /topics` | VIDEO topics | VIDEO topics (`List<TopicView>`, no wrapper) | ⚪ Unchanged |
| `POST /topics` | Create VIDEO topic | Create VIDEO topic via **query params** | ⚪ Unchanged |
| `DELETE /topics/{topicId}` | Delete VIDEO topic | Delete VIDEO topic — returns **`204 No Content`** | ⚪ Unchanged |

**Endpoint count:** The old doc header said "12 Endpoints" but its summary table listed 10; the controller exposes **10**. The new doc reflects the actual 10. There is no caching layer to expose or remove.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `videos` DB indexes | Not documented | 🟢 Documented: `idx_video_type`, `idx_video_album`, `idx_video_pub_date`, `idx_video_topic`, `idx_video_title_ckb`, `idx_video_title_kmr` |
| `video_clip_items` DB indexes | Not documented | 🟢 Documented: `idx_clip_video_id`, `idx_clip_clip_number` |
| `video_logs` DB indexes | Not documented | 🟢 Documented: `idx_vlog_video_id`, `idx_vlog_action`, `idx_vlog_timestamp` |
| `videoClipItems` ordering | "Ordered by `clipNumber ASC`" | ⚪ Unchanged — enforced by `@OrderBy("clipNumber ASC")` |
| Helper methods on `Video` | Not documented | 🟢 Documented: `addClipItem`, `removeClipItem`, `isVideoClipAlbumOfMemories()` |
| Description content type | Plain TEXT | 🟡 Now processed as **Tiptap HTML** by `TiptapHtmlProcessor` on save |
| `@ElementCollection` fetch type | Not specified | 🟢 Documented: all five collections are `FetchType.EAGER` + `@BatchSize(25)` |

### C) DTO comparison

| Item | Old | New |
| --- | --- | --- |
| `VideoDTO` shape | Same | ⚪ Unchanged |
| `VideoDTO` `@JsonInclude(NON_NULL)` | Documented | ⚪ Unchanged |
| `VideoContentDTO.description` | Plain text | 🟡 Now **Tiptap HTML** (processed on save) |
| Mapper null-guard on empty content block | Documented | ⚪ Unchanged |
| `VideoClipItemDTO` at-least-one-source rule | Documented as "should" | 🟡 Now **enforced** by the service with `video.clip.source.required` |
| `TopicView` | Documented (note: example had a duplicated `nameCkb` key — typo) | 🟢 Corrected — single `nameCkb`, single `nameKmr`, plus `createdAt` |

### D) Validation / error-key comparison

| Old error key | New error key | Change |
| --- | --- | --- |
| `video.type.required` | `video.type.required` | ⚪ Unchanged |
| `video.not.found` | `video.not_found` | 🟡 **Renamed** (underscore instead of dot in the second segment) |
| `video.topic.not.found` | (removed — replaced by `topic.type.mismatch`) | 🟡 Renamed/refocused |
| `video.topic.delete.not.found` | — | 🔴 **Removed** — delete now throws a generic `NotFoundException` rather than this specific key |
| `video.tag.value.required` | (Bean-validation / generic 400) | 🟡 No longer a dedicated business key; the controller relies on the framework's "missing query param" response |
| `video.keyword.value.required` | (Bean-validation / generic 400) | 🟡 Same as above |
| — | `video.dto.required` | 🟢 **Added** — null request body |
| — | `video.id.required` | 🟢 **Added** — null path id on update/delete |
| — | `video.clip.source.required` | 🟢 **Added** — enforces "at least one URL" rule on each `VIDEO_CLIP` item |
| — | `video.topic.names.required` | 🟢 **Added** — `POST /topics` with both names blank |
| — | `topic.type.mismatch` | 🟢 **Added** — `topicId` points to a non-VIDEO topic |
| — | `video.cover.ckb.required` / `video.cover.kmr.required` / `video.cover.hover.required` | 🟡 **Mentioned in JavaDoc only** — not actually thrown by the current service. Covers remain optional on create and update |
| — | `video.clip.id.invalid` | 🟢 **Added** — clip item `id` not found on the video being updated |
| — | `video.clip.id.duplicate` | 🟢 **Added** — same clip item `id` used twice in one update request |

### E) Caching & performance

| Item | Old | New |
| --- | --- | --- |
| `@Cacheable` / `@CacheEvict` annotations | Not claimed | 🚧 **Confirmed absent** — unlike News / Image / SoundTrack, the Video service has no caching layer. Every read hits the DB |
| `@BatchSize(25)` on all `@ElementCollection` | Documented | ⚪ Unchanged |
| `@ElementCollection(FetchType.EAGER)` | Not specified | 🟢 Documented |

### F) Response envelope

| Endpoint group | Old | New |
| --- | --- | --- |
| Write endpoints (`POST /`, `PUT /{id}`) | Returns `VideoDTO` directly | ⚪ Unchanged |
| Read endpoints (`GET /`, `GET /{id}`, search) | No `ApiResponse` wrapper | ⚪ Unchanged |
| Delete endpoints (`DELETE /{id}`, `DELETE /topics/{topicId}`) | `204 No Content` | ⚪ Unchanged |
| Topic endpoints (`GET /topics`, `POST /topics`) | Plain `List<TopicView>` / `TopicView` | ⚪ Unchanged |

The "no `ApiResponse` wrapper" is a Video-specific quirk and is preserved as-is.

### G) Summary

- 🟡 **Changed:** `ckbContent.description` and `kmrContent.description` are now **Tiptap HTML**, processed by `TiptapHtmlProcessor` on save; `video.not.found` was renamed to `video.not_found`; `VideoClipItemDTO` URL requirement is now enforced (was documented as soft "should").
- 🟢 **Added:** comprehensive DB-index documentation across `videos`, `video_clip_items`, and `video_logs`; new error keys `video.dto.required`, `video.id.required`, `video.clip.source.required`, `video.topic.names.required`, `topic.type.mismatch`; entity helper methods (`addClipItem`, `removeClipItem`, `isVideoClipAlbumOfMemories()`); explicit documentation that `@ElementCollection` fields are `EAGER` with `@BatchSize(25)`; `videoFiles` list part replacing the old single `videoFile` part — VIDEO_CLIP now supports direct multi-file upload where `videoFiles[i]` maps to `videoClipItems[i]` by index; new error keys `video.clip.id.invalid` and `video.clip.id.duplicate` for clip identity validation on update.
- 🔴 **Removed:** the dedicated `video.topic.delete.not.found`, `video.tag.value.required`, and `video.keyword.value.required` keys — the controller now relies on framework validation / generic `NotFoundException` for these cases.
- 🟡 **Documented but not enforced:** `video.cover.ckb.required`, `video.cover.kmr.required`, `video.cover.hover.required` appear in the service JavaDoc but are **not actually thrown** by the code. Covers remain optional on create and update.
- 🚧 **Caching absent:** unlike other publishment modules, the Video service has no `@Cacheable` / `@CacheEvict` annotations — every read hits the DB. Worth flagging if you want to align with News / Image / SoundTrack performance behaviour.
- ⚪ **Unchanged:** all 10 endpoints (paths, query/path params, multipart layout), `VideoType` and `Language` enums, three cover slots, the type-vs-fields business rules (`FILM` clears clip items; `VIDEO_CLIP` clears source URLs; `albumOfMemories` forced to `false` for `FILM`), `@JsonInclude(NON_NULL)` on `VideoDTO`, partial-merge update semantics, the absence of an `ApiResponse<T>` wrapper, the `204 No Content` delete responses, and the `ON DELETE SET NULL` topic FK behaviour.
