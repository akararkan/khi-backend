# KHI Backend — GET Endpoints Reference

Complete public reference for every `GET` endpoint exposed by the KHI backend (Spring Boot, Java 21, PostgreSQL). Covers `about`, `contact`, `services`, `news`, `projects`, `videos`, `image-collections`, `sound-tracks` (incl. album-of-memories), `writings`, `topics`, and global `search`.

> **Base URL.** All endpoints below are mounted under the API base path.
> - Local: `http://localhost:8080`
> - Production (Railway): `https://<your-railway-host>`

---

## Table of Contents

1. [Conventions](#1-conventions)
   - 1.1 [Standard response envelope](#11-standard-response-envelope-apiresponset)
   - 1.2 [Pagination model](#12-pagination-model)
   - 1.3 [Bilingual content (CKB / KMR)](#13-bilingual-content-ckb--kmr)
   - 1.4 [Localization of error messages](#14-localization-of-error-messages)
2. [Error model](#2-error-model)
   - 2.1 [ApiErrorResponse shape](#21-apierrorresponse-shape)
   - 2.2 [Global HTTP status mapping](#22-global-http-status-mapping)
   - 2.3 [Per-entity exceptions](#23-per-entity-exceptions)
3. [Enums (canonical values)](#3-enums-canonical-values)
4. [About](#4-about--apiv1about)
5. [Contact](#5-contact--apiv1contact)
6. [Services](#6-services--apiv1services)
7. [News](#7-news--apiv1news)
8. [Projects](#8-projects--apiv1projects)
9. [Videos](#9-videos--apiv1videos)
10. [Image Collections](#10-image-collections--apiv1image-collections)
11. [Sound Tracks (incl. Album of Memories)](#11-sound-tracks--apiv1sound-tracks)
12. [Writings](#12-writings--apiv1writings)
13. [Publishment Topics](#13-publishment-topics--apiv1topics)
14. [Global Search](#14-global-search--apiv1search)
15. [Cross-controller quirks & gotchas](#15-cross-controller-quirks--gotchas)

---

## 1. Conventions

### 1.1 Standard response envelope (`ApiResponse<T>`)

Most controllers wrap their payload in this envelope:

```json
{
  "success": true,
  "message": "OK",
  "data": { /* T */ }
}
```

| Field     | Type    | Notes                                            |
|-----------|---------|--------------------------------------------------|
| `success` | boolean | `true` on 2xx, omitted-or-`false` on errors.     |
| `message` | string  | Human-readable summary.                          |
| `data`    | `T`     | Typed payload. Omitted when null (`NON_NULL`).   |

> **Exceptions to the envelope** — `AboutController`, `VideoController`, and `PublishmentTopicController` return raw DTOs / lists without `ApiResponse` wrapping. Endpoint headers below call this out where it applies.

### 1.2 Pagination model

Every paginated endpoint accepts two query params:

| Param  | Type | Default | Notes                                    |
|--------|------|---------|------------------------------------------|
| `page` | int  | `0`     | 0-based page index.                      |
| `size` | int  | varies  | See per-endpoint default (commonly 20).  |

**There is no `@PageableDefault` and no client-supplied `sort` parameter** — sort order is fixed per endpoint by the service layer (typically `createdAt DESC`). The response is a Spring `Page<T>`:

```json
{
  "content": [ /* T[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 7,
  "totalElements": 132,
  "last": false,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 20,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "empty": false
}
```

### 1.3 Bilingual content (CKB / KMR)

The platform stores content side-by-side in two Kurdish dialects:

- **CKB** — Soranî (Central Kurdish).
- **KMR** — Kurmancî (Northern Kurdish).

These appear throughout DTOs as paired fields/objects (`ckbContent` / `kmrContent`, `titleCkb` / `titleKmr`, `tagsCkb` / `tagsKmr`, etc.). Long-form fields (`description`, `body`) contain **Tiptap-generated HTML** (paragraphs, images, audio, video, links), not plain text.

Search endpoints typically accept a `language` query param with values `ckb`, `kmr`, or `both` (default `both`).

### 1.4 Localization of error messages

The `GlobalExceptionHandler` honours the `Accept-Language` header. Supported tags:

- `en` — English (fallback).
- `ku` — Kurdish (resolved through `Locale.forLanguageTag("ku")`).

Every error body always includes both `messageEn` and `messageKu`; the top-level `message` is whichever the request's `Accept-Language` resolved to.

---

## 2. Error model

### 2.1 `ApiErrorResponse` shape

```json
{
  "timestamp": "2026-05-23T19:00:00Z",
  "status": 404,
  "path": "/api/v1/news/9999",
  "method": "GET",
  "traceId": "b2c3d4...",
  "code": "NEWS_NOT_FOUND",
  "message": "خبر نه‌دۆزرایه‌وه‌",
  "messageEn": "News not found",
  "messageKu": "خبر نه‌دۆزرایه‌وه‌",
  "fieldErrors": [
    {
      "field": "language",
      "message": "must not be blank",
      "messageEn": "must not be blank",
      "messageKu": "نابێت بەتاڵ بێت"
    }
  ],
  "details": { "id": 9999 }
}
```

| Field         | Type                   | Notes                                                              |
|---------------|------------------------|--------------------------------------------------------------------|
| `timestamp`   | ISO-8601 instant       | Server clock at the time of failure.                               |
| `status`      | int                    | HTTP status code (mirrors the response status line).               |
| `path`        | string                 | Request path that failed.                                          |
| `method`      | string                 | HTTP verb (`GET`, `POST`, …).                                      |
| `traceId`     | string                 | Correlates with server logs (`%X{traceId}` in `logback`).          |
| `code`        | `ErrorCode` (string)   | Stable machine-readable code — see §2.2 / §2.3.                    |
| `message`     | string                 | Localized message (driven by `Accept-Language`).                   |
| `messageEn`   | string                 | English copy (always present).                                     |
| `messageKu`   | string                 | Kurdish copy (always present).                                     |
| `fieldErrors` | array&lt;ApiFieldError&gt; | Present on validation failures.                                  |
| `details`     | object                 | Optional bag of context (`field`, `id`, etc.) supplied by the throw site. |

### 2.2 Global HTTP status mapping

Defined in `GlobalExceptionHandler` (`@RestControllerAdvice`).

| Exception                                  | Status | `code`              |
|--------------------------------------------|--------|---------------------|
| `AppException` (and all subclasses)        | varies | per-exception (see §2.3) |
| `UserAlreadyExistsException`               | 409    | `CONFLICT`          |
| `BadCredentialsException`                  | 401    | `UNAUTHORIZED`      |
| `LockedException`                          | 423    | `ACCOUNT_LOCKED`    |
| `UsernameNotFoundException`                | 404    | `NOT_FOUND`         |
| `AccessDeniedException`                    | 403    | `FORBIDDEN`         |
| `IllegalArgumentException`                 | 400    | `BAD_REQUEST`       |
| `IllegalStateException`                    | 400    | `BAD_REQUEST`       |
| `MethodArgumentNotValidException`          | 400    | `VALIDATION_ERROR`  |
| `ConstraintViolationException`             | 400    | `VALIDATION_ERROR`  |
| `HttpRequestMethodNotSupportedException`   | 405    | `METHOD_NOT_ALLOWED`|
| `HttpMessageNotReadableException`          | 400    | `BAD_REQUEST`       |
| `MissingServletRequestParameterException`  | 400    | `MISSING_PARAMETER` |
| `MaxUploadSizeExceededException`           | 413    | `PAYLOAD_TOO_LARGE` |
| `MultipartException`                       | 400    | `BAD_REQUEST`       |
| `DataIntegrityViolationException`          | 409    | `CONFLICT`          |
| any other `Exception` (fallback)           | 500    | `INTERNAL_ERROR`    |

### 2.3 Per-entity exceptions

All inherit from `AppException` (carries `ErrorCode`, `HttpStatus`, `messageKey`, `details`).

| Exception                              | `code`                | HTTP |
|----------------------------------------|-----------------------|------|
| `BadRequestException`                  | `BAD_REQUEST`         | 400  |
| `ConflictException`                    | `CONFLICT`            | 409  |
| `ForbiddenException`                   | `FORBIDDEN`           | 403  |
| `NotFoundException` / `ResourceNotFoundException` | `NOT_FOUND` | 404  |
| `UnauthorizedException`                | `UNAUTHORIZED`        | 401  |
| `NewsNotFoundException`                | `NEWS_NOT_FOUND`      | 404  |
| `NewsConflictException`                | `NEWS_CONFLICT`       | 409  |
| `NewsValidationException`              | `NEWS_VALIDATION`     | 400  |
| `NewsStorageException`                 | `STORAGE_ERROR`       | 502  |
| `NewsInternalException`                | `INTERNAL_ERROR`      | 500  |
| `ProjectNotFoundException`             | `PROJECT_NOT_FOUND`   | 404  |
| `ProjectConflictException`             | `PROJECT_CONFLICT`    | 409  |
| `ProjectValidationException` / `ProjectSearchValidationException` | `PROJECT_VALIDATION` | 400 |
| `ProjectStorageException`              | `STORAGE_ERROR`       | 502  |
| `ProjectInternalException`             | `INTERNAL_ERROR`      | 500  |
| `ImageCollectionNotFoundException`     | `IMAGE_NOT_FOUND`     | 404  |
| `ImageCollectionConflictException`     | `IMAGE_CONFLICT`      | 409  |
| `ImageCollectionValidationException`   | `IMAGE_VALIDATION`    | 400  |
| `ImageCollectionMediaException`        | `IMAGE_MEDIA_INVALID` | 400  |
| `ImageCollectionStorageException`      | `STORAGE_ERROR`       | 502  |
| `ImageCollectionInternalException`     | `INTERNAL_ERROR`      | 500  |
| `SoundTrackNotFoundException`          | `SOUND_NOT_FOUND`     | 404  |
| `SoundTrackConflictException`          | `SOUND_CONFLICT`      | 409  |
| `SoundTrackValidationException`        | `SOUND_VALIDATION`    | 400  |
| `SoundTrackMediaException`             | `SOUND_MEDIA_INVALID` | 400  |
| `SoundTrackStorageException`           | `STORAGE_ERROR`       | 502  |
| `SoundTrackInternalException`          | `INTERNAL_ERROR`      | 500  |
| `VideoNotFoundException`               | `VIDEO_NOT_FOUND`     | 404  |
| `VideoValidationException`             | `VIDEO_VALIDATION`    | 400  |
| `VideoMediaException`                  | `VIDEO_MEDIA_INVALID` | 400  |
| `VideoStorageException`                | `STORAGE_ERROR`       | 502  |
| `VideoInternalException`               | `INTERNAL_ERROR`      | 500  |
| `WritingNotFoundException`             | `WRITING_NOT_FOUND`   | 404  |
| `WritingValidationException`           | `WRITING_VALIDATION`  | 400  |
| `WritingMediaException`                | `WRITING_MEDIA_INVALID` | 400 |
| `WritingStorageException`              | `STORAGE_ERROR`       | 502  |
| `WritingInternalException`             | `INTERNAL_ERROR`      | 500  |

---

## 3. Enums (canonical values)

| Enum                 | Values |
|----------------------|--------|
| `Language`           | `CKB`, `KMR` *(JSON accepts lowercase, trimmed)* |
| `MediaKind`          | `IMAGE`, `VIDEO`, `AUDIO` |
| `ProjectStatus`      | `ONGOING`, `COMPLETED` |
| `ProjectMediaType`   | `IMAGE`, `VIDEO`, `AUDIO`, `DOCUMENT`, `PDF`, `TEXT` |
| `ImageCollectionType`| `SINGLE`, `GALLERY`, `PHOTO_STORY` |
| `TrackState`         | `SINGLE`, `MULTI` |
| `AttachmentType`     | `PDF`, `VIDEO`, `IMAGE`, `AUDIO`, `OTHER` |
| `AudioChannel`       | `MONO`, `STEREO` |
| `FileType` (audio)   | `MP3`, `WAV`, `OGG`, `AAC`, `FLAC`, `OTHER` |
| `WritingFileFormat`  | `PDF`, `DOCX`, `DOC`, `TXT`, `EPUB`, `ODT`, `RTF`, `HTML`, `OTHER` |
| `BookGenre`          | `POETRY`, `NOVEL`, `SHORT_STORY`, `DRAMA`, `HISTORY`, `BIOGRAPHY`, `PHILOSOPHY`, `RELIGION`, `FOLKLORE`, `POLITICS`, `SOCIOLOGY`, `ECONOMICS`, `LAW`, `LINGUISTICS`, `ARTS`, `CULTURAL`, `SCIENCE`, `MEDICINE`, `EDUCATIONAL`, `CHILDREN`, `TRAVEL`, `OTHER` |
| `AlbumType`          | `AUDIO`, `VIDEO` |
| `FilmType`           | `DOCUMENTARY`, `EVIDENCE`, `SHORT_FILM`, `FEATURE_FILM`, `INTERVIEW`, `ARCHIVAL_FOOTAGE`, `EDUCATIONAL`, `OTHER` |
| `SoundType` (legacy) | `LAWK`, `HAIRAN` |
| `VideoType`          | `FILM`, `VIDEO_CLIP` |
| `ErrorCode`          | `VALIDATION_ERROR`, `MISSING_PARAMETER`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`, `ACCOUNT_LOCKED`, `CONFLICT`, `BAD_REQUEST`, `METHOD_NOT_ALLOWED`, `PAYLOAD_TOO_LARGE`, `PROJECT_*`, `NEWS_*`, `VIDEO_*`, `IMAGE_*`, `SOUND_*`, `WRITING_*`, `DB_ERROR`, `STORAGE_ERROR`, `EXTERNAL_ERROR`, `INTERNAL_ERROR` |

---

## 4. About — `/api/v1/about`

> Returns **raw DTOs** (no `ApiResponse` envelope).

### 4.1 `GET /api/v1/about` — list all active

| Aspect | Value |
|--------|-------|
| Auth   | Public |
| Query  | _none_ |
| Response | `List<AboutResponse>` |

### 4.2 `GET /api/v1/about/{slug}` — fetch by slug

| Aspect | Value |
|--------|-------|
| Path   | `slug` *(string — Sorani **or** Kurmanji slug)* |
| Response | `AboutResponse` |
| Errors | `404 NOT_FOUND` if slug doesn't resolve. |

#### `AboutResponse` shape

```json
{
  "id": 1,
  "slugCkb": "دەربارەی-ئێمە",
  "slugKmr": "derbare-me",
  "ckbContent": {
    "title": "دەربارەی دامەزراوەی KHI",
    "subtitle": "لۆتکەی شارستانیەتی کوردی",
    "metaDescription": "...",
    "body": "<h1>...</h1><p>...</p>"
  },
  "kmrContent": { "title": "Derbarê me", "subtitle": "…", "metaDescription": "…", "body": "<h1>…</h1>" },
  "active": true,
  "stats": [
    { "labelCkb": "کتێب",   "labelKmr": "Pirtûk", "value": "5,000+" },
    { "labelCkb": "توێژینەوە", "labelKmr": "Lêkolîn", "value": "1,200+" }
  ],
  "createdAt": "2026-05-23 20:00:10",
  "updatedAt": "2026-05-23 20:00:10"
}
```

`AboutContentResponse` = `{ title, subtitle, metaDescription, body (HTML) }`.
`StatItemDto` = `{ labelCkb, labelKmr, value }`.

---

## 5. Contact — `/api/v1/contact`

### 5.1 `GET /api/v1/contact` — list all (admin, includes inactive)
### 5.2 `GET /api/v1/contact/active` — list public/active only
### 5.3 `GET /api/v1/contact/{id}` — fetch by numeric ID
### 5.4 `GET /api/v1/contact/slug/{slug}` — fetch by slug

All responses wrap a `ContactResponse` (single or `List<ContactResponse>`) in `ApiResponse`.

#### `ContactResponse` shape

```json
{
  "id": 1,
  "slugCkb": "پەیوەندی-هەولێر",
  "slugKmr": "peywendi-hewler",
  "ckbContent": {
    "title": "پەیوەندیمان پێوەبکە - نووسینگەی هەولێر",
    "subtitle": "ئامادەین بۆ خزمەتکردنت",
    "address": "شەقامی ٦٠ مەتری، نزیک قەڵای هەولێر",
    "workingHours": "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٧:٠٠",
    "description": "<h2>سەردانیمان بکە</h2><p>…</p>"
  },
  "kmrContent": { /* same shape */ },
  "phone": "+964 750 123 4567",
  "secondaryPhone": "+964 770 987 6543",
  "email": "info@khi-institute.org",
  "mapEmbedUrl": "https://www.google.com/maps/embed?…",
  "latitude": 36.1911,
  "longitude": 44.0091,
  "active": true,
  "createdAt": "2026-05-23 20:00:10",
  "updatedAt": "2026-05-23 20:00:10"
}
```

**Errors:** `404 NOT_FOUND` on unknown id/slug.

---

## 6. Services — `/api/v1/services`

### 6.1 `GET /api/v1/services` — list active (optional filter)

| Param  | Type   | Default | Notes |
|--------|--------|---------|-------|
| `type` | string | _none_  | When provided, filters by `serviceType`. |
| `page` | int    | `0`     | |
| `size` | int    | `20`    | |

Response: `ApiResponse<Page<ServiceResponse>>`.

### 6.2 `GET /api/v1/services/all` — admin list (includes inactive)
Same paging params; includes inactive rows.

### 6.3 `GET /api/v1/services/{id}` — by id
Response: `ApiResponse<ServiceResponse>`. Throws `404 NOT_FOUND`.

### 6.4 `GET /api/v1/services/types` — distinct service-type names
Response: `ApiResponse<List<String>>`.

### 6.5 `GET /api/v1/services/search` — public search

| Param | Type   | Default | Notes |
|-------|--------|---------|-------|
| `q`   | string | **required** | Searches title + description across CKB/KMR. |
| `page`| int    | `0`     | |
| `size`| int    | `20`    | |

### 6.6 `GET /api/v1/services/search/admin` — admin search
Same shape as 6.5; includes inactive services.

#### `ServiceResponse` shape

```json
{
  "id": 1,
  "serviceType": "Training",
  "location": "Erbil, KHI Hall",
  "active": true,
  "publishedAt": "2026-04-23 20:00:10",
  "contents": [
    {
      "id": 11,
      "languageCode": "CKB",
      "title": "خولی فێرکاری نووسینی ئەکادیمی",
      "description": "<h2>…</h2>"
    },
    {
      "id": 12,
      "languageCode": "KMR",
      "title": "Kursa fêrkirina nivîsîna akademîk",
      "description": "<h2>…</h2>"
    }
  ],
  "createdAt": "2026-05-23 20:00:10",
  "updatedAt": "2026-05-23 20:00:10"
}
```

**Errors:** `400 MISSING_PARAMETER` if `q` is missing on search; `404 NOT_FOUND` for unknown id.

---

## 7. News — `/api/v1/news`

### 7.1 `GET /api/v1/news`, `/`, `/all` — list all (paged)

The same handler is mounted on all three paths (`""`, `"/"`, `"/all"`).

| Param  | Type | Default | Notes |
|--------|------|---------|-------|
| `page` | int  | `0`     | |
| `size` | int  | `20`    | |

Response: `ApiResponse<Page<NewsDto>>`.

### 7.2 `GET /api/v1/news/{id}` — fetch by id
Response: `ApiResponse<NewsDto>`. Throws `404 NEWS_NOT_FOUND` (code `NEWS_NOT_FOUND`).

### 7.3 `GET /api/v1/news/search` — global text search
| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `q`   | string | **required** | Free-text query across titles + descriptions (both languages). |
| `page`| int | `0` | |
| `size`| int | `20` | |

### 7.4 `GET /api/v1/news/search/keyword`
| Param      | Type   | Default | Notes |
|------------|--------|---------|-------|
| `keyword`  | string | **required** | |
| `language` | string | `both`  | `ckb` \| `kmr` \| `both`. |
| `page`/`size` | int | `0` / `20` | |

### 7.5 `GET /api/v1/news/search/tag`
Same shape as 7.4, with `tag` instead of `keyword`.

### 7.6 `GET /api/v1/news/search/category`
| Param  | Type | Default | Notes |
|--------|------|---------|-------|
| `name` | string | **required** | Matches Sorani or Kurmanji category name. |
| `page`/`size` | int | `0` / `20` | |

### 7.7 `GET /api/v1/news/search/subcategory`
Same shape as 7.6.

#### `NewsDto` shape

```json
{
  "id": 1,
  "coverUrl": "https://images.unsplash.com/photo-...",
  "coverMediaType": "IMAGE",
  "coverThumbnailUrl": "https://…/thumb.jpg",
  "mediaGallery": [
    {
      "url": "https://…",
      "kind": "IMAGE",
      "thumbnailUrl": "https://…",
      "captionCkb": "…",
      "captionKmr": "…",
      "sortOrder": 0
    }
  ],
  "datePublished": "2026-05-21",
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10",
  "contentLanguages": ["CKB", "KMR"],
  "category":    { "ckbName": "هونەر",  "kmrName": "Huner" },
  "subCategory": { "ckbName": "شیعر",  "kmrName": "Helbest" },
  "ckbContent":  { "title": "…", "description": "<p>…</p>" },
  "kmrContent":  { "title": "…", "description": "<p>…</p>" },
  "tags":     { "ckb": ["شیعر"], "kmr": ["Helbest"] },
  "keywords": { "ckb": ["شیعر"], "kmr": ["Helbest"] }
}
```

**Errors:**
- `400 MISSING_PARAMETER` — missing `q`, `keyword`, `tag`, or `name`.
- `400 BAD_REQUEST` — invalid `language` (must be `ckb` / `kmr` / `both`).
- `404 NEWS_NOT_FOUND`.

---

## 8. Projects — `/api/v1/projects`

### 8.1 `GET /api/v1/projects/getAll` — paginated list
Produces `application/json`.

| Param | Type | Default |
|-------|------|---------|
| `page`| int | `0` |
| `size`| int | `20` |

Response: `ApiResponse<Page<ProjectResponse>>`.

### 8.2 `GET /api/v1/projects/search/tag`
| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `tag` | string | **required** | Searches `tagsCkb` + `tagsKmr`. |
| `page`/`size` | int | `0` / `20` | |

### 8.3 `GET /api/v1/projects/search/keyword`
Same shape — query param `keyword`, searches `keywordsCkb` + `keywordsKmr`.

#### `ProjectResponse` shape

```json
{
  "id": 1,
  "coverUrl": "https://images.unsplash.com/photo-…",
  "coverMediaType": "IMAGE",
  "coverThumbnailUrl": "https://…/thumb.jpg",
  "mediaGallery": [ /* MediaItem[] */ ],
  "projectTypeCkb": "فۆلکلۆر",
  "projectTypeKmr": "Folklor",
  "status": "ONGOING",
  "projectDate": "2025-11-23",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "پاراستنی فۆلکلۆری کوردی",
    "description": "<p>…</p>",
    "location": "سلێمانی"
  },
  "kmrContent": { "title": "Parastina folklora kurdî", "description": "<p>…</p>", "location": "Silêmanî" },
  "tagsCkb": ["فۆلکلۆر"],
  "tagsKmr": ["Folklor"],
  "keywordsCkb": ["پاراستن"],
  "keywordsKmr": ["Parastin"],
  "createdAt": "2026-05-23T20:00:10Z",
  "updatedAt": "2026-05-23T20:00:10Z",
  "createdBy": "SYSTEM",
  "updatedBy": "SYSTEM"
}
```

**Errors:**
- `400 PROJECT_VALIDATION` — empty/blank `tag` or `keyword`.
- `404 PROJECT_NOT_FOUND`.

---

## 9. Videos — `/api/v1/videos`

> **Returns raw DTOs** (no `ApiResponse` envelope).
> **Default `size` is `10`** (not 20).
> Search params are named **`value`** (not `tag` / `keyword`).

### 9.1 `GET /api/v1/videos/topics` — list video topics
Response: `List<VideoDTO.TopicView>` where each item is `{ id, nameCkb, nameKmr, createdAt }`.

### 9.2 `GET /api/v1/videos` — paginated list
| Param | Type | Default |
|-------|------|---------|
| `page`| int | `0` |
| `size`| int | `10` |

Response: `Page<VideoDTO>`.

### 9.3 `GET /api/v1/videos/{id}`
Response: `VideoDTO`. Throws `404 VIDEO_NOT_FOUND`.

### 9.4 `GET /api/v1/videos/search/tag`
| Param  | Type | Default | Notes |
|--------|------|---------|-------|
| `value`| string | **required** | Substring across both `tagsCkb`/`tagsKmr`. |
| `page`/`size` | int | `0` / `10` | |

### 9.5 `GET /api/v1/videos/search/keyword`
Same shape — `value` searches both keyword sets.

#### `VideoDTO` shape

```json
{
  "id": 1,
  "ckbCoverUrl": "https://images.unsplash.com/photo-…",
  "kmrCoverUrl": "https://images.unsplash.com/photo-…",
  "hoverCoverUrl": "https://…",
  "videoType": "FILM",
  "albumOfMemories": false,
  "topicId": 1,
  "topicNameCkb": "بەلگەفیلمی مێژوویی",
  "topicNameKmr": "Belgefîlma dîrokî",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "چاوپێکەوتنێک لەگەڵ شاعیر شێرکۆ بێکەس",
    "description": "<p>…</p>",
    "location": "هەولێر",
    "director": "هاوژین",
    "producer": "KHI"
  },
  "kmrContent": { "title": "Hevpeyvîn bi Şêrko Bêkes re", "description": "<p>…</p>", "location": "Hewlêr", "director": "Hawjîn", "producer": "KHI" },
  "sourceUrl": "https://commondatastorage.googleapis.com/.../BigBuckBunny.mp4",
  "sourceExternalUrl": null,
  "sourceEmbedUrl": null,
  "videoClipItems": [
    {
      "id": 10,
      "url": "https://…/clip-1.mp4",
      "externalUrl": null,
      "embedUrl": null,
      "clipNumber": 1,
      "durationSeconds": 220,
      "resolution": "1080p",
      "fileFormat": "mp4",
      "fileSizeMb": 12.5,
      "titleCkb": "کلیپی یەکەم",
      "titleKmr": "Klîpê yekem",
      "descriptionCkb": "<p>…</p>",
      "descriptionKmr": "<p>…</p>"
    }
  ],
  "fileFormat": "mp4",
  "durationSeconds": 1850,
  "publishmentDate": "2026-03-12",
  "resolution": "1080p",
  "fileSizeMb": 412.7,
  "tagsCkb": ["چاوپێکەوتن"], "tagsKmr": ["Hevpeyvîn"],
  "keywordsCkb": ["شێرکۆ"],   "keywordsKmr": ["Şêrko"],
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10"
}
```

> **Note.** `VideoDTO` is also used as the request body for `POST`/`PUT`, so write-only fields (`newTopic`, `clearTopic`) may appear in your generated Swagger but are ignored on read.

**Errors:**
- `400 VIDEO_VALIDATION` — invalid filters.
- `404 VIDEO_NOT_FOUND`.

---

## 10. Image Collections — `/api/v1/image-collections`

### 10.1 `GET /api/v1/image-collections` — paginated list
| Param | Type | Default |
|-------|------|---------|
| `page`| int  | `0` |
| `size`| int  | `20` |

Response: `ApiResponse<Page<ImageCollectionDTO.Response>>`.

### 10.2 `GET /api/v1/image-collections/{id}`
Response: `ApiResponse<ImageCollectionDTO.Response>`. Throws `404 IMAGE_NOT_FOUND`.

### 10.3 `GET /api/v1/image-collections/topics`
Response: `ApiResponse<List<{id, nameCkb, nameKmr}>>` (topics of `entityType = IMAGE`).

#### `ImageCollectionDTO.Response` shape

```json
{
  "id": 3,
  "collectionType": "GALLERY",
  "ckbCoverUrl": "https://images.unsplash.com/photo-…",
  "kmrCoverUrl": "https://images.unsplash.com/photo-…",
  "hoverCoverUrl": "https://…",
  "topicId": 11,
  "topicNameCkb": "وێنەی فۆلکلۆریک",
  "topicNameKmr": "Wêneyên folklorîk",
  "publishmentDate": "2026-04-01",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "گالێریی فۆلکلۆر",
    "description": "<p>…</p>",
    "topic": "فۆلکلۆر",
    "location": "هەولێر",
    "collectedBy": "تیمی KHI ی بەرپێ"
  },
  "kmrContent": { /* same shape */ },
  "tags":     { "ckb": ["فرش"], "kmr": ["Tişt"] },
  "keywords": { "ckb": ["فۆلکلۆر"], "kmr": ["Destçêk"] },
  "imageAlbum": [
    {
      "id": 30,
      "imageUrl": "https://images.unsplash.com/photo-…",
      "externalUrl": null,
      "embedUrl": null,
      "captionCkb": "هەنگاو ١",
      "captionKmr": "Gav 1",
      "descriptionCkb": "<p>…</p>",
      "descriptionKmr": "<p>…</p>",
      "sortOrder": 1,
      "fileSizeBytes": 450000,
      "widthPx": 1600,
      "heightPx": 1067,
      "mimeType": "image/jpeg",
      "aspectRatio": 1.50,
      "humanReadableSize": "439.5 KB"
    }
  ],
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10"
}
```

**Errors:**
- `404 IMAGE_NOT_FOUND` for unknown id.

---

## 11. Sound Tracks — `/api/v1/sound-tracks`

This module also covers the **Album of Memories** stream (`albumOfMemories = true`).

### 11.1 `GET /api/v1/sound-tracks` — paginated list
`page=0`, `size=20`. Response: `ApiResponse<Page<SoundTrackDtos.Response>>`.

### 11.2 `GET /api/v1/sound-tracks/{id}`
Response: `ApiResponse<SoundTrackDtos.Response>`. Throws `404 SOUND_NOT_FOUND`.

### 11.3 `GET /api/v1/sound-tracks/by-state`
| Param  | Type | Default | Notes |
|--------|------|---------|-------|
| `state`| `TrackState` enum | **required** | `SINGLE` \| `MULTI`. |
| `page` / `size` | int | `0` / `20` | |

**Errors:** `400 SOUND_VALIDATION` if `state` is null/missing.

### 11.4 `GET /api/v1/sound-tracks/by-sound-type`
| Param       | Type   | Default | Notes |
|-------------|--------|---------|-------|
| `soundType` | string | **required** | Free-form sound type label (e.g. `Religious`). |
| `page` / `size` | int | `0` / `20` | |

**Errors:** `400 SOUND_VALIDATION` if blank.

### 11.5 `GET /api/v1/sound-tracks/by-topic`
| Param     | Type | Default | Notes |
|-----------|------|---------|-------|
| `topicId` | long | **required** | |
| `page` / `size` | int | `0` / `20` | |

**Errors:** `400 SOUND_VALIDATION` if missing.

### 11.6 `GET /api/v1/sound-tracks/album-of-memories`
Paginated stream of sound tracks where `albumOfMemories = true`.

| Param | Type | Default |
|-------|------|---------|
| `page`| int  | `0` |
| `size`| int  | `20` |

### 11.7 `GET /api/v1/sound-tracks/search/tag`
| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `tag` | string | **required** | Searches both `tagsCkb` + `tagsKmr`. |

**Errors:** `400 BAD_REQUEST` if blank.

### 11.8 `GET /api/v1/sound-tracks/search/keyword`
| Param     | Type   | Default | Notes |
|-----------|--------|---------|-------|
| `keyword` | string | **required** | Searches `keywordsCkb` + `keywordsKmr`. |

### 11.9 `GET /api/v1/sound-tracks/search` — global text
| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `q`   | string | **required** | Free-text across title + description + tags + keywords (both languages). |

**Errors:** `400 BAD_REQUEST` if `q` blank — body lists `field: "q"` in `details`.

### 11.10 `GET /api/v1/sound-tracks/topics`
Response: `ApiResponse<List<{id, nameCkb, nameKmr}>>` for `entityType = SOUND`.

#### `SoundTrackDtos.Response` shape

```json
{
  "id": 2,
  "ckbCoverUrl": "https://images.unsplash.com/photo-…",
  "kmrCoverUrl": "https://images.unsplash.com/photo-…",
  "hoverCoverUrl": "https://…",
  "soundType": "Religious",
  "trackState": "MULTI",
  "albumOfMemories": true,
  "topicId": 2,
  "topicNameCkb": "ستران",
  "topicNameKmr": "Stran",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": { "title": "ئەلبووم — یاد", "description": "<p>…</p>" },
  "kmrContent": { "title": "Albûm — Bîranîn",  "description": "<p>…</p>" },
  "locations": ["Erbil", "Sulaymaniyah"],
  "reader": "مەلا عەلی",
  "directors": ["د. خالد"],
  "terms": "Creative Commons BY-NC",
  "thisProjectOfInstitute": true,
  "tags":     { "ckb": ["یاد"],     "kmr": ["Bîranîn"] },
  "keywords": { "ckb": ["مەولوود"], "kmr": ["Mewlûd"] },
  "files": [
    {
      "id": 100,
      "fileUrl":     "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
      "externalUrl": null,
      "embedUrl":    null,
      "title": "تراکی یەکەم",
      "fileType": "MP3",
      "publishmentYear": 1995,
      "sizeBytes": 5200000,
      "durationSeconds": 240,
      "durationMinutes": 4.0,
      "bitRate": "192",
      "sampleRate": "44100",
      "audioChannel": "STEREO",
      "form": "Solo",
      "genre": "Religious",
      "recordingVenue": "Erbil Studio A",
      "brochures": [
        { "id": 1, "imageUrl": "https://…/cover.jpg", "caption": "Front cover", "brochureOrder": 1 }
      ]
    }
  ],
  "totalDurationSeconds": 1800,
  "totalSizeBytes": 50000000,
  "albumName": "Album of Memories Vol. 1",
  "publishmentYear": 1995,
  "cdNumber": 2,
  "totalTracks": 12,
  "attachments": [
    {
      "id": 5,
      "fileUrl": "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
      "title": "ناساندنی ئەلبووم",
      "attachmentType": "PDF",
      "sizeBytes": 248329,
      "mimeType": "application/pdf",
      "attachmentOrder": 1
    }
  ],
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10"
}
```

**Errors summary:**
- `400 SOUND_VALIDATION` — missing/invalid `state`, `soundType`, `topicId`.
- `400 BAD_REQUEST` — blank `tag`, `keyword`, or `q`.
- `404 SOUND_NOT_FOUND`.

---

## 12. Writings — `/api/v1/writings`

> Sort order is fixed to `createdAt DESC` on all paginated endpoints; clients cannot override.

### 12.1 `GET /api/v1/writings` — paginated list
`page=0`, `size=20`. Response: `ApiResponse<Page<WritingDtos.Response>>`.

### 12.2 `GET /api/v1/writings/{id}`
Throws `404 WRITING_NOT_FOUND`.

### 12.3 `GET /api/v1/writings/series/parents`
List the "parent" book of every multi-volume series.

| Param | Type | Default |
|-------|------|---------|
| `page`| int  | `0` |
| `size`| int  | **`100`** |

### 12.4 `GET /api/v1/writings/series/{seriesId}`
Fetch every book in one series, ordered by `seriesOrder`.

| Path       | Type   | Notes |
|------------|--------|-------|
| `seriesId` | string | UUID-style identifier shared by all books in the series. |

Response: `ApiResponse<WritingDtos.SeriesResponse>`.

### 12.5 `GET /api/v1/writings/search/writer`
| Param      | Type   | Default | Notes |
|------------|--------|---------|-------|
| `name`     | string | **required** | Author name (CKB or KMR). |
| `language` | string | `both`  | `ckb` \| `kmr` \| `both`. |
| `page`/`size`| int | `0` / `20` | |

### 12.6 `GET /api/v1/writings/search/tag`
Same shape as 12.5, with `tag` instead of `name`.

### 12.7 `GET /api/v1/writings/search/keyword`
Same shape as 12.5, with `keyword` instead of `name`.

### 12.8 `GET /api/v1/writings/topics`
Response: `ApiResponse<List<{id, nameCkb, nameKmr}>>` for `entityType = WRITING`.

#### `WritingDtos.Response` shape

```json
{
  "id": 1,
  "contentLanguages": ["CKB", "KMR"],
  "ckbCoverUrl":   "https://images.unsplash.com/photo-…",
  "kmrCoverUrl":   "https://images.unsplash.com/photo-…",
  "hoverCoverUrl": "https://…",
  "ckbContent": {
    "title": "دیوانی شیعر",
    "description": "<h2>دیوانی شیعر</h2><p>…</p>",
    "writer": "بەختیار عەلی",
    "fileUrl": "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
    "fileFormat": "PDF",
    "fileSizeBytes": 29000000,
    "pageCount": 240,
    "genre": "Poetry"
  },
  "kmrContent": { /* same shape, KMR text */ },
  "topic": { "id": 11, "nameCkb": "ئەدەبیات", "nameKmr": "Wêje" },
  "bookGenres": ["POETRY", "CULTURAL"],
  "publishedByInstitute": true,
  "tags":     { "ckb": ["شیعر"],  "kmr": ["Helbest"] },
  "keywords": { "ckb": ["بەختیار"],"kmr": ["Bextiyar"] },
  "seriesInfo": {
    "seriesId": "01HXYZ…",
    "seriesName": "Dîwan",
    "seriesOrder": 1.0,
    "parentBookId": null,
    "totalBooks": 4,
    "isParent": true
  },
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10"
}
```

#### `WritingDtos.SeriesResponse` shape

```json
{
  "seriesId": "01HXYZ…",
  "seriesName": "Dîwan",
  "totalBooks": 4,
  "books": [
    { "id": 1, "titleCkb": "دیوان — بەش ١", "titleKmr": "Dîwan — Beş 1", "seriesOrder": 1.0, "createdAt": "2026-05-23T20:00:10" },
    { "id": 2, "titleCkb": "دیوان — بەش ٢", "titleKmr": "Dîwan — Beş 2", "seriesOrder": 2.0, "createdAt": "2026-05-23T20:00:10" }
  ]
}
```

**Errors:**
- `400 WRITING_VALIDATION` — invalid filter parameters.
- `404 WRITING_NOT_FOUND` — unknown id / seriesId.

---

## 13. Publishment Topics — `/api/v1/topics`

Used by Image / Sound / Video / Writing modules to look up shared topic metadata.

> Returns the **raw JPA entity** `PublishmentTopic` (no `ApiResponse` envelope).

### 13.1 `GET /api/v1/topics/{entityType}` — list topics of a given entity type
| Path         | Type   | Notes |
|--------------|--------|-------|
| `entityType` | string | One of `VIDEO`, `SOUND`, `IMAGE`, `WRITING`. |

### 13.2 `GET /api/v1/topics/{entityType}/{id}`
| Path         | Type   |
|--------------|--------|
| `entityType` | string |
| `id`         | long   |

> Implementation note: `entityType` is accepted in the path for routing consistency, but only `id` is used by the service lookup.

#### `PublishmentTopic` shape

```json
{
  "id": 1,
  "entityType": "SOUND",
  "nameCkb": "ستران",
  "nameKmr": "Stran",
  "createdAt": "2026-05-23T20:00:10",
  "updatedAt": "2026-05-23T20:00:10"
}
```

**Errors:** `404 NOT_FOUND` on unknown id.

---

## 14. Global Search — `/api/v1/search`

Single multiplex endpoint that searches across every content type.

### 14.1 `GET /api/v1/search`

| Param | Type   | Default | Notes |
|-------|--------|---------|-------|
| `q`   | string | **required** | Free-text query (matched against titles + descriptions across CKB/KMR). |
| `type`| string | `ALL`   | `ALL` \| `PROJECT` \| `NEWS` \| `VIDEO` \| `WRITING` \| `SOUNDTRACK` \| `IMAGE`. |
| `page`| int    | `0`     | Applies per section. |
| `size`| int    | **`10`**| Applies per section (so `type=ALL` returns up to 6×size items overall). |

Response: `ApiResponse<GlobalSearchResponse>`.

#### `GlobalSearchResponse` shape

```json
{
  "query": "هونەر",
  "page": 0,
  "size": 10,
  "type": "ALL",
  "projects":         { "items": [ /* SearchItem[] */ ], "totalElements": 5, "totalPages": 1, "currentPage": 0, "size": 10 },
  "news":             { "items": [ … ], "totalElements": 8, "totalPages": 1, "currentPage": 0, "size": 10 },
  "videos":           { "items": [ … ], "totalElements": 3, "totalPages": 1, "currentPage": 0, "size": 10 },
  "writings":         { "items": [ … ], "totalElements": 2, "totalPages": 1, "currentPage": 0, "size": 10 },
  "soundTracks":      { "items": [ … ], "totalElements": 4, "totalPages": 1, "currentPage": 0, "size": 10 },
  "imageCollections": { "items": [ … ], "totalElements": 1, "totalPages": 1, "currentPage": 0, "size": 10 }
}
```

Each `SearchItem`:

```json
{
  "id": 12,
  "type": "NEWS",
  "titleCkb": "…",
  "titleKmr": "…",
  "descriptionCkb": "<p>…</p>",
  "descriptionKmr": "<p>…</p>",
  "coverUrl": "https://…",
  "createdAt": "2026-05-23T20:00:10"
}
```

When `type` is anything other than `ALL`, only the corresponding section is populated and the others come back empty.

**Errors:**
- `400 MISSING_PARAMETER` — `q` missing.
- `400 BAD_REQUEST` — unrecognized `type`.

---

## 15. Cross-controller quirks & gotchas

The following details aren't bugs — they are deliberate behaviours that can trip up frontend integrations. Keep them in mind:

1. **`ApiResponse` envelope is not universal.**
   - `AboutController`, `VideoController`, and `PublishmentTopicController` return raw DTOs / `Page<…>` / `List<…>` without wrapping. All other GET controllers wrap.

2. **Video search uses `value` (not `tag` / `keyword`).**
   - `GET /videos/search/tag?value=…` and `GET /videos/search/keyword?value=…`.

3. **Video default `size` is 10, not 20.**
   - Same for `GET /api/v1/search` (per section).

4. **Writings `series/parents` defaults to `size=100`.**
   - Designed to return the full catalogue of series in a single page.

5. **News list is mounted on three paths.**
   - `GET /api/v1/news`, `GET /api/v1/news/`, and `GET /api/v1/news/all` all execute the same handler.

6. **`PublishmentTopic` is returned as a JPA entity.**
   - The `entityType` field is a string (`"VIDEO" | "SOUND" | "IMAGE" | "WRITING"`), not an enum. Don't rely on lazy-loaded relationships.

7. **`language` query param is a free-form `String`, not the `Language` enum.**
   - Accepted values: `ckb`, `kmr`, `both` (case-insensitive). Anything else is treated as `both` by the service or yields `400 BAD_REQUEST` depending on the entity.

8. **Search-param naming is inconsistent on purpose.**

   | Entity         | Tag       | Keyword   | Free-text |
   |----------------|-----------|-----------|-----------|
   | News           | `tag`     | `keyword` | `q` |
   | Projects       | `tag`     | `keyword` | _none_ |
   | Sound Tracks   | `tag`     | `keyword` | `q` |
   | Videos         | `value`   | `value`   | _none_ |
   | Writings       | `tag`     | `keyword` | _none_ (+ `name` for writer) |
   | Services       | _none_    | _none_    | `q` |
   | Global Search  | _none_    | _none_    | `q` (+ `type`) |

9. **Sort order is server-controlled.** No GET endpoint exposes a `sort` query parameter; default is `createdAt DESC` for most listings.

10. **`topics` listings appear on every publishment module** (`/image-collections/topics`, `/sound-tracks/topics`, `/writings/topics`, `/videos/topics`) — they all wrap a list of `{ id, nameCkb, nameKmr }` (Videos returns a typed `TopicView` instead of a `Map`). `PublishmentTopicController` is the canonical CRUD endpoint and exposes the entire `PublishmentTopic` JPA entity.

---

_Last updated: 2026-05-23. Source of truth: `src/main/java/ak/dev/khi_backend/khi_app/api/**/*.java`._
