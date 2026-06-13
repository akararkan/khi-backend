# Sound Tracks Module

> Bilingual (CKB / KMR) sound tracks with single and multi states, audio files, brochures, attachments, topic taxonomy, multi-axis search. Public reads, authenticated writes (see §07 caveat).

## Table of Contents
- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — SoundTrack](#02--data-model--soundtrack)
- [03 · Data Model — SoundTrackContent](#03--data-model--soundtrackcontent)
- [04 · Data Model — SoundTrackFile](#04--data-model--soundtrackfile)
- [05 · Data Model — SoundTrackBrochure](#05--data-model--soundtrackbrochure)
- [06 · Data Model — SoundTrackAttachment](#06--data-model--soundtrackattachment)
- [07 · Authentication & Roles (incl. matcher caveat)](#07--authentication--roles-incl-matcher-caveat)
- [08 · Enums](#08--enums)
- [09 · Public API](#09--public-api)
- [10 · Internal API](#10--internal-api)
- [11 · DTO Reference](#11--dto-reference)
- [12 · Multipart Layout](#12--multipart-layout)
- [13 · Response Envelope](#13--response-envelope)
- [14 · Error Responses](#14--error-responses)
- [15 · Notes](#15--notes)

---

## 01 · Module Overview

- Base path: `/api/v1/sound-tracks`
- Controller: `SoundTrackController` (package `ak.dev.khi_backend.khi_app.api.publishment.sound`)
- Service:    `SoundTrackService`
- Reads:      `GET` — public (covered by global GET rule in SecurityConfig).
- Writes:     `POST` / `PUT` / `DELETE` — `multipart/form-data` only (see §07 caveat).
- Caching:    Read methods are `@Cacheable("soundTracks")`; writes evict the whole cache via `@CacheEvict(allEntries = true)`.
- Domain:     Bilingual CKB (Sorani) / KMR (Kurmanji) sound publishments — poems, lectures, songs, recitations, etc.

### Endpoint Summary

| Method | Path                                | Purpose                                      | Public |
|--------|-------------------------------------|----------------------------------------------|--------|
| GET    | `/api/v1/sound-tracks`              | Paginated list                               | yes    |
| GET    | `/api/v1/sound-tracks/{id}`         | Single track by id                           | yes    |
| GET    | `/api/v1/sound-tracks/by-state`     | Filter by `TrackState` (`SINGLE` / `MULTI`)  | yes    |
| GET    | `/api/v1/sound-tracks/by-sound-type`| Filter by `soundType` string                 | yes    |
| GET    | `/api/v1/sound-tracks/by-topic`     | Filter by `topicId`                          | yes    |
| GET    | `/api/v1/sound-tracks/album-of-memories` | Tracks flagged as album of memories     | yes    |
| GET    | `/api/v1/sound-tracks/search/tag`   | Tag search (CKB or KMR)                      | yes    |
| GET    | `/api/v1/sound-tracks/search/keyword` | Keyword search (CKB or KMR)                | yes    |
| GET    | `/api/v1/sound-tracks/search`       | Global cross-field search                    | yes    |
| GET    | `/api/v1/sound-tracks/topics`       | List `SOUND` topics for autocomplete         | yes    |
| POST   | `/api/v1/sound-tracks`              | Create (`multipart/form-data`)               | auth   |
| PUT    | `/api/v1/sound-tracks/{id}`         | Update (`multipart/form-data`)               | auth   |
| DELETE | `/api/v1/sound-tracks/{id}`         | Delete                                       | auth   |

---

## 02 · Data Model — SoundTrack

JPA `@Entity` mapped to table `sound_tracks`. Strategy: LAZY collections with `@BatchSize(50)` to avoid N+1 / Cartesian explosions.

### Table indexes

| Name                          | Columns                  |
|-------------------------------|--------------------------|
| `idx_soundtrack_type`         | `sound_type`             |
| `idx_soundtrack_state`        | `track_state`            |
| `idx_soundtrack_album`        | `is_album_of_memories`   |
| `idx_soundtrack_topic`        | `topic_id`               |
| `idx_soundtrack_created_at`   | `created_at`             |
| `idx_soundtrack_updated_at`   | `updated_at`             |

### Fields

| Field                    | Java type                  | Column                 | Nullable | Length / Notes                                                |
|--------------------------|----------------------------|------------------------|----------|---------------------------------------------------------------|
| `id`                     | `Long`                     | `id`                   | no       | `@Id @GeneratedValue(IDENTITY)`                               |
| `ckbCoverUrl`            | `String`                   | `ckb_cover_url`        | yes      | `length = 1000`                                                |
| `kmrCoverUrl`            | `String`                   | `kmr_cover_url`        | yes      | `length = 1000`                                                |
| `hoverCoverUrl`          | `String`                   | `hover_cover_url`      | yes      | `length = 1000`                                                |
| `soundType`              | `String`                   | `sound_type`           | no       | `length = 100`                                                 |
| `trackState`             | `TrackState`               | `track_state`          | no       | `@Enumerated(STRING) length = 10` (`SINGLE` / `MULTI`)         |
| `albumOfMemories`        | `boolean`                  | `is_album_of_memories` | no       | default `false`                                                |
| `topic`                  | `PublishmentTopic`         | `topic_id`             | yes      | `@ManyToOne(LAZY)` → `publishment_topics`                      |
| `contentLanguages`       | `Set<Language>`            | `language`             | no       | `@ElementCollection` → `sound_track_content_languages` (len 10)|
| `ckbContent`             | `SoundTrackContent`        | `title_ckb`/`description_ckb` | yes | Embedded; title `length = 200`, description `TEXT`            |
| `kmrContent`             | `SoundTrackContent`        | `title_kmr`/`description_kmr` | yes | Embedded; title `length = 200`, description `TEXT`            |
| `locations`              | `Set<String>`              | `location`             | no       | `@ElementCollection` → `sound_track_locations` (len 255)       |
| `reader`                 | `String`                   | `reader_name`          | yes      | `length = 255` — single reader / performer name                |
| `directors`              | `Set<String>`              | `director_name`        | no       | `@ElementCollection` → `sound_track_directors` (len 255)       |
| `terms`                  | `String`                   | `terms`                | yes      | `length = 200` — dialect / terms label                         |
| `thisProjectOfInstitute` | `boolean`                  | `is_institute_project` | no       | flag                                                           |
| `keywordsCkb`            | `Set<String>`              | `keyword_ckb`          | no       | `@ElementCollection` → `sound_track_keywords_ckb` (len 100)    |
| `keywordsKmr`            | `Set<String>`              | `keyword_kmr`          | no       | `@ElementCollection` → `sound_track_keywords_kmr` (len 100)    |
| `tagsCkb`                | `Set<String>`              | `tag_ckb`              | no       | `@ElementCollection` → `sound_track_tags_ckb` (len 60)         |
| `tagsKmr`                | `Set<String>`              | `tag_kmr`              | no       | `@ElementCollection` → `sound_track_tags_kmr` (len 60)         |
| `files`                  | `List<SoundTrackFile>`     | —                      | —        | `@OneToMany(mappedBy = "soundTrack")` cascade ALL + orphanRemoval; `@OrderBy("id ASC")` |
| `albumName`              | `String`                   | `album_name`           | yes      | `length = 300` (multi-album)                                   |
| `publishmentYear`        | `Integer`                  | `publishment_year`     | yes      | album-level year for MULTI                                     |
| `cdNumber`               | `Integer`                  | `cd_number`            | yes      | multi-album disc number                                        |
| `totalTracks`            | `Integer`                  | `total_tracks`         | yes      | multi-album track count                                        |
| `attachments`            | `List<SoundTrackAttachment>` | —                    | —        | `@OneToMany(mappedBy = "soundTrack")` cascade ALL + orphanRemoval; `@OrderBy("id ASC")` |
| `createdAt`              | `LocalDateTime`            | `created_at`           | no       | set on `@PrePersist`                                           |
| `updatedAt`              | `LocalDateTime`            | `updated_at`           | no       | set on `@PrePersist` + `@PreUpdate`                            |

### Helpers

```java
public void    addFile(SoundTrackFile file)
public void    removeFile(SoundTrackFile file)
public void    addAttachment(SoundTrackAttachment a)
public void    removeAttachment(SoundTrackAttachment a)
public boolean isMultiAlbumOfMemories()  // trackState == MULTI && albumOfMemories
public boolean isMulti()                 // trackState == MULTI
```

---

## 03 · Data Model — SoundTrackContent

`@Embeddable` — embedded twice on `SoundTrack` for CKB and KMR halves.

| Field         | Type     | Column                                          | Notes                       |
|---------------|----------|-------------------------------------------------|-----------------------------|
| `title`       | `String` | `title_ckb` / `title_kmr` (overridden)          | `length = 200`              |
| `description` | `String` | `description_ckb` / `description_kmr` (overridden) | `columnDefinition = "TEXT"` |

Descriptions are processed by `TiptapHtmlProcessor` before being persisted (see `SoundTrackService.buildContent`).

---

## 04 · Data Model — SoundTrackFile

Table `sound_track_files`. Indexes: `idx_sound_file_type(file_type)`, `idx_sound_file_track(sound_track_id)`.

| Field             | Java type            | Column            | Nullable | Length / Notes                                          |
|-------------------|----------------------|-------------------|----------|---------------------------------------------------------|
| `id`              | `Long`               | `id`              | no       | `@Id @GeneratedValue(IDENTITY)`                         |
| `fileUrl`         | `String`             | `file_url`        | yes      | `length = 1200` — direct hosted URL                     |
| `externalUrl`     | `String`             | `external_url`    | yes      | `columnDefinition = "TEXT"` — page link                 |
| `embedUrl`        | `String`             | `embed_url`       | yes      | `columnDefinition = "TEXT"` — iframe URL                |
| `title`           | `String`             | `title`           | yes      | `length = 300`                                          |
| `fileType`        | `FileType`           | `file_type`       | no       | `@Enumerated(STRING) length = 10`                       |
| `publishmentYear` | `Integer`            | `publishment_year`| yes      | per-file year                                           |
| `fileFormat`      | `String`             | `file_format`     | yes      | `length = 50` — free-text codec label                   |
| `sizeBytes`       | `long`               | `size_bytes`      | no       | `0` when unknown; auto-set on upload                    |
| `durationSeconds` | `long`               | `duration_seconds`| no       | `0` when unknown                                        |
| `bitRate`         | `String`             | `bit_rate`        | yes      | `length = 50` (e.g. `320 kbps`)                         |
| `sampleRate`      | `String`             | `sample_rate`     | yes      | `length = 50` (e.g. `44100 Hz`)                         |
| `audioChannel`    | `AudioChannel`       | `audio_channel`   | yes      | `@Enumerated(STRING) length = 10`                       |
| `form`            | `String`             | `form`            | yes      | `length = 150` — musical/linguistic form                |
| `genre`           | `String`             | `genre`           | yes      | `length = 100`                                          |
| `recordingVenue`  | `String`             | `recording_venue` | yes      | `length = 500`                                          |
| `brochures`       | `List<SoundTrackBrochure>` | —          | —        | `@OneToMany(mappedBy="soundTrackFile")` cascade ALL + orphanRemoval; `@OrderBy("id ASC")` |
| `soundTrack`      | `SoundTrack`         | `sound_track_id`  | no       | `@ManyToOne(LAZY, optional=false)`                      |

Convenience: `getDurationMinutes() = durationSeconds / 60.0` (exposed in DTO as `durationMinutes`).

At least one of `fileUrl` / `externalUrl` / `embedUrl` must resolve — the service throws `soundTrack.file.source.required` otherwise.

---

## 05 · Data Model — SoundTrackBrochure

Table `sound_track_brochures`. Index: `idx_brochure_file(sound_track_file_id)`.

| Field            | Java type        | Column                | Nullable | Length / Notes                       |
|------------------|------------------|-----------------------|----------|--------------------------------------|
| `id`             | `Long`           | `id`                  | no       | `@Id @GeneratedValue(IDENTITY)`      |
| `imageUrl`       | `String`         | `image_url`           | no       | `length = 1200`                      |
| `caption`        | `String`         | `caption`             | yes      | `length = 300`                       |
| `brochureOrder`  | `Integer`        | `brochure_order`      | yes      | set by service (per-file position)   |
| `soundTrackFile` | `SoundTrackFile` | `sound_track_file_id` | no       | `@ManyToOne(LAZY, optional=false)`   |

Each `SoundTrackFile` owns its brochures (parent-side `cascade=ALL, orphanRemoval=true`).

---

## 06 · Data Model — SoundTrackAttachment

Table `sound_track_attachments`. Indexes: `idx_attachment_track(sound_track_id)`, `idx_attachment_type(attachment_type)`.

| Field             | Java type         | Column             | Nullable | Length / Notes                                          |
|-------------------|-------------------|--------------------|----------|---------------------------------------------------------|
| `id`              | `Long`            | `id`               | no       | `@Id @GeneratedValue(IDENTITY)`                         |
| `fileUrl`         | `String`          | `file_url`         | no       | `length = 1200`                                          |
| `title`           | `String`          | `title`            | yes      | `length = 300`                                           |
| `attachmentType`  | `AttachmentType`  | `attachment_type`  | no       | `@Enumerated(STRING) length = 20` — defaults to `OTHER` when missing |
| `sizeBytes`       | `long`            | `size_bytes`       | no       | `0` when unknown                                         |
| `mimeType`        | `String`          | `mime_type`        | yes      | `length = 100`                                           |
| `attachmentOrder` | `Integer`         | `attachment_order` | yes      | set by service (index in list)                           |
| `soundTrack`      | `SoundTrack`      | `sound_track_id`   | no       | `@ManyToOne(LAZY, optional=false)`                       |

Attachments are available for BOTH `SINGLE` and `MULTI` track states.

---

## 07 · Authentication & Roles (incl. matcher caveat)

The global SecurityConfig grants public access to all `GET /api/v1/**` endpoints.

> **Caveat — SecurityConfig matcher mismatch.**
> SecurityConfig matchers use `/api/v1/soundtracks/**` (no hyphen) for role-based write access, while this controller's base path is `/api/v1/sound-tracks` (WITH hyphen). The role-based `POST`/`PUT`/`DELETE` matchers therefore do NOT apply here; writes fall through to `.anyRequest().authenticated()`, meaning **any authenticated user (including GUEST) can write**. This is a real bug worth surfacing.

| Method | Path                                       | Auth Required          | Roles (effective) |
|--------|--------------------------------------------|------------------------|-------------------|
| GET    | `/api/v1/sound-tracks`                     | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/{id}`                | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/by-state`            | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/by-sound-type`       | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/by-topic`            | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/album-of-memories`   | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/search/tag`          | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/search/keyword`      | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/search`              | no (public)            | — (anonymous)     |
| GET    | `/api/v1/sound-tracks/topics`              | no (public)            | — (anonymous)     |
| POST   | `/api/v1/sound-tracks`                     | yes (auth only)        | any authenticated |
| PUT    | `/api/v1/sound-tracks/{id}`                | yes (auth only)        | any authenticated |
| DELETE | `/api/v1/sound-tracks/{id}`                | yes (auth only)        | any authenticated |

---

## 08 · Enums

### `TrackState` — `ak.dev.khi_backend.khi_app.enums.publishment.TrackState`

| Value    | Meaning                                |
|----------|----------------------------------------|
| `SINGLE` | Single-track release                   |
| `MULTI`  | Multi-track album / compilation        |

### `Language` — `ak.dev.khi_backend.khi_app.enums.Language`

| Value | Meaning              |
|-------|----------------------|
| `CKB` | Kurdish Central (Sorani)   |
| `KMR` | Kurdish Kurmanji            |

### `FileType` — `ak.dev.khi_backend.khi_app.enums.publishment.FileType`

| Value   |
|---------|
| `MP3`   |
| `WAV`   |
| `OGG`   |
| `AAC`   |
| `FLAC`  |
| `OTHER` |

### `AudioChannel` — `ak.dev.khi_backend.khi_app.enums.publishment.AudioChannel`

| Value    | Meaning                  |
|----------|--------------------------|
| `MONO`   | Single channel           |
| `STEREO` | Two channels (L + R)     |

### `AttachmentType` — `ak.dev.khi_backend.khi_app.enums.publishment.AttachmentType`

| Value   | Meaning                                              |
|---------|------------------------------------------------------|
| `PDF`   | PDF booklet, lyric sheet, liner notes …              |
| `VIDEO` | Promotional or documentary video                     |
| `IMAGE` | Standalone image (poster, artwork …)                 |
| `AUDIO` | Extra audio clip (intro, interview …)                |
| `OTHER` | Default fallback when DTO `attachmentType` is null   |

---

## 09 · Public API

All endpoints below are GET and require no authentication.

### 9.1 `GET /api/v1/sound-tracks`

Paginated list of every sound track.

| Query Param | Type | Default | Required |
|-------------|------|---------|----------|
| `page`      | int  | `0`     | no       |
| `size`      | int  | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks?page=0&size=20"
```

```json
{
  "success": true,
  "message": "SoundTracks fetched successfully",
  "data": {
    "content": [
      {
        "id": 14,
        "ckbCoverUrl": "https://cdn.example.com/cov/14_ckb.jpg",
        "kmrCoverUrl": "https://cdn.example.com/cov/14_kmr.jpg",
        "hoverCoverUrl": null,
        "soundType": "هۆنراوە",
        "trackState": "SINGLE",
        "albumOfMemories": false,
        "topicId": 5,
        "topicNameCkb": "ئەدەبی فۆلکلۆری",
        "topicNameKmr": "Wêjeya Gelêrî",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "هاوار",
          "description": "<p>هۆنراوەی گەلێ بەناوبانگی کوردی.</p>"
        },
        "kmrContent": {
          "title": "Hawar",
          "description": "<p>Helbestek navdar a Kurdî.</p>"
        },
        "locations": ["سلێمانی"],
        "reader": "هەژار موکریانی",
        "directors": ["شیرکۆ بێکەس"],
        "terms": "سۆرانی",
        "thisProjectOfInstitute": true,
        "tags": { "ckb": ["کلاسیک"], "kmr": ["Klasîk"] },
        "keywords": { "ckb": ["شیعر"], "kmr": ["Helbest"] },
        "files": [],
        "totalDurationSeconds": 0,
        "totalSizeBytes": 0,
        "albumName": null,
        "publishmentYear": 1985,
        "cdNumber": null,
        "totalTracks": null,
        "attachments": [],
        "createdAt": "2026-05-01T10:23:14",
        "updatedAt": "2026-05-12T08:51:42"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 124,
    "totalPages": 7,
    "first": true,
    "last": false,
    "number": 0,
    "size": 20,
    "numberOfElements": 1,
    "empty": false
  }
}
```

### 9.2 `GET /api/v1/sound-tracks/{id}`

Single track hydrated with full graph.

| Path Param | Type | Required |
|------------|------|----------|
| `id`       | Long | yes      |

Response: `ApiResponse<Response>` — message `"SoundTrack fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/14"
```

```json
{
  "success": true,
  "message": "SoundTrack fetched successfully",
  "data": {
    "id": 14,
    "soundType": "هۆنراوە",
    "trackState": "SINGLE",
    "albumOfMemories": false,
    "contentLanguages": ["CKB"],
    "ckbContent": { "title": "هاوار", "description": "<p>دەقی شیعر.</p>" },
    "files": [],
    "attachments": []
  }
}
```

Returns 404 with code `SOUND_NOT_FOUND` when the id does not exist.

### 9.3 `GET /api/v1/sound-tracks/by-state`

Filter by `TrackState`.

| Query Param | Type         | Default | Required |
|-------------|--------------|---------|----------|
| `state`     | `TrackState` | —       | yes      |
| `page`      | int          | `0`     | no       |
| `size`      | int          | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks by state fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/by-state?state=MULTI&page=0&size=10"
```

### 9.4 `GET /api/v1/sound-tracks/by-sound-type`

Filter by `soundType` free-text field.

| Query Param | Type   | Default | Required |
|-------------|--------|---------|----------|
| `soundType` | String | —       | yes (non-blank) |
| `page`      | int    | `0`     | no       |
| `size`      | int    | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks by sound type fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/by-sound-type?soundType=%D9%87%DB%86%D9%86%D8%B1%D8%A7%D9%88%DB%95"
```

### 9.5 `GET /api/v1/sound-tracks/by-topic`

Filter by Topic id (`PublishmentTopic` with `entityType = "SOUND"`).

| Query Param | Type | Default | Required |
|-------------|------|---------|----------|
| `topicId`   | Long | —       | yes      |
| `page`      | int  | `0`     | no       |
| `size`      | int  | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks by topic fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/by-topic?topicId=5&page=0&size=20"
```

### 9.6 `GET /api/v1/sound-tracks/album-of-memories`

Returns only tracks whose `albumOfMemories` flag is `true`.

| Query Param | Type | Default |
|-------------|------|---------|
| `page`      | int  | `0`     |
| `size`      | int  | `20`    |

Response: `ApiResponse<Page<Response>>` — message `"Album of memories fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/album-of-memories?page=0&size=20"
```

### 9.7 `GET /api/v1/sound-tracks/search/tag`

Tag search across `tagsCkb` and `tagsKmr`.

| Query Param | Type   | Default | Required |
|-------------|--------|---------|----------|
| `tag`       | String | —       | yes (non-blank) |
| `page`      | int    | `0`     | no       |
| `size`      | int    | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks by tag fetched successfully"`.
Throws `tag.required` (400) when blank.

```bash
curl "https://api.example.com/api/v1/sound-tracks/search/tag?tag=%DA%A9%D9%84%D8%A7%D8%B3%DB%8C%DA%A9&page=0&size=20"
```

### 9.8 `GET /api/v1/sound-tracks/search/keyword`

Keyword search across `keywordsCkb` and `keywordsKmr`.

| Query Param | Type   | Default | Required |
|-------------|--------|---------|----------|
| `keyword`   | String | —       | yes (non-blank) |
| `page`      | int    | `0`     | no       |
| `size`      | int    | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks by keyword fetched successfully"`.
Throws `keyword.required` (400) when blank.

```bash
curl "https://api.example.com/api/v1/sound-tracks/search/keyword?keyword=%D8%B4%DB%8C%D8%B9%D8%B1&page=0&size=20"
```

### 9.9 `GET /api/v1/sound-tracks/search`

Global multi-axis search (title, description, soundType, tags, keywords, reader, directors, locations, album name, …).

| Query Param | Type   | Default | Required |
|-------------|--------|---------|----------|
| `q`         | String | —       | yes (non-blank) |
| `page`      | int    | `0`     | no       |
| `size`      | int    | `20`    | no       |

Response: `ApiResponse<Page<Response>>` — message `"SoundTracks global search fetched successfully"`.
Throws `keyword.required` (400) when blank.

```bash
curl "https://api.example.com/api/v1/sound-tracks/search?q=%D9%87%D8%A7%D9%88%D8%A7%D8%B1&page=0&size=20"
```

### 9.10 `GET /api/v1/sound-tracks/topics`

Returns all `PublishmentTopic` rows whose `entityType = "SOUND"`, simplified to `{ id, nameCkb, nameKmr }` for autocomplete.

Response: `ApiResponse<List<Map<String, Object>>>` — message `"SOUND topics fetched successfully"`.

```bash
curl "https://api.example.com/api/v1/sound-tracks/topics"
```

```json
{
  "success": true,
  "message": "SOUND topics fetched successfully",
  "data": [
    { "id": 1, "nameCkb": "ئەدەبی فۆلکلۆری", "nameKmr": "Wêjeya Gelêrî" },
    { "id": 2, "nameCkb": "هۆنراوە", "nameKmr": "Helbest" },
    { "id": 5, "nameCkb": "گۆرانیی کلاسیک", "nameKmr": "Stranên Klasîk" }
  ]
}
```

---

## 10 · Internal API

Writes are `multipart/form-data` only. See §07 for the auth caveat.

### 10.1 `POST /api/v1/sound-tracks`

Creates a new sound track.

- `Consumes`: `multipart/form-data`
- `Produces`: `application/json`

| Part name         | Required | Type                  | Description                                                        |
|-------------------|----------|------------------------|--------------------------------------------------------------------|
| `data`            | yes      | JSON string            | Body of `SoundTrackDtos.CreateRequest` (see §11)                   |
| `ckbCoverImage`   | no       | file (image)           | Sorani cover image                                                 |
| `kmrCoverImage`   | no       | file (image)           | Kurmanji cover image                                               |
| `hoverCoverImage` | no       | file (image)           | Hover-state cover image                                            |
| `audioFiles`      | no       | file[]                 | Audio binaries; index-matched to `data.files[i]`                   |
| `brochureFiles`   | no       | file[]                 | Flat list consumed across all files in order                       |
| `attachmentFiles` | no       | file[]                 | Attachment binaries; index-matched to `data.attachments[i]`        |

Response: `ApiResponse<Response>` — HTTP `201 CREATED`, message `"SoundTrack created successfully"`.

```bash
curl -X POST "https://api.example.com/api/v1/sound-tracks" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
    "soundType": "هۆنراوە",
    "trackState": "SINGLE",
    "albumOfMemories": false,
    "topicId": 5,
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title": "هاوار",
      "description": "<p>هۆنراوەی هاوار، نیشانەی خەباتی کوردە.</p>"
    },
    "kmrContent": {
      "title": "Hawar",
      "description": "<p>Helbesta Hawar, nîşana têkoşîna Kurd.</p>"
    },
    "locations": ["سلێمانی"],
    "reader": "هەژار موکریانی",
    "directors": ["شیرکۆ بێکەس"],
    "terms": "سۆرانی",
    "thisProjectOfInstitute": true,
    "tags": { "ckb": ["کلاسیک"], "kmr": ["Klasîk"] },
    "keywords": { "ckb": ["شیعر","هۆنراوە"], "kmr": ["Helbest"] },
    "files": [
      {
        "title": "هاوار - بەشی یەکەم",
        "fileType": "MP3",
        "publishmentYear": 1985,
        "bitRate": "320 kbps",
        "sampleRate": "44100 Hz",
        "audioChannel": "STEREO",
        "form": "کێشدار",
        "genre": "Folk",
        "recordingVenue": "ستۆدیۆی سلێمانی",
        "brochures": [
          { "caption": "بەرگی پێشەوە" }
        ]
      }
    ],
    "attachments": [
      { "title": "دەقی شیعر", "attachmentType": "PDF", "mimeType": "application/pdf" }
    ]
  };type=application/json' \
  -F "ckbCoverImage=@/path/cover_ckb.jpg" \
  -F "kmrCoverImage=@/path/cover_kmr.jpg" \
  -F "audioFiles=@/path/hawar.mp3" \
  -F "brochureFiles=@/path/front_cover.jpg" \
  -F "attachmentFiles=@/path/lyrics.pdf"
```

```json
{
  "success": true,
  "message": "SoundTrack created successfully",
  "data": {
    "id": 142,
    "ckbCoverUrl": "https://cdn.example.com/uploads/cov_ckb_xyz.jpg",
    "kmrCoverUrl": "https://cdn.example.com/uploads/cov_kmr_xyz.jpg",
    "hoverCoverUrl": null,
    "soundType": "هۆنراوە",
    "trackState": "SINGLE",
    "albumOfMemories": false,
    "topicId": 5,
    "topicNameCkb": "ئەدەبی فۆلکلۆری",
    "topicNameKmr": "Wêjeya Gelêrî",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "هاوار", "description": "<p>هۆنراوەی هاوار...</p>" },
    "kmrContent": { "title": "Hawar", "description": "<p>Helbesta Hawar...</p>" },
    "files": [
      {
        "id": 311,
        "fileUrl": "https://cdn.example.com/uploads/hawar_abc.mp3",
        "title": "هاوار - بەشی یەکەم",
        "fileType": "MP3",
        "sizeBytes": 5_120_000,
        "durationSeconds": 0,
        "durationMinutes": 0.0,
        "bitRate": "320 kbps",
        "sampleRate": "44100 Hz",
        "audioChannel": "STEREO",
        "form": "کێشدار",
        "genre": "Folk",
        "recordingVenue": "ستۆدیۆی سلێمانی",
        "brochures": [
          {
            "id": 901,
            "imageUrl": "https://cdn.example.com/uploads/front_cover_xyz.jpg",
            "caption": "بەرگی پێشەوە",
            "brochureOrder": 0
          }
        ]
      }
    ],
    "totalDurationSeconds": 0,
    "totalSizeBytes": 5_120_000,
    "attachments": [
      {
        "id": 511,
        "fileUrl": "https://cdn.example.com/uploads/lyrics_xyz.pdf",
        "title": "دەقی شیعر",
        "attachmentType": "PDF",
        "sizeBytes": 87340,
        "mimeType": "application/pdf",
        "attachmentOrder": 0
      }
    ],
    "createdAt": "2026-05-31T10:14:02",
    "updatedAt": "2026-05-31T10:14:02"
  }
}
```

### 10.2 `PUT /api/v1/sound-tracks/{id}`

Updates an existing sound track.

- `Consumes`: `multipart/form-data`
- `Produces`: `application/json`

Rules (from controller JavaDoc and `SoundTrackService.update`):

- Only fields present in `data` JSON are changed; absent fields are left untouched.
- To **remove the current topic**: set `"clearTopic": true` in `data`.
- To **replace ALL files**: send a `files` array in `data` plus the matching `audioFiles` parts.
- To **leave files unchanged**: omit both `data.files` and `audioFiles` entirely.
- Same rule applies to attachments: omit both `data.attachments` and `attachmentFiles` to leave them as-is; sending either triggers a full replace.
- `reader`: pass `null` to leave unchanged, pass `""` (empty string) to clear.
- Uploaded cover binary wins over any existing URL.

Path & part table:

| Part / Param      | Required | Type        | Description                                            |
|-------------------|----------|-------------|--------------------------------------------------------|
| `{id}` (path)     | yes      | Long        | Sound track id                                         |
| `data`            | yes      | JSON string | `SoundTrackDtos.UpdateRequest`                         |
| `ckbCoverImage`   | no       | file        | Replaces CKB cover when sent                           |
| `kmrCoverImage`   | no       | file        | Replaces KMR cover when sent                           |
| `hoverCoverImage` | no       | file        | Replaces hover cover when sent                         |
| `audioFiles`      | no       | file[]      | Index-matched to `data.files[i]`                       |
| `brochureFiles`   | no       | file[]      | Flat list across all files                             |
| `attachmentFiles` | no       | file[]      | Index-matched to `data.attachments[i]`                 |

Response: `ApiResponse<Response>` — message `"SoundTrack updated successfully"`. 404 with `SOUND_NOT_FOUND` if id missing.

```bash
curl -X PUT "https://api.example.com/api/v1/sound-tracks/142" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
    "soundType": "هۆنراوە",
    "albumOfMemories": true,
    "clearTopic": false,
    "reader": "",
    "tags": { "ckb": ["کلاسیک","فۆلکلۆر"] }
  };type=application/json' \
  -F "ckbCoverImage=@/path/new_cover_ckb.jpg"
```

```json
{
  "success": true,
  "message": "SoundTrack updated successfully",
  "data": {
    "id": 142,
    "ckbCoverUrl": "https://cdn.example.com/uploads/new_cover_ckb_abc.jpg",
    "albumOfMemories": true,
    "reader": null,
    "tags": { "ckb": ["کلاسیک", "فۆلکلۆر"], "kmr": ["Klasîk"] }
  }
}
```

### 10.3 `DELETE /api/v1/sound-tracks/{id}`

Hard-deletes the track and cascades to files / brochures / attachments. Writes a `DELETED` row to `sound_track_logs` first.

| Path Param | Type | Required |
|------------|------|----------|
| `id`       | Long | yes      |

Response: `ApiResponse<Void>` — message `"SoundTrack deleted successfully"` (`data: null`). 404 with `SOUND_NOT_FOUND` if id missing.

```bash
curl -X DELETE "https://api.example.com/api/v1/sound-tracks/142" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "success": true,
  "message": "SoundTrack deleted successfully",
  "data": null
}
```

---

## 11 · DTO Reference

All DTOs are nested in `SoundTrackDtos` (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`).

### 11.1 `SoundTrackDtos.LanguageContentDto`

| Field         | Type     | Validation        |
|---------------|----------|-------------------|
| `title`       | `String` | `@Size(max = 200)` |
| `description` | `String` | `@Size(max = 4000)` |

### 11.2 `SoundTrackDtos.BilingualSet`

| Field | Type          | Validation |
|-------|---------------|------------|
| `ckb` | `Set<String>` | —          |
| `kmr` | `Set<String>` | —          |

### 11.3 `SoundTrackDtos.InlineTopicRequest`

`@JsonInclude(NON_NULL)`.

| Field     | Type     | Validation |
|-----------|----------|------------|
| `nameCkb` | `String` | —          |
| `nameKmr` | `String` | —          |

At least one of `nameCkb` / `nameKmr` must be non-blank (enforced by service — `error.validation`).

### 11.4 `SoundTrackDtos.TopicView`

`@JsonInclude(NON_NULL)`.

| Field       | Type            |
|-------------|-----------------|
| `id`        | `Long`          |
| `nameCkb`   | `String`        |
| `nameKmr`   | `String`        |
| `createdAt` | `LocalDateTime` |

### 11.5 `SoundTrackDtos.BrochureRequest`

| Field      | Type     | Validation         |
|------------|----------|--------------------|
| `imageUrl` | `String` | `@Size(max = 1200)` (no `@NotBlank` — binary may supply via `brochureFiles`) |
| `caption`  | `String` | `@Size(max = 300)` |

### 11.6 `SoundTrackDtos.BrochureResponse`

| Field           | Type      |
|-----------------|-----------|
| `id`            | `Long`    |
| `imageUrl`      | `String`  |
| `caption`       | `String`  |
| `brochureOrder` | `Integer` |

### 11.7 `SoundTrackDtos.AttachmentRequest`

| Field            | Type             | Validation                                                       |
|------------------|------------------|------------------------------------------------------------------|
| `fileUrl`        | `String`         | `@Size(max = 1200)` (no `@NotBlank` — binary may supply via parts) |
| `title`          | `String`         | `@Size(max = 300)`                                               |
| `attachmentType` | `AttachmentType` | — (defaults to `OTHER` in service when null)                     |
| `sizeBytes`      | `long`           | —                                                                |
| `mimeType`       | `String`         | `@Size(max = 100)`                                               |

### 11.8 `SoundTrackDtos.AttachmentResponse`

| Field             | Type             |
|-------------------|------------------|
| `id`              | `Long`           |
| `fileUrl`         | `String`         |
| `title`           | `String`         |
| `attachmentType`  | `AttachmentType` |
| `sizeBytes`       | `long`           |
| `mimeType`        | `String`         |
| `attachmentOrder` | `Integer`        |

### 11.9 `SoundTrackDtos.FileCreateRequest`

| Field             | Type                     | Validation                                                                |
|-------------------|--------------------------|---------------------------------------------------------------------------|
| `fileUrl`         | `String`                 | `@Size(max = 1200)`                                                        |
| `externalUrl`     | `String`                 | —                                                                         |
| `embedUrl`        | `String`                 | —                                                                         |
| `title`           | `String`                 | `@Size(max = 300)`                                                         |
| `fileType`        | `FileType`               | `@NotNull(message = "fileType is required")`                               |
| `publishmentYear` | `Integer`                | —                                                                         |
| `sizeBytes`       | `long`                   | —                                                                         |
| `durationSeconds` | `long`                   | —                                                                         |
| `bitRate`         | `String`                 | `@Size(max = 50)`                                                          |
| `sampleRate`      | `String`                 | `@Size(max = 50)`                                                          |
| `audioChannel`    | `AudioChannel`           | —                                                                         |
| `form`            | `String`                 | `@Size(max = 150)`                                                         |
| `genre`           | `String`                 | `@Size(max = 100)`                                                         |
| `recordingVenue`  | `String`                 | `@Size(max = 500)`                                                         |
| `sortOrder`       | `Integer`                | —                                                                         |
| `brochures`       | `List<BrochureRequest>`  | —                                                                         |

Service-enforced rule: at least one of `fileUrl`, `externalUrl`, `embedUrl` must resolve, else `soundTrack.file.source.required` (400).

### 11.10 `SoundTrackDtos.FileResponse`

| Field             | Type                       |
|-------------------|----------------------------|
| `id`              | `Long`                     |
| `fileUrl`         | `String`                   |
| `externalUrl`     | `String`                   |
| `embedUrl`        | `String`                   |
| `title`           | `String`                   |
| `fileType`        | `FileType`                 |
| `publishmentYear` | `Integer`                  |
| `sizeBytes`       | `long`                     |
| `durationSeconds` | `long`                     |
| `durationMinutes` | `double`                   |
| `bitRate`         | `String`                   |
| `sampleRate`      | `String`                   |
| `audioChannel`    | `AudioChannel`             |
| `form`            | `String`                   |
| `genre`           | `String`                   |
| `recordingVenue`  | `String`                   |
| `brochures`       | `List<BrochureResponse>`   |

### 11.11 `SoundTrackDtos.CreateRequest`

| Field                    | Type                          | Validation                                                                                          |
|--------------------------|-------------------------------|-----------------------------------------------------------------------------------------------------|
| `soundType`              | `String`                      | `@NotBlank(message = "soundType is required")` + `@Size(max = 100)`                                  |
| `trackState`             | `TrackState`                  | `@NotNull(message = "trackState is required")`                                                       |
| `albumOfMemories`        | `Boolean`                     | —                                                                                                   |
| `topicId`                | `Long`                        | —                                                                                                   |
| `newTopic`               | `InlineTopicRequest`          | —                                                                                                   |
| `contentLanguages`       | `Set<Language>`               | `@NotNull` + `@NotEmpty(message = "At least one content language is required")`                      |
| `ckbContent`             | `LanguageContentDto`          | —                                                                                                   |
| `kmrContent`             | `LanguageContentDto`          | —                                                                                                   |
| `locations`              | `Set<String>`                 | —                                                                                                   |
| `reader`                 | `String`                      | `@Size(max = 255)`                                                                                  |
| `directors`              | `Set<String>`                 | —                                                                                                   |
| `terms`                  | `String`                      | `@Size(max = 200)`                                                                                  |
| `thisProjectOfInstitute` | `boolean`                     | `@JsonProperty("thisProjectOfInstitute")`                                                            |
| `tags`                   | `BilingualSet`                | —                                                                                                   |
| `keywords`               | `BilingualSet`                | —                                                                                                   |
| `files`                  | `List<FileCreateRequest>`     | —                                                                                                   |
| `albumName`              | `String`                      | `@Size(max = 300)`                                                                                  |
| `publishmentYear`        | `Integer`                     | —                                                                                                   |
| `cdNumber`               | `Integer`                     | —                                                                                                   |
| `totalTracks`            | `Integer`                     | —                                                                                                   |
| `attachments`            | `List<AttachmentRequest>`     | — (available for both SINGLE and MULTI)                                                              |

### 11.12 `SoundTrackDtos.UpdateRequest`

All fields are optional (partial update). Sending `null` leaves the existing value unchanged unless otherwise noted.

| Field                    | Type                          | Validation                                                                                |
|--------------------------|-------------------------------|-------------------------------------------------------------------------------------------|
| `soundType`              | `String`                      | `@Size(max = 100)`                                                                        |
| `trackState`             | `TrackState`                  | —                                                                                         |
| `albumOfMemories`        | `Boolean`                     | —                                                                                         |
| `topicId`                | `Long`                        | —                                                                                         |
| `newTopic`               | `InlineTopicRequest`          | —                                                                                         |
| `clearTopic`             | `boolean`                     | When `true`, disassociates current topic (overrides `topicId`/`newTopic`)                  |
| `contentLanguages`       | `Set<Language>`               | —                                                                                         |
| `ckbContent`             | `LanguageContentDto`          | —                                                                                         |
| `kmrContent`             | `LanguageContentDto`          | —                                                                                         |
| `locations`              | `Set<String>`                 | —                                                                                         |
| `reader`                 | `String`                      | `@Size(max = 255)` — `null` to leave unchanged, `""` to clear                              |
| `directors`              | `Set<String>`                 | —                                                                                         |
| `terms`                  | `String`                      | `@Size(max = 200)`                                                                        |
| `thisProjectOfInstitute` | `Boolean`                     | `@JsonProperty("thisProjectOfInstitute")`                                                  |
| `tags`                   | `BilingualSet`                | Per-side replace; null side leaves that side unchanged                                     |
| `keywords`               | `BilingualSet`                | Per-side replace; null side leaves that side unchanged                                     |
| `files`                  | `List<FileCreateRequest>`     | Sending it triggers full file replacement (see §10.2)                                      |
| `albumName`              | `String`                      | `@Size(max = 300)`                                                                        |
| `publishmentYear`        | `Integer`                     | —                                                                                         |
| `cdNumber`               | `Integer`                     | —                                                                                         |
| `totalTracks`            | `Integer`                     | —                                                                                         |
| `attachments`            | `List<AttachmentRequest>`     | Sending it triggers full attachment replacement                                            |

### 11.13 `SoundTrackDtos.Response`

| Field                    | Type                              |
|--------------------------|-----------------------------------|
| `id`                     | `Long`                            |
| `ckbCoverUrl`            | `String`                          |
| `kmrCoverUrl`            | `String`                          |
| `hoverCoverUrl`          | `String`                          |
| `soundType`              | `String`                          |
| `trackState`             | `TrackState`                      |
| `albumOfMemories`        | `Boolean`                         |
| `topicId`                | `Long`                            |
| `topicNameCkb`           | `String`                          |
| `topicNameKmr`           | `String`                          |
| `contentLanguages`       | `Set<Language>`                   |
| `ckbContent`             | `LanguageContentDto`              |
| `kmrContent`             | `LanguageContentDto`              |
| `locations`              | `Set<String>`                     |
| `reader`                 | `String`                          |
| `directors`              | `Set<String>`                     |
| `terms`                  | `String`                          |
| `thisProjectOfInstitute` | `Boolean` (`@JsonProperty(...)` ) |
| `tags`                   | `BilingualSet`                    |
| `keywords`               | `BilingualSet`                    |
| `files`                  | `List<FileResponse>`              |
| `totalDurationSeconds`   | `long` (sum across `files`)       |
| `totalSizeBytes`         | `long` (sum across `files`)       |
| `albumName`              | `String`                          |
| `publishmentYear`        | `Integer`                         |
| `cdNumber`               | `Integer`                         |
| `totalTracks`            | `Integer`                         |
| `attachments`            | `List<AttachmentResponse>`        |
| `createdAt`              | `LocalDateTime`                   |
| `updatedAt`              | `LocalDateTime`                   |

---

## 12 · Multipart Layout

Form-data parts and how they bind to the `data` JSON DTO. All file parts are optional; `data` is always required.

| Form-data part     | DTO mapping                                  | Matching rule                                                                                                 |
|--------------------|----------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `data`             | `CreateRequest` / `UpdateRequest` (JSON)     | Required; parsed by Jackson `ObjectMapper.readValue`                                                          |
| `ckbCoverImage`    | `SoundTrack.ckbCoverUrl`                     | Uploaded to S3 → URL stored. Binary wins over any DTO-supplied URL.                                          |
| `kmrCoverImage`    | `SoundTrack.kmrCoverUrl`                     | Same as above for KMR.                                                                                       |
| `hoverCoverImage`  | `SoundTrack.hoverCoverUrl`                   | Same as above for hover state.                                                                               |
| `audioFiles[i]`    | `data.files[i]` (`FileCreateRequest`)        | Index-matched. Uploaded binary wins over `data.files[i].fileUrl`. Service iterates `max(dtoCount, audioCount)`. If no binary, `fileUrl` is used. |
| `brochureFiles`    | `data.files[*].brochures[*]`                 | Flat list consumed globally across all files in declared order (per-file order tracked by service via running index). Binary wins over `imageUrl`. |
| `attachmentFiles[i]` | `data.attachments[i]` (`AttachmentRequest`) | Index-matched. Uploaded binary wins over `data.attachments[i].fileUrl`. Skipped if both URL and binary resolve to blank. |

Notes:

- The controller only forwards parts to the service; the service does all the matching logic (`SoundTrackService.buildAndAttachFiles`, `buildAndAttachBrochures`, `buildAndAttachAttachments`).
- A file entry with no `fileUrl`, no `externalUrl`, no `embedUrl`, and no `audioFiles[i]` binary triggers `soundTrack.file.source.required` (400).
- An attachment entry whose resolved `fileUrl` is blank (no binary + no DTO URL) is silently skipped.
- A brochure entry whose resolved `imageUrl` is blank is silently skipped.

---

## 13 · Response Envelope

Every endpoint returns `ResponseEntity<ApiResponse<T>>`.

```json
{
  "success": true,
  "message": "human-readable status message",
  "data": { /* T */ }
}
```

For paginated endpoints, `T = Page<Response>` and `data` follows Spring Data's standard `Page` shape:

```json
{
  "content": [ /* Response[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": false, "unsorted": true, "empty": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 124,
  "totalPages": 7,
  "last": false,
  "first": true,
  "number": 0,
  "size": 20,
  "numberOfElements": 20,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "empty": false
}
```

For `DELETE`, `data` is `null`.

---

## 14 · Error Responses

The module throws domain errors via the `Errors` helper. Bilingual `ApiErrorResponse` envelope:

```json
{
  "success": false,
  "code": "SOUND_NOT_FOUND",
  "messageCkb": "...",
  "messageKmr": "...",
  "messageEn": "...",
  "details": { "field": "..." },
  "status": 404,
  "timestamp": "2026-05-31T10:14:02"
}
```

### Codes used in this module

| Code                       | HTTP | Where it fires                                                                                       |
|----------------------------|------|------------------------------------------------------------------------------------------------------|
| `SOUND_NOT_FOUND`          | 404  | `getById` / `update` / `delete` when id does not exist (`Errors.soundNotFound(id)`)                  |
| `SOUND_VALIDATION`         | 400  | `validateCreate`, `by-state` missing state, `by-sound-type` blank, `by-topic` null id, file source missing, topic mismatch, languages missing |
| `SOUND_CONFLICT`           | 409  | Domain conflict (e.g. duplicate constraint) — surfaced via `Errors`                                  |
| `SOUND_MEDIA_INVALID`      | 400  | Invalid media payload (e.g. unsupported mime/type) — surfaced via `Errors`                           |
| `SOUND_STORAGE_FAILED`     | 500  | S3 upload failure during create/update (`Errors.soundStorageFailed`, key `sound.media_upload_failed`) |
| `PAYLOAD_TOO_LARGE`        | 413  | Multipart limit exceeded by infrastructure                                                           |
| `VALIDATION_ERROR`         | 400  | Bean-validation failures on DTO fields (`@NotBlank`, `@NotNull`, `@Size`, `@NotEmpty`)               |
| `UNAUTHORIZED`             | 401  | Missing/invalid auth on a write endpoint                                                             |
| `FORBIDDEN`                | 403  | Role check failure (not applicable here in practice — see §07 caveat)                                |

### Selected controller-thrown validation messages

| Source                                | Key                                | Field             | Trigger                                       |
|---------------------------------------|------------------------------------|-------------------|-----------------------------------------------|
| `getByState`                          | `soundTrack.state.required`        | `state`           | Missing/null `state` param                    |
| `getBySoundType`                      | `soundTrack.soundType.required`    | `soundType`       | Blank `soundType` param                       |
| `getByTopic`                          | `error.validation`                 | `topicId`         | Null `topicId`                                |
| `searchByTag`                         | `tag.required`                     | `tag`             | Blank `tag` (HTTP 400 via `Errors.badRequest`)|
| `searchByKeyword`                     | `keyword.required`                 | `keyword`         | Blank `keyword`                               |
| `globalSearch`                        | `keyword.required`                 | `q`               | Blank `q`                                     |
| Service `validateCreate`              | `error.validation`                 | `soundType`       | `جۆری دەنگ پێویستە`                            |
| Service `validateCreate`              | `error.validation`                 | `trackState`      | `دۆخی تراک پێویستە`                            |
| Service `validateCreate`              | `soundTrack.languages.required`    | `contentLanguages`| No CKB/KMR selected                           |
| Service file builder                  | `soundTrack.file.source.required`  | `index`           | File entry with no URL and no binary          |
| Service topic resolver                | `topic.not_found`                  | `id`              | Topic id does not exist                       |
| Service topic resolver                | `topic.type.mismatch`              | —                 | Topic's `entityType` is not `"SOUND"`          |

Example validation error response:

```json
{
  "success": false,
  "code": "SOUND_VALIDATION",
  "messageCkb": "زانیاری دروست نییە.",
  "messageKmr": "Daneya nederbasdar.",
  "details": { "field": "soundType", "message": "جۆری دەنگ پێویستە" },
  "status": 400,
  "timestamp": "2026-05-31T10:14:02"
}
```

---

## 15 · Notes

### SINGLE vs MULTI track behavior

- `TrackState.SINGLE` — a single audio recording. Multi-album fields (`albumName`, `cdNumber`, `totalTracks`) are typically left null. The track may still contain multiple `files` and `attachments`.
- `TrackState.MULTI` — a multi-track release (album / CD / compilation). `publishmentYear`, `albumName`, `cdNumber`, `totalTracks` describe the album-level metadata; each `SoundTrackFile` also has its own per-file `publishmentYear`.
- `SoundTrack.isMulti()` and `SoundTrack.isMultiAlbumOfMemories()` are domain helpers; the latter returns `true` only when `trackState == MULTI && albumOfMemories == true`.
- Attachments are available for BOTH `SINGLE` and `MULTI` (confirmed by `buildAndAttachAttachments` in the service and the JavaDoc on `CreateRequest.attachments`).

### Topics

- Stored in shared `publishment_topics` table with `entityType` discriminator. The Sound module uses `entityType = "SOUND"` exclusively (constant `TOPIC_ENTITY_TYPE` in `SoundTrackService`).
- `/topics` endpoint queries `PublishmentTopicRepository.findByEntityType("SOUND")` and returns a simplified `{ id, nameCkb, nameKmr }` projection (any null name is coerced to `""`).
- `CreateRequest`/`UpdateRequest` accept either `topicId` (existing) or `newTopic` (`InlineTopicRequest`) — the service creates a new SOUND topic inline when `newTopic` is provided.
- Topic type guard: passing a `topicId` whose row has a different `entityType` throws `topic.type.mismatch`.

### Album of Memories

- Backed by the boolean column `is_album_of_memories` on `sound_tracks` (default `false`), indexed by `idx_soundtrack_album`.
- A track qualifies for the `/album-of-memories` listing simply when `albumOfMemories == true`. There is no track-state restriction at the entity / endpoint level — both SINGLE and MULTI rows with the flag enabled are returned by `soundTrackRepository.findIdsAlbumOfMemories(...)`.
- The domain helper `isMultiAlbumOfMemories()` exists for code paths that want the stricter combination `MULTI + albumOfMemories`, but the public endpoint does NOT apply that constraint.

### Caching

- Read methods are annotated `@Cacheable(value = "soundTracks", key = ...)` with cache keys built from query parameters (lower-cased for case-insensitive search).
- Writes (`create`, `update`, `delete`) all use `@CacheEvict(value = "soundTracks", allEntries = true)` — any mutation invalidates the entire `soundTracks` cache.

### Audit log

- `SoundTrackLog` (table `sound_track_logs`) records `CREATED`, `UPDATED`, `DELETED` actions, stores a snapshot title and ref id so logs survive a hard delete, and is written best-effort (failures are swallowed and logged at WARN).
