# KHI Backend — Writing API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 12 Endpoints · Multipart + JSON · Paginated

Complete documentation for all writing/book management endpoints — create, update, delete, list, search, series management, and topic lookup — including bilingual content, multi-slot cover images, per-language book files, multi-genre support, series/edition linking, enums, DTOs, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Enums](#03--enums)
- [04 · Authentication & Performance Notes](#04--authentication--performance-notes)
- [05 · Create Writing](#05--create-writing) — `POST /` (multipart)
- [06 · Update Writing](#06--update-writing) — `PUT /{id}` (multipart, partial-merge)
- [07 · Read](#07--read)
  - `GET /` (getAll)
  - `GET /{id}`
- [08 · Delete](#08--delete) — `DELETE /{id}`
- [09 · Series Management](#09--series-management)
  - `GET /series/parents`
  - `POST /series/link`
  - `GET /series/{seriesId}`
- [10 · Search Endpoints](#10--search-endpoints)
  - `GET /search/writer`
  - `GET /search/tag`
  - `GET /search/keyword`
- [11 · Topics](#11--topics) — `GET /topics`
- [12 · DTO Reference](#12--dto-reference)
- [13 · Error Responses](#13--error-responses)
- [14 · Change Log — Old vs. New](#14--change-log--old-vs-new)

---

## 01 · Overview

The Writing module manages bilingual book/writing publishments for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each writing carries:

- **Three cover image slots** — CKB cover, KMR cover, and a hover overlay (entity-level, not embedded)
- **Per-language content blocks** (`ckbContent` / `kmrContent`) each holding: title, description, writer name, book file URL, file format, file size, page count, and a free-text genre label
- **Per-language book files** — separate upload slots (`ckbBookFile`, `kmrBookFile`) so the Sorani and Kurmanji files can be different
- **Multiple book genres** (`Set<BookGenre>`) — a book can belong to more than one genre (e.g. `HISTORY + NOVEL` for a historical novel). Stored in the `writing_book_genres` collection table
- **Bilingual tags and keywords**
- An optional **topic** (looked up by ID or created inline via `TopicPayload`)
- **Series / edition support** — books can be grouped into an ordered series with `seriesId`, `seriesOrder`, `seriesName`, `parentBook`, and `seriesTotalBooks`
- A flag `publishedByInstitute` marking KHI-produced books
- A full **audit trail** via `WritingLog`

### Base URL

```
/api/v1/writings

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create writing — `multipart/form-data` |
| `PUT` | `/{id}` | Update writing — `multipart/form-data` (partial-merge) |
| `GET` | `/` | Paginated list of all writings (sorted `createdAt DESC`) |
| `GET` | `/{id}` | Fetch a single writing by ID |
| `DELETE` | `/{id}` | Delete a single writing |
| `GET` | `/series/parents` | List all series parent books |
| `POST` | `/series/link` | Link a book to a series |
| `GET` | `/series/{seriesId}` | Get all books in a series |
| `GET` | `/search/writer` | Search by writer name (language-aware) |
| `GET` | `/search/tag` | Search by tag (language-aware) |
| `GET` | `/search/keyword` | Search by keyword (language-aware) |
| `GET` | `/topics` | List all `WRITING` topics for autocomplete |

> ℹ️ **Endpoint count:** Old doc header advertised "14 Endpoints" but its summary table listed 12; the controller exposes **12**. The new doc reflects the actual 12.

---

## 02 · Data Models

Three JPA entities and one embeddable make up the Writing module. `Writing` is the aggregate root.

### Writing — `writings`

Aggregate root. Manages `@PrePersist` / `@PreUpdate` lifecycle hooks for `createdAt`, `updatedAt`, and auto-generated `seriesId` / default `seriesOrder`.

**DB indexes:** `idx_writing_topic_id`, `idx_writing_institute`, `idx_writing_created_at`, `idx_writing_updated_at`, `idx_writer_ckb`, `idx_writer_kmr`, `idx_series_id`, `idx_series_composite (series_id, series_order)`, `idx_parent_book`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `ckbCoverUrl` | `ckb_cover_url` | TEXT | NULLABLE | Sorani cover |
| `kmrCoverUrl` | `kmr_cover_url` | TEXT | NULLABLE | Kurmanji cover |
| `hoverCoverUrl` | `hover_cover_url` | TEXT | NULLABLE | Hover overlay image |
| `bookGenres` | writing_book_genres | @ElementCollection (EAGER, BatchSize 25) | NOT NULL (≥1) | `Set<BookGenre>` — at least one required |
| `topic` | `topic_id` | FK → publishment_topics | NULLABLE | Associated topic (`entityType = "WRITING"`). LAZY |
| `seriesId` | `series_id` | VARCHAR(100) | NULLABLE | Auto-generated on `@PrePersist` as `"series-{timestamp}"` if null |
| `seriesName` | `series_name` | VARCHAR(300) | NULLABLE | Human-readable series name |
| `seriesOrder` | `series_order` | DOUBLE | NULLABLE | Position within the series. Default `1.0` on `@PrePersist` |
| `parentBook` | `parent_book_id` | FK → writings | NULLABLE | Parent book for series members. `null` = standalone or series parent |
| `seriesBooks` | — | @OneToMany (LAZY) | NULLABLE | Child books in this series. `@OrderBy("seriesOrder ASC")`. **No cascade, no orphanRemoval** |
| `seriesTotalBooks` | `series_total_books` | INT | NULLABLE | Declared total books in the series |
| `contentLanguages` | writing_content_languages | @ElementCollection (EAGER, BatchSize 25) | NOT NULL (≥1) | `Set<Language>` |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani: title, description, writer, fileUrl, fileFormat, fileSizeBytes, pageCount, genre |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji: title, description, writer, fileUrl, fileFormat, fileSizeBytes, pageCount, genre |
| `publishedByInstitute` | `published_by_institute` | BOOLEAN | NOT NULL | `true` if published by the KHI institute |
| `keywordsCkb` | writing_keywords_ckb | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | CKB keyword strings (120 chars each) |
| `keywordsKmr` | writing_keywords_kmr | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | KMR keyword strings (120 chars each) |
| `tagsCkb` | writing_tags_ckb | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | CKB tag strings (80 chars each) |
| `tagsKmr` | writing_tags_kmr | @ElementCollection (EAGER, BatchSize 25) | NULLABLE | KMR tag strings (80 chars each) |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` (`LocalDateTime`) |
| `updatedAt` | `updated_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` and `@PreUpdate` |

> ℹ️ **Series auto-generation:** On `@PrePersist`, if `seriesId` is `null`, the entity auto-assigns `"series-{System.currentTimeMillis()}"`. If `seriesOrder` is `null`, it defaults to `1.0`. Every book technically belongs to a single-book series by default unless explicitly linked to a shared series.

> ℹ️ **Cover URL fallback:** `getAnyCoverUrl()` returns the first non-blank URL in priority order: `ckbCoverUrl` → `kmrCoverUrl` → `hoverCoverUrl`.

**Helper methods on `Writing`:**

| Method | Returns | Description |
| --- | --- | --- |
| `isPartOfSeries()` | `boolean` | `seriesId != null && (seriesTotalBooks == null \|\| seriesTotalBooks > 1)` |
| `isSeriesParent()` | `boolean` | `parentBook == null && isPartOfSeries()` |
| `getEffectiveSeriesName()` | `String` | `seriesName`, else CKB title, else KMR title, else `"Unknown Series"` |
| `getAnyCoverUrl()` | `String` | First non-blank cover URL (CKB → KMR → hover) |
| `hasGenre(genre)` | `boolean` | `true` if `bookGenres` contains the given genre |
| `getPrimaryGenre()` | `BookGenre` | First genre in the set, or `null` if empty |

> ⚠️ **DB Migration (single-genre → multi-genre):** If upgrading from a schema with a single `book_genre` column:
>
> ```sql
> CREATE TABLE IF NOT EXISTS writing_book_genres (
>     writing_id BIGINT NOT NULL REFERENCES writings(id) ON DELETE CASCADE,
>     book_genre VARCHAR(30) NOT NULL,
>     PRIMARY KEY (writing_id, book_genre)
> );
> CREATE INDEX idx_wbg_genre ON writing_book_genres (book_genre);
>
> INSERT INTO writing_book_genres (writing_id, book_genre)
> SELECT id, book_genre FROM writings WHERE book_genre IS NOT NULL
> ON CONFLICT DO NOTHING;
>
> ALTER TABLE writings DROP COLUMN IF EXISTS book_genre;
> DROP INDEX IF EXISTS idx_writing_genre;
> ```

### WritingContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `Writing`. Holds all language-specific text AND file fields. Cover images are intentionally excluded — they live as dedicated columns on the `Writing` entity.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(300) | Book title |
| `description` | `description_ckb` | `description_kmr` | TEXT | Book description / synopsis |
| `writer` | `writer_ckb` | `writer_kmr` | VARCHAR(200) | Author/writer name |
| `fileUrl` | `file_url_ckb` | `file_url_kmr` | VARCHAR(1000) | Book file URL (PDF, DOCX, EPUB, etc.) |
| `fileFormat` | `file_format_ckb` | `file_format_kmr` | VARCHAR(20) | `WritingFileFormat` enum value |
| `fileSizeBytes` | `file_size_bytes_ckb` | `file_size_bytes_kmr` | BIGINT | File size in bytes |
| `pageCount` | `page_count_ckb` | `page_count_kmr` | INT | Number of pages in this language version |
| `genre` | `genre_ckb` | `genre_kmr` | VARCHAR(150) | Free-text genre label (e.g. `"Novel"`, `"ڕۆمان"`) |

### WritingLog — `writing_logs`

Append-only audit log. Uses both a live FK (`writing`) and a denormalized snapshot column (`writingId`) so DELETE log entries can survive after the Writing row has been removed.

**DB indexes:** `idx_wlog_writing`, `idx_wlog_writing_ref`, `idx_wlog_action`, `idx_wlog_created_at`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `writing` | `writing_id` | FK → writings | NULLABLE | Live FK. Set to `null` for DELETE logs to avoid FK constraint violations |
| `writingId` | `writing_id_ref` | BIGINT | **NOT NULL** | Denormalized ID snapshot — always populated, survives deletion |
| `action` | `action` | VARCHAR(40) | NOT NULL | `"CREATED"` \| `"UPDATED"` \| `"DELETED"` \| `"FILE_UPLOADED"` |
| `actorId` | `actor_id` | VARCHAR(120) | NULLABLE | ID of the acting user/principal |
| `actorName` | `actor_name` | VARCHAR(200) | NULLABLE | Actor's display name |
| `requestId` | `request_id` | VARCHAR(120) | NULLABLE | Trace/request ID for debugging |
| `meta` | `meta` | VARCHAR(1000) | NULLABLE | Additional metadata (IP, device, etc.) |
| `details` | `details` | TEXT | NULLABLE | Details of what changed (free-text or JSON diff) |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Defaults to `LocalDateTime.now()` on `@PrePersist` |

> ⚠️ **WritingLog DELETE design:** During a delete, the service sets `writing = null` on the log entry before flush to prevent a `TransientPropertyValueException`. The `writingId` ref column still holds the original PK as a tombstone for audit queries.

---

## 03 · Enums

### BookGenre

A book can belong to **multiple** genres simultaneously. Accepts case-insensitive JSON input via `@JsonCreator`.

| Group | Values |
| --- | --- |
| Literature & Creative Writing | `POETRY` (شیعر), `NOVEL` (ڕۆمان), `SHORT_STORY` (چیرۆکی کورت), `DRAMA` (شانۆ) |
| Humanities | `HISTORY` (مێژوو), `BIOGRAPHY` (ژیاننامە), `PHILOSOPHY` (فەلسەفە), `RELIGION` (ئایین), `FOLKLORE` (زارگوتن) |
| Social & Political Sciences | `POLITICS` (سیاسەت), `SOCIOLOGY` (کۆمەڵناسی), `ECONOMICS` (ئابووری), `LAW` (یاسا) |
| Language & Arts | `LINGUISTICS` (زمانناسی), `ARTS` (هونەر), `CULTURAL` (کولتووری) |
| Science & Applied Fields | `SCIENCE` (زانست), `MEDICINE` (پزیشکی), `EDUCATIONAL` (پەروەردەیی) |
| Special Categories | `CHILDREN` (منداڵان), `TRAVEL` (گەشتوگوزار), `OTHER` (یتر) |

**22 values total.**

### WritingFileFormat

Accepts case-insensitive JSON input via `@JsonCreator`.

| Value | Description |
| --- | --- |
| `PDF` | PDF documents |
| `DOCX` | Microsoft Word (modern) |
| `DOC` | Microsoft Word (legacy) |
| `TXT` | Plain text |
| `EPUB` | E-book format |
| `ODT` | OpenDocument Text |
| `RTF` | Rich Text Format |
| `HTML` | HTML document |
| `OTHER` | Other formats |

### Language

| Value | Description |
| --- | --- |
| `CKB` | Sorani Kurdish |
| `KMR` | Kurmanji Kurdish |

---

## 04 · Authentication & Performance Notes

> ℹ️ **All writing endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> 🚧 **No `@Cacheable` / `@CacheEvict` layer here.** Like the Video module, the Writing service has **no caching annotations**. Every read hits the DB directly. The performance budget comes from `@BatchSize(25)` and DB indexes alone.

> ⚡ **N+1 Protection — `@BatchSize(25)` strategy:** All six `@ElementCollection` fields use `@BatchSize(25)`. For a page of 20 writings, Hibernate fires ~7 focused `IN`-queries instead of 120 individual SELECTs.
>
> | Query | Target |
> | --- | --- |
> | Q1 | `writings` — base rows |
> | Q2 | `writing_book_genres` |
> | Q3 | `writing_content_languages` |
> | Q4 | `writing_keywords_ckb` |
> | Q5 | `writing_keywords_kmr` |
> | Q6 | `writing_tags_ckb` |
> | Q7 | `writing_tags_kmr` |

> ℹ️ **Default sort:** `GET /`, `GET /series/parents`, and all `/search/*` endpoints return writings sorted by `createdAt DESC`.

> ℹ️ **Update semantics:** On `PUT /{id}`, a `null` field in the `data` JSON means "do not change that field." Collections (`bookGenres`, `tags`, `keywords`, `contentLanguages`) are replaced only when their value is non-null in the DTO.

---

## 05 · Create Writing

### `POST /api/v1/writings` — Multipart

🔒 **Auth Required** · `Content-Type: multipart/form-data`

Create a new writing/book. The JSON payload goes in the `data` part. Cover images and per-language book files are uploaded as additional multipart parts.

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON String | **Yes** | Full `CreateRequest` as a JSON string |
| `ckbCoverImage` | File (image/*) | No | Sorani cover image. Stored in `ckbCoverUrl` after upload |
| `kmrCoverImage` | File (image/*) | No | Kurmanji cover image |
| `hoverCoverImage` | File (image/*) | No | Hover overlay image |
| `ckbBookFile` | File (any) | No | Sorani book file (PDF, EPUB, DOCX, etc.). Sets `ckbContent.fileUrl` after upload |
| `kmrBookFile` | File (any) | No | Kurmanji book file. Sets `kmrContent.fileUrl` after upload |

---

### `data` JSON Part — Both Languages, Multi-Genre, File Upload

```json
{
  "contentLanguages":     ["CKB", "KMR"],
  "bookGenres":           ["NOVEL", "HISTORY"],
  "topicId":              3,
  "publishedByInstitute": true,
  "ckbContent": {
    "title":         "شاری خەموکی",
    "description":   "ڕۆمانێکی مێژوویی دەربارەی ژیانی کوردی لە سەدەی بیستەم",
    "writer":        "ئەحمەد کەریم",
    "fileUrl":       null,
    "fileFormat":    "PDF",
    "fileSizeBytes": 0,
    "pageCount":     320,
    "genre":         "ڕۆمانی مێژووی"
  },
  "kmrContent": {
    "title":         "Bajarê Xemgîn",
    "description":   "Romaneke dîrokî li ser jiyana Kurdî di sedsala bîstem de",
    "writer":        "Ehmed Kerîm",
    "fileUrl":       null,
    "fileFormat":    "PDF",
    "fileSizeBytes": 0,
    "pageCount":     335,
    "genre":         "Romana Dîrokî"
  },
  "tags":     { "ckb": ["ڕۆمان", "مێژوو", "کوردستان"], "kmr": ["roman", "dîrok", "Kurdistan"] },
  "keywords": { "ckb": ["ئەدەبی کوردی", "سەدەی بیست"], "kmr": ["edebiyata kurdî", "sedsala bîstem"] },
  "seriesId":     null,
  "seriesName":   null,
  "seriesOrder":  1.0,
  "parentBookId": null
}
```

> ℹ️ `fileUrl: null` and `fileSizeBytes: 0` indicate the book binary will be supplied via `ckbBookFile` / `kmrBookFile` multipart parts. The service fills in the S3 URL and auto-detected file size after upload.

---

### `data` JSON Part — CKB Only, Single Genre, URL-Only Sources

```json
{
  "contentLanguages":     ["CKB"],
  "bookGenres":           ["POETRY"],
  "topicId":              5,
  "publishedByInstitute": false,
  "ckbContent": {
    "title":         "گولێکی سووری",
    "description":   "دیوانی شیعری کلاسیکی کوردی",
    "writer":        "محمد شیخ ئەلی",
    "fileUrl":       "https://cdn.khi.iq/writings/poetry-001.pdf",
    "fileFormat":    "PDF",
    "fileSizeBytes": 5242880,
    "pageCount":     180,
    "genre":         "شیعر"
  },
  "tags":     { "ckb": ["شیعر", "کلاسیک", "کوردی"], "kmr": [] },
  "keywords": { "ckb": ["ئەدەبیات", "دیوان"],       "kmr": [] },
  "seriesOrder": 1.0
}
```

---

### `data` JSON Part — Inline Topic Creation, Series Member

```json
{
  "contentLanguages": ["CKB", "KMR"],
  "bookGenres":       ["HISTORY"],
  "newTopic":         { "nameCkb": "مێژووی کوردستان", "nameKmr": "Dîroka Kurdistanê" },
  "publishedByInstitute": true,
  "ckbContent": {
    "title":      "ئەنسیکلۆپیدیای کوردستان — بەشی یەکەم",
    "description": "وردبینی مێژووی کوردستان لە دەمی کۆنەوە",
    "writer":     "ئەرشیفی کهی",
    "fileUrl":    null,
    "fileFormat": "PDF",
    "pageCount":  520
  },
  "kmrContent": {
    "title":      "Ensîklopediya Kurdistanê — Beşa Yekem",
    "description": "Vekolîna berfireh a dîroka Kurdistanê ji kevnariyê ve",
    "writer":     "Arşîva KHI",
    "fileUrl":    null,
    "fileFormat": "PDF",
    "pageCount":  540
  },
  "tags":     { "ckb": ["مێژوو", "ئەنسیکلۆپیدیا"], "kmr": ["dîrok", "ensîklopedî"] },
  "keywords": { "ckb": ["کوردستان", "ئەرشیف"],       "kmr": ["Kurdistan", "arşîv"] },
  "seriesId":    "series-encyclopedia-kurdistan",
  "seriesName":  "ئەنسیکلۆپیدیای کوردستان",
  "seriesOrder": 1.0
}
```

---

### Request · curl Example (Both Languages + File Upload)

```bash
curl -X POST https://api.khi.iq/api/v1/writings \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "contentLanguages":["CKB","KMR"],
    "bookGenres":["NOVEL","HISTORY"],
    "topicId":3,
    "publishedByInstitute":true,
    "ckbContent":{"title":"شاری خەموکی","description":"ڕۆمانی مێژووی","writer":"ئەحمەد کەریم","fileFormat":"PDF","pageCount":320,"genre":"ڕۆمانی مێژووی"},
    "kmrContent":{"title":"Bajarê Xemgîn","description":"Romana dîrokî","writer":"Ehmed Kerîm","fileFormat":"PDF","pageCount":335,"genre":"Romana Dîrokî"},
    "tags":{"ckb":["ڕۆمان","مێژوو"],"kmr":["roman","dîrok"]},
    "keywords":{"ckb":["ئەدەبی کوردی"],"kmr":["edebiyata kurdî"]},
    "seriesOrder":1.0
  };type=application/json' \
  -F "ckbCoverImage=@ckb-cover.jpg;type=image/jpeg" \
  -F "kmrCoverImage=@kmr-cover.jpg;type=image/jpeg" \
  -F "hoverCoverImage=@hover.jpg;type=image/jpeg" \
  -F "ckbBookFile=@novel-ckb.pdf;type=application/pdf" \
  -F "kmrBookFile=@novel-kmr.pdf;type=application/pdf"
```

---

### Response · 201 Created

```json
{
  "success": true,
  "message": "Writing created successfully",
  "data": {
    "id":               88,
    "contentLanguages": ["CKB", "KMR"],
    "ckbCoverUrl":      "https://cdn.khi.iq/writings/88/ckb-cover.jpg",
    "kmrCoverUrl":      "https://cdn.khi.iq/writings/88/kmr-cover.jpg",
    "hoverCoverUrl":    "https://cdn.khi.iq/writings/88/hover.jpg",
    "ckbContent": {
      "title":         "شاری خەموکی",
      "description":   "ڕۆمانێکی مێژوویی دەربارەی ژیانی کوردی لە سەدەی بیستەم",
      "writer":        "ئەحمەد کەریم",
      "fileUrl":       "https://cdn.khi.iq/writings/88/novel-ckb.pdf",
      "fileFormat":    "PDF",
      "fileSizeBytes": 8388608,
      "pageCount":     320,
      "genre":         "ڕۆمانی مێژووی"
    },
    "kmrContent": {
      "title":         "Bajarê Xemgîn",
      "description":   "Romaneke dîrokî li ser jiyana Kurdî di sedsala bîstem de",
      "writer":        "Ehmed Kerîm",
      "fileUrl":       "https://cdn.khi.iq/writings/88/novel-kmr.pdf",
      "fileFormat":    "PDF",
      "fileSizeBytes": 9175040,
      "pageCount":     335,
      "genre":         "Romana Dîrokî"
    },
    "topic":      { "id": 3, "nameCkb": "ئەدەب", "nameKmr": "Edebiyat" },
    "bookGenres": ["NOVEL", "HISTORY"],
    "publishedByInstitute": true,
    "tags":     { "ckb": ["ڕۆمان", "مێژوو", "کوردستان"], "kmr": ["roman", "dîrok", "Kurdistan"] },
    "keywords": { "ckb": ["ئەدەبی کوردی", "سەدەی بیست"], "kmr": ["edebiyata kurdî", "sedsala bîstem"] },
    "seriesInfo": {
      "seriesId":    "series-1763871800000",
      "seriesName":  null,
      "seriesOrder": 1.0,
      "parentBookId": null,
      "totalBooks":  null,
      "isParent":    true
    },
    "createdAt": "2026-04-11T21:30:00",
    "updatedAt": "2026-04-11T21:30:00"
  }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | (`@NotEmpty` envelope) | `contentLanguages` is empty or null |
| `400` | (`@NotEmpty` envelope) | `bookGenres` is null or empty |
| `400` | various (`@Size`, `@Min`) | DTO size/min constraint violations |
| `400` | Invalid JSON | `data` part is not valid JSON |
| `401` | — | Missing or expired JWT |

---

## 06 · Update Writing

### `PUT /api/v1/writings/{id}`

🔒 **Auth Required** · `Content-Type: multipart/form-data`

**Partial-merge** update. Only non-null fields in the `data` JSON are applied. Collections (`bookGenres`, `tags`, `keywords`, `contentLanguages`) are fully replaced when non-null; `null` means "leave unchanged." To clear the topic, send `"clearTopic": true`. To replace book files, upload new `ckbBookFile` / `kmrBookFile` parts.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the writing to update |

### Form Parts

Same as `POST /` — all parts except `data` are optional.

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `contentLanguages` | Replaces the language set | Existing kept |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | Replaces (if no matching file part) | Existing kept |
| `ckbContent` / `kmrContent` | Replaces the block | Existing kept |
| `topicId` | Assigns existing WRITING topic by ID | Existing kept |
| `newTopic` | Creates and assigns a new inline topic | Existing kept |
| `clearTopic: true` | Removes the topic relation | No-op |
| `bookGenres` (non-null) | **Replaces** the entire genre set | Existing kept |
| `publishedByInstitute` | Replaces the flag | Existing kept |
| `tags` / `keywords` (non-null) | **Replaces** the entire set | Existing kept |
| `seriesName` / `seriesOrder` / `parentBookId` | Replaces the value | Existing kept |

> ℹ️ The update DTO **does not include `seriesId`** — series ID is managed via `POST /series/link` or the auto-generation logic, not via `PUT /{id}`.

### `data` JSON Part — Update Genres + Bilingual Content

```json
{
  "bookGenres": ["NOVEL", "HISTORY", "BIOGRAPHY"],
  "ckbContent": {
    "title":      "شاری خەموکی — چاپی دووەم",
    "description": "نوێکراوەوەی ڕۆمانی مێژووی",
    "writer":     "ئەحمەد کەریم",
    "pageCount":  340
  },
  "kmrContent": {
    "title":      "Bajarê Xemgîn — Çapa Duyem",
    "description": "Nûvekirina romana dîrokî",
    "writer":     "Ehmed Kerîm",
    "pageCount":  355
  },
  "tags": { "ckb": ["ڕۆمان", "مێژوو", "بیۆگرافیا"], "kmr": ["roman", "dîrok", "biyografî"] }
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
{ "newTopic": { "nameCkb": "ئەدەبی نوێ", "nameKmr": "Edebiyata Nû" } }
```

### `data` JSON Part — Update Series Metadata

```json
{
  "seriesName":   "ئەنسیکلۆپیدیای کوردستان — ویرایشکراوە",
  "seriesOrder":  2.0,
  "parentBookId": 85
}
```

### `data` JSON Part — Toggle Institute Flag Only

```json
{ "publishedByInstitute": false }
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/writings/88 \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "bookGenres":["NOVEL","HISTORY","BIOGRAPHY"],
    "tags":{"ckb":["ڕۆمان","مێژوو","بیۆگرافیا"],"kmr":["roman","dîrok","biyografî"]}
  };type=application/json' \
  -F "ckbCoverImage=@new-cover.jpg;type=image/jpeg"
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Writing updated successfully",
  "data": { /* full Response — same shape as create */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | various | Same validation envelopes as `POST /` |
| `401` | — | Missing or expired JWT |
| `404` | — | Writing with given `id` does not exist |

---

## 07 · Read

### `GET /api/v1/writings` — getAll

🔒 **Auth Required**

Returns a paginated list of all writings sorted by `createdAt DESC`.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Items per page |

### Request

```
GET /api/v1/writings?page=0&size=20
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Writings fetched successfully",
  "data": {
    "content":          [ /* Response objects */ ],
    "pageable":         { "pageNumber": 0, "pageSize": 20, "sort": { "sorted": true } },
    "totalElements":    145,
    "totalPages":       8,
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

### `GET /api/v1/writings/{id}`

🔒 **Auth Required**

Fetch a single writing by primary key.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the writing |

### Response · 200 OK

Same shape as one element of `getAll` `content`.

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |
| `404` | Writing with given `id` does not exist |

---

## 08 · Delete

### `DELETE /api/v1/writings/{id}`

🔒 **Auth Required**

Permanently deletes a writing. A `WritingLog` record is created with `action = "DELETED"`. The log entry sets `writing = null` (live FK) but preserves `writingId` as a tombstone so audit history is retained.

> ℹ️ `seriesBooks` children are **not** cascade-deleted. The `@OneToMany(mappedBy = "parentBook")` relation does not set `cascade = CascadeType.ALL`. Child books retain their `parent_book_id` FK until explicitly updated — investigate this before deleting a series parent in production.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the writing to delete |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Writing deleted successfully",
  "data":    null
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |
| `404` | Writing with given `id` does not exist |

---

## 09 · Series Management

### `GET /api/v1/writings/series/parents`

🔒 **Auth Required**

Returns all writings that are series parents — i.e., books where `parentBook = null` and `isPartOfSeries() = true`. Sorted `createdAt DESC`. Default page size is `100`.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `100` | Items per page |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Series parents fetched",
  "data": {
    "content":       [ /* full Response objects for parent books only */ ],
    "totalElements": 12,
    "totalPages":    1
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `POST /api/v1/writings/series/link`

🔒 **Auth Required** · `Content-Type: application/json`

Link an existing book to a series by assigning it a `parentBook` and a `seriesOrder`. Validated with `@Valid`. Both books must already exist.

### Request Body — `LinkToSeriesRequest`

```json
{
  "bookId":       92,
  "parentBookId": 88,
  "seriesOrder":  2.0,
  "seriesName":   "ئەنسیکلۆپیدیای کوردستان"
}
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Book linked to series",
  "data": {
    "id":         92,
    "ckbContent": { "title": "ئەنسیکلۆپیدیای کوردستان — بەشی دووەم" },
    "seriesInfo": {
      "seriesId":     "series-encyclopedia-kurdistan",
      "seriesName":   "ئەنسیکلۆپیدیای کوردستان",
      "seriesOrder":  2.0,
      "parentBookId": 88,
      "totalBooks":   null,
      "isParent":     false
    },
    "createdAt": "2026-04-10T09:00:00",
    "updatedAt": "2026-04-12T11:30:00"
  }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `@NotNull` envelopes | `bookId`, `parentBookId`, or `seriesOrder` is null |
| `400` | `@Min(1)` envelope | `seriesOrder` is less than 1 |
| `401` | — | Missing or expired JWT |
| `404` | — | `bookId` or `parentBookId` does not exist |

---

### `GET /api/v1/writings/series/{seriesId}`

🔒 **Auth Required**

Returns all books that share the given `seriesId`, summarized as lightweight `SeriesBookSummary` items ordered by `seriesOrder ASC`.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `seriesId` | String | **Yes** | The series identifier string |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Series books fetched",
  "data": {
    "seriesId":   "series-encyclopedia-kurdistan",
    "seriesName": "ئەنسیکلۆپیدیای کوردستان",
    "totalBooks": 3,
    "books": [
      { "id": 88, "titleCkb": "ئەنسیکلۆپیدیای کوردستان — بەشی یەکەم",  "titleKmr": "Ensîklopediya Kurdistanê — Beşa Yekem",  "seriesOrder": 1.0, "createdAt": "2026-04-09T10:00:00" },
      { "id": 92, "titleCkb": "ئەنسیکلۆپیدیای کوردستان — بەشی دووەم",  "titleKmr": "Ensîklopediya Kurdistanê — Beşa Duyem",  "seriesOrder": 2.0, "createdAt": "2026-04-10T09:00:00" },
      { "id": 97, "titleCkb": "ئەنسیکلۆپیدیای کوردستان — بەشی سێیەم",  "titleKmr": "Ensîklopediya Kurdistanê — Beşa Sêyem",  "seriesOrder": 3.0, "createdAt": "2026-04-11T08:00:00" }
    ]
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |
| `404` | No books found for the given `seriesId` |

---

## 10 · Search Endpoints

All three search endpoints accept a `language` parameter to scope the search to a specific language or both.

### Language Parameter Values

| Value | Behavior |
| --- | --- |
| `ckb` | Search only in CKB (Sorani) fields |
| `kmr` | Search only in KMR (Kurmanji) fields |
| `both` | Search in both CKB and KMR fields (default) |

> ℹ️ All three search endpoints sort results by `createdAt DESC`.

---

### `GET /api/v1/writings/search/writer`

🔒 **Auth Required**

Search writings by writer/author name. Searches `writer_ckb` when `language=ckb`, `writer_kmr` when `language=kmr`, or both when `language=both`.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `name` | String | **Yes** | — | Writer name to search |
| `language` | String | No | `both` | `ckb` \| `kmr` \| `both` |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Request

```
GET /api/v1/writings/search/writer?name=Ehmed+Kerim&language=both&page=0&size=20
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `name` is missing or blank |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/writings/search/tag`

🔒 **Auth Required**

Search writings by tag value with optional language scope.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `tag` | String | **Yes** | — | Tag value to search |
| `language` | String | No | `both` | `ckb` \| `kmr` \| `both` |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `tag` is missing or blank |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/writings/search/keyword`

🔒 **Auth Required**

Search writings by keyword value with optional language scope.

### Query Parameters

| Param | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `keyword` | String | **Yes** | — | Keyword value to search |
| `language` | String | No | `both` | `ckb` \| `kmr` \| `both` |
| `page` | int | No | `0` | Zero-based page index |
| `size` | int | No | `20` | Items per page |

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `keyword` is missing or blank |
| `401` | Missing or expired JWT |

---

## 11 · Topics

### `GET /api/v1/writings/topics`

🔒 **Auth Required**

Returns all `PublishmentTopic` records with `entityType = "WRITING"`. Designed for frontend autocomplete/dropdown. Returns the minimum fields: `id`, `nameCkb`, `nameKmr`. Null names are returned as empty strings.

### Response · 200 OK

```json
{
  "success": true,
  "message": "WRITING topics fetched",
  "data": [
    { "id": 1, "nameCkb": "ئەدەب",        "nameKmr": "Edebiyat" },
    { "id": 2, "nameCkb": "مێژوو",         "nameKmr": "Dîrok" },
    { "id": 3, "nameCkb": "ئەنسیکلۆپیدیا", "nameKmr": "Ensîklopedî" }
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

Sent as a JSON string in the `data` multipart part. Validated with Bean Validation.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `contentLanguages` | `Set<Language>` | **Yes** | `@NotNull @NotEmpty` |
| `ckbCoverUrl` | String | No | `@Size(max=2000)` |
| `kmrCoverUrl` | String | No | `@Size(max=2000)` |
| `hoverCoverUrl` | String | No | `@Size(max=2000)` |
| `ckbContent` | `LanguageContentDto` | No* | Used when CKB is in `contentLanguages` |
| `kmrContent` | `LanguageContentDto` | No* | Used when KMR is in `contentLanguages` |
| `topicId` | Long | No | ID of an existing `WRITING` topic. Takes precedence over `newTopic` |
| `newTopic` | `TopicPayload` | No | Creates and assigns a new topic inline |
| `bookGenres` | `Set<BookGenre>` | **Yes** | `@NotNull @NotEmpty` |
| `publishedByInstitute` | boolean | No | Default `false` |
| `tags` | `BilingualSet` | No | Bilingual tag sets |
| `keywords` | `BilingualSet` | No | Bilingual keyword sets |
| `seriesId` | String | No | `@Size(max=100)` |
| `seriesName` | String | No | `@Size(max=300)` |
| `seriesOrder` | Double | No | `@Min(0)`. Default `1.0` on persist |
| `parentBookId` | Long | No | ID of the series parent book |

### UpdateRequest

All fields are optional — partial-merge.

| Field | Type | Description |
| --- | --- | --- |
| `contentLanguages` | `Set<Language>` | Replaces the content language set |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | String | Replaces each cover URL |
| `ckbContent` / `kmrContent` | `LanguageContentDto` | Replaces each content block |
| `topicId` | Long | Assigns existing topic by ID |
| `newTopic` | `TopicPayload` | Creates and assigns a new topic inline |
| `clearTopic` | Boolean | `true` removes the current topic. Default `false` |
| `bookGenres` | `Set<BookGenre>` | **Replaces** the entire genre set. `null` = keep existing |
| `publishedByInstitute` | Boolean | Replaces the institute flag |
| `tags` / `keywords` | `BilingualSet` | Replaces each bilingual set. `null` = keep existing |
| `seriesName` | String | `@Size(max=300)` |
| `seriesOrder` | Double | `@Min(0)` |
| `parentBookId` | Long | Assigns or replaces the parent book |

> ℹ️ **`seriesId` is not in `UpdateRequest`** — series ID is managed via `POST /series/link` and the auto-generation logic on create.

### Response

Returned by all write and read endpoints. **Note: no `@JsonInclude(NON_NULL)` annotation** — null fields are serialized as `null` in the JSON (unlike Video / Image Collection / SoundTrack which omit them).

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `contentLanguages` | `Set<Language>` | Active languages |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | String | Cover URLs |
| `ckbContent` / `kmrContent` | `LanguageContentDto` | Bilingual content blocks |
| `topic` | `TopicInfo` | `{ id, nameCkb, nameKmr }` or `null` |
| `bookGenres` | `Set<BookGenre>` | All genres assigned |
| `publishedByInstitute` | boolean | Institute project flag |
| `tags` / `keywords` | `BilingualSet` | Bilingual tag / keyword sets |
| `seriesInfo` | `SeriesInfoDto` | Series metadata |
| `createdAt` / `updatedAt` | `LocalDateTime` | ISO-8601 local datetime |

### LanguageContentDto

| Field | Type | Validation | Description |
| --- | --- | --- | --- |
| `title` | String | `@Size(max=300)` | Book title in this language |
| `description` | String | `@Size(max=10000)` | Description / synopsis |
| `writer` | String | `@Size(max=200)` | Author/writer name |
| `fileUrl` | String | `@Size(max=1000)` | Book file URL |
| `fileFormat` | `WritingFileFormat` | — | Enum value |
| `fileSizeBytes` | Long | `@Min(0)` | File size in bytes |
| `pageCount` | Integer | `@Min(1)` | Page count |
| `genre` | String | `@Size(max=150)` | Free-text genre label in this language |

### SeriesInfoDto

| Field | Type | Description |
| --- | --- | --- |
| `seriesId` | String | Series identifier string |
| `seriesName` | String | Human-readable series name, or `null` |
| `seriesOrder` | Double | Position of this book within the series |
| `parentBookId` | Long | ID of the parent book, or `null` if this book is the parent |
| `totalBooks` | Integer | Declared total books in the series, or `null` |
| `isParent` | boolean | `true` when `parentBook = null` and `isPartOfSeries() = true` |

### SeriesBookSummary

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `titleCkb` | String | Sorani title |
| `titleKmr` | String | Kurmanji title |
| `seriesOrder` | Double | Position within the series |
| `createdAt` | `LocalDateTime` | When the book was created |

### SeriesResponse

| Field | Type | Description |
| --- | --- | --- |
| `seriesId` | String | Series identifier |
| `seriesName` | String | Human-readable series name |
| `totalBooks` | Integer | Number of books in the series |
| `books` | `List<SeriesBookSummary>` | Books ordered by `seriesOrder ASC` |

### LinkToSeriesRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bookId` | Long | **Yes** | `@NotNull` |
| `parentBookId` | Long | **Yes** | `@NotNull` |
| `seriesOrder` | Double | **Yes** | `@NotNull @Min(1)` |
| `seriesName` | String | No | `@Size(max=300)` |

### TopicPayload

| Field | Type | Description |
| --- | --- | --- |
| `nameCkb` | String | `@Size(max=300)` |
| `nameKmr` | String | `@Size(max=300)` |

### TopicInfo

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key of the topic |
| `nameCkb` | String | Sorani topic name |
| `nameKmr` | String | Kurmanji topic name |

### BilingualSet

| Field | Type | Description |
| --- | --- | --- |
| `ckb` | `Set<String>` | CKB strings. Tags max 80 chars; Keywords max 120 chars |
| `kmr` | `Set<String>` | KMR strings. Same caps |

### SearchRequest *(defined but not used by any controller endpoint)*

A `SearchRequest` POJO exists in the DTOs (`bookGenres`, `instituteOnly`, `writer`, `language`, `seriesId`, `seriesParentsOnly`) but is **not currently wired into the controller**. The three exposed `/search/*` endpoints take individual query params instead. Treat `SearchRequest` as forward-looking infrastructure.

### ApiResponse&lt;T&gt;

All endpoints return this wrapper. `data` is omitted on failure due to `@JsonInclude(NON_NULL)`.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure |

---

## 13 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New writing saved successfully |
| `200 OK` | Update, delete, read, series link, or search succeeded |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | Writing with given id does not exist |
| `500 Internal Error` | Unexpected server failure — check logs |

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "errors": [
    { "field": "contentLanguages", "message": "At least one content language is required" },
    { "field": "bookGenres",       "message": "At least one book genre is required" }
  ]
}
```

### Common Business Error Keys

These are the keys most commonly surfaced by the Writing service. Some validations come straight from Bean Validation `@NotEmpty`/`@Size`/`@Min` envelopes rather than dedicated business keys.

| Error Key / Source | Trigger |
| --- | --- |
| `@NotNull @NotEmpty` on `contentLanguages` | `contentLanguages` is empty or null on create |
| `@NotNull @NotEmpty` on `bookGenres` | `bookGenres` is null or empty on create |
| `@Size` / `@Min` envelopes | Length/min constraint violations on DTO fields |
| Invalid JSON (`IllegalArgumentException`) | `data` part is not parseable JSON — controller throws `IllegalArgumentException("Invalid JSON: …")` |
| `writing.not.found` | No writing found for the given `id` |
| `writing.topic.not.found` | `topicId` provided but no matching WRITING topic exists |
| `writing.series.book.not.found` | `bookId` or `parentBookId` in `/series/link` does not exist |
| `writing.series.not.found` | No books found for the given `seriesId` |

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
  "message":   "Writing not found with id: 88"
}
```

> ℹ️ All `createdAt` and `updatedAt` fields in the `Response` DTO are `LocalDateTime`.

---

## 14 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /` | Multipart create | Multipart create | ⚪ Unchanged |
| `PUT /{id}` | Multipart update | Multipart update — **partial-merge** semantics formalized | 🟡 Behaviour clarified |
| `GET /` | Paginated list | Paginated list (sorted `createdAt DESC`) | ⚪ Unchanged |
| `GET /{id}` | Get by id | Get by id | ⚪ Unchanged |
| `DELETE /{id}` | Delete | Delete (returns `ApiResponse<Void>`, not `204`) | ⚪ Unchanged |
| `GET /series/parents` | List parents | List parents | ⚪ Unchanged |
| `POST /series/link` | Link to series | Link to series | ⚪ Unchanged |
| `GET /series/{seriesId}` | Series books | Series books | ⚪ Unchanged |
| `GET /search/writer` | Writer search | Writer search | ⚪ Unchanged |
| `GET /search/tag` | Tag search | Tag search | ⚪ Unchanged |
| `GET /search/keyword` | Keyword search | Keyword search | ⚪ Unchanged |
| `GET /topics` | WRITING topics | WRITING topics | ⚪ Unchanged |

**Endpoint count:** Old doc header said "14 Endpoints" but its summary table listed 12; the controller exposes **12**. The new doc reflects the actual 12.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `writings` DB indexes | Not documented | 🟢 Documented: `idx_writing_topic_id`, `idx_writing_institute`, `idx_writing_created_at`, `idx_writing_updated_at`, `idx_writer_ckb`, `idx_writer_kmr`, `idx_series_id`, `idx_series_composite (series_id, series_order)`, `idx_parent_book` |
| `writing_logs` DB indexes | Not documented | 🟢 Documented: `idx_wlog_writing`, `idx_wlog_writing_ref`, `idx_wlog_action`, `idx_wlog_created_at` |
| `WritingContent.fileSizeBytes` KMR column | 🟥 **Doc typo**: old doc showed `file_size_bytes_ckb` for BOTH CKB and KMR | 🟢 Corrected: KMR column is `file_size_bytes_kmr` (per the `@AttributeOverride`) |
| `seriesBooks` ordering | "ordered by `seriesOrder ASC`" | ⚪ Unchanged — `@OrderBy("seriesOrder ASC")` confirmed |
| Helper methods on `Writing` | Only `getAnyCoverUrl()` documented | 🟢 Now documented: `isPartOfSeries()`, `isSeriesParent()`, `getEffectiveSeriesName()`, `getAnyCoverUrl()`, `hasGenre(genre)`, `getPrimaryGenre()` |
| `@ElementCollection` fetch type | Not specified | 🟢 Documented: all six collections are `FetchType.EAGER` + `@BatchSize(25)` |
| `WritingLog.writingId` (ref column) | Documented as NOT NULL | ⚪ Unchanged — confirmed `nullable = false` |

### C) Enum comparison

| Enum | Old | New |
| --- | --- | --- |
| `BookGenre` count | Old listed 16 generic values with disclaimer "exact values depend on your enum" | 🟢 Actual is **22 values** grouped into 6 thematic categories |
| `BookGenre` extras | Old missed: `PHILOSOPHY`, `RELIGION` (named `RELIGIOUS`), `POLITICS` (named `POLITICAL`), `SOCIOLOGY`, `ECONOMICS`, `LAW`, `ARTS`, `CULTURAL`, `SCIENCE`, `MEDICINE`, `EDUCATIONAL`, `TRAVEL`, `GEOGRAPHY` (replaced by `TRAVEL`) | 🟢 Full set documented with Kurdish glosses |
| `BookGenre` renames vs. old | `RELIGIOUS → RELIGION`, `POLITICAL → POLITICS`, `ACADEMIC → EDUCATIONAL`, `REFERENCE → OTHER`/`EDUCATIONAL`-adjacent | 🟡 The doc's old guess differs from code |
| `BookGenre.@JsonCreator` / `@JsonValue` | Not documented | 🟢 Documented: case-insensitive JSON input via `@JsonCreator`, name output via `@JsonValue` |
| `WritingFileFormat` count | 5 (`PDF`, `DOCX`, `EPUB`, `TXT`, `OTHER`) with "exact values depend on your enum" disclaimer | 🟢 Actual is **9 values**: `PDF`, `DOCX`, `DOC`, `TXT`, `EPUB`, `ODT`, `RTF`, `HTML`, `OTHER` |
| `WritingFileFormat.@JsonCreator` | Not documented | 🟢 Documented |

### D) DTO comparison

| Item | Old | New |
| --- | --- | --- |
| `CreateRequest` shape | Same | ⚪ Unchanged |
| `UpdateRequest.seriesId` | Old doc implied it could be updated | 🟡 **Corrected**: `seriesId` is NOT in `UpdateRequest` — only `seriesName`, `seriesOrder`, `parentBookId` can be updated. Series ID is managed via `POST /series/link` or auto-generation |
| `UpdateRequest.clearTopic` | Documented as `boolean` | 🟡 Corrected: actual type is `Boolean` (nullable wrapper) |
| `Response.@JsonInclude(NON_NULL)` | Old doc implied null fields are omitted | 🟡 **Corrected**: `Response` has no `@JsonInclude` annotation — null fields are serialized as `null`. This differs from Video / Image Collection / SoundTrack |
| `LanguageContentDto.description` size | Not specified | 🟢 `@Size(max=10000)` |
| `LanguageContentDto.fileSizeBytes` | Not specified | 🟢 `@Min(0)` validation |
| `LanguageContentDto.pageCount` | Not specified | 🟢 `@Min(1)` validation |
| `LanguageContentDto.writer` size | Documented as max 200 | ⚪ Unchanged — confirmed `@Size(max=200)` |
| `BilingualSet` per-element max lengths | Documented (Tags 80 / Keywords 120) | ⚪ Unchanged |
| `SearchRequest` DTO | Not mentioned | 🟢 **Documented but flagged as unused** — exists in the DTOs (`bookGenres`, `instituteOnly`, `writer`, `language`, `seriesId`, `seriesParentsOnly`) but is **not wired into the controller** |

### E) Validation / error-key comparison

| Old error key | New form | Change |
| --- | --- | --- |
| `writing.languages.required` | `@NotNull @NotEmpty` envelope | 🟡 Surfaced as Bean Validation envelope, not a dedicated key |
| `writing.genres.required` | `@NotNull @NotEmpty` envelope | 🟡 Same as above |
| `writing.ckb.title.required` / `writing.kmr.title.required` | Not enforced as dedicated keys | 🔴 Removed — title is optional via `@Size(max=300)` only |
| `writing.not.found` | `writing.not.found` | ⚪ Unchanged |
| `writing.topic.not.found` | `writing.topic.not.found` | ⚪ Unchanged |
| `writing.series.book.not.found` | `writing.series.book.not.found` | ⚪ Unchanged |
| `writing.series.not.found` | `writing.series.not.found` | ⚪ Unchanged |
| `writing.search.name.required` / `writing.search.tag.required` / `writing.search.keyword.required` | Bean Validation envelope (missing query param `400`) | 🟡 No longer dedicated business keys — the controller relies on the framework's `MissingServletRequestParameterException` |
| `error.json.parse` | `IllegalArgumentException("Invalid JSON: …")` | 🟡 Surfaced as `400 IllegalArgumentException` — there's no `error.json.parse` key in the code; the controller throws this generic exception |

### F) Caching & performance

| Item | Old | New |
| --- | --- | --- |
| `@Cacheable` / `@CacheEvict` | Not claimed | 🚧 **Confirmed absent** — like the Video module, the Writing service has no caching layer. Every read hits the DB |
| `@BatchSize(25)` on all `@ElementCollection` | Documented | ⚪ Unchanged |
| `@ElementCollection(FetchType.EAGER)` | Not specified | 🟢 Documented |

### G) Summary

- 🟢 **Added (documentation):** full DB-index inventory across `writings` and `writing_logs`; corrected `WritingContent.fileSizeBytes` KMR column name (was a typo); helper-method catalogue on `Writing` (`isPartOfSeries`, `isSeriesParent`, `getEffectiveSeriesName`, `hasGenre`, `getPrimaryGenre`); `@JsonCreator`/`@JsonValue` behaviour on `BookGenre` and `WritingFileFormat`; the `SearchRequest` DTO (with a flag noting it is **not** wired into the controller yet).
- 🟢 **Added (enums):** the `BookGenre` enum now documents all **22 actual values** in 6 thematic groups with Kurdish glosses (the old doc listed 16 guesses with a disclaimer); `WritingFileFormat` now documents all **9 actual values** (old doc listed 5 guesses).
- 🟡 **Corrected:** `UpdateRequest` does **not** include `seriesId` — series ID lives elsewhere (auto-generated on persist or assigned via `POST /series/link`); `UpdateRequest.clearTopic` is `Boolean` (nullable), not primitive `boolean`; `Response` has **no `@JsonInclude(NON_NULL)`** — null fields are serialized as `null` (unlike Video / Image Collection / SoundTrack).
- 🔴 **Removed:** the dedicated error keys `writing.ckb.title.required`, `writing.kmr.title.required`, `writing.search.*.required`, `writing.languages.required`, `writing.genres.required`, `error.json.parse` — most of these are now surfaced as Bean Validation envelopes or generic exceptions rather than business-key strings.
- 🚧 **Caching absent:** the Writing service has no `@Cacheable` / `@CacheEvict` annotations (same as Video). Worth flagging if alignment with the News / Image Collection / SoundTrack performance behaviour is desired.
- ⚪ **Unchanged:** all 12 endpoints (paths, query/path params, multipart layout), the three cover slots (entity-level), `WritingContent` embeddable, the auto-generated `seriesId = "series-{timestamp}"` pattern, default `seriesOrder = 1.0`, partial-merge update semantics, `ApiResponse<T>` envelope on every endpoint, `WritingLog` tombstone design with `writingId` ref column, `@OrderBy("seriesOrder ASC")` on `seriesBooks`, and the `ON DELETE SET NULL` semantics on the topic FK.
