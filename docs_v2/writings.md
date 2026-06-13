# Writings Module (Books)

> Bilingual (CKB / KMR) book entries with multi-genre support, multipart cover and book-file uploads, series management, multi-axis search, topic taxonomy. Public reads, role-gated writes.

## Table of Contents

- [01 - Module Overview](#01--module-overview)
- [02 - Data Model - Writing](#02--data-model--writing)
- [03 - Data Model - WritingContent](#03--data-model--writingcontent)
- [04 - Data Model - WritingLog](#04--data-model--writinglog)
- [05 - Enums (BookGenre, WritingFileFormat, Language)](#05--enums-bookgenre-writingfileformat-language)
- [06 - Authentication & Roles](#06--authentication--roles)
- [07 - Public API](#07--public-api)
- [08 - Internal API](#08--internal-api)
- [09 - Series Management](#09--series-management)
- [10 - DTO Reference](#10--dto-reference)
- [11 - Multipart Layout](#11--multipart-layout)
- [12 - Response Envelope](#12--response-envelope)
- [13 - Error Responses](#13--error-responses)
- [14 - Notes](#14--notes)

---

## 01 - Module Overview

The Writings module manages bilingual (Sorani CKB / Kurmanji KMR) book entries. Each writing carries up to three cover images (CKB, KMR, hover overlay), two book-file binaries (CKB, KMR), a multi-genre classification, optional topic linkage, bilingual tags & keywords, and full series/edition support (parent + linked children).

- **Base path**: `/api/v1/writings`
- **Writes**: `multipart/form-data` only (cover images & book files travel as parts; the structured JSON payload travels as the `data` part).
- **Series link** (`POST /series/link`): `application/json`.
- **Multi-genre**: `bookGenres` is a `Set<BookGenre>` (e.g. a historical novel = `["HISTORY", "NOVEL"]`).
- **Default sort**: `createdAt DESC`.
- **Reads**: all GETs are public per the global `GET /api/v1/writings/**` rule.

### Endpoint summary

| # | Method | Path | Purpose | Auth |
|---|--------|------|---------|------|
| 1 | POST | `/api/v1/writings` | Create writing (multipart) | EMPLOYEE+ |
| 2 | GET | `/api/v1/writings` | List writings (paginated) | Public |
| 3 | GET | `/api/v1/writings/{id}` | Get writing by id | Public |
| 4 | PUT | `/api/v1/writings/{id}` | Update writing (multipart) | EMPLOYEE+ |
| 5 | DELETE | `/api/v1/writings/{id}` | Delete writing | ADMIN+ |
| 6 | GET | `/api/v1/writings/series/parents` | List series-parent books | Public |
| 7 | POST | `/api/v1/writings/series/link` | Link a book to a parent series | EMPLOYEE+ |
| 8 | GET | `/api/v1/writings/series/{seriesId}` | Get all books in a series | Public |
| 9 | GET | `/api/v1/writings/search/writer` | Search by writer name | Public |
| 10 | GET | `/api/v1/writings/search/tag` | Search by tag | Public |
| 11 | GET | `/api/v1/writings/search/keyword` | Search by keyword | Public |
| 12 | GET | `/api/v1/writings/topics` | List WRITING topics (autocomplete) | Public |

---

## 02 - Data Model - Writing

Entity: `ak.dev.khi_backend.khi_app.model.publishment.writing.Writing`
Table: `writings`

### Table-level indexes

| Index name | Columns |
|------------|---------|
| `idx_writing_topic_id` | `topic_id` |
| `idx_writing_institute` | `published_by_institute` |
| `idx_writing_created_at` | `created_at` |
| `idx_writing_updated_at` | `updated_at` |
| `idx_writer_ckb` | `writer_ckb` |
| `idx_writer_kmr` | `writer_kmr` |
| `idx_series_id` | `series_id` |
| `idx_series_composite` | `series_id, series_order` |
| `idx_parent_book` | `parent_book_id` |

### Columns

| Field | Type | Column | Constraints / Notes |
|-------|------|--------|---------------------|
| `id` | `Long` | `id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `ckbCoverUrl` | `String` | `ckb_cover_url` | `columnDefinition = "TEXT"` - Sorani cover |
| `kmrCoverUrl` | `String` | `kmr_cover_url` | `columnDefinition = "TEXT"` - Kurmanji cover |
| `hoverCoverUrl` | `String` | `hover_cover_url` | `columnDefinition = "TEXT"` - hover overlay |
| `topic` | `PublishmentTopic` | `topic_id` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `seriesId` | `String` | `series_id` | `length = 100` - auto-set in `@PrePersist` if null: `"series-" + currentTimeMillis()` |
| `seriesName` | `String` | `series_name` | `length = 300` |
| `seriesOrder` | `Double` | `series_order` | Defaults to `1.0` in `@PrePersist` if null |
| `parentBook` | `Writing` | `parent_book_id` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `seriesBooks` | `List<Writing>` | (mappedBy) | `@OneToMany(mappedBy = "parentBook")`, `@OrderBy("seriesOrder ASC")` |
| `seriesTotalBooks` | `Integer` | `series_total_books` | Recomputed by service after CREATE / UPDATE / DELETE / link |
| `publishedByInstitute` | `boolean` | `published_by_institute` | `nullable = false` |
| `ckbContent` | `WritingContent` | (embedded) | See Section 03 - column-overridden with `_ckb` suffix |
| `kmrContent` | `WritingContent` | (embedded) | See Section 03 - column-overridden with `_kmr` suffix |
| `createdAt` | `LocalDateTime` | `created_at` | `nullable = false, updatable = false` - set in `@PrePersist` |
| `updatedAt` | `LocalDateTime` | `updated_at` | `nullable = false` - touched in `@PrePersist` and `@PreUpdate` |

### `@ElementCollection` side tables

All marked with `@BatchSize(size = 25)` and eager fetch.

| Field | Collection table | Join column | Value column | Element type |
|-------|------------------|-------------|--------------|--------------|
| `bookGenres` | `writing_book_genres` | `writing_id` | `book_genre` (`length = 30`, `nullable = false`, `@Enumerated(EnumType.STRING)`) | `BookGenre` |
| `contentLanguages` | `writing_content_languages` | `writing_id` | `language` (`length = 10`, `nullable = false`, `@Enumerated(EnumType.STRING)`) | `Language` |
| `keywordsCkb` | `writing_keywords_ckb` | `writing_id` | `keyword_ckb` (`length = 120`, `nullable = false`) | `String` |
| `keywordsKmr` | `writing_keywords_kmr` | `writing_id` | `keyword_kmr` (`length = 120`, `nullable = false`) | `String` |
| `tagsCkb` | `writing_tags_ckb` | `writing_id` | `tag_ckb` (`length = 80`, `nullable = false`) | `String` |
| `tagsKmr` | `writing_tags_kmr` | `writing_id` | `tag_kmr` (`length = 80`, `nullable = false`) | `String` |

### Embedded language blocks

`ckbContent` and `kmrContent` are two `WritingContent` embeddables stored on the same `writings` row. Each field on the embeddable maps to a column suffixed by `_ckb` or `_kmr`:

| Embedded field | CKB column | KMR column |
|----------------|------------|------------|
| `title` | `title_ckb` (`length = 300`) | `title_kmr` (`length = 300`) |
| `description` | `description_ckb` (TEXT) | `description_kmr` (TEXT) |
| `writer` | `writer_ckb` (`length = 200`) | `writer_kmr` (`length = 200`) |
| `fileUrl` | `file_url_ckb` (`length = 1000`) | `file_url_kmr` (`length = 1000`) |
| `fileFormat` | `file_format_ckb` (`length = 20`) | `file_format_kmr` (`length = 20`) |
| `fileSizeBytes` | `file_size_bytes_ckb` | `file_size_bytes_kmr` |
| `pageCount` | `page_count_ckb` | `page_count_kmr` |
| `genre` | `genre_ckb` (`length = 150`) | `genre_kmr` (`length = 150`) |

### Lifecycle helpers

```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (seriesId == null)    seriesId    = "series-" + System.currentTimeMillis();
    if (seriesOrder == null) seriesOrder = 1.0;
}

@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

### Entity convenience helpers

| Method | Returns |
|--------|---------|
| `isPartOfSeries()` | `true` if `seriesId != null` and `seriesTotalBooks == null` or `> 1` |
| `isSeriesParent()` | `true` if `parentBook == null` AND `isPartOfSeries()` |
| `getEffectiveSeriesName()` | First non-blank of `seriesName`, CKB title, KMR title, else `"Unknown Series"` |
| `getAnyCoverUrl()` | First non-blank of `ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl`, else `null` |
| `hasGenre(BookGenre)` | `true` if `bookGenres` contains the supplied genre |
| `getPrimaryGenre()` | First element of `bookGenres` or `null` |

---

## 03 - Data Model - WritingContent

Embeddable: `ak.dev.khi_backend.khi_app.model.publishment.writing.WritingContent`
Type: `@Embeddable` (no own table)

Holds the language-specific text/file fields for a single language slot. Cover images live on the `Writing` entity (three URL columns), not on the embeddable.

| Field | Type | Column (logical) | Constraints |
|-------|------|------------------|-------------|
| `title` | `String` | `title` | `length = 300` |
| `description` | `String` | `description` | `columnDefinition = "TEXT"` - processed by `TiptapHtmlProcessor` on write |
| `writer` | `String` | `writer` | `length = 200` |
| `fileUrl` | `String` | `file_url` | `length = 1000` - S3 URL after upload or external URL |
| `fileFormat` | `WritingFileFormat` | `file_format` | `@Enumerated(EnumType.STRING)`, `length = 20` |
| `fileSizeBytes` | `Long` | `file_size_bytes` | bytes |
| `pageCount` | `Integer` | `page_count` | number of pages |
| `genre` | `String` | `genre` | `length = 150` - free-text language-specific label, distinct from the enum `BookGenre` set on the parent |

(The physical column names above are overridden per-instance to `_ckb` / `_kmr` variants by the `@AttributeOverrides` block on `Writing`.)

---

## 04 - Data Model - WritingLog

Entity: `ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog`
Table: `writing_logs`

Audit log capturing CREATE / UPDATE / DELETE / LINKED_TO_SERIES actions. The FK to `Writing` is nullable so that DELETE rows can survive after the parent row is removed; the denormalised `writingId` (column `writing_id_ref`) preserves the original PK as a tombstone.

### Indexes

| Index name | Columns |
|------------|---------|
| `idx_wlog_writing` | `writing_id` |
| `idx_wlog_writing_ref` | `writing_id_ref` |
| `idx_wlog_action` | `action` |
| `idx_wlog_created_at` | `created_at` |

### Columns

| Field | Type | Column | Constraints |
|-------|------|--------|-------------|
| `id` | `Long` | `id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `writing` | `Writing` | `writing_id` | `@ManyToOne(fetch = FetchType.LAZY, optional = true)`, `nullable = true` - null after DELETE |
| `writingId` | `Long` | `writing_id_ref` | `nullable = false` - tombstone PK |
| `action` | `String` | `action` | `nullable = false`, `length = 40` - one of `CREATED`, `UPDATED`, `DELETED`, `LINKED_TO_SERIES`, `FILE_UPLOADED`, etc. |
| `actorId` | `String` | `actor_id` | `length = 120` |
| `actorName` | `String` | `actor_name` | `length = 200` |
| `requestId` | `String` | `request_id` | `length = 120` |
| `meta` | `String` | `meta` | `length = 1000` |
| `details` | `String` | `details` | `columnDefinition = "TEXT"` - free text or JSON |
| `createdAt` | `LocalDateTime` | `created_at` | `nullable = false` - set in `@PrePersist` if null |

---

## 05 - Enums (BookGenre, WritingFileFormat, Language)

### `BookGenre`

Package: `ak.dev.khi_backend.khi_app.enums.publishment`
`@JsonCreator` accepts case-insensitive trimmed strings; `@JsonValue` serializes the constant name.

| Value | CKB label | Description |
|-------|-----------|-------------|
| `POETRY` | شیعر | Poetry collections |
| `NOVEL` | ڕۆمان | Novels & long-form fiction |
| `SHORT_STORY` | چیرۆکی کورت | Short stories / novellas |
| `DRAMA` | شانۆ | Plays & dramatic works |
| `HISTORY` | مێژوو | Historical works |
| `BIOGRAPHY` | ژیاننامە | Biographies & memoirs |
| `PHILOSOPHY` | فەلسەفە | Philosophy |
| `RELIGION` | ئایین | Religious & theological texts |
| `FOLKLORE` | زارگوتن | Folklore, oral tradition, mythology |
| `POLITICS` | سیاسەت | Political science & theory |
| `SOCIOLOGY` | کۆمەڵناسی | Sociology & social studies |
| `ECONOMICS` | ئابووری | Economics & finance |
| `LAW` | یاسا | Law & legal studies |
| `LINGUISTICS` | زمانناسی | Linguistics & language studies |
| `ARTS` | هونەر | Visual arts, music, crafts |
| `CULTURAL` | کولتووری | Cultural studies & heritage |
| `SCIENCE` | زانست | Natural & applied sciences |
| `MEDICINE` | پزیشکی | Medical & health sciences |
| `EDUCATIONAL` | پەروەردەیی | Textbooks & academic works |
| `CHILDREN` | منداڵان | Children's books |
| `TRAVEL` | گەشتوگوزار | Travel & geography |
| `OTHER` | یتر | Uncategorised / other |

### `WritingFileFormat`

| Value | Description |
|-------|-------------|
| `PDF` | PDF documents |
| `DOCX` | Microsoft Word (modern) |
| `DOC` | Microsoft Word (legacy) |
| `TXT` | Plain text |
| `EPUB` | E-book format |
| `ODT` | OpenDocument Text |
| `RTF` | Rich Text Format |
| `HTML` | HTML document |
| `OTHER` | Other formats |

### `Language`

| Value | Description |
|-------|-------------|
| `CKB` | Kurdish Central (Sorani) |
| `KMR` | Kurdish Kurmanji |

---

## 06 - Authentication & Roles

The Spring Security config matches the entire `/api/v1/writings/**` subtree:

| Method | Pattern | Roles |
|--------|---------|-------|
| `GET` | `/api/v1/writings/**` | Public |
| `POST` | `/api/v1/writings/**` | EMPLOYEE, ADMIN, SUPER_ADMIN |
| `PUT` | `/api/v1/writings/**` | EMPLOYEE, ADMIN, SUPER_ADMIN |
| `DELETE` | `/api/v1/writings/**` | ADMIN, SUPER_ADMIN |

Per-endpoint resolution:

| Endpoint | Method | Auth | Roles |
|----------|--------|------|-------|
| `/api/v1/writings` | POST | Required | EMPLOYEE+ |
| `/api/v1/writings` | GET | Public | - |
| `/api/v1/writings/{id}` | GET | Public | - |
| `/api/v1/writings/{id}` | PUT | Required | EMPLOYEE+ |
| `/api/v1/writings/{id}` | DELETE | Required | ADMIN+ |
| `/api/v1/writings/series/parents` | GET | Public | - |
| `/api/v1/writings/series/link` | POST | Required | EMPLOYEE+ |
| `/api/v1/writings/series/{seriesId}` | GET | Public | - |
| `/api/v1/writings/search/writer` | GET | Public | - |
| `/api/v1/writings/search/tag` | GET | Public | - |
| `/api/v1/writings/search/keyword` | GET | Public | - |
| `/api/v1/writings/topics` | GET | Public | - |

---

## 07 - Public API

All endpoints in this section return `ResponseEntity<ApiResponse<T>>` with HTTP `200 OK` on success and require no auth.

### 7.1 List writings

```
GET /api/v1/writings?page={page}&size={size}
```

Returns a `Page<Response>` sorted by `createdAt DESC`.

| Query | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

```bash
curl -s "https://api.example.com/api/v1/writings?page=0&size=20"
```

```json
{
  "success": true,
  "message": "Writings fetched successfully",
  "data": {
    "content": [
      {
        "id": 12,
        "contentLanguages": ["CKB", "KMR"],
        "ckbCoverUrl": "https://s3.example.com/writings/12/cover-ckb.jpg",
        "kmrCoverUrl": "https://s3.example.com/writings/12/cover-kmr.jpg",
        "hoverCoverUrl": "https://s3.example.com/writings/12/hover.jpg",
        "ckbContent": {
          "title": "مێژووی کوردستان",
          "description": "<p>کتێبێکی مێژوویی لەسەر کوردستان</p>",
          "writer": "د. عەلی ئەحمەد",
          "fileUrl": "https://s3.example.com/writings/12/book-ckb.pdf",
          "fileFormat": "PDF",
          "fileSizeBytes": 5242880,
          "pageCount": 320,
          "genre": "مێژوو"
        },
        "kmrContent": {
          "title": "Dîroka Kurdistanê",
          "description": "<p>Pirtûkek dîrokî li ser Kurdistanê</p>",
          "writer": "Dr. Eli Ehmed",
          "fileUrl": "https://s3.example.com/writings/12/book-kmr.pdf",
          "fileFormat": "PDF",
          "fileSizeBytes": 5100000,
          "pageCount": 312,
          "genre": "Dîrok"
        },
        "topic": { "id": 4, "nameCkb": "مێژووی کورد", "nameKmr": "Dîroka Kurd" },
        "bookGenres": ["HISTORY", "BIOGRAPHY"],
        "publishedByInstitute": true,
        "tags": { "ckb": ["مێژوو"], "kmr": ["dîrok"] },
        "keywords": { "ckb": ["کوردستان", "کۆن"], "kmr": ["kurdistan"] },
        "seriesInfo": {
          "seriesId": "series-1717159234001",
          "seriesName": "مێژووی کورد",
          "seriesOrder": 1.0,
          "parentBookId": null,
          "totalBooks": 3,
          "isParent": true
        },
        "createdAt": "2026-05-01T10:11:12",
        "updatedAt": "2026-05-12T08:30:00"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 57,
    "totalPages": 3,
    "number": 0,
    "size": 20,
    "first": true,
    "last": false
  }
}
```

### 7.2 Get writing by id

```
GET /api/v1/writings/{id}
```

| Path | Type | Description |
|------|------|-------------|
| `id` | Long | Writing PK |

```bash
curl -s "https://api.example.com/api/v1/writings/12"
```

Response data is a single `Response` object identical to one element of the list above. Errors with `WRITING_NOT_FOUND` if no such writing exists.

### 7.3 List series-parent books

```
GET /api/v1/writings/series/parents?page={page}&size={size}
```

Returns a `Page<Response>` containing only books that are series parents (those with no `parentBook` themselves). Sorted by `createdAt DESC`.

| Query | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Zero-based page index |
| `size` | int | `100` | Page size |

```bash
curl -s "https://api.example.com/api/v1/writings/series/parents?page=0&size=100"
```

```json
{
  "success": true,
  "message": "Series parents fetched",
  "data": { "content": [ /* Response[] */ ], "totalElements": 5, "totalPages": 1 }
}
```

### 7.4 Get all books in a series

```
GET /api/v1/writings/series/{seriesId}
```

Returns a `SeriesResponse` listing all books that share `seriesId`, ordered by `seriesOrder` ascending.

| Path | Type | Description |
|------|------|-------------|
| `seriesId` | String | Series identifier (the entity sets a default `"series-<millis>"` when first created) |

```bash
curl -s "https://api.example.com/api/v1/writings/series/series-1717159234001"
```

```json
{
  "success": true,
  "message": "Series books fetched",
  "data": {
    "seriesId": "series-1717159234001",
    "seriesName": "مێژووی کورد",
    "totalBooks": 3,
    "books": [
      {
        "id": 12,
        "titleCkb": "مێژووی کوردستان",
        "titleKmr": "Dîroka Kurdistanê",
        "seriesOrder": 1.0,
        "createdAt": "2026-05-01T10:11:12"
      },
      {
        "id": 17,
        "titleCkb": "مێژووی کورد ٢",
        "titleKmr": "Dîroka Kurd 2",
        "seriesOrder": 2.0,
        "createdAt": "2026-05-09T09:00:00"
      }
    ]
  }
}
```

Errors with `WRITING_NOT_FOUND` (code `series.not_found`) if the series has zero books.

### 7.5 Search by writer

```
GET /api/v1/writings/search/writer?name={name}&language={ckb|kmr|both}&page={page}&size={size}
```

Returns a `Page<Response>` of books whose CKB / KMR writer name contains the supplied substring (case-insensitive). Sorted by `createdAt DESC`.

| Query | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | String | (required) | Substring to match (trimmed by the service) |
| `language` | String | `both` | `ckb`, `kmr`, or `both` |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

`name` blank/whitespace produces a `BAD_REQUEST` with code `search.writer.required`.

```bash
curl -s "https://api.example.com/api/v1/writings/search/writer?name=%D8%B9%D9%87%D9%84%DB%8C&language=ckb&page=0&size=20"
```

### 7.6 Search by tag

```
GET /api/v1/writings/search/tag?tag={tag}&language={ckb|kmr|both}&page={page}&size={size}
```

Returns a `Page<Response>` of books carrying the supplied tag in CKB / KMR. Sorted by `createdAt DESC`.

| Query | Type | Default | Description |
|-------|------|---------|-------------|
| `tag` | String | (required) | Tag literal to match |
| `language` | String | `both` | `ckb`, `kmr`, or `both` |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

Blank `tag` produces a `BAD_REQUEST` with code `search.tag.required`.

```bash
curl -s "https://api.example.com/api/v1/writings/search/tag?tag=%D9%85%DB%8E%DA%98%D9%88%D9%88&language=both"
```

### 7.7 Search by keyword

```
GET /api/v1/writings/search/keyword?keyword={keyword}&language={ckb|kmr|both}&page={page}&size={size}
```

Returns a `Page<Response>` of books carrying the supplied keyword in CKB / KMR. Sorted by `createdAt DESC`.

| Query | Type | Default | Description |
|-------|------|---------|-------------|
| `keyword` | String | (required) | Keyword literal to match |
| `language` | String | `both` | `ckb`, `kmr`, or `both` |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

Blank `keyword` produces a `BAD_REQUEST` with code `search.keyword.required`.

```bash
curl -s "https://api.example.com/api/v1/writings/search/keyword?keyword=%DA%A9%D9%88%D8%B1%D8%AF%D8%B3%D8%AA%D8%A7%D9%86&language=both"
```

### 7.8 List WRITING topics (autocomplete)

```
GET /api/v1/writings/topics
```

Returns a simplified list of `PublishmentTopic` rows with `entityType = "WRITING"`. Designed for autocomplete pickers in the admin UI.

```bash
curl -s "https://api.example.com/api/v1/writings/topics"
```

```json
{
  "success": true,
  "message": "WRITING topics fetched",
  "data": [
    { "id": 1, "nameCkb": "مێژووی کورد", "nameKmr": "Dîroka Kurd" },
    { "id": 2, "nameCkb": "ئەدەبی کوردی", "nameKmr": "Wêjeya Kurdî" },
    { "id": 3, "nameCkb": "زمانناسی", "nameKmr": "Zimanzanî" }
  ]
}
```

The items are `Map<String, Object>` with three keys only: `id`, `nameCkb`, `nameKmr`. Null names are coerced to `""`.

---

## 08 - Internal API

All endpoints in this section return `ResponseEntity<ApiResponse<T>>` and require a JWT carrying one of the listed roles.

### 8.1 Create writing

```
POST /api/v1/writings
Content-Type: multipart/form-data
```

Roles: **EMPLOYEE, ADMIN, SUPER_ADMIN**

The `data` part is a JSON-encoded `CreateRequest`. The five file parts are optional individually but at least one cover image and at least one matching book file should be supplied for usable output. The service performs S3 upload of each file part before saving.

Status on success: `201 CREATED` with body `ApiResponse.success(response, "Writing created successfully")`.

Multipart parts:

| Part | Required | Type | Maps to |
|------|----------|------|---------|
| `data` | Yes | `application/json` (string) | `CreateRequest` JSON payload |
| `ckbCoverImage` | No | image binary | uploaded to S3, populates `Writing.ckbCoverUrl` (overrides `CreateRequest.ckbCoverUrl`) |
| `kmrCoverImage` | No | image binary | uploaded to S3, populates `Writing.kmrCoverUrl` (overrides `CreateRequest.kmrCoverUrl`) |
| `hoverCoverImage` | No | image binary | uploaded to S3, populates `Writing.hoverCoverUrl` (overrides `CreateRequest.hoverCoverUrl`) |
| `ckbBookFile` | No | book binary (PDF / EPUB / DOCX / etc.) | uploaded to S3, populates `Writing.ckbContent.fileUrl` |
| `kmrBookFile` | No | book binary | uploaded to S3, populates `Writing.kmrContent.fileUrl` |

`data` JSON shape - see [Section 10](#10--dto-reference) for the full DTO field tables. Example body:

```json
{
  "contentLanguages": ["CKB", "KMR"],
  "ckbCoverUrl": null,
  "kmrCoverUrl": null,
  "hoverCoverUrl": null,
  "ckbContent": {
    "title": "مێژووی کوردستان",
    "description": "<p>کتێبێکی گرنگ لەسەر مێژووی کوردستان</p>",
    "writer": "د. عەلی ئەحمەد",
    "fileFormat": "PDF",
    "fileSizeBytes": 5242880,
    "pageCount": 320,
    "genre": "مێژوو"
  },
  "kmrContent": {
    "title": "Dîroka Kurdistanê",
    "description": "<p>Pirtûkek girîng li ser dîroka Kurdistanê</p>",
    "writer": "Dr. Eli Ehmed",
    "fileFormat": "PDF",
    "fileSizeBytes": 5100000,
    "pageCount": 312,
    "genre": "Dîrok"
  },
  "topicId": 4,
  "newTopic": null,
  "bookGenres": ["HISTORY", "BIOGRAPHY"],
  "publishedByInstitute": true,
  "tags": { "ckb": ["مێژوو", "کوردستان"], "kmr": ["dîrok", "kurdistan"] },
  "keywords": { "ckb": ["مێژوو"], "kmr": ["dîrok"] },
  "seriesId": null,
  "seriesName": "مێژووی کورد",
  "seriesOrder": 1.0,
  "parentBookId": null
}
```

Validation that the service enforces beyond Bean Validation:

- `contentLanguages` must be non-empty (`writing.languages.required`).
- `bookGenres` must be non-empty (`writing.genres.required`).
- For every language in `contentLanguages`, the matching `ckbContent` / `kmrContent` block must be present (`writing.content.missing`).
- On CREATE only, the title for each chosen language must be non-blank (`writing.title.required`).

Topic resolution rules:

- If `topicId` is set, the existing topic is looked up; missing topic produces `topic.not_found`.
- Else if `newTopic` carries at least one non-blank name, a new `PublishmentTopic(entityType = "WRITING")` is created inline.
- Else `topic` stays null.

Series initialisation:

- If `parentBookId` is set, `seriesId` is copied from the parent, and `seriesOrder` defaults to `max(seriesOrder) + 1` within the parent's series.
- Otherwise, the entity's `@PrePersist` assigns `seriesId = "series-" + currentTimeMillis()` and `seriesOrder = 1.0`.

```bash
curl -X POST "https://api.example.com/api/v1/writings" \
  -H "Authorization: Bearer ${JWT}" \
  -F 'data=@create.json;type=application/json' \
  -F "ckbCoverImage=@cover-ckb.jpg" \
  -F "kmrCoverImage=@cover-kmr.jpg" \
  -F "hoverCoverImage=@hover.jpg" \
  -F "ckbBookFile=@book-ckb.pdf" \
  -F "kmrBookFile=@book-kmr.pdf"
```

```json
{
  "success": true,
  "message": "Writing created successfully",
  "data": {
    "id": 27,
    "contentLanguages": ["CKB", "KMR"],
    "ckbCoverUrl": "https://s3.example.com/writings/27/cover-ckb.jpg",
    "kmrCoverUrl": "https://s3.example.com/writings/27/cover-kmr.jpg",
    "hoverCoverUrl": "https://s3.example.com/writings/27/hover.jpg",
    "ckbContent": { "title": "مێژووی کوردستان", "fileUrl": "https://s3.example.com/writings/27/book-ckb.pdf", "fileFormat": "PDF", "pageCount": 320, "genre": "مێژوو" },
    "kmrContent": { "title": "Dîroka Kurdistanê", "fileUrl": "https://s3.example.com/writings/27/book-kmr.pdf", "fileFormat": "PDF", "pageCount": 312, "genre": "Dîrok" },
    "topic": { "id": 4, "nameCkb": "مێژووی کورد", "nameKmr": "Dîroka Kurd" },
    "bookGenres": ["HISTORY", "BIOGRAPHY"],
    "publishedByInstitute": true,
    "tags": { "ckb": ["مێژوو", "کوردستان"], "kmr": ["dîrok", "kurdistan"] },
    "keywords": { "ckb": ["مێژوو"], "kmr": ["dîrok"] },
    "seriesInfo": {
      "seriesId": "series-1748678910000",
      "seriesName": "مێژووی کورد",
      "seriesOrder": 1.0,
      "parentBookId": null,
      "totalBooks": 1,
      "isParent": false
    },
    "createdAt": "2026-05-31T10:25:01",
    "updatedAt": "2026-05-31T10:25:01"
  }
}
```

### 8.2 Update writing

```
PUT /api/v1/writings/{id}
Content-Type: multipart/form-data
```

Roles: **EMPLOYEE, ADMIN, SUPER_ADMIN**

The `data` part is a JSON-encoded `UpdateRequest`. All fields are optional - omit or null to leave that aspect unchanged. Cover-image resolution: an uploaded file part wins; otherwise the URL field in `data` is applied (empty string clears the slot); otherwise the existing value is kept.

Multipart parts: identical to CREATE - see Section 11.

Update-specific behaviour:

- `bookGenres`: if non-null and non-empty, the entire existing genre set is cleared and replaced. Pass `null` (or omit) to leave genres alone.
- `clearTopic = true` wipes the topic; otherwise `topicId` / `newTopic` are resolved the same way as on create.
- `contentLanguages` (when non-empty) is replaced.
- `ckbContent` / `kmrContent`: merge-style - only non-null sub-fields are applied via `mergeContent(...)`. New uploaded `fileUrl` wins over the DTO's `fileUrl`.
- `tags` / `keywords`: each language side replaces its `Set` only if its inner set is non-null. Blank entries are filtered out.
- `seriesName`, `seriesOrder` are applied if non-null.
- `parentBookId`: if non-null, the new parent is looked up; the book's `parentBook` is set, its `seriesId` is copied from the parent, and the *old* series count is recomputed in addition to the new one.

```bash
curl -X PUT "https://api.example.com/api/v1/writings/27" \
  -H "Authorization: Bearer ${JWT}" \
  -F 'data=@update.json;type=application/json' \
  -F "kmrBookFile=@book-kmr-v2.pdf"
```

```json
{
  "success": true,
  "message": "Writing updated successfully",
  "data": { "id": 27, "...": "..." }
}
```

### 8.3 Delete writing

```
DELETE /api/v1/writings/{id}
```

Roles: **ADMIN, SUPER_ADMIN**

Writes a `WritingLog` row with `action = "DELETED"` and `writing = null` (the tombstone PK is preserved in `writing_id_ref`), then deletes the writing and flushes. Series totals for the previous `seriesId` are recomputed afterwards.

```bash
curl -X DELETE "https://api.example.com/api/v1/writings/27" \
  -H "Authorization: Bearer ${JWT}"
```

```json
{
  "success": true,
  "message": "Writing deleted successfully",
  "data": null
}
```

---

## 09 - Series Management

### 9.1 Link an existing book to a series

```
POST /api/v1/writings/series/link
Content-Type: application/json
```

Roles: **EMPLOYEE, ADMIN, SUPER_ADMIN**

Re-parents an existing book under an existing parent book, copying the parent's `seriesId`, taking the supplied `seriesOrder`, and using the supplied `seriesName` if present (else the parent's `seriesName`). The shared series totals are recomputed. A `WritingLog` row with `action = "LINKED_TO_SERIES"` is written.

Request body (`LinkToSeriesRequest`):

| Field | Type | Constraints |
|-------|------|-------------|
| `bookId` | Long | `@NotNull(message = "Book ID is required")` |
| `parentBookId` | Long | `@NotNull(message = "Parent book ID is required")` |
| `seriesOrder` | Double | `@NotNull(message = "Series order is required")`, `@Min(1)` |
| `seriesName` | String | `@Size(max = 300)` |

```bash
curl -X POST "https://api.example.com/api/v1/writings/series/link" \
  -H "Authorization: Bearer ${JWT}" \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": 31,
    "parentBookId": 12,
    "seriesOrder": 3,
    "seriesName": "مێژووی کورد"
  }'
```

```json
{
  "success": true,
  "message": "Book linked to series",
  "data": {
    "id": 31,
    "seriesInfo": {
      "seriesId": "series-1717159234001",
      "seriesName": "مێژووی کورد",
      "seriesOrder": 3.0,
      "parentBookId": 12,
      "totalBooks": 3,
      "isParent": false
    },
    "...": "..."
  }
}
```

Errors:

- Missing `bookId` -> `WRITING_NOT_FOUND` (code `writing.not_found`)
- Missing `parentBookId` -> `WRITING_NOT_FOUND` (code `parent_book.not_found`)
- Validation failures -> `VALIDATION_ERROR`

---

## 10 - DTO Reference

All DTOs live in `ak.dev.khi_backend.khi_app.dto.publishment.writing.WritingDtos` as static nested classes.

### 10.1 `LanguageContentDto`

The per-language content block. Used inside `CreateRequest`, `UpdateRequest`, and `Response`.

| Field | Type | Validation |
|-------|------|------------|
| `title` | `String` | `@Size(max = 300)` |
| `description` | `String` | `@Size(max = 10000)` |
| `writer` | `String` | `@Size(max = 200)` |
| `fileUrl` | `String` | `@Size(max = 1000)` |
| `fileFormat` | `WritingFileFormat` | - |
| `fileSizeBytes` | `Long` | `@Min(0)` |
| `pageCount` | `Integer` | `@Min(1)` |
| `genre` | `String` | `@Size(max = 150)` - free-text language-specific label |

### 10.2 `BilingualSet`

| Field | Type | Notes |
|-------|------|-------|
| `ckb` | `Set<String>` | CKB values |
| `kmr` | `Set<String>` | KMR values |

### 10.3 `TopicPayload`

Used to create a new topic inline alongside a writing.

| Field | Type | Validation |
|-------|------|------------|
| `nameCkb` | `String` | `@Size(max = 300)` |
| `nameKmr` | `String` | `@Size(max = 300)` |

### 10.4 `TopicInfo`

Returned on `Response.topic`.

| Field | Type |
|-------|------|
| `id` | `Long` |
| `nameCkb` | `String` |
| `nameKmr` | `String` |

### 10.5 `SeriesInfoDto`

Returned on `Response.seriesInfo` when the writing has a `seriesId`.

| Field | Type | Notes |
|-------|------|-------|
| `seriesId` | `String` | Stable series key |
| `seriesName` | `String` | Display name |
| `seriesOrder` | `Double` | Position within the series |
| `parentBookId` | `Long` | Null if this book is the series parent |
| `totalBooks` | `Integer` | Computed from `series_total_books` |
| `isParent` | `boolean` | `true` for the parent book |

### 10.6 `SeriesBookSummary`

Element of `SeriesResponse.books`.

| Field | Type |
|-------|------|
| `id` | `Long` |
| `titleCkb` | `String` |
| `titleKmr` | `String` |
| `seriesOrder` | `Double` |
| `createdAt` | `LocalDateTime` |

### 10.7 `CreateRequest`

| Field | Type | Validation |
|-------|------|------------|
| `contentLanguages` | `Set<Language>` | `@NotNull`, `@NotEmpty(message = "At least one content language is required")` |
| `ckbCoverUrl` | `String` | `@Size(max = 2000)` |
| `kmrCoverUrl` | `String` | `@Size(max = 2000)` |
| `hoverCoverUrl` | `String` | `@Size(max = 2000)` |
| `ckbContent` | `LanguageContentDto` | - |
| `kmrContent` | `LanguageContentDto` | - |
| `topicId` | `Long` | - |
| `newTopic` | `TopicPayload` | Used when `topicId` is null |
| `bookGenres` | `Set<BookGenre>` | `@NotNull(message = "At least one book genre is required")`, `@NotEmpty(message = "At least one book genre is required")` |
| `publishedByInstitute` | `boolean` | - |
| `tags` | `BilingualSet` | - |
| `keywords` | `BilingualSet` | - |
| `seriesId` | `String` | `@Size(max = 100)` |
| `seriesName` | `String` | `@Size(max = 300)` |
| `seriesOrder` | `Double` | `@Min(0)` |
| `parentBookId` | `Long` | When set, derives `seriesId` from the parent |

### 10.8 `UpdateRequest`

All fields are nullable / optional.

| Field | Type | Validation |
|-------|------|------------|
| `contentLanguages` | `Set<Language>` | - |
| `ckbCoverUrl` | `String` | - |
| `kmrCoverUrl` | `String` | - |
| `hoverCoverUrl` | `String` | - |
| `ckbContent` | `LanguageContentDto` | - |
| `kmrContent` | `LanguageContentDto` | - |
| `topicId` | `Long` | - |
| `newTopic` | `TopicPayload` | - |
| `clearTopic` | `Boolean` | `true` wipes the topic before topic resolution |
| `bookGenres` | `Set<BookGenre>` | Non-null + non-empty replaces the entire set; null/empty leaves it unchanged |
| `publishedByInstitute` | `Boolean` | - |
| `tags` | `BilingualSet` | - |
| `keywords` | `BilingualSet` | - |
| `seriesName` | `String` | `@Size(max = 300)` |
| `seriesOrder` | `Double` | `@Min(0)` |
| `parentBookId` | `Long` | Re-parent into another series |

### 10.9 `Response`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | |
| `contentLanguages` | `Set<Language>` | Always non-null (empty set if no languages) |
| `ckbCoverUrl` | `String` | |
| `kmrCoverUrl` | `String` | |
| `hoverCoverUrl` | `String` | |
| `ckbContent` | `LanguageContentDto` | Null when no CKB content |
| `kmrContent` | `LanguageContentDto` | Null when no KMR content |
| `topic` | `TopicInfo` | Null when no topic |
| `bookGenres` | `Set<BookGenre>` | Always non-null (may be empty for legacy data) |
| `publishedByInstitute` | `boolean` | |
| `tags` | `BilingualSet` | Always non-null |
| `keywords` | `BilingualSet` | Always non-null |
| `seriesInfo` | `SeriesInfoDto` | Populated when `seriesId != null` |
| `createdAt` | `LocalDateTime` | |
| `updatedAt` | `LocalDateTime` | |

### 10.10 `SeriesResponse`

| Field | Type |
|-------|------|
| `seriesId` | `String` |
| `seriesName` | `String` |
| `totalBooks` | `Integer` |
| `books` | `List<SeriesBookSummary>` |

### 10.11 `LinkToSeriesRequest`

See [Section 9.1](#91-link-an-existing-book-to-a-series).

### 10.12 `SearchRequest` (internal DTO, not bound to an endpoint)

Declared in `WritingDtos` for completeness; the public search endpoints in this controller take individual query parameters rather than this body.

| Field | Type |
|-------|------|
| `bookGenres` | `Set<BookGenre>` |
| `instituteOnly` | `Boolean` |
| `writer` | `String` |
| `language` | `String` |
| `seriesId` | `String` |
| `seriesParentsOnly` | `Boolean` |

---

## 11 - Multipart Layout

`POST /api/v1/writings` and `PUT /api/v1/writings/{id}` both consume `multipart/form-data`. The structured payload is a JSON document passed as a *string-typed* part named `data` (note: it is read via `@RequestPart String dataJson` and parsed with Jackson, so the part's `Content-Type` is not strictly required - clients may send `application/json` for clarity).

| Form-data part | Required | Maps to | Notes |
|----------------|----------|---------|-------|
| `data` | Yes | `CreateRequest` / `UpdateRequest` (JSON string) | Parsed via Jackson; invalid JSON -> `IllegalArgumentException("Invalid JSON: ...")` -> typically surfaced as `VALIDATION_ERROR` |
| `ckbCoverImage` | No | `Writing.ckbCoverUrl` | Uploaded to S3; overrides `data.ckbCoverUrl` when present |
| `kmrCoverImage` | No | `Writing.kmrCoverUrl` | Uploaded to S3; overrides `data.kmrCoverUrl` when present |
| `hoverCoverImage` | No | `Writing.hoverCoverUrl` | Uploaded to S3; overrides `data.hoverCoverUrl` when present |
| `ckbBookFile` | No | `Writing.ckbContent.fileUrl` | Actual book binary (PDF / EPUB / DOCX / etc.) |
| `kmrBookFile` | No | `Writing.kmrContent.fileUrl` | Actual book binary |

Resolution rules per cover slot on **PUT**:

1. If a multipart file is supplied, upload it and store the new URL.
2. Else, if the corresponding URL field on `data` is non-null, set the cover to that URL (empty string clears the slot).
3. Else, leave the existing value untouched.

```bash
curl -X POST "https://api.example.com/api/v1/writings" \
  -H "Authorization: Bearer ${JWT}" \
  -F 'data={"contentLanguages":["CKB","KMR"],"bookGenres":["NOVEL","HISTORY"],"ckbContent":{"title":"شاری ڕۆژاوا","writer":"نووسەری نموونە","fileFormat":"PDF","pageCount":256,"genre":"ڕۆمان"},"kmrContent":{"title":"Bajara Rojava","writer":"Nivîskarê Mînak","fileFormat":"PDF","pageCount":248,"genre":"Roman"},"publishedByInstitute":true,"tags":{"ckb":["ڕۆمان"],"kmr":["roman"]},"keywords":{"ckb":["کوردستان"],"kmr":["kurdistan"]}};type=application/json' \
  -F "ckbCoverImage=@cover-ckb.jpg" \
  -F "kmrCoverImage=@cover-kmr.jpg" \
  -F "hoverCoverImage=@hover.jpg" \
  -F "ckbBookFile=@book-ckb.pdf" \
  -F "kmrBookFile=@book-kmr.pdf"
```

---

## 12 - Response Envelope

Every endpoint returns an `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Writings fetched successfully",
  "data": { /* T */ }
}
```

For paginated endpoints, `T = Page<Response>`. Spring Data serializes a `Page` with the following shape:

```json
{
  "content": [ /* Response[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 57,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "numberOfElements": 20,
  "empty": false,
  "sort": { "sorted": true, "unsorted": false, "empty": false }
}
```

HTTP status codes:

| Operation | Status |
|-----------|--------|
| Create | `201 CREATED` |
| Read / Update / Delete / Search / Topics / Series | `200 OK` |

---

## 13 - Error Responses

All errors follow the project-wide bilingual `ApiErrorResponse` envelope. Common shape:

```json
{
  "success": false,
  "code": "WRITING_NOT_FOUND",
  "messageCkb": "نووسراو نەدۆزرایەوە",
  "messageKmr": "Nivîsar nehat dîtin",
  "details": { "id": 27 },
  "timestamp": "2026-05-31T10:25:01"
}
```

| Code | Trigger | HTTP |
|------|---------|------|
| `WRITING_NOT_FOUND` | `writing.not_found` / `series.not_found` / `parent_book.not_found` / `topic.not_found` | `404` |
| `WRITING_VALIDATION` | `writing.languages.required`, `writing.genres.required`, `writing.content.missing`, `writing.title.required`, `search.writer.required`, `search.tag.required`, `search.keyword.required` | `400` |
| `WRITING_CONFLICT` | Duplicate or conflicting series/edition state | `409` |
| `WRITING_MEDIA_INVALID` | Unsupported file format / corrupt upload | `400` |
| `PAYLOAD_TOO_LARGE` | Multipart part exceeds the configured limit | `413` |
| `VALIDATION_ERROR` | Bean Validation failure on `LinkToSeriesRequest` or invalid `data` JSON | `400` |
| `UNAUTHORIZED` | Missing or invalid JWT on a write endpoint | `401` |
| `FORBIDDEN` | Authenticated but wrong role | `403` |
| `MEDIA_UPLOAD_FAILED` | `media.upload.failed` - S3 I/O failure | `500` |

Note: the `code` strings above (`WRITING_NOT_FOUND`, etc.) are the user-facing envelope codes; the service raises internal keys such as `writing.not_found`, `writing.genres.required`, `search.tag.required`, `media.upload.failed` which the global exception handler maps to the envelope codes and bilingual messages.

---

## 14 - Notes

- **Multi-genre semantics on PUT**: when `bookGenres` is non-null and non-empty, the service clears the existing collection and re-adds the new values verbatim. Pass `null` (or omit the field) to keep the existing genres unchanged. The set is stored as a `LinkedHashSet`, so order is preserved.
- **Default sort**: all list endpoints sort by `createdAt DESC`. The list page size defaults are `20` for `/writings` and the search endpoints, `100` for `/series/parents`.
- **`language` filter values**: `both` (default), `ckb`, `kmr` - case-insensitive. Any other value falls through to the both-language repository query.
- **Topics**: `/topics` is sourced from `PublishmentTopicRepository.findByEntityType("WRITING")`. The same registry is shared with VIDEO / SOUND / IMAGE modules via the `entity_type` discriminator.
- **Series shape**: a series is identified by `seriesId` and exposes both a parent (`parentBook == null` and `isSeriesParent() == true`) and its linked children (via `parentBook` references). `GET /series/{seriesId}` returns the full ordered list flatly via `SeriesBookSummary`.
- **Series initialisation**: the `Writing` entity guarantees a `seriesId` even for standalone books - `@PrePersist` assigns `"series-" + currentTimeMillis()` when `seriesId` is null. `isPartOfSeries()` then returns `false` until at least one sibling joins.
- **Cover-image fallback in CREATE**: if a multipart cover file is not supplied, the corresponding URL field on `CreateRequest` is used as-is. On UPDATE the empty string is treated as a clear instruction.
- **Description processing**: `LanguageContentDto.description` is passed through `TiptapHtmlProcessor.process(...)` before storage. Submit rich Tiptap-emitted HTML.
- **Audit log behaviour**: every CREATE, UPDATE, DELETE, and LINKED_TO_SERIES operation produces a `WritingLog` row with `actorId = "system"`, `actorName = "System"`. DELETE rows have `writing = null` but retain `writingId` (column `writing_id_ref`) as a tombstone.
- **Series count recompute**: after CREATE, UPDATE (both old and new series if `seriesId` changes), DELETE, and series link, the service refreshes `seriesTotalBooks` on every member of the affected series.
- **Cascade**: the `writing_book_genres` collection table has `ON DELETE CASCADE` per the migration block in the entity Javadoc; the other `writing_*` collection tables follow the same JPA `@ElementCollection` semantics.
- **Hibernate `@BatchSize(25)`**: applied to all `@ElementCollection`s on `Writing` to avoid the N+1 problem when loading a page of 20 writings (otherwise 20x6 = 120 extra SELECTs).
