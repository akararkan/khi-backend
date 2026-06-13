# KHI Backend — SoundTrack API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 13 Endpoints · Multipart + JSON · Paginated · Cached

Complete documentation for all sound track management endpoints — create, update, delete, list, filter, and search — including bilingual content, multi-slot cover images, audio files with technical metadata, brochure images, supplementary attachments, topic management, enums, DTOs, and full request/response examples.

> 🚨 **BASE URL CHANGED.** The route is now `/api/v1/sound-tracks` (with a hyphen), **not** `/api/v1/soundtracks`. Update all client calls.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Enums](#03--enums)
- [04 · Authentication, Caching & Performance Notes](#04--authentication-caching--performance-notes)
- [05 · Create SoundTrack](#05--create-soundtrack) — `POST /` (multipart)
- [06 · Update SoundTrack](#06--update-soundtrack) — `PUT /{id}` (multipart, partial-merge)
- [07 · Read](#07--read)
  - `GET /` (getAll)
  - `GET /{id}`
- [08 · Delete](#08--delete) — `DELETE /{id}`
- [09 · Filter Endpoints](#09--filter-endpoints)
  - `GET /by-state`
  - `GET /by-sound-type`
  - `GET /by-topic`
  - `GET /album-of-memories`
- [10 · Search Endpoints](#10--search-endpoints)
  - `GET /search/tag`
  - `GET /search/keyword`
  - `GET /search`
- [11 · Topics](#11--topics) — `GET /topics`
- [12 · DTO Reference](#12--dto-reference)
- [13 · File Matching Rules](#13--file-matching-rules)
- [14 · Error Responses](#14--error-responses)
- [15 · Change Log — Old vs. New](#15--change-log--old-vs-new)

---

## 01 · Overview

The SoundTrack module manages bilingual audio / sound publishments for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each track carries:

- A **track state** (`SINGLE` or `MULTI`) — determines whether it is a standalone recording or a multi-track album
- **Three cover image slots** — CKB cover, KMR cover, and a hover overlay
- A **sound type** (free-text: poem, music, folk, religious, speech, etc.)
- **Bilingual embedded content** per language (title, description)
- **Metadata fields**: locations, single reader/performer, directors, terms/dialect, institute flag
- One or more **audio files** (`SoundTrackFile`), each with technical metadata (format, size, duration, bit rate, sample rate, channel), content/style metadata (form, genre, recording venue), and an ordered list of **brochure images**
- **Multi-album fields** (`albumName`, `publishmentYear`, `cdNumber`, `totalTracks`) — relevant when `trackState = MULTI`
- **Supplementary attachments** (PDF booklets, promo videos, lyric sheets) applicable to both `SINGLE` and `MULTI` tracks
- **Bilingual tags and keywords**
- An optional **topic** (looked up by ID or created inline)
- A full **audit trail** via `SoundTrackLog`

### Base URL

```
/api/v1/sound-tracks      ← new (hyphenated)

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create soundtrack — `multipart/form-data` |
| `PUT` | `/{id}` | Update soundtrack — `multipart/form-data` (partial-merge) |
| `GET` | `/` | Paginated list of all soundtracks |
| `GET` | `/{id}` | Fetch a single soundtrack by ID |
| `DELETE` | `/{id}` | Delete a single soundtrack |
| `GET` | `/by-state` | Filter by `TrackState` (`SINGLE` \| `MULTI`) |
| `GET` | `/by-sound-type` | Filter by `soundType` string |
| `GET` | `/by-topic` | Filter by topic ID |
| `GET` | `/album-of-memories` | List all Album-of-Memories tracks |
| `GET` | `/search/tag` | Search by tag (bilingual) |
| `GET` | `/search/keyword` | Search by keyword (bilingual) |
| `GET` | `/search` | Global full-text search |
| `GET` | `/topics` | List all `SOUND` topics for autocomplete |

---

## 02 · Data Models

Six JPA entities make up the SoundTrack module. `SoundTrack` is the aggregate root. All lazy collections use `@BatchSize(50)` to eliminate N+1 queries.

### SoundTrack — `sound_tracks`

Aggregate root. Manages `@PrePersist` / `@PreUpdate` lifecycle hooks for `createdAt` and `updatedAt`.

**DB indexes:** `idx_soundtrack_type`, `idx_soundtrack_state`, `idx_soundtrack_album`, `idx_soundtrack_topic`, `idx_soundtrack_created_at`, `idx_soundtrack_updated_at`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `ckbCoverUrl` | `ckb_cover_url` | VARCHAR(1000) | NULLABLE | Sorani cover — S3 uploaded or external URL |
| `kmrCoverUrl` | `kmr_cover_url` | VARCHAR(1000) | NULLABLE | Kurmanji cover |
| `hoverCoverUrl` | `hover_cover_url` | VARCHAR(1000) | NULLABLE | Hover overlay image |
| `soundType` | `sound_type` | VARCHAR(100) | NOT NULL | Free-text sound category |
| `trackState` | `track_state` | VARCHAR(10) | NOT NULL | `SINGLE` \| `MULTI` |
| `albumOfMemories` | `is_album_of_memories` | BOOLEAN | NOT NULL | Marks a MULTI track as a special Album of Memories. Default `false` |
| `topic` | `topic_id` | FK → publishment_topics | NULLABLE | Associated topic. LAZY |
| `contentLanguages` | sound_track_content_languages | @ElementCollection (LAZY) | NOT NULL | `Set<Language>` — at least one required |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani: `title` (200), `description` (TEXT) |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji: `title` (200), `description` (TEXT) |
| `locations` | sound_track_locations | @ElementCollection (LAZY) | NULLABLE | Set of location strings (VARCHAR 255) |
| `reader` | `reader_name` | VARCHAR(255) | NULLABLE | Single reader / performer name |
| `directors` | sound_track_directors | @ElementCollection (LAZY) | NULLABLE | Set of director/producer names (VARCHAR 255) |
| `terms` | `terms` | VARCHAR(200) | NULLABLE | Dialect / terms label |
| `thisProjectOfInstitute` | `is_institute_project` | BOOLEAN | NOT NULL | `true` if produced by the KHI institute |
| `keywordsCkb` | sound_track_keywords_ckb | @ElementCollection (LAZY) | NULLABLE | CKB keyword strings (100 chars each) |
| `keywordsKmr` | sound_track_keywords_kmr | @ElementCollection (LAZY) | NULLABLE | KMR keyword strings (100 chars each) |
| `tagsCkb` | sound_track_tags_ckb | @ElementCollection (LAZY) | NULLABLE | CKB tag strings (60 chars each) |
| `tagsKmr` | sound_track_tags_kmr | @ElementCollection (LAZY) | NULLABLE | KMR tag strings (60 chars each) |
| `files` | — | @OneToMany (LAZY) | NULLABLE | Ordered `List<SoundTrackFile>`. Cascade ALL + orphanRemoval. `@OrderBy("id ASC")` |
| `albumName` | `album_name` | VARCHAR(300) | NULLABLE | Album name — relevant when `trackState = MULTI` |
| `publishmentYear` | `publishment_year` | INT | NULLABLE | Album-level publication year (MULTI tracks) |
| `cdNumber` | `cd_number` | INT | NULLABLE | CD / disc number in a multi-disc set |
| `totalTracks` | `total_tracks` | INT | NULLABLE | Total number of tracks in the album |
| `attachments` | — | @OneToMany (LAZY) | NULLABLE | Ordered `List<SoundTrackAttachment>`. Cascade ALL + orphanRemoval. `@OrderBy("id ASC")` |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` (`LocalDateTime`) |
| `updatedAt` | `updated_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` and `@PreUpdate` |

**Convenience methods on `SoundTrack`:**

| Method | Returns | Description |
| --- | --- | --- |
| `isMulti()` | `boolean` | `trackState == TrackState.MULTI` |
| `isMultiAlbumOfMemories()` | `boolean` | `trackState == MULTI && albumOfMemories == true` |
| `addFile(file)` / `removeFile(file)` | `void` | Maintains the bi-directional FK pointer |
| `addAttachment(att)` / `removeAttachment(att)` | `void` | Maintains the bi-directional FK pointer |

### SoundTrackContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `SoundTrack`.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(200) | Track title in this language |
| `description` | `description_ckb` | `description_kmr` | TEXT | Full description. DTO-side `@Size(max=4000)` is enforced |

### SoundTrackFile — `sound_track_files`

Each row represents a single audio file (or external/embed link) attached to a `SoundTrack`. One track can have multiple files (e.g. songs in a MULTI album).

**DB indexes:** `idx_sound_file_type`, `idx_sound_file_track`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `fileUrl` | `file_url` | VARCHAR(1200) | NULLABLE | Direct hosted URL (S3, CDN) |
| `externalUrl` | `external_url` | TEXT | NULLABLE | External page link (YouTube, SoundCloud, etc.) |
| `embedUrl` | `embed_url` | TEXT | NULLABLE | iframe-embeddable URL |
| `title` | `title` | VARCHAR(300) | NULLABLE | Title of this individual track/file |
| `fileType` | `file_type` | VARCHAR(10) | NOT NULL | `AUDIO` \| `VIDEO` \| `OTHER` |
| `publishmentYear` | `publishment_year` | INT | NULLABLE | Year this specific file was published |
| `fileFormat` | `file_format` | VARCHAR(50) | NULLABLE | Container/codec label: `"MP3"`, `"FLAC"`, `"WAV"`, `"AAC"`, `"OGG"` |
| `sizeBytes` | `size_bytes` | BIGINT | NOT NULL | File size in bytes. `0` when unknown. Auto-set by service on upload |
| `durationSeconds` | `duration_seconds` | BIGINT | NOT NULL | Duration in seconds. `0` when unknown. Auto-set by service |
| `bitRate` | `bit_rate` | VARCHAR(50) | NULLABLE | e.g. `"24-bit"`, `"320 kbps"` |
| `sampleRate` | `sample_rate` | VARCHAR(50) | NULLABLE | e.g. `"44100 Hz"`, `"48000 Hz"` |
| `audioChannel` | `audio_channel` | VARCHAR(10) | NULLABLE | `STEREO` \| `MONO` |
| `form` | `form` | VARCHAR(150) | NULLABLE | Musical/linguistic form. Kurdish values: کێشدار، بێکەش، بێکێش و کێشدار |
| `genre` | `genre` | VARCHAR(100) | NULLABLE | Genre: `"Folk"`, `"Classical"`, `"Religious"`, `"Poetry"` |
| `recordingVenue` | `recording_venue` | VARCHAR(500) | NULLABLE | Where the audio was recorded |
| `brochures` | — | @OneToMany (LAZY) | NULLABLE | Ordered brochure images. Cascade ALL + orphanRemoval. `@OrderBy("id ASC")` |
| `soundTrack` | `sound_track_id` | FK → sound_tracks | NOT NULL | Parent track — LAZY |

**Computed helper on `SoundTrackFile`:**

| Method | Return Type | Description |
| --- | --- | --- |
| `getDurationMinutes()` | `double` | `durationSeconds / 60.0`. e.g. `90s → 1.5 min` |

### SoundTrackBrochure — `sound_track_brochures`

Each row is a single brochure / cover image attached to a `SoundTrackFile`.

**DB index:** `idx_brochure_file`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `imageUrl` | `image_url` | VARCHAR(1200) | **NOT NULL** (at the entity level) | URL of the brochure image |
| `caption` | `caption` | VARCHAR(300) | NULLABLE | Optional label: `"Front Cover"`, `"Page 2"`, `"Inner Booklet"` |
| `brochureOrder` | `brochure_order` | INT | NULLABLE | Position in the brochure list (legacy column kept for backwards compatibility; ordering is now enforced by `@OrderBy("id ASC")` on the parent) |
| `soundTrackFile` | `sound_track_file_id` | FK → sound_track_files | NOT NULL | Parent file — LAZY |

### SoundTrackAttachment — `sound_track_attachments`

Supplementary files attached to a `SoundTrack` (not to an individual file). Used for PDF booklets, promo videos, lyric sheets, or any companion material.

**DB indexes:** `idx_attachment_track`, `idx_attachment_type`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `fileUrl` | `file_url` | VARCHAR(1200) | NOT NULL (entity) | URL pointing to the attachment file |
| `title` | `title` | VARCHAR(300) | NULLABLE | Human-readable label |
| `attachmentType` | `attachment_type` | VARCHAR(20) | NOT NULL (entity) | `PDF` \| `VIDEO` \| `IMAGE` \| `AUDIO` \| `OTHER`. Service defaults to `OTHER` when DTO omits it |
| `sizeBytes` | `size_bytes` | BIGINT | NOT NULL | File size in bytes. `0` when unknown |
| `mimeType` | `mime_type` | VARCHAR(100) | NULLABLE | MIME type |
| `attachmentOrder` | `attachment_order` | INT | NULLABLE | Position in the attachments list (legacy column; ordering enforced by `@OrderBy("id ASC")` on parent) |
| `soundTrack` | `sound_track_id` | FK → sound_tracks | NOT NULL | Parent track — LAZY |

### SoundTrackLog — `sound_track_logs`

Append-only audit log. Stores a `soundTrackRefId` column snapshot so log history is retained even after a track is deleted. The FK `soundTrack` is nullable and set to `null` on `DELETED` logs.

**DB indexes:** `idx_stlog_soundtrack`, `idx_stlog_action`, `idx_stlog_created_at`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `soundTrack` | `sound_track_id` | FK → sound_tracks | NULLABLE | Live relation. `null` for DELETE logs |
| `soundTrackRefId` | `sound_track_ref_id` | BIGINT (column) | NULLABLE | ID snapshot — retained after deletion |
| `soundTrackTitle` | `sound_track_title` | VARCHAR(300) | NULLABLE | Title snapshot at time of action |
| `action` | `action` | VARCHAR(40) | NOT NULL | `"CREATED"` \| `"UPDATED"` \| `"DELETED"` \| `"FILE_ADDED"` \| … (free-text, no enum) |
| `actorId` | `actor_id` | VARCHAR(120) | NULLABLE | ID of the acting user/principal |
| `actorName` | `actor_name` | VARCHAR(200) | NULLABLE | Name of the acting user/principal |
| `requestId` | `request_id` | VARCHAR(120) | NULLABLE | Trace/request ID for correlation |
| `meta` | `meta` | VARCHAR(1000) | NULLABLE | Optional metadata (IP, device, etc.) |
| `details` | `details` | VARCHAR(8000) | NULLABLE | Changed fields summary or JSON diff |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Defaults to `LocalDateTime.now()` on `@PrePersist` |

---

## 03 · Enums

### TrackState

| Value | Description |
| --- | --- |
| `SINGLE` | A standalone single recording or track |
| `MULTI` | A multi-track album. Enables `albumName`, `publishmentYear`, `cdNumber`, `totalTracks` |

### FileType

| Value | Description |
| --- | --- |
| `AUDIO` | Standard audio file (MP3, FLAC, WAV, etc.) |
| `VIDEO` | Video file or embedded video link |
| `OTHER` | Any other file type not covered above |

### AudioChannel

| Value | Description |
| --- | --- |
| `STEREO` | Two-channel stereo audio |
| `MONO` | Single-channel mono audio |

### AttachmentType

| Value | Description |
| --- | --- |
| `PDF` | PDF document (booklets, lyric sheets, liner notes) |
| `VIDEO` | Promotional or making-of video |
| `IMAGE` | Supplementary image (poster, artwork) |
| `AUDIO` | Supplementary audio clip |
| `OTHER` | Any other attachment type. **Service default when `attachmentType` is absent on the DTO** |

### Language

| Value | Description |
| --- | --- |
| `CKB` | Sorani Kurdish |
| `KMR` | Kurmanji Kurdish |

---

## 04 · Authentication, Caching & Performance Notes

> ℹ️ **All soundtrack endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> ⚡ **Caching:** All read endpoints (`getAll`, `getById`, `getByState`, `getBySoundType`, `getByTopic`, `getAlbumOfMemories`, `searchByTag`, `searchByKeyword`, `globalSearch`) are `@Cacheable(value="soundTracks")`. Every write (`create`, `update`, `delete`) does `@CacheEvict(value="soundTracks", allEntries=true)`.

> ⚡ **N+1 Protection — `@BatchSize(50)` strategy:** For a page of N tracks, Hibernate fires approximately 11 focused `IN`-queries instead of one Cartesian monster join:
>
> | Query | Target |
> | --- | --- |
> | Q1 | `sound_tracks` — base rows |
> | Q2 | `sound_track_content_languages` |
> | Q3 | `sound_track_locations` |
> | Q4 | `sound_track_directors` |
> | Q5 | `sound_track_keywords_ckb` |
> | Q6 | `sound_track_keywords_kmr` |
> | Q7 | `sound_track_tags_ckb` |
> | Q8 | `sound_track_tags_kmr` |
> | Q9 | `sound_track_files` |
> | Q10 | `sound_track_attachments` |
> | Q11 | `publishment_topics` (via class-level `@BatchSize` on `PublishmentTopic`) |

---

## 05 · Create SoundTrack

### `POST /api/v1/sound-tracks` — Multipart

🔒 **Auth Required** · `Content-Type: multipart/form-data`

Create a new soundtrack. The JSON payload goes in the `data` part. Cover images, audio binaries, brochure images, and attachment files are uploaded as additional multipart parts.

See [§13 · File Matching Rules](#13--file-matching-rules) for how uploaded binaries are index-matched to their DTO counterparts.

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON String | **Yes** | Full `CreateRequest` as a JSON string |
| `ckbCoverImage` | File (image/*) | No | Sorani cover image |
| `kmrCoverImage` | File (image/*) | No | Kurmanji cover image |
| `hoverCoverImage` | File (image/*) | No | Hover overlay image |
| `audioFiles` | File[] (audio/*) | No | Audio binaries — index-matched to `dto.files[i]`. Uploaded binary wins over `dto.files[i].fileUrl` |
| `brochureFiles` | File[] (image/*) | No | Brochure images — flat list consumed globally across all files in index order |
| `attachmentFiles` | File[] | No | Attachment binaries — index-matched to `dto.attachments[i]` |

---

### `data` JSON Part — SINGLE Track (Both Languages, File Upload)

```json
{
  "soundType":       "شیعر",
  "trackState":      "SINGLE",
  "albumOfMemories": false,
  "topicId":         4,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "هاوار",
    "description": "دەنگخوێندنی شیعری کلاسیکی کوردی"
  },
  "kmrContent": {
    "title":       "Hawar",
    "description": "Xwandina helbestên klasîk ên kurdî"
  },
  "locations":              ["سلێمانی", "کوردستان"],
  "reader":                 "محمد شیخ",
  "directors":              ["ئەحمەد کەریم"],
  "terms":                  "کێشدار",
  "thisProjectOfInstitute": true,
  "tags":     { "ckb": ["شیعر", "کلاسیک", "کوردستان"], "kmr": ["helbest", "klasîk", "Kurdistan"] },
  "keywords": { "ckb": ["دەنگخوێندن", "کوردی سۆرانی"], "kmr": ["xwendin", "kurdî"] },
  "files": [
    {
      "fileUrl":         null,
      "fileType":        "AUDIO",
      "title":           "هاوار — بەشی یەکەم",
      "publishmentYear": 2026,
      "fileFormat":      "MP3",
      "sizeBytes":       0,
      "durationSeconds": 0,
      "bitRate":         "320 kbps",
      "sampleRate":      "44100 Hz",
      "audioChannel":    "STEREO",
      "form":            "کێشدار",
      "genre":           "Classical",
      "recordingVenue":  "ستودیۆی کهی — سلێمانی",
      "brochures": [
        { "imageUrl": null, "caption": "Front Cover" },
        { "imageUrl": null, "caption": "Back Cover" }
      ]
    }
  ],
  "attachments": [
    {
      "fileUrl":        null,
      "title":          "تەختەی هەواڵ",
      "attachmentType": "PDF",
      "sizeBytes":      0,
      "mimeType":       "application/pdf"
    }
  ]
}
```

> ℹ️ `fileUrl: null` and `sizeBytes: 0` / `durationSeconds: 0` mean the binary will be supplied via the `audioFiles` multipart part. The service sets the final URL and auto-extracts size/duration after upload. Similarly, `brochures[].imageUrl: null` means the binary comes from `brochureFiles`, and `attachments[].fileUrl: null` means it comes from `attachmentFiles`.

---

### `data` JSON Part — MULTI Album (URL-only sources, CKB only)

```json
{
  "soundType":       "موسیقا",
  "trackState":      "MULTI",
  "albumOfMemories": true,
  "topicId":         2,
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title":       "ئەلبووم: گوتارەکانی مێژوو",
    "description": "کۆمەڵێک گۆرانی کلاسیکی کوردی لە دەیەی ١٩٧٠"
  },
  "locations":              ["هەولێر", "سلێمانی"],
  "reader":                 "ئەحمەد کەریم",
  "directors":              ["سەعید محەمەد", "ئاوات حەمە"],
  "terms":                  "بێکەش",
  "thisProjectOfInstitute": false,
  "tags":     { "ckb": ["موسیقا", "مێژووی", "١٩٧٠"], "kmr": [] },
  "keywords": { "ckb": ["گۆرانی کلاسیک", "ئەرشیف"],   "kmr": [] },
  "albumName":       "گوتارەکانی مێژوو",
  "publishmentYear": 1972,
  "cdNumber":        1,
  "totalTracks":     12,
  "files": [
    {
      "fileUrl":         "https://cdn.khi.iq/audio/album/track-01.mp3",
      "fileType":        "AUDIO",
      "title":           "گوتاری یەکەم",
      "publishmentYear": 1972,
      "fileFormat":      "MP3",
      "sizeBytes":       8126464,
      "durationSeconds": 203,
      "bitRate":         "320 kbps",
      "sampleRate":      "44100 Hz",
      "audioChannel":    "MONO",
      "form":            "بێکەش",
      "genre":           "Folk",
      "recordingVenue":  "رادیۆی هەولێر",
      "brochures": [
        { "imageUrl": "https://cdn.khi.iq/audio/album/booklet-p1.jpg", "caption": "Front Cover" }
      ]
    },
    {
      "fileUrl":         "https://cdn.khi.iq/audio/album/track-02.mp3",
      "fileType":        "AUDIO",
      "title":           "گوتاری دووەم",
      "publishmentYear": 1972,
      "fileFormat":      "MP3",
      "sizeBytes":       7340032,
      "durationSeconds": 183,
      "bitRate":         "320 kbps",
      "sampleRate":      "44100 Hz",
      "audioChannel":    "MONO",
      "form":            "بێکەش",
      "genre":           "Folk",
      "recordingVenue":  "رادیۆی هەولێر",
      "brochures":       []
    }
  ],
  "attachments": [
    {
      "fileUrl":        "https://cdn.khi.iq/audio/album/booklet.pdf",
      "title":          "Album Booklet",
      "attachmentType": "PDF",
      "sizeBytes":      2097152,
      "mimeType":       "application/pdf"
    }
  ]
}
```

---

### `data` JSON Part — Inline Topic Creation

```json
{
  "soundType":  "مووسیقای فۆلکلۆری",
  "trackState": "SINGLE",
  "newTopic":   { "nameCkb": "فۆلکلۆر", "nameKmr": "Folklor" },
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title":       "دەنگی دێرین",
    "description": "گۆرانیەکی فۆلکلۆری کلاسیکی"
  },
  "thisProjectOfInstitute": false,
  "files": [
    {
      "fileUrl":         "https://cdn.khi.iq/audio/folk-001.mp3",
      "fileType":        "AUDIO",
      "fileFormat":      "FLAC",
      "sizeBytes":       52428800,
      "durationSeconds": 245,
      "bitRate":         "24-bit",
      "sampleRate":      "96000 Hz",
      "audioChannel":    "STEREO"
    }
  ]
}
```

---

### Request · curl Example (File Upload — Cover + Audio + Brochure + Attachment)

```bash
curl -X POST https://api.khi.iq/api/v1/sound-tracks \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "soundType":"شیعر",
    "trackState":"SINGLE",
    "topicId":4,
    "contentLanguages":["CKB","KMR"],
    "ckbContent":{"title":"هاوار","description":"دەنگخوێندنی شیعری کلاسیکی"},
    "kmrContent":{"title":"Hawar","description":"Xwandina helbestê"},
    "locations":["سلێمانی"],
    "reader":"محمد شیخ",
    "thisProjectOfInstitute":true,
    "tags":{"ckb":["شیعر","کلاسیک"],"kmr":["helbest","klasîk"]},
    "keywords":{"ckb":["دەنگخوێندن"],"kmr":["xwendin"]},
    "files":[{
      "fileUrl":null,
      "fileType":"AUDIO",
      "title":"هاوار — بەشی یەکەم",
      "fileFormat":"MP3",
      "sizeBytes":0,
      "durationSeconds":0,
      "bitRate":"320 kbps",
      "sampleRate":"44100 Hz",
      "audioChannel":"STEREO",
      "form":"کێشدار",
      "genre":"Classical",
      "brochures":[{"imageUrl":null,"caption":"Front Cover"},{"imageUrl":null,"caption":"Back Cover"}]
    }],
    "attachments":[{"fileUrl":null,"title":"Lyric Sheet","attachmentType":"PDF","sizeBytes":0}]
  };type=application/json' \
  -F "ckbCoverImage=@ckb-cover.jpg;type=image/jpeg" \
  -F "kmrCoverImage=@kmr-cover.jpg;type=image/jpeg" \
  -F "hoverCoverImage=@hover.jpg;type=image/jpeg" \
  -F "audioFiles=@track-01.mp3;type=audio/mpeg" \
  -F "brochureFiles=@front-cover.jpg;type=image/jpeg" \
  -F "brochureFiles=@back-cover.jpg;type=image/jpeg" \
  -F "attachmentFiles=@lyrics.pdf;type=application/pdf"
```

---

### Response · 201 Created

```json
{
  "success": true,
  "message": "SoundTrack created successfully",
  "data": {
    "id":               77,
    "ckbCoverUrl":      "https://cdn.khi.iq/audio/tracks/77/ckb-cover.jpg",
    "kmrCoverUrl":      "https://cdn.khi.iq/audio/tracks/77/kmr-cover.jpg",
    "hoverCoverUrl":    "https://cdn.khi.iq/audio/tracks/77/hover.jpg",
    "soundType":        "شیعر",
    "trackState":       "SINGLE",
    "albumOfMemories":  false,
    "topicId":          4,
    "topicNameCkb":     "ئەدەب",
    "topicNameKmr":     "Edebiyat",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "هاوار", "description": "دەنگخوێندنی شیعری کلاسیکی کوردی" },
    "kmrContent": { "title": "Hawar", "description": "Xwandina helbestên klasîk ên kurdî" },
    "locations":              ["سلێمانی", "کوردستان"],
    "reader":                 "محمد شیخ",
    "directors":              ["ئەحمەد کەریم"],
    "terms":                  "کێشدار",
    "thisProjectOfInstitute": true,
    "tags":     { "ckb": ["شیعر", "کلاسیک", "کوردستان"], "kmr": ["helbest", "klasîk", "Kurdistan"] },
    "keywords": { "ckb": ["دەنگخوێندن", "کوردی سۆرانی"], "kmr": ["xwendin", "kurdî"] },
    "files": [
      {
        "id":              301,
        "fileUrl":         "https://cdn.khi.iq/audio/tracks/77/track-01.mp3",
        "externalUrl":     null,
        "embedUrl":        null,
        "title":           "هاوار — بەشی یەکەم",
        "fileType":        "AUDIO",
        "publishmentYear": 2026,
        "sizeBytes":       11534336,
        "durationSeconds": 288,
        "durationMinutes": 4.8,
        "bitRate":         "320 kbps",
        "sampleRate":      "44100 Hz",
        "audioChannel":    "STEREO",
        "form":            "کێشدار",
        "genre":           "Classical",
        "recordingVenue":  "ستودیۆی کهی — سلێمانی",
        "brochures": [
          { "id": 501, "imageUrl": "https://cdn.khi.iq/audio/tracks/77/brochure-front.jpg", "caption": "Front Cover", "brochureOrder": 0 },
          { "id": 502, "imageUrl": "https://cdn.khi.iq/audio/tracks/77/brochure-back.jpg",  "caption": "Back Cover",  "brochureOrder": 1 }
        ]
      }
    ],
    "totalDurationSeconds": 288,
    "totalSizeBytes":       11534336,
    "albumName":            null,
    "publishmentYear":      null,
    "cdNumber":             null,
    "totalTracks":          null,
    "attachments": [
      {
        "id":              401,
        "fileUrl":         "https://cdn.khi.iq/audio/tracks/77/lyrics.pdf",
        "title":           "Lyric Sheet",
        "attachmentType":  "PDF",
        "sizeBytes":       204800,
        "mimeType":        "application/pdf",
        "attachmentOrder": 0
      }
    ],
    "createdAt": "2026-04-11T21:30:00",
    "updatedAt": "2026-04-11T21:30:00"
  }
}
```

> ℹ️ `totalDurationSeconds` and `totalSizeBytes` on the `Response` are computed aggregates across all files in the track.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `soundTrack.soundType.required` (or `@NotBlank` envelope) | `soundType` is blank |
| `400` | `soundTrack.state.required` (or `@NotNull` envelope) | `trackState` is null |
| `400` | `soundTrack.languages.required` (or `@NotEmpty` envelope) | `contentLanguages` is empty |
| `400` | `soundTrack.file.type.required` (or `@NotNull`) | `fileType` is null on any `FileCreateRequest` |
| `400` | `soundTrack.ckb.title.required` | CKB selected but `ckbContent.title` is blank |
| `400` | `soundTrack.kmr.title.required` | KMR selected but `kmrContent.title` is blank |
| `400` | `soundTrack.topic.not.found` | `topicId` provided but no matching SOUND topic exists |
| `401` | — | Missing or expired JWT |
| `500` | (storage failure) | S3/disk upload failed |

---

## 06 · Update SoundTrack

### `PUT /api/v1/sound-tracks/{id}`

🔒 **Auth Required** · `Content-Type: multipart/form-data`

**Partial-merge** update — only fields present in the `data` JSON are applied; absent/null fields keep their existing values. To clear the topic, send `"clearTopic": true`. To replace all files, send a new `files` array; omit it entirely to keep the existing file list.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the soundtrack to update |

### Form Parts

Same as `POST /` — see §05.

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `soundType` | Replaces the value | Existing kept |
| `trackState` | Replaces the track state | Existing kept |
| `albumOfMemories` | Replaces the flag | Existing kept |
| `topicId` | Assigns existing topic by ID | Existing kept |
| `newTopic` | Creates and assigns a new inline topic | Existing kept |
| `clearTopic: true` | Removes the topic | No-op |
| `contentLanguages` | Replaces the language set | Existing kept |
| `ckbContent` / `kmrContent` | Replaces the block | Existing kept |
| `locations` / `directors` | Replaces the set | Existing kept |
| `reader` | `""` (empty string) clears the reader. Non-empty replaces. | Existing kept |
| `terms` | Replaces the value | Existing kept |
| `thisProjectOfInstitute` | Replaces the flag | Existing kept |
| `tags` / `keywords` | Replaces the entire set | Existing kept |
| `files` (non-null) | **Replaces** the entire file list | — |
| `files` (null / omitted) | — | Existing files kept |
| `albumName` / `publishmentYear` / `cdNumber` / `totalTracks` | Replaces the value | Existing kept |
| `attachments` (non-null) | **Replaces** the entire attachment list | — |
| `attachments` (null / omitted) | — | Existing attachments kept |

### `data` JSON Part — Update Core Fields + Replace CKB Cover

```json
{
  "soundType":        "گۆرانی فۆلکلۆری",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "هاوار — ویرایشکراوە",
    "description": "وردبینی نوێکراوەی شیعر"
  },
  "kmrContent": {
    "title":       "Hawar — Nûvekirî",
    "description": "Danasîna nû ya helbest"
  },
  "reader": "سالار کەریم",
  "tags":   { "ckb": ["گۆرانی", "فۆلکلۆر"], "kmr": ["goranî", "folklor"] }
}
```

### `data` JSON Part — Topic actions

```json
{ "clearTopic": true }
```
```json
{ "topicId": 6 }
```
```json
{ "newTopic": { "nameCkb": "ئەدەب", "nameKmr": "Edebiyat" } }
```

### `data` JSON Part — Replace All Files (MULTI Album Update)

```json
{
  "trackState":      "MULTI",
  "albumName":       "گوتارەکانی مێژوو — ویرایشکراوە",
  "publishmentYear": 1972,
  "cdNumber":        2,
  "totalTracks":     10,
  "files": [
    {
      "fileUrl":         "https://cdn.khi.iq/audio/album/v2-track-01.mp3",
      "fileType":        "AUDIO",
      "title":           "گوتاری نوێی یەکەم",
      "fileFormat":      "MP3",
      "sizeBytes":       9437184,
      "durationSeconds": 235,
      "bitRate":         "320 kbps",
      "sampleRate":      "44100 Hz",
      "audioChannel":    "MONO",
      "brochures":       []
    }
  ]
}
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/sound-tracks/77 \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "soundType":"گۆرانی فۆلکلۆری",
    "reader":"سالار کەریم",
    "tags":{"ckb":["گۆرانی","فۆلکلۆر"],"kmr":["goranî","folklor"]}
  };type=application/json' \
  -F "ckbCoverImage=@new-ckb-cover.jpg;type=image/jpeg"
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "SoundTrack updated successfully",
  "data": { /* full Response — same shape as create */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | various | Same validation envelopes as `POST /` |
| `400` | `soundTrack.topic.not.found` | `topicId` points to a non-SOUND topic |
| `401` | — | Missing or expired JWT |
| `404` | `soundTrack.not.found` | Soundtrack with given `id` does not exist |

---

## 07 · Read

### `GET /api/v1/sound-tracks` — getAll

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Returns a paginated list of all soundtracks including full bilingual content, cover URLs, topic, files with brochures, attachments, tags, and keywords.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Items per page |

### Request

```
GET /api/v1/sound-tracks?page=0&size=20
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "SoundTracks fetched successfully",
  "data": {
    "content": [
      { /* Response — see DTO Reference */ }
    ],
    "pageable":         { "pageNumber": 0, "pageSize": 20 },
    "totalElements":    134,
    "totalPages":       7,
    "last":             false,
    "first":            true,
    "numberOfElements": 20,
    "empty":            false
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/{id}`

🔒 **Auth Required**

Fetch a single soundtrack by primary key.

### Response · 200 OK

Same shape as one item from `getAll`.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `soundTrack.not.found` | Soundtrack with given `id` does not exist |

---

## 08 · Delete

### `DELETE /api/v1/sound-tracks/{id}`

🔒 **Auth Required**

Permanently deletes a soundtrack and all its cascaded children: `SoundTrackFile` records (and their `SoundTrackBrochure` children), and `SoundTrackAttachment` records. The `SoundTrackLog` entry for this deletion is retained — the log stores `soundTrackRefId` as a plain snapshot column and sets `soundTrack = null`.

### Response · 200 OK

```json
{
  "success": true,
  "message": "SoundTrack deleted successfully",
  "data":    null
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `soundTrack.not.found` | Soundtrack with given `id` does not exist |

---

## 09 · Filter Endpoints

### `GET /api/v1/sound-tracks/by-state`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Filter soundtracks by `TrackState`.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `state` | `TrackState` | **Yes** | — | `SINGLE` \| `MULTI` |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Request

```
GET /api/v1/sound-tracks/by-state?state=MULTI&page=0&size=20
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `soundTrack.state.required` | `state` is missing |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/by-sound-type`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Filter by the free-text `soundType` field.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `soundType` | String | **Yes** | — | Sound type value. URL-encode non-ASCII |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Request

```
GET /api/v1/sound-tracks/by-sound-type?soundType=poem&page=0&size=10
GET /api/v1/sound-tracks/by-sound-type?soundType=%D8%B4%DB%8C%D8%B9%D8%B1
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `soundTrack.soundType.required` | `soundType` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/by-topic`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Filter by the assigned topic ID.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `topicId` | Long | **Yes** | — | ID of the `PublishmentTopic` |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | `topicId` is missing or null |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/album-of-memories`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Returns all soundtracks where `albumOfMemories = true` (typically with `trackState = MULTI`).

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Items per page |

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

## 10 · Search Endpoints

### `GET /api/v1/sound-tracks/search/tag`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Search by tag across both `tagsCkb` and `tagsKmr`. URL-encode non-ASCII (Kurdish) tag values.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `tag` | String | **Yes** | — | Tag value |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `tag.required` | `tag` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/search/keyword`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Search by keyword across both `keywordsCkb` and `keywordsKmr`.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `keyword` | String | **Yes** | — | Keyword value |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `keyword.required` | `keyword` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/sound-tracks/search`

🔒 **Auth Required** · `@Cacheable("soundTracks")`

Global full-text search across title, description, tags, and keywords in both languages.

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `q` | String | **Yes** | — | Search query |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `keyword.required` | `q` is missing or blank |
| `401` | — | Missing or expired JWT |

---

## 11 · Topics

### `GET /api/v1/sound-tracks/topics`

🔒 **Auth Required**

Returns all `PublishmentTopic` records with `entityType = "SOUND"`. Designed for frontend autocomplete/dropdown. Returns the minimum fields: `id`, `nameCkb`, `nameKmr`. Null name values are returned as empty strings.

### Response · 200 OK

```json
{
  "success": true,
  "message": "SOUND topics fetched successfully",
  "data": [
    { "id": 1, "nameCkb": "موسیقا",  "nameKmr": "Mûzîk" },
    { "id": 2, "nameCkb": "فۆلکلۆر", "nameKmr": "Folklor" },
    { "id": 4, "nameCkb": "ئەدەب",   "nameKmr": "Edebiyat" }
  ]
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

## 12 · DTO Reference

### CreateRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `soundType` | String | **Yes** | `@NotBlank @Size(max=100)` |
| `trackState` | `TrackState` | **Yes** | `@NotNull` · `SINGLE` \| `MULTI` |
| `albumOfMemories` | Boolean | No | Marks as Album of Memories. Default `false` |
| `topicId` | Long | No | ID of an existing `PublishmentTopic` with `entityType = "SOUND"` |
| `newTopic` | `InlineTopicRequest` | No | Creates a new topic inline. Ignored if `topicId` is set |
| `contentLanguages` | `Set<Language>` | **Yes** | `@NotNull @NotEmpty` |
| `ckbContent` | `LanguageContentDto` | No* | Used when CKB is selected |
| `kmrContent` | `LanguageContentDto` | No* | Used when KMR is selected |
| `locations` | `Set<String>` | No | Recording / origin locations |
| `reader` | String | No | `@Size(max=255)` — single reader/performer |
| `directors` | `Set<String>` | No | One or more director/producer names |
| `terms` | String | No | `@Size(max=200)` — dialect / stylistic terms |
| `thisProjectOfInstitute` | boolean | No | Default `false` |
| `tags` | `BilingualSet` | No | Bilingual tag sets |
| `keywords` | `BilingualSet` | No | Bilingual keyword sets |
| `files` | `List<FileCreateRequest>` | No | Audio file descriptors. Index-matched to `audioFiles` parts |
| `albumName` | String | No | `@Size(max=300)` — relevant for `MULTI` |
| `publishmentYear` | Integer | No | Album publication year (MULTI) |
| `cdNumber` | Integer | No | CD/disc number |
| `totalTracks` | Integer | No | Total track count in the album |
| `attachments` | `List<AttachmentRequest>` | No | Supplementary attachments. Index-matched to `attachmentFiles` parts |

### UpdateRequest

All fields are optional — partial-merge.

| Field | Type | Description |
| --- | --- | --- |
| `soundType` | String | Replaces the value |
| `trackState` | `TrackState` | Replaces the value |
| `albumOfMemories` | Boolean | Replaces the value |
| `topicId` | Long | Assigns existing topic by ID |
| `newTopic` | `InlineTopicRequest` | Creates and assigns a new topic |
| `clearTopic` | boolean | `true` removes the topic. Default `false` |
| `contentLanguages` | `Set<Language>` | Replaces the language set |
| `ckbContent` / `kmrContent` | `LanguageContentDto` | Replaces each content block |
| `locations` / `directors` | `Set<String>` | Replaces each set |
| `reader` | String | Pass `""` to clear; non-empty replaces; `null` keeps |
| `terms` | String | Replaces the value |
| `thisProjectOfInstitute` | Boolean | Replaces the flag |
| `tags` / `keywords` | `BilingualSet` | Replaces each bilingual set |
| `files` | `List<FileCreateRequest>` | `null` = keep existing; non-null = replace entire list |
| `albumName` / `publishmentYear` / `cdNumber` / `totalTracks` | various | Replaces each |
| `attachments` | `List<AttachmentRequest>` | `null` = keep; non-null = replace |

### Response

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | String | Cover image URLs |
| `soundType` | String | Free-text audio category |
| `trackState` | `TrackState` | `SINGLE` \| `MULTI` |
| `albumOfMemories` | Boolean | `true` for Album of Memories tracks |
| `topicId` | Long | ID of the assigned topic, or `null` |
| `topicNameCkb` / `topicNameKmr` | String | Topic names, or `null` |
| `contentLanguages` | `Set<Language>` | Active languages |
| `ckbContent` / `kmrContent` | `LanguageContentDto` | Bilingual content blocks |
| `locations` | `Set<String>` | Location strings |
| `reader` | String | Single reader / performer |
| `directors` | `Set<String>` | Director / producer names |
| `terms` | String | Dialect / terms label |
| `thisProjectOfInstitute` | Boolean | Institute project flag |
| `tags` / `keywords` | `BilingualSet` | Bilingual tag / keyword sets |
| `files` | `List<FileResponse>` | Audio files ordered by `id ASC` |
| `totalDurationSeconds` | long | Sum of `durationSeconds` across all files |
| `totalSizeBytes` | long | Sum of `sizeBytes` across all files |
| `albumName` / `publishmentYear` / `cdNumber` / `totalTracks` | various | Multi-album fields |
| `attachments` | `List<AttachmentResponse>` | Ordered by `id ASC` |
| `createdAt` / `updatedAt` | `LocalDateTime` | ISO-8601 local datetime |

### FileCreateRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fileUrl` | String | No* | `@Size(max=1200)` — `null` when binary supplied via `audioFiles[i]` |
| `externalUrl` | String | No | External page link |
| `embedUrl` | String | No | iframe-embeddable URL |
| `title` | String | No | `@Size(max=300)` |
| `fileType` | `FileType` | **Yes** | `@NotNull` · `AUDIO` \| `VIDEO` \| `OTHER` |
| `publishmentYear` | Integer | No | — |
| `sizeBytes` | long | No | `0` when service will auto-detect |
| `durationSeconds` | long | No | `0` when service will auto-detect |
| `bitRate` | String | No | `@Size(max=50)` |
| `sampleRate` | String | No | `@Size(max=50)` |
| `audioChannel` | `AudioChannel` | No | `STEREO` \| `MONO` |
| `form` | String | No | `@Size(max=150)` |
| `genre` | String | No | `@Size(max=100)` |
| `recordingVenue` | String | No | `@Size(max=500)` |
| `sortOrder` | Integer | No | Hint only — actual order is `id ASC` |
| `brochures` | `List<BrochureRequest>` | No | Brochure images for this file |
| `fileFormat` | String | No | Container/codec label: `"MP3"`, `"FLAC"`, `"WAV"`, `"AAC"`, `"OGG"` |

> *At least one of `fileUrl`, `externalUrl`, `embedUrl`, or a binary in `audioFiles[i]` should be provided.

### FileResponse

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `fileUrl` / `externalUrl` / `embedUrl` | String | URLs |
| `title` | String | Track/file title |
| `fileType` | `FileType` | `AUDIO` \| `VIDEO` \| `OTHER` |
| `publishmentYear` | Integer | File publication year |
| `sizeBytes` | long | File size in bytes |
| `durationSeconds` | long | Duration in seconds |
| `durationMinutes` | double | `durationSeconds / 60.0` |
| `bitRate` / `sampleRate` | String | Bit / sample rate labels |
| `audioChannel` | `AudioChannel` | `STEREO` \| `MONO` |
| `form` / `genre` / `recordingVenue` | String | Content/style metadata |
| `brochures` | `List<BrochureResponse>` | Ordered brochure images |

### BrochureRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `imageUrl` | String | No* | `@Size(max=1200)`. `null` when binary supplied via `brochureFiles` flat list |
| `caption` | String | No | `@Size(max=300)` |

> *Validation: `@NotBlank` has been **removed** in the current code — `imageUrl` can be `null` when supplying the binary through the multipart `brochureFiles` part. The service skips entries with blank resolved URLs.

### BrochureResponse

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `imageUrl` | String | URL of the brochure image |
| `caption` | String | Optional label |
| `brochureOrder` | Integer | Legacy position column |

### AttachmentRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fileUrl` | String | No* | `@Size(max=1200)`. `null` when binary supplied via `attachmentFiles[i]` |
| `title` | String | No | `@Size(max=300)` |
| `attachmentType` | `AttachmentType` | No | `PDF` \| `VIDEO` \| `IMAGE` \| `AUDIO` \| `OTHER`. **Service defaults to `OTHER` when absent** |
| `sizeBytes` | long | No | `0` when unknown |
| `mimeType` | String | No | `@Size(max=100)` |

> *Validation: `@NotBlank` on `fileUrl` and `@NotNull` on `attachmentType` have been **removed** to prevent 500 errors from `ConstraintViolationException` when those fields are absent. The service skips attachments with blank resolved URLs and defaults `attachmentType` to `OTHER`.

### AttachmentResponse

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `fileUrl` | String | URL of the attachment file |
| `title` | String | Human-readable label |
| `attachmentType` | `AttachmentType` | Attachment category |
| `sizeBytes` | long | File size in bytes |
| `mimeType` | String | MIME type string |
| `attachmentOrder` | Integer | Legacy position column |

### LanguageContentDto

| Field | Type | Max Length | Description |
| --- | --- | --- | --- |
| `title` | String | `@Size(max=200)` | Track title in this language |
| `description` | String | `@Size(max=4000)` | Full description. Stored as `TEXT` on the DB |

### InlineTopicRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `nameCkb` | String | **Yes** | Sorani name for the new topic |
| `nameKmr` | String | **Yes** | Kurmanji name for the new topic |

### BilingualSet

| Field | Type | Description |
| --- | --- | --- |
| `ckb` | `Set<String>` | CKB strings. Tags max 60 chars; keywords max 100 chars |
| `kmr` | `Set<String>` | KMR strings. Tags max 60 chars; keywords max 100 chars |

### ApiResponse&lt;T&gt;

All endpoints return this wrapper. `data` is omitted on failure due to `@JsonInclude(NON_NULL)`.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure |

---

## 13 · File Matching Rules

The multipart `POST /` and `PUT /{id}` endpoints accept up to **three classes of binary files** in addition to the cover images. The service maps each uploaded binary to its corresponding DTO entry using strict index-based matching.

### Audio Files — `audioFiles[i]` ↔︎ `dto.files[i]`

```
audioFiles[0]  →  dto.files[0]   (uploaded binary overrides dto.files[0].fileUrl)
audioFiles[1]  →  dto.files[1]
audioFiles[2]  →  dto.files[2]
...
```

- If `audioFiles` contains fewer entries than `dto.files`, trailing file DTOs use their `fileUrl` values.
- If `audioFiles` contains more entries than `dto.files`, extra uploaded files are ignored.
- To upload a file without a DTO URL, set `dto.files[i].fileUrl = null`. The service fills it in after upload.

### Brochure Files — `brochureFiles` (flat global list)

`brochureFiles` is a **flat list consumed globally** across all files in index order, regardless of which file each brochure belongs to. The service iterates `dto.files[0].brochures`, then `dto.files[1].brochures`, etc., and assigns `brochureFiles[0]`, `[1]`, `[2]`, … in the order encountered.

**Example:**

```
dto.files[0].brochures[0].imageUrl = null  →  brochureFiles[0]
dto.files[0].brochures[1].imageUrl = null  →  brochureFiles[1]
dto.files[1].brochures[0].imageUrl = null  →  brochureFiles[2]
```

For brochures with a non-null `imageUrl` in the DTO, no `brochureFiles` slot is consumed — the service uses the URL directly and advances the brochure index only on `null` entries.

### Attachment Files — `attachmentFiles[i]` ↔︎ `dto.attachments[i]`

```
attachmentFiles[0]  →  dto.attachments[0]
attachmentFiles[1]  →  dto.attachments[1]
...
```

Same index-matching rules as audio files. Set `dto.attachments[i].fileUrl = null` when the binary is supplied via `attachmentFiles[i]`.

### Cover Images

Cover images are not index-matched — each has a named part:

| Part | Target field |
| --- | --- |
| `ckbCoverImage` | `SoundTrack.ckbCoverUrl` |
| `kmrCoverImage` | `SoundTrack.kmrCoverUrl` |
| `hoverCoverImage` | `SoundTrack.hoverCoverUrl` |

---

## 14 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New soundtrack saved successfully |
| `200 OK` | Update, delete, or read succeeded |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | Soundtrack with given id does not exist |
| `500 Internal Error` | Unexpected server failure — check logs |

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "errors": [
    { "field": "soundType",        "message": "soundType is required" },
    { "field": "trackState",       "message": "trackState is required" },
    { "field": "contentLanguages", "message": "At least one content language is required" }
  ]
}
```

### Business Rule Error Body — `400`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "error":     "Bad Request",
  "message":   "soundTrack.soundType.required"
}
```

### Common Business Error Keys

| Error Key | Trigger |
| --- | --- |
| `soundTrack.soundType.required` | `soundType` is blank on create, or blank `soundType` param on `/by-sound-type` |
| `soundTrack.state.required` | `trackState` is null on create, or missing `state` param on `/by-state` |
| `soundTrack.languages.required` | `contentLanguages` is empty or null |
| `soundTrack.ckb.title.required` | CKB active but `ckbContent.title` is blank |
| `soundTrack.kmr.title.required` | KMR active but `kmrContent.title` is blank |
| `soundTrack.file.type.required` | `fileType` is null on a `FileCreateRequest` entry |
| `soundTrack.not.found` | No soundtrack found for the given `id` |
| `soundTrack.topic.not.found` | `topicId` provided but topic is not a SOUND topic |
| `error.validation` | `topicId` param missing on `/by-topic`, or generic envelope |
| `tag.required` | `/search/tag` called with blank `tag` |
| `keyword.required` | `/search/keyword` or `/search` called with blank input |

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
  "message":   "soundTrack.not.found"
}
```

> ℹ️ All `createdAt` and `updatedAt` fields in the `Response` DTO are `LocalDateTime`.

---

## 15 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| **Base URL** | `/api/v1/soundtracks` | `/api/v1/sound-tracks` | 🚨 **BREAKING — hyphenated path** |
| `POST /` | Multipart create | Multipart create | ⚪ Unchanged shape (URL only) |
| `PUT /{id}` | Multipart update | Multipart update — **partial-merge** semantics formalized | 🟡 Behaviour clarified |
| `GET /` (getAll) | List | List — now `@Cacheable("soundTracks")` | 🟡 Behaviour clarified |
| `GET /{id}` | Get by id | Get by id | ⚪ Unchanged |
| `DELETE /{id}` | Delete | Delete | ⚪ Unchanged |
| `GET /by-state` | Filter by state | Filter by state — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /by-sound-type` | Filter by soundType | Filter by soundType — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /by-topic` | Filter by topic | Filter by topic — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /album-of-memories` | Album-of-memories filter | Album-of-memories filter — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /search/tag` | Tag search | Tag search — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /search/keyword` | Keyword search | Keyword search — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /search` | Global search | Global search — `@Cacheable` | 🟡 Behaviour clarified |
| `GET /topics` | SOUND topics | SOUND topics | ⚪ Unchanged |

**Endpoint count:** Old doc advertised 14 endpoints but listed 13 in its summary table; the controller exposes 13 (the doc had a counting typo). The new doc reflects the actual 13. All 13 are reachable under the new hyphenated base URL.

> 🚨 **Action required for clients:** every existing call to `/api/v1/soundtracks/*` returns `404`. Update to `/api/v1/sound-tracks/*`.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `sound_tracks` DB indexes | Not documented | 🟢 Documented: `idx_soundtrack_type`, `idx_soundtrack_state`, `idx_soundtrack_album`, `idx_soundtrack_topic`, `idx_soundtrack_created_at`, `idx_soundtrack_updated_at` |
| `sound_track_files` DB indexes | Not documented | 🟢 Documented: `idx_sound_file_type`, `idx_sound_file_track` |
| `sound_track_brochures` DB index | Not documented | 🟢 Documented: `idx_brochure_file` |
| `sound_track_attachments` DB indexes | Not documented | 🟢 Documented: `idx_attachment_track`, `idx_attachment_type` |
| `sound_track_logs` DB indexes | Not documented | 🟢 Documented: `idx_stlog_soundtrack`, `idx_stlog_action`, `idx_stlog_created_at` |
| Brochure / attachment ordering | Said `@OrderColumn("brochure_order")` and `@OrderColumn("attachment_order")` | 🟡 **Clarification**: ordering is enforced by `@OrderBy("id ASC")` on the parent collections; the `brochure_order` / `attachment_order` columns exist but are **legacy** — not maintained by the new `@OrderBy` strategy |
| Cover URL lengths | Not specified | 🟢 Documented: `VARCHAR(1000)` for all three slots |
| Helper methods on `SoundTrack` | Not documented | 🟢 Documented: `isMulti()`, `isMultiAlbumOfMemories()`, `addFile/removeFile`, `addAttachment/removeAttachment` |
| `SoundTrackLog.action` | Listed `CREATE`/`UPDATE`/`DELETE` | 🟡 Now also includes `"FILE_ADDED"`, `"CREATED"`, `"UPDATED"`, `"DELETED"` (free-text, no enum) |
| `SoundTrackLog` extra fields | Not documented | 🟢 Documented: `actorId`, `actorName`, `requestId`, `meta`, `details` (8000-char column) |
| `SoundTrack` ↔︎ `SoundTrackLog` FK | Not documented | 🟢 Clarified: `SoundTrackLog.soundTrack` is **nullable** and set to `null` on `DELETED` logs; `soundTrackRefId` keeps the original ID |

### C) DTO comparison

| Item | Old | New |
| --- | --- | --- |
| `CreateRequest` shape | Same | ⚪ Unchanged structurally |
| `UpdateRequest` shape | Partial-merge | ⚪ Unchanged — semantics formalized in §06 |
| `Response` shape (incl. `totalDurationSeconds`, `totalSizeBytes` aggregates) | Same | ⚪ Unchanged |
| `BrochureRequest.imageUrl` validation | Documented as required | 🟡 **Relaxed**: `@NotBlank` has been **removed** — `imageUrl` can be `null` when a binary is supplied via `brochureFiles`. Service skips entries with blank resolved URL |
| `AttachmentRequest.fileUrl` validation | Documented as required | 🟡 **Relaxed**: `@NotBlank` has been **removed** — can be `null` when supplied via `attachmentFiles[i]` |
| `AttachmentRequest.attachmentType` validation | Documented as required | 🟡 **Relaxed**: `@NotNull` has been **removed**; service now **defaults to `OTHER`** when absent. This prevents the old `ConstraintViolationException → 500` |
| `BilingualSet` max lengths | Not specified | 🟢 Tags: 60 chars; Keywords: 100 chars |
| `LanguageContentDto.description` DTO size | Not specified | 🟢 `@Size(max=4000)` |
| `JsonProperty("thisProjectOfInstitute")` mapping | Not documented | 🟢 Explicit on `CreateRequest`, `UpdateRequest`, `Response` |

### D) Validation / error-key comparison

| Error key | Old | New |
| --- | --- | --- |
| `soundTrack.soundType.required` | Present | ⚪ Unchanged |
| `soundTrack.state.required` | Present | ⚪ Unchanged |
| `soundTrack.languages.required` | Present | ⚪ Unchanged |
| `soundTrack.ckb.title.required` / `soundTrack.kmr.title.required` | Documented | ⚪ Unchanged |
| `soundTrack.file.type.required` | Documented | ⚪ Unchanged |
| `soundTrack.not.found` | Documented | ⚪ Unchanged |
| `soundTrack.topic.not.found` | Documented | ⚪ Unchanged |
| `tag.required` / `keyword.required` | Documented | ⚪ Unchanged |
| `error.validation` | Documented for `/by-topic` | ⚪ Unchanged (also generic envelope) |
| (storage errors) | Generic 500 | 🟢 Now wrapped via `Errors.soundStorageFailed(...)` family on upload failures |

### E) Caching & performance

| Item | Old | New |
| --- | --- | --- |
| `@Cacheable("soundTracks")` on all reads | Not documented | 🟢 Documented — applies to `getAll`, `getByState`, `getBySoundType`, `getByTopic`, `getAlbumOfMemories`, `searchByTag`, `searchByKeyword`, `globalSearch` |
| `@CacheEvict(allEntries=true)` on writes | Not documented | 🟢 Documented — `create`, `update`, `delete` evict the whole `soundTracks` cache |
| `@BatchSize(50)` strategy (~11 IN-queries per page) | Documented | ⚪ Unchanged |

### F) Summary

- 🚨 **BREAKING — base URL changed:** `/api/v1/soundtracks` → `/api/v1/sound-tracks` (hyphenated). All client integrations must update their paths or every call will return `404`.
- 🟢 **Added (operational):** `@Cacheable("soundTracks")` on every read endpoint; `@CacheEvict(allEntries=true)` on every write; full DB-index documentation across `sound_tracks`, `sound_track_files`, `sound_track_brochures`, `sound_track_attachments`, and `sound_track_logs`; entity helper methods (`isMulti`, `isMultiAlbumOfMemories`, `addFile`, etc.); explicit `SoundTrackLog` snapshot semantics with `soundTrackRefId` retained after delete.
- 🟡 **Relaxed (validation):** `BrochureRequest.imageUrl` and `AttachmentRequest.fileUrl` no longer carry `@NotBlank`; `AttachmentRequest.attachmentType` no longer carries `@NotNull` and now defaults to `OTHER` in the service — this eliminates spurious `500 ConstraintViolationException` errors when binaries are supplied via the multipart parts.
- 🟡 **Clarified:** brochure/attachment ordering is now enforced by `@OrderBy("id ASC")` on the parent collections — the legacy `brochure_order` / `attachment_order` columns remain in the schema but are not maintained by the new ordering strategy; `UpdateRequest.reader` accepts `""` to explicitly clear; `LanguageContentDto.description` has a `@Size(max=4000)` cap on the DTO side; `SoundTrackLog.action` is free-text (`CREATED`, `UPDATED`, `DELETED`, `FILE_ADDED`, …) and the log can keep a snapshot row after the parent track is deleted.
- ⚪ **Unchanged:** the 13 endpoint paths (relative to the new base URL), the multipart `data` + cover/audio/brochure/attachment file layout, the three cover slots, all enums (`TrackState`, `FileType`, `AudioChannel`, `AttachmentType`, `Language`), the `BilingualSet` shape for tags/keywords, the partial-merge update model, the file-matching rules (audio/brochure flat-list/attachment), the `Response` aggregates (`totalDurationSeconds`, `totalSizeBytes`), and the topic create-or-lookup behaviour.
