# Videos Module

> Bilingual (CKB / KMR) videos with multipart uploads, topics CRUD inside the module, multi-axis search. Public reads, authenticated writes.

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — Video](#02--data-model--video)
- [03 · Data Model — VideoContent](#03--data-model--videocontent)
- [04 · Data Model — VideoClipItem](#04--data-model--videoclipitem)
- [05 · Data Model — VideoType](#05--data-model--videotype)
- [06 · Data Model — VideoLog](#06--data-model--videolog)
- [07 · Authentication & Roles](#07--authentication--roles)
- [08 · Public API](#08--public-api)
- [09 · Internal API](#09--internal-api)
- [10 · DTO Reference](#10--dto-reference)
- [11 · Multipart Layout](#11--multipart-layout)
- [12 · Response Shape](#12--response-shape)
- [13 · Error Responses](#13--error-responses)
- [14 · Notes](#14--notes)

---

## 01 · Module Overview

The Videos module is a publishment vertical that manages two related sub-types of video content:

- **FILM** — a single traditional film or documentary with one source (hosted file, external watch page, or embed URL).
- **VIDEO_CLIP** — a collection of short clips, each stored as a `VideoClipItem`. May optionally be flagged as an "Album of Memories" (memorial / retrospective).

Everything is fully bilingual: titles, descriptions, locations, directors, producers, tags, and keywords are all stored in two parallel languages — CKB (Sorani / کوردیی ناوەندی) and KMR (Kurmanji / کوردیی باکوور). Covers are also bilingual: a CKB cover, a KMR cover, and a shared hover cover.

| Aspect | Value |
| --- | --- |
| Base path | `/api/v1/videos` |
| Controller class | `VideoController` |
| Service class | `VideoService` |
| Write content-type | `multipart/form-data` only |
| Read content-type | `application/json` |
| Response envelope | None — raw DTO / `Page<VideoDTO>` |
| Topics CRUD | Co-located at `/api/v1/videos/topics` |
| Default sort | `createdAt DESC` |
| Page size clamp | `1 ≤ size ≤ 100` (default `10`) |
| Page number clamp | `page ≥ 0` (default `0`) |

### Endpoint summary

| # | Method | Path | Purpose | Auth |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/v1/videos` | Create a video (multipart) | Authenticated |
| 2 | `GET` | `/api/v1/videos/topics` | List all VIDEO topics | Public |
| 3 | `POST` | `/api/v1/videos/topics` | Create a VIDEO topic | Authenticated |
| 4 | `DELETE` | `/api/v1/videos/topics/{topicId}` | Delete a VIDEO topic | Authenticated |
| 5 | `GET` | `/api/v1/videos` | List videos (paged) | Public |
| 6 | `GET` | `/api/v1/videos/{id}` | Get one video by ID | Public |
| 7 | `GET` | `/api/v1/videos/search/tag` | Search by tag (paged) | Public |
| 8 | `GET` | `/api/v1/videos/search/keyword` | Search by keyword (paged) | Public |
| 9 | `PUT` | `/api/v1/videos/{id}` | Update a video (multipart) | Authenticated |
| 10 | `DELETE` | `/api/v1/videos/{id}` | Delete a video | Authenticated |

---

## 02 · Data Model — Video

JPA entity: `ak.dev.khi_backend.khi_app.model.publishment.video.Video`
Table: `videos`

### Table indexes

| Index name | Column |
| --- | --- |
| `idx_video_type` | `video_type` |
| `idx_video_album` | `is_album_of_memories` |
| `idx_video_pub_date` | `publishment_date` |
| `idx_video_topic` | `topic_id` |
| `idx_video_title_ckb` | `title_ckb` |
| `idx_video_title_kmr` | `title_kmr` |

### Field reference

| Java field | Column | Type / JPA | Notes |
| --- | --- | --- | --- |
| `id` | `id` | `Long`, `@Id @GeneratedValue(IDENTITY)` | Primary key |
| `ckbCoverUrl` | `ckb_cover_url` | `@Column(length = 1000)` | CKB cover image URL |
| `kmrCoverUrl` | `kmr_cover_url` | `@Column(length = 1000)` | KMR cover image URL |
| `hoverCoverUrl` | `hover_cover_url` | `@Column(length = 1000)` | Hover/teaser image URL |
| `videoType` | `video_type` | `@Enumerated(STRING) @Column(nullable = false, length = 20)` | `FILM` or `VIDEO_CLIP` |
| `albumOfMemories` | `is_album_of_memories` | `@Column(nullable = false)`, default `false` | Only meaningful for `VIDEO_CLIP`; always `false` for `FILM` |
| `topic` | `topic_id` | `@ManyToOne(LAZY) @JoinColumn`, nullable | FK → `PublishmentTopic` where `entityType = "VIDEO"` |
| `ckbContent` | embedded `*_ckb` cols | `@Embedded VideoContent` | Bilingual content block (CKB) |
| `kmrContent` | embedded `*_kmr` cols | `@Embedded VideoContent` | Bilingual content block (KMR) |
| `sourceUrl` | `source_url` | `@Column(columnDefinition = "TEXT")` | FILM only — hosted file (S3/CDN) |
| `sourceExternalUrl` | `source_external_url` | `@Column(columnDefinition = "TEXT")` | FILM only — external watch URL |
| `sourceEmbedUrl` | `source_embed_url` | `@Column(columnDefinition = "TEXT")` | FILM only — iframe-embeddable URL |
| `videoClipItems` | child table | `@OneToMany(mappedBy="video", cascade=ALL, orphanRemoval=true, LAZY)` `@OrderBy("clipNumber ASC")` | VIDEO_CLIP only |
| `fileFormat` | `file_format` | `@Column(length = 20)` | e.g. `mp4` |
| `durationSeconds` | `duration_seconds` | `Integer` | Total duration |
| `publishmentDate` | `publishment_date` | `LocalDate` | When the video was published |
| `resolution` | `resolution` | `@Column(length = 20)` | e.g. `1080p` |
| `fileSizeMb` | `file_size_mb` | `Double` | Size in megabytes |
| `contentLanguages` | `video_content_languages` | `@ElementCollection(EAGER) @Enumerated(STRING) @BatchSize(25) @Column(nullable=false, length=10)` | `Set<Language>` of available content languages |
| `tagsCkb` | `video_tags_ckb` | `@ElementCollection(EAGER) @BatchSize(25) @Column(nullable=false, length=100)` | `Set<String>` |
| `tagsKmr` | `video_tags_kmr` | `@ElementCollection(EAGER) @BatchSize(25) @Column(nullable=false, length=100)` | `Set<String>` |
| `keywordsCkb` | `video_keywords_ckb` | `@ElementCollection(EAGER) @BatchSize(25) @Column(nullable=false, length=150)` | `Set<String>` |
| `keywordsKmr` | `video_keywords_kmr` | `@ElementCollection(EAGER) @BatchSize(25) @Column(nullable=false, length=150)` | `Set<String>` |
| `createdAt` | `created_at` | `@Column(nullable = false, updatable = false)` | Set in `@PrePersist` |
| `updatedAt` | `updated_at` | `@Column(nullable = false)` | Set in `@PrePersist` & `@PreUpdate` |

### Lifecycle hooks

```text
@PrePersist  → createdAt = now; updatedAt = now
@PreUpdate   → updatedAt = now
```

### Helper methods

| Method | Purpose |
| --- | --- |
| `addClipItem(VideoClipItem item)` | Add a clip and back-link `item.video = this` |
| `removeClipItem(VideoClipItem item)` | Remove a clip and null the back-link |
| `isVideoClipAlbumOfMemories()` | `true` only when `videoType == VIDEO_CLIP && albumOfMemories` |

### Performance note

Every `@ElementCollection` (languages, tags, keywords) is annotated with `@BatchSize(size = 25)`. Loading a page of 20 videos costs 5 collection queries (one per collection, batched with `WHERE video_id IN (...)`) instead of `20 × 5 = 100` separate queries.

---

## 03 · Data Model — VideoContent

JPA: `@Embeddable` — embedded twice into `Video` (once for CKB, once for KMR) via `@AttributeOverrides`.

| Field | Column (CKB) | Column (KMR) | Definition |
| --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | `@Column(length = 300)` |
| `description` | `description_ckb` | `description_kmr` | `@Column(columnDefinition = "TEXT")` — Tiptap-processed HTML |
| `location` | `location_ckb` | `location_kmr` | `@Column(length = 250)` |
| `director` | `director_ckb` | `director_kmr` | `@Column(length = 250)` |
| `producer` | `producer_ckb` | `producer_kmr` | `@Column(length = 250)` |

The topic relation is **not** in `VideoContent`; it lives on the parent `Video` as a shared `@ManyToOne` to `PublishmentTopic`.

---

## 04 · Data Model — VideoClipItem

JPA entity: `ak.dev.khi_backend.khi_app.model.publishment.video.VideoClipItem`
Table: `video_clip_items`

### Indexes

| Index name | Column |
| --- | --- |
| `idx_clip_video_id` | `video_id` |
| `idx_clip_clip_number` | `clip_number` |

### Field reference

| Java field | Column | Type / JPA | Notes |
| --- | --- | --- | --- |
| `id` | `id` | `Long`, `@Id @GeneratedValue(IDENTITY)` | Primary key |
| `video` | `video_id` | `@ManyToOne(LAZY, optional=false) @JoinColumn(nullable=false)` | Parent video (back-reference) |
| `url` | `url` | `@Column(columnDefinition = "TEXT")` | Direct hosted file (S3/CDN) |
| `externalUrl` | `external_url` | `@Column(columnDefinition = "TEXT")` | External watch page (YouTube etc.) |
| `embedUrl` | `embed_url` | `@Column(columnDefinition = "TEXT")` | Embeddable iframe URL |
| `clipNumber` | `clip_number` | `Integer` | Ordering / sequence number |
| `durationSeconds` | `duration_seconds` | `Integer` | This clip's duration |
| `resolution` | `resolution` | `@Column(length = 20)` | e.g. `1080p`, `720p` |
| `fileFormat` | `file_format` | `@Column(length = 20)` | e.g. `mp4`, `webm` |
| `fileSizeMb` | `file_size_mb` | `Double` | Size in megabytes |
| `titleCkb` | `title_ckb` | `@Column(length = 300)` | Clip title in CKB |
| `titleKmr` | `title_kmr` | `@Column(length = 300)` | Clip title in KMR |
| `descriptionCkb` | `description_ckb` | `@Column(columnDefinition = "TEXT")` | CKB description (Tiptap-processed) |
| `descriptionKmr` | `description_kmr` | `@Column(columnDefinition = "TEXT")` | KMR description (Tiptap-processed) |

### Source rule

At least one of `url`, `externalUrl`, or `embedUrl` must be supplied per clip. The service throws `BadRequestException("video.clip.source.required", ...)` otherwise.

---

## 05 · Data Model — VideoType

Enum: `ak.dev.khi_backend.khi_app.model.publishment.video.VideoType`

| Value | Meaning |
| --- | --- |
| `FILM` | A traditional film or documentary. Has a single video source (`sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl`). `albumOfMemories` is always `false`. No clip list. |
| `VIDEO_CLIP` | A collection of short clips stored as `VideoClipItem` children. `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl` are unused. `albumOfMemories` may be `true` (memorial set) or `false`. |

Persisted as `@Enumerated(EnumType.STRING)` in `video_type` (`length = 20`, `nullable = false`).

---

## 06 · Data Model — VideoLog

JPA entity: `ak.dev.khi_backend.khi_app.model.publishment.video.VideoLog`
Table: `video_logs`

Audit log for `CREATED`, `UPDATED`, `DELETED` actions on videos. Stored as a snapshot — records survive deletion of the parent video.

### Indexes

| Index name | Column |
| --- | --- |
| `idx_vlog_video_id` | `video_id` |
| `idx_vlog_action` | `action` |
| `idx_vlog_timestamp` | `timestamp` |

### Field reference

| Java field | Column | Type / JPA | Notes |
| --- | --- | --- | --- |
| `id` | `id` | `Long`, `@Id @GeneratedValue(IDENTITY)` | Primary key |
| `videoId` | `video_id` | `Long` (nullable) | Snapshot of affected video's ID |
| `videoTitle` | `video_title` | `@Column(length = 300)` | Title snapshot at time of action |
| `action` | `action` | `@Column(nullable = false, length = 30)` | `"CREATED"`, `"UPDATED"`, `"DELETED"` |
| `details` | `details` | `@Column(columnDefinition = "TEXT")` | Human-readable detail line |
| `performedBy` | `performed_by` | `@Column(length = 150)` | Actor (future auth integration) |
| `timestamp` | `timestamp` | `@Column(nullable = false)` | Set in `@PrePersist` if null |

---

## 07 · Authentication & Roles

The `SecurityConfig` role-based POST/PUT matchers cover these path prefixes only:

```text
/api/v1/projects/**
/api/v1/news/**
/api/v1/films/**
/api/v1/image-collections/**
/api/v1/soundtracks/**
/api/v1/albums/**
/api/v1/writings/**
```

`/api/v1/videos/**` is **not** in that list. Therefore writes on the Videos module fall through to `.anyRequest().authenticated()` — **any authenticated user (any role) can perform writes**. There is no role check enforced by the security layer for this module.

`GET /api/v1/videos/**` is public (covered by the global GET rule).

### Per-endpoint matrix

| Method | Path | Auth required | Required roles |
| --- | --- | --- | --- |
| `GET` | `/api/v1/videos` | No | — (public) |
| `GET` | `/api/v1/videos/{id}` | No | — (public) |
| `GET` | `/api/v1/videos/search/tag` | No | — (public) |
| `GET` | `/api/v1/videos/search/keyword` | No | — (public) |
| `GET` | `/api/v1/videos/topics` | No | — (public) |
| `POST` | `/api/v1/videos` | Yes | Any role |
| `PUT` | `/api/v1/videos/{id}` | Yes | Any role |
| `DELETE` | `/api/v1/videos/{id}` | Yes | Any role |
| `POST` | `/api/v1/videos/topics` | Yes | Any role |
| `DELETE` | `/api/v1/videos/topics/{topicId}` | Yes | Any role |

---

## 08 · Public API

All endpoints in this section require **no authentication**.

### 8.1 · `GET /api/v1/videos` — List videos (paged)

Returns a Spring Data `Page<VideoDTO>` of all videos, sorted by `createdAt DESC`.

| Query parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `page` | `int` | `0` | Zero-based; clamped to `≥ 0` |
| `size` | `int` | `10` | Clamped to `1..100` |

**Response:** `200 OK` — `Page<VideoDTO>` (content array + paging metadata).

**curl:**

```bash
curl -X GET "https://api.example.com/api/v1/videos?page=0&size=10"
```

**Sample response body:**

```json
{
  "content": [
    {
      "id": 42,
      "ckbCoverUrl": "https://cdn.example.com/videos/42/cover-ckb.jpg",
      "kmrCoverUrl": "https://cdn.example.com/videos/42/cover-kmr.jpg",
      "hoverCoverUrl": "https://cdn.example.com/videos/42/hover.jpg",
      "videoType": "FILM",
      "albumOfMemories": false,
      "topicId": 7,
      "topicNameCkb": "مێژووی کوردستان",
      "topicNameKmr": "Dîroka Kurdistanê",
      "contentLanguages": ["CKB", "KMR"],
      "ckbContent": {
        "title": "هەڵەبجە - یادی شەهیدان",
        "description": "<p>دۆکیومێنتارییەکی مێژوویی دەربارەی هەڵەبجە</p>",
        "location": "هەڵەبجە",
        "director": "ئاراس کەریم",
        "producer": "ستۆدیۆی ڕۆژهەڵات"
      },
      "kmrContent": {
        "title": "Helebce - Bîranîna Şehîdan",
        "description": "<p>Belgefîlmek dîrokî li ser Helebceyê</p>",
        "location": "Helebce",
        "director": "Aras Kerîm",
        "producer": "Stûdyoya Rojhilat"
      },
      "sourceUrl": "https://cdn.example.com/videos/42/film.mp4",
      "sourceExternalUrl": null,
      "sourceEmbedUrl": null,
      "fileFormat": "mp4",
      "durationSeconds": 5400,
      "publishmentDate": "2024-03-16",
      "resolution": "1080p",
      "fileSizeMb": 1820.5,
      "tagsCkb": ["مێژوو", "هەڵەبجە"],
      "tagsKmr": ["Dîrok", "Helebce"],
      "keywordsCkb": ["شەهید", "ئەنفال"],
      "keywordsKmr": ["şehîd", "Enfal"],
      "createdAt": "2024-03-16T10:12:33",
      "updatedAt": "2024-03-16T10:12:33"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": { "sorted": true, "unsorted": false, "empty": false }
  },
  "totalElements": 87,
  "totalPages": 9,
  "last": false,
  "first": true,
  "size": 10,
  "number": 0,
  "numberOfElements": 10,
  "empty": false
}
```

---

### 8.2 · `GET /api/v1/videos/{id}` — Get one video

| Path parameter | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | Video ID |

**Response:** `200 OK` — raw `VideoDTO`.

**Errors:** `404 Not Found` (`video.not_found`) if no video exists with the given ID; `400 Bad Request` (`video.id.required`) if the ID parameter is null.

**curl:**

```bash
curl -X GET "https://api.example.com/api/v1/videos/42"
```

**Sample response body:** see §8.1 — same `VideoDTO` shape.

---

### 8.3 · `GET /api/v1/videos/search/tag` — Search by tag (paged)

Searches both `tagsCkb` and `tagsKmr` for the supplied value.

| Query parameter | Type | Default | Required |
| --- | --- | --- | --- |
| `value` | `String` | — | Yes |
| `page` | `int` | `0` | No |
| `size` | `int` | `10` | No |

**Response:** `200 OK` — `Page<VideoDTO>`.

**Errors:** `400 Bad Request` (`search.tag.required`) when `value` is blank.

**curl:**

```bash
curl -X GET "https://api.example.com/api/v1/videos/search/tag?value=%D9%85%DB%8E%DA%98%D9%88%D9%88&page=0&size=10"
```

---

### 8.4 · `GET /api/v1/videos/search/keyword` — Search by keyword (paged)

Searches both `keywordsCkb` and `keywordsKmr` for the supplied value.

| Query parameter | Type | Default | Required |
| --- | --- | --- | --- |
| `value` | `String` | — | Yes |
| `page` | `int` | `0` | No |
| `size` | `int` | `10` | No |

**Response:** `200 OK` — `Page<VideoDTO>`.

**Errors:** `400 Bad Request` (`search.keyword.required`) when `value` is blank.

**curl:**

```bash
curl -X GET "https://api.example.com/api/v1/videos/search/keyword?value=%D8%B4%DB%95%D9%87%DB%8C%D8%AF&page=0&size=10"
```

---

### 8.5 · `GET /api/v1/videos/topics` — List VIDEO topics

Returns every `PublishmentTopic` whose `entityType = "VIDEO"`.

**Response:** `200 OK` — `List<VideoDTO.TopicView>`.

**curl:**

```bash
curl -X GET "https://api.example.com/api/v1/videos/topics"
```

**Sample response body:**

```json
[
  {
    "id": 7,
    "nameCkb": "مێژووی کوردستان",
    "nameKmr": "Dîroka Kurdistanê",
    "createdAt": "2024-01-12T09:00:00"
  },
  {
    "id": 8,
    "nameCkb": "هونەری شانۆ",
    "nameKmr": "Hunera Şanoyê",
    "createdAt": "2024-02-04T11:42:18"
  }
]
```

---

## 09 · Internal API

All endpoints in this section require **a valid authenticated session** (any role). They are not role-restricted because `/api/v1/videos/**` is not covered by `SecurityConfig`'s role-based matchers.

### 9.1 · `POST /api/v1/videos` — Create a video

Multipart-only. The `data` part carries a JSON-encoded `VideoDTO` and is parsed via Jackson `ObjectMapper.readValue(dtoJson, VideoDTO.class)`.

| Property | Value |
| --- | --- |
| Content-Type | `multipart/form-data` |
| Success status | `201 Created` |
| Response body | `VideoDTO` (created) |

#### Multipart parts

| Part name | Required | Type | Purpose |
| --- | --- | --- | --- |
| `data` | Yes | `String` (JSON `VideoDTO`) | All structured fields of the video |
| `ckbCoverImage` | No | `MultipartFile` | CKB cover image; overrides `data.ckbCoverUrl` |
| `kmrCoverImage` | No | `MultipartFile` | KMR cover image; overrides `data.kmrCoverUrl` |
| `hoverImage` | No | `MultipartFile` | Hover image; overrides `data.hoverCoverUrl` |
| `videoFile` | No | `MultipartFile` | FILM only — hosted source file. Uploaded to S3; sets `sourceUrl` and clears the other two source fields |

#### Source / cover precedence

- **Cover image:** uploaded file takes precedence over the URL in `data` (`resolveCoverUrl`).
- **FILM source:** if `videoFile` is non-empty, it is uploaded to S3 and `sourceUrl` is set (other source fields cleared). Otherwise, the trio (`sourceUrl`, `sourceExternalUrl`, `sourceEmbedUrl`) from `data` is applied if any are non-blank.
- **VIDEO_CLIP:** the FILM source fields are forcibly cleared. Each clip in `videoClipItems` must supply at least one of `url`, `externalUrl`, `embedUrl`.
- **Album of Memories:** forced to `false` when `videoType == FILM`. Honored only when `videoType == VIDEO_CLIP`.

#### Topic resolution (precedence: topicId → newTopic → null)

1. If `data.topicId != null`: look up `PublishmentTopic` by ID; if found but its `entityType != "VIDEO"`, throws `topic.type.mismatch`.
2. Else if `data.newTopic != null`: requires at least one of `nameCkb` / `nameKmr`. Creates a new `PublishmentTopic` with `entityType = "VIDEO"`.
3. Else: no topic.

#### curl

```bash
curl -X POST "https://api.example.com/api/v1/videos" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
        "videoType": "FILM",
        "topicId": 7,
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "هەڵەبجە - یادی شەهیدان",
          "description": "<p>دۆکیومێنتارییەکی مێژوویی دەربارەی هەڵەبجە لە ساڵی ١٩٨٨</p>",
          "location": "هەڵەبجە",
          "director": "ئاراس کەریم",
          "producer": "ستۆدیۆی ڕۆژهەڵات"
        },
        "kmrContent": {
          "title": "Helebce - Bîranîna Şehîdan",
          "description": "<p>Belgefîlmek dîrokî li ser Helebceyê di sala 1988an de</p>",
          "location": "Helebce",
          "director": "Aras Kerîm",
          "producer": "Stûdyoya Rojhilat"
        },
        "fileFormat": "mp4",
        "durationSeconds": 5400,
        "publishmentDate": "2024-03-16",
        "resolution": "1080p",
        "fileSizeMb": 1820.5,
        "tagsCkb": ["مێژوو", "هەڵەبجە"],
        "tagsKmr": ["Dîrok", "Helebce"],
        "keywordsCkb": ["شەهید", "ئەنفال"],
        "keywordsKmr": ["şehîd", "Enfal"]
      };type=application/json' \
  -F "ckbCoverImage=@/path/to/cover-ckb.jpg;type=image/jpeg" \
  -F "kmrCoverImage=@/path/to/cover-kmr.jpg;type=image/jpeg" \
  -F "hoverImage=@/path/to/hover.jpg;type=image/jpeg" \
  -F "videoFile=@/path/to/film.mp4;type=video/mp4"
```

#### Sample `data` JSON for a VIDEO_CLIP

```json
{
  "videoType": "VIDEO_CLIP",
  "albumOfMemories": true,
  "newTopic": {
    "nameCkb": "بیرەوەرییەکانی شار",
    "nameKmr": "Bîranînên Bajêr"
  },
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "ئەلبومی بیرەوەرییەکانی هەولێر",
    "description": "<p>کۆمەڵە کلیپێکی کورت لە هەولێری کۆن</p>"
  },
  "kmrContent": {
    "title": "Albûma Bîranînên Hewlêrê",
    "description": "<p>Komikek vîdyoyên kurt ji Hewlêra kevn</p>"
  },
  "videoClipItems": [
    {
      "clipNumber": 1,
      "externalUrl": "https://youtu.be/abc123",
      "durationSeconds": 95,
      "titleCkb": "بازاڕی قەیسەری",
      "titleKmr": "Sûka Qeyserî"
    },
    {
      "clipNumber": 2,
      "url": "https://cdn.example.com/clips/clip-02.mp4",
      "durationSeconds": 110,
      "resolution": "1080p",
      "fileFormat": "mp4",
      "titleCkb": "قەڵای هەولێر",
      "titleKmr": "Kelaya Hewlêrê"
    }
  ]
}
```

#### Sample response (201 Created)

```json
{
  "id": 43,
  "videoType": "FILM",
  "albumOfMemories": false,
  "topicId": 7,
  "topicNameCkb": "مێژووی کوردستان",
  "topicNameKmr": "Dîroka Kurdistanê",
  "ckbCoverUrl": "https://cdn.example.com/videos/43/cover-ckb.jpg",
  "kmrCoverUrl": "https://cdn.example.com/videos/43/cover-kmr.jpg",
  "hoverCoverUrl": "https://cdn.example.com/videos/43/hover.jpg",
  "sourceUrl": "https://cdn.example.com/videos/43/film.mp4",
  "createdAt": "2024-05-31T12:01:09",
  "updatedAt": "2024-05-31T12:01:09"
}
```

---

### 9.2 · `PUT /api/v1/videos/{id}` — Update a video

Same multipart layout as create. Behaviours specific to update:

- Uploaded covers replace existing covers per-language independently. Each cover is only touched when its corresponding multipart file is non-empty **or** the matching URL in `data` is non-blank.
- `clearTopic = true` in `data` unsets the topic. Otherwise, if `topicId` or `newTopic` is supplied, the topic is reassigned via the same precedence rules as create.
- Toggling to `FILM`: clip items are cleared, and FILM source fields are applied (file upload overrides URL fields).
- Toggling to `VIDEO_CLIP`: FILM source fields are cleared. If `videoClipItems` is non-null, existing clips are cleared and replaced by the new list.
- `albumOfMemories` flag is honored only when `videoType == VIDEO_CLIP`; for `FILM`, it is forced back to `false`.
- Tiptap HTML in both `ckbContent.description` and `kmrContent.description` (and each clip item's bilingual description) is post-processed: inline base64 data URIs are uploaded to S3 and the HTML is rewritten in place.

| Property | Value |
| --- | --- |
| Content-Type | `multipart/form-data` |
| Success status | `200 OK` |
| Response body | `VideoDTO` (updated) |

| Path parameter | Type |
| --- | --- |
| `id` | `Long` |

**Multipart parts:** identical to §9.1 (`data`, `ckbCoverImage`, `kmrCoverImage`, `hoverImage`, `videoFile`).

**curl:**

```bash
curl -X PUT "https://api.example.com/api/v1/videos/43" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
        "videoType": "FILM",
        "clearTopic": false,
        "topicId": 9,
        "ckbContent": {
          "title": "هەڵەبجە - یادی شەهیدان (نوێکراوە)",
          "description": "<p>وەشانی نوێکراوەی دۆکیومێنتارییەکە</p>"
        },
        "kmrContent": {
          "title": "Helebce - Bîranîn (Nûjenkirî)",
          "description": "<p>Versiyona nûjenkirî ya belgefîlmê</p>"
        },
        "durationSeconds": 5520,
        "tagsCkb": ["مێژوو", "هەڵەبجە", "ئەنفال"]
      };type=application/json' \
  -F "ckbCoverImage=@/path/to/new-cover-ckb.jpg;type=image/jpeg"
```

---

### 9.3 · `DELETE /api/v1/videos/{id}` — Delete a video

Hard-deletes the video, cascading `videoClipItems` and `@ElementCollection` rows. A `DELETED` row is written to `video_logs` with a title snapshot.

| Path parameter | Type |
| --- | --- |
| `id` | `Long` |

**Response:** `204 No Content` (empty body).

**Errors:** `404` if not found, `400` if `id` is null.

**curl:**

```bash
curl -X DELETE "https://api.example.com/api/v1/videos/43" \
  -H "Authorization: Bearer $TOKEN"
```

---

### 9.4 · `POST /api/v1/videos/topics` — Create a VIDEO topic

Query-parameter create. Persists a `PublishmentTopic` with `entityType = "VIDEO"`. Requires at least one name; both languages may be supplied.

| Query parameter | Type | Required | Notes |
| --- | --- | --- | --- |
| `nameCkb` | `String` | At least one of these two | CKB topic name |
| `nameKmr` | `String` | At least one of these two | KMR topic name |

**Response:** `201 Created` — `VideoDTO.TopicView`.

**Errors:** `400 Bad Request` (`video.topic.names.required`) when both names are blank.

**curl:**

```bash
curl -X POST "https://api.example.com/api/v1/videos/topics?nameCkb=%D8%A8%DB%8C%D8%B1%DB%95%D9%88%DB%95%D8%B1%DB%8C&nameKmr=B%C3%AEran%C3%AEn" \
  -H "Authorization: Bearer $TOKEN"
```

**Sample response:**

```json
{
  "id": 12,
  "nameCkb": "بیرەوەری",
  "nameKmr": "Bîranîn",
  "createdAt": "2024-05-31T12:08:44"
}
```

---

### 9.5 · `DELETE /api/v1/videos/topics/{topicId}` — Delete a VIDEO topic

Detaches the topic from every linked video (`video.topic = null`) and then deletes the topic.

| Path parameter | Type |
| --- | --- |
| `topicId` | `Long` |

**Response:** `204 No Content`.

**Errors:** `404 Not Found` (`topic.not_found`) when no such topic exists.

**curl:**

```bash
curl -X DELETE "https://api.example.com/api/v1/videos/topics/12" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 10 · DTO Reference

The Videos module does **not** use bean-validation annotations (`@NotNull`, `@Size`, etc.) on the DTO. All validation is performed in `VideoService` and surfaced via `BadRequestException` (`video.dto.required`, `video.type.required`, `video.id.required`, `video.clip.source.required`, `search.keyword.required`, `search.tag.required`, `video.topic.names.required`).

### 10.1 · `VideoDTO`

Class: `ak.dev.khi_backend.khi_app.dto.publishment.video.VideoDTO`
Jackson: `@JsonInclude(JsonInclude.Include.NON_NULL)` — null fields are omitted on serialization.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | Identity (response only) |
| `ckbCoverUrl` | `String` | CKB cover URL (overridden by `ckbCoverImage` part) |
| `kmrCoverUrl` | `String` | KMR cover URL (overridden by `kmrCoverImage` part) |
| `hoverCoverUrl` | `String` | Hover cover URL (overridden by `hoverImage` part) |
| `videoType` | `VideoType` | **Required.** `FILM` or `VIDEO_CLIP`. Service throws `video.type.required` if null |
| `albumOfMemories` | `Boolean` | Only honored for `VIDEO_CLIP`; forced `false` for `FILM` |
| `topicId` | `Long` | Assign an existing topic by ID. Takes precedence over `newTopic` |
| `newTopic` | `InlineTopicRequest` | Create-and-attach a new topic inline. Ignored on response |
| `clearTopic` | `boolean` (`@Builder.Default = false`) | **Update only** — when `true`, unsets the topic |
| `topicNameCkb` | `String` | Response only — CKB name of the assigned topic |
| `topicNameKmr` | `String` | Response only — KMR name of the assigned topic |
| `contentLanguages` | `Set<Language>` | Available content languages |
| `ckbContent` | `VideoContentDTO` | Bilingual content block (CKB) |
| `kmrContent` | `VideoContentDTO` | Bilingual content block (KMR) |
| `sourceUrl` | `String` | **FILM only** — hosted source URL |
| `sourceExternalUrl` | `String` | **FILM only** — external watch URL |
| `sourceEmbedUrl` | `String` | **FILM only** — iframe-embeddable URL |
| `videoClipItems` | `List<VideoClipItemDTO>` | **VIDEO_CLIP only** — ordered clips |
| `fileFormat` | `String` | File format (e.g. `mp4`) |
| `durationSeconds` | `Integer` | Total duration |
| `publishmentDate` | `LocalDate` | Publication date |
| `resolution` | `String` | e.g. `1080p` |
| `fileSizeMb` | `Double` | Size in megabytes |
| `tagsCkb` | `Set<String>` | CKB tags |
| `tagsKmr` | `Set<String>` | KMR tags |
| `keywordsCkb` | `Set<String>` | CKB keywords |
| `keywordsKmr` | `Set<String>` | KMR keywords |
| `createdAt` | `LocalDateTime` | Response only |
| `updatedAt` | `LocalDateTime` | Response only |

### 10.2 · `VideoDTO.InlineTopicRequest`

| Field | Type | Notes |
| --- | --- | --- |
| `nameCkb` | `String` | Either this or `nameKmr` must be supplied |
| `nameKmr` | `String` | Either this or `nameCkb` must be supplied |

If both are blank → `BadRequestException("video.topic.names.required")`.

### 10.3 · `VideoDTO.TopicView`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | Topic ID |
| `nameCkb` | `String` | CKB topic name |
| `nameKmr` | `String` | KMR topic name |
| `createdAt` | `LocalDateTime` | When the topic was created |

### 10.4 · `VideoDTO.VideoContentDTO`

| Field | Type | Persisted as |
| --- | --- | --- |
| `title` | `String` | `length = 300` |
| `description` | `String` | `TEXT` (Tiptap HTML) |
| `location` | `String` | `length = 250` |
| `director` | `String` | `length = 250` |
| `producer` | `String` | `length = 250` |

### 10.5 · `VideoDTO.VideoClipItemDTO`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | Clip ID (response only) |
| `url` | `String` | Hosted file URL |
| `externalUrl` | `String` | External watch page |
| `embedUrl` | `String` | Embeddable iframe URL |
| `clipNumber` | `Integer` | Order within the collection |
| `durationSeconds` | `Integer` | This clip's duration |
| `resolution` | `String` | e.g. `1080p` |
| `fileFormat` | `String` | e.g. `mp4`, `webm` |
| `fileSizeMb` | `Double` | Size in megabytes |
| `titleCkb` | `String` | Clip title in CKB |
| `titleKmr` | `String` | Clip title in KMR |
| `descriptionCkb` | `String` | CKB description (Tiptap HTML) |
| `descriptionKmr` | `String` | KMR description (Tiptap HTML) |

At least one of `url` / `externalUrl` / `embedUrl` per clip is required (`video.clip.source.required`).

### 10.6 · `VideoLogDTO`

Class: `ak.dev.khi_backend.khi_app.dto.publishment.video.VideoLogDTO`
Jackson: `@JsonInclude(JsonInclude.Include.NON_NULL)`.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | Log row ID |
| `videoId` | `Long` | Snapshot of the affected video's ID |
| `videoTitle` | `String` | Snapshot of the affected video's title |
| `action` | `String` | `"CREATED"`, `"UPDATED"`, `"DELETED"` |
| `details` | `String` | Human-readable detail |
| `performedBy` | `String` | Future auth integration |
| `timestamp` | `LocalDateTime` | When the action ran |

> Note: `VideoLogDTO` is not directly exposed by `VideoController`. It is the DTO shape used by the audit-log layer (`VideoLog` entity ↔ `VideoLogDTO`).

---

## 11 · Multipart Layout

Writes (`POST /api/v1/videos`, `PUT /api/v1/videos/{id}`) accept exactly the following parts. The `data` part is parsed by Jackson into a `VideoDTO`; file parts are post-processed by `VideoService` (uploaded to S3 via `S3Service.upload`).

| Part name | Required | MIME type | Maps to | Notes |
| --- | --- | --- | --- | --- |
| `data` | Yes | `application/json` (as text part) | `VideoDTO` | JSON-encoded as a string |
| `ckbCoverImage` | No | `image/*` | `Video.ckbCoverUrl` | Upload overrides `data.ckbCoverUrl` |
| `kmrCoverImage` | No | `image/*` | `Video.kmrCoverUrl` | Upload overrides `data.kmrCoverUrl` |
| `hoverImage` | No | `image/*` | `Video.hoverCoverUrl` | Upload overrides `data.hoverCoverUrl` |
| `videoFile` | No | `video/*` | `Video.sourceUrl` (FILM) | When supplied, `sourceExternalUrl` and `sourceEmbedUrl` are cleared. Ignored for VIDEO_CLIP |

Precedence inside the service:

```text
file part > url-in-data > existing value (update only)
```

---

## 12 · Response Shape

`VideoController` returns **raw DTO objects** directly — there is no `ApiResponse<T>` envelope around the payload.

| Endpoint | Body type | Status |
| --- | --- | --- |
| `POST /api/v1/videos` | `VideoDTO` | `201 Created` |
| `GET /api/v1/videos` | `Page<VideoDTO>` | `200 OK` |
| `GET /api/v1/videos/{id}` | `VideoDTO` | `200 OK` |
| `GET /api/v1/videos/search/tag` | `Page<VideoDTO>` | `200 OK` |
| `GET /api/v1/videos/search/keyword` | `Page<VideoDTO>` | `200 OK` |
| `PUT /api/v1/videos/{id}` | `VideoDTO` | `200 OK` |
| `DELETE /api/v1/videos/{id}` | (empty) | `204 No Content` |
| `GET /api/v1/videos/topics` | `List<VideoDTO.TopicView>` | `200 OK` |
| `POST /api/v1/videos/topics` | `VideoDTO.TopicView` | `201 Created` |
| `DELETE /api/v1/videos/topics/{topicId}` | (empty) | `204 No Content` |

### `Page<VideoDTO>` shape (Spring Data default JSON)

```json
{
  "content": [ /* VideoDTO[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 87,
  "totalPages": 9,
  "last": false,
  "first": true,
  "size": 10,
  "number": 0,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "numberOfElements": 10,
  "empty": false
}
```

---

## 13 · Error Responses

The Videos module uses the project-wide bilingual error envelope (`ApiErrorResponse`). Errors are raised inside `VideoService` via `BadRequestException`, `NotFoundException` (via `Errors.videoNotFound` / `Errors.notFound`), or by the framework (validation, file size, security).

### Common error codes

| Code | HTTP | Source | Cause |
| --- | --- | --- | --- |
| `VIDEO_NOT_FOUND` (`video.not_found`) | `404` | `Errors.videoNotFound(id)` | No video matches the given ID |
| `VIDEO_VALIDATION` (`video.dto.required`, `video.type.required`, `video.id.required`) | `400` | `requireDto`, `findOrThrow` | Missing/invalid DTO or required field |
| `VIDEO_CONFLICT` | `409` | (reserved) | Conflict on uniqueness or state |
| `VIDEO_MEDIA_INVALID` (`video.clip.source.required`, `video.cover.ckb.required`, `video.cover.kmr.required`, `video.cover.hover.required`, `media.upload.failed`) | `400` | `buildAndAttachClipItems`, `uploadToS3` | Bad media payload or S3 upload failed |
| `PAYLOAD_TOO_LARGE` | `413` | Spring multipart limits | Uploaded file exceeds configured size |
| `VALIDATION_ERROR` (`search.tag.required`, `search.keyword.required`, `video.topic.names.required`, `topic.type.mismatch`, `topic.not_found`) | `400` / `404` | `normalizeRequiredSearch`, `findTopicOrThrow`, `createTopic`, `resolveOrCreateTopic` | Missing query value, mismatched topic type, or unknown topic |
| `UNAUTHORIZED` | `401` | Security layer | No or invalid authentication |
| `FORBIDDEN` | `403` | Security layer | Authenticated but not permitted (shouldn't normally arise here — Videos has no role gate) |

### Bilingual envelope (example)

```json
{
  "code": "VIDEO_NOT_FOUND",
  "messageCkb": "ڤیدیۆکە نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم",
  "messageKmr": "Vîdyo nehat dîtin",
  "messageEn": "Video not found",
  "status": 404,
  "timestamp": "2024-05-31T12:42:09",
  "path": "/api/v1/videos/999",
  "details": { "id": 999 }
}
```

---

## 14 · Notes

- **Topic CRUD duplication.** Topic create/list/delete is exposed twice: once co-located in this controller (`POST /api/v1/videos/topics`, `GET /api/v1/videos/topics`, `DELETE /api/v1/videos/topics/{topicId}`) and again under the central `PublishmentTopicController` at `/api/v1/topics`. Both backends operate on the same `PublishmentTopic` table filtered by `entityType = "VIDEO"`, so there is a single source of truth. Use whichever is more convenient for the client; results are identical.
- **VideoType values** (from `VideoType.java`):
  - `FILM` — single hosted/external/embed source; no clip list; `albumOfMemories` always `false`.
  - `VIDEO_CLIP` — ordered list of `VideoClipItem`s; FILM source fields are unused; `albumOfMemories` may be `true` or `false`.
- **Cover semantics:**
  - `ckbCoverImage` / `ckbCoverUrl` → CKB-language cover, shown to CKB readers.
  - `kmrCoverImage` / `kmrCoverUrl` → KMR-language cover, shown to KMR readers.
  - `hoverImage` / `hoverCoverUrl` → shared cover swapped in on hover/teaser interactions; not language-specific.
- **Upload precedence (covers and source):** if a multipart file part is non-empty, it is uploaded to S3 via `S3Service.upload` and replaces the URL field. If the part is absent but the DTO URL is non-blank, the URL is used. On update, fields not mentioned in `data` and not supplied as a file are left untouched.
- **Tiptap post-processing.** After persistence-related transforms, `VideoService.processTiptapHtml` rewrites both `ckbContent.description` and `kmrContent.description` (and each clip item's bilingual description) so any inline base64 data URIs are externalized to S3 and the HTML rewritten in place.
- **Album-of-Memories enforcement.** `enforceAlbumRule` always coerces `albumOfMemories = false` when `videoType == FILM`, regardless of what was supplied in the DTO.
- **Topic type guard.** Assigning by `topicId` triggers a check that the topic's `entityType` equals `"VIDEO"`; otherwise `topic.type.mismatch` is thrown to prevent cross-module topic leakage.
- **Default sort** for paged listings is always `createdAt DESC` (newest first); page size is clamped to `1..100`.
- **Audit log.** Every successful create / update / delete writes a `VideoLog` row with a snapshot of the video title at the time of the operation — log records remain readable after the underlying video is hard-deleted.
