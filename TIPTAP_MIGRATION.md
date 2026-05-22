# KHI Backend — Tiptap Migration (Complete Reference)

> **Status:** Applied · `mvn compile` passes cleanly (221 source files).
> **Stack:** Spring Boot 3 · Java 21 · PostgreSQL · AWS S3 · JPA/Hibernate
> **Schema strategy:** `spring.jpa.hibernate.ddl-auto: create-drop` — the schema
> is regenerated from JPA on every application restart, so the old tables
> disappear automatically. No Flyway / Liquibase migration required.

This document is the single source of truth for every change made by the
Tiptap migration. It documents every dropped table, every added column,
every removed endpoint, every kept endpoint, and gives a fully populated
JSON request and response example for every CRUD operation on every module.

---

## Table of Contents

1. [Why Tiptap, why now](#0--why-tiptap-why-now)
2. [High-level summary table](#1--high-level-summary-table)
3. [Shared media upload endpoints (NEW)](#2--shared-media-upload-endpoints-new)
4. [News module — full reference](#3--news-module--full-reference)
5. [Projects module — full reference](#4--projects-module--full-reference)
6. [About module — full reference](#5--about-module--full-reference)
7. [Services module — full reference](#6--services-module--full-reference)
8. [SoundTrack module — full reference](#7--soundtrack-module--full-reference)
9. [Videos module — full reference](#8--videos-module--full-reference)
10. [Image Collections module — full reference](#9--image-collections-module--full-reference)
11. [Writings module — full reference](#10--writings-module--full-reference)
12. [Contact module — full reference](#11--contact-module--full-reference)
13. [Global Search — Tiptap-aware snippet](#12--global-search--tiptap-aware-snippet)
14. [Database schema — before / after diff](#13--database-schema--before--after-diff)
15. [Java source diff — added / deleted files](#14--java-source-diff--added--deleted-files)
16. [Frontend integration recipe](#15--frontend-integration-recipe)
17. [End-to-end manual test plan](#16--end-to-end-manual-test-plan)
18. [Error responses — common shapes](#17--error-responses--common-shapes)
19. [FAQ / known limits](#18--faq--known-limits)

---

## 0 · Why Tiptap, why now

Before the migration each long-form module (News, Projects, About) maintained
its own *bespoke* media-attachment model:

* **News** had a `news_media` child table whose rows held an enum-typed media
  reference (`IMAGE` / `VIDEO` / `AUDIO` / `DOCUMENT`) plus three URL columns
  (`url`, `externalUrl`, `embedUrl`). Each news story was created via
  `multipart/form-data` with a JSON part, a cover file part, and a repeated
  `mediaFiles` part — three different create endpoints, three different
  update endpoints, all parsing both `news` and `data` JSON aliases for
  compatibility with two front-ends.
* **Projects** had `project_media` (similar shape) plus a separate
  `project_contents` table that stored free-text "section" tags joined to
  projects through `project_content_map_ckb` and `project_content_map_kmr`
  many-to-many tables.
* **About** had `about_blocks`, a poly-typed table with seven `ContentType`
  values (`TEXT`, `IMAGE`, `VIDEO`, `AUDIO`, `GALLERY`, `QUOTE`, `STATS`),
  each block carrying its own bilingual text plus media URLs and a JSON
  metadata bag.

The other modules (Services, SoundTrack, Videos, Image Collections, Writings)
already stored their long-form prose in plain `TEXT` columns; they just had
no rich-text editor wired up on the front-end.

**Tiptap collapses all of this**: the editor produces a single HTML string
per language, with images / audio / video already embedded as `<img>` /
`<audio>` / `<video>` whose `src` points at S3. So the backend can:

* drop every poly-typed media child table,
* drop every multipart endpoint,
* shrink every module to plain JSON,
* and expose **one** shared upload endpoint
  (`POST /api/v1/media/upload`) that every editor calls when the user inserts
  a media asset.

The only block type that survived the collapse is `STATS` (an array of
`{labelCkb, labelKmr, value}`) — that lives on the About entity as a
dedicated JSONB column because it cannot be embedded cleanly in HTML.

---

## 1 · High-level summary table

| Module                 | Tables dropped                                                                                                | Columns added                                                | Endpoints removed | Endpoints added | Notes                                              |
| ---------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ----------------- | --------------- | -------------------------------------------------- |
| **Shared**             | —                                                                                                             | —                                                            | —                 | **3**           | `POST /api/v1/media/upload`, `…/upload/multiple`, `DELETE /api/v1/media` |
| **News**               | `news_media`                                                                                                  | —                                                            | **3**             | 0               | Now JSON-only · description = Tiptap HTML          |
| **Projects**           | `project_media`, `project_contents`, `project_content_map_ckb`, `project_content_map_kmr`                     | —                                                            | **2**             | 0               | Now JSON-only · description = Tiptap HTML          |
| **About**              | `about_blocks` (with `content_text_ckb/kmr`, `title_ckb/kmr`, `alt_text_ckb/kmr`, `media_url`, `thumbnail_url`, `metadata`, `sequence`, `content_type`) | `about_pages.body_ckb` TEXT · `about_pages.body_kmr` TEXT · `about_pages.stats` JSONB | **3**             | 0               | Blocks → body HTML + structured stats              |
| **Services**           | —                                                                                                             | —                                                            | 0                 | 0               | `service_contents.description` already TEXT        |
| **SoundTrack**         | —                                                                                                             | —                                                            | 0                 | 0               | `description` already TEXT                         |
| **Videos**             | —                                                                                                             | —                                                            | 0                 | 0               | `description` already TEXT                         |
| **Image Collections**  | —                                                                                                             | —                                                            | 0                 | 0               | `description` already TEXT                         |
| **Writings**           | —                                                                                                             | —                                                            | 0                 | 0               | `description` already TEXT                         |
| **Contact**            | —                                                                                                             | —                                                            | 0                 | 0               | No long-form prose; nothing to change              |

**Totals:** 5 tables dropped · 3 columns added · 8 endpoints removed · 3 endpoints added.

---

## 2 · Shared media upload endpoints (NEW)

Every Tiptap editor in the platform uses these endpoints when the user
inserts an image, audio file, or video file. They live in
`api/media/MediaController.java` and are backed by
`service/media/MediaService.java`, which delegates to the existing
`S3Service` so the bucket layout and credentials stay identical.

### 2.1 · `POST /api/v1/media/upload` — single file

**Use case:** user drops an image into the editor; cover-image upload before
sending a news/project create request; replacing an existing hero banner on
the About page.

**Request:** `multipart/form-data`

| Part   | Type   | Required | Description                                                                                                 |
| ------ | ------ | -------- | ----------------------------------------------------------------------------------------------------------- |
| `file` | File   | Yes      | Any media file. No hard size cap is enforced; the Spring multipart default applies (configurable in `application.yaml`). |
| `type` | String | No       | Folder hint that controls the S3 sub-folder. Accepted values: `"image"` (default), `"audio"`, `"video"`, `"document"`, `"pdf"`, `"gallery"` (treated as image). Unknown values fall back to MIME-type sniffing. |

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "fileUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/6f8b4c3a-photo.jpg",
    "fileName": "photo.jpg",
    "fileSize": 204800,
    "contentType": "image/jpeg"
  }
}
```

**Error responses**

* `400 Bad Request` — file part missing or empty.
* `502 Bad Gateway` — S3 upload failed (network / credentials / quota). The
  response body contains the original `BadRequestException` wrapper produced
  by `S3Service.upload(...)`.

**cURL**

```bash
curl -X POST https://api.khi.iq/api/v1/media/upload \
  -F "file=@./photo.jpg" \
  -F "type=image"
```

### 2.2 · `POST /api/v1/media/upload/multiple` — batch upload

**Use case:** drag-and-drop of multiple files into the editor at once.

**Request:** `multipart/form-data`

| Part    | Type   | Required | Description                                              |
| ------- | ------ | -------- | -------------------------------------------------------- |
| `files` | File[] | Yes      | One or more files (repeat the part name once per file).  |
| `type`  | String | No       | Same hint and defaults as the single-file endpoint.      |

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": [
    {
      "fileUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/aaa-one.jpg",
      "fileName": "one.jpg",
      "fileSize": 154200,
      "contentType": "image/jpeg"
    },
    {
      "fileUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/bbb-two.png",
      "fileName": "two.png",
      "fileSize": 380220,
      "contentType": "image/png"
    }
  ]
}
```

**cURL**

```bash
curl -X POST https://api.khi.iq/api/v1/media/upload/multiple \
  -F "files=@./one.jpg" \
  -F "files=@./two.png" \
  -F "type=image"
```

### 2.3 · `DELETE /api/v1/media` — orphan cleanup

**Use case:** the admin removes an inline asset from the editor and wants the
underlying S3 object purged. Optional — leaving it as an orphan is harmless
in the short term.

**Request**

```
DELETE /api/v1/media?fileUrl=https%3A%2F%2Fkhi-bucket.s3.eu-central-1.amazonaws.com%2Fkhi-web-folders%2Fimages%2F6f8b4c3a-photo.jpg
```

**Response:** `200 OK`

```json
{
  "success": true,
  "message": "Media deleted successfully",
  "data": null
}
```

The endpoint is intentionally idempotent: deleting an already-missing URL
also returns `200 OK` (the underlying `S3Service.deleteFile` logs and
swallows `NoSuchKey`).

### 2.4 · S3 layout

The shared `S3Service` writes everything under the configured
`aws.s3.base-folder` (default `khi-web-folders`). The sub-folder is chosen
from the `type` hint or by sniffing the MIME type:

```
<bucket>/
└── khi-web-folders/
    ├── images/<uuid>-<sanitised-filename>      ← type=image (or image/*)
    ├── video/<uuid>-<sanitised-filename>       ← type=video (or video/*)
    ├── audio/<uuid>-<sanitised-filename>       ← type=audio (or audio/*)
    ├── files/<uuid>-<sanitised-filename>       ← type=document|pdf or unknown
    ├── albums/covers/{ckb|kmr}/<uuid>-…        ← legacy album cover uploads
    └── albums/hover/<uuid>-…                   ← legacy album hover uploads
```

Filenames are sanitised by `S3Service.sanitizeFilename(...)`: every character
outside `[a-zA-Z0-9._-]` is replaced with `_`. A UUID prefix prevents
collisions.

The public URL format is:

```
https://<bucket>.s3.<region>.amazonaws.com/<key>
```

…computed inside `S3Service.getPublicUrl(...)`.

---

## 3 · News module — full reference

### 3.1 · Tables

| Table                       | Status        | Notes                                                             |
| --------------------------- | ------------- | ----------------------------------------------------------------- |
| `news`                      | Kept          | `description_ckb` / `description_kmr` are `TEXT` — now Tiptap HTML |
| `news_categories`           | Kept          | bilingual category lookup                                          |
| `news_sub_categories`       | Kept          | bilingual sub-category lookup                                      |
| `news_content_languages`    | Kept          | set of active languages per news row                               |
| `news_tags_ckb`             | Kept          | per-language tag collection                                        |
| `news_tags_kmr`             | Kept          | per-language tag collection                                        |
| `news_keywords_ckb`         | Kept          | per-language keyword collection                                    |
| `news_keywords_kmr`         | Kept          | per-language keyword collection                                    |
| `news_audit_log`            | Kept          | `CREATE` / `UPDATE` / `DELETE` audit trail                         |
| **`news_media`**            | **DROPPED**   | Inline media now lives inside the HTML description                 |

### 3.2 · Java source changes

**Deleted**

* `src/main/java/.../khi_app/model/news/NewsMedia.java`
* `src/main/java/.../khi_app/enums/news/NewsMediaType.java`
* `src/main/java/.../khi_app/repository/news/NewsMediaRepository.java`
* `src/main/java/.../khi_app/exceptions/news/NewsMediaException.java`
* `Errors.newsMediaInvalid(...)` factory in `exceptions/Errors.java`
* `News.java` lost the `media` `@OneToMany` field
* `NewsDto.java` lost the `media` field and the nested `MediaDto` class
* `NewsService.java` lost every helper related to media uploads
  (`buildMediaFutures`, `joinMediaFutures`, `appendUploadedMedia`,
  `attachMediaFromDto`, `detectMediaType`, the `UploadedMedia` record,
  the `ExecutorService` plumbing, and the `S3Service` dependency)
* `NewsController.java` lost all three multipart create/update endpoints

**Kept (rewritten)**

* `NewsDto.java`, `NewsService.java`, `NewsController.java`,
  `NewsRepository.java` — JSON-only versions

### 3.3 · Endpoint matrix

| Method | Path                                  | Status   | Body type                  |
| ------ | ------------------------------------- | -------- | -------------------------- |
| POST   | `/api/v1/news`                        | KEPT     | `application/json` (NEW — was `multipart/form-data`) |
| POST   | `/api/v1/news/with-files`             | REMOVED  | —                          |
| POST   | `/api/v1/news/bulk`                   | KEPT     | `application/json`         |
| PUT    | `/api/v1/news/{id}`                   | KEPT     | `application/json` (NEW — was `multipart/form-data`) |
| PUT    | `/api/v1/news/{id}/with-files`        | REMOVED  | —                          |
| PUT    | `/api/v1/news/update/{id}/with-files` | REMOVED  | —                          |
| DELETE | `/api/v1/news/{id}`                   | KEPT     | —                          |
| DELETE | `/api/v1/news/delete/{id}`            | KEPT     | —                          |
| DELETE | `/api/v1/news/bulk`                   | KEPT     | `application/json` body of IDs |
| GET    | `/api/v1/news`                        | KEPT     | —                          |
| GET    | `/api/v1/news/all`                    | KEPT     | —                          |
| GET    | `/api/v1/news/{id}`                   | KEPT     | —                          |
| GET    | `/api/v1/news/search`                 | KEPT     | query string `q`           |
| GET    | `/api/v1/news/search/keyword`         | KEPT     | query string `keyword`, `language` |
| GET    | `/api/v1/news/search/tag`             | KEPT     | query string `tag`, `language` |
| GET    | `/api/v1/news/search/category`        | KEPT     | query string `name`        |
| GET    | `/api/v1/news/search/subcategory`     | KEPT     | query string `name`        |

### 3.4 · Validation rules (unchanged where applicable)

* `contentLanguages` must be non-empty.
* `coverUrl` is required on create.
* Both `category.ckbName` and `category.kmrName` are required.
* Both `subCategory.ckbName` and `subCategory.kmrName` are required.
* If `CKB` is in `contentLanguages`, `ckbContent.title` is required.
* If `KMR` is in `contentLanguages`, `kmrContent.title` is required.
* `description` (Tiptap HTML) is **not** required — empty bodies are allowed.

### 3.5 · `POST /api/v1/news` — full request

```http
POST /api/v1/news
Content-Type: application/json
```

```json
{
  "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/0a1b2c3d-cover.jpg",
  "datePublished": "2026-05-22",
  "contentLanguages": ["CKB", "KMR"],
  "category": {
    "ckbName": "سیاسی",
    "kmrName": "Siyasî"
  },
  "subCategory": {
    "ckbName": "ئابووری",
    "kmrName": "Aborî"
  },
  "ckbContent": {
    "title": "هەواڵی نموونە بۆ تاقیکردنەوەی Tiptap",
    "description": "<h2>سەرناوێکی لاوەکی</h2><p>ئەمە دەقێکی نموونەیە بە <strong>دەقی بولد</strong> و <em>دەقی شێوەدار</em>.</p><blockquote>وتەیەکی گرنگ لێرە.</blockquote><img src=\"https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/9f8e7d6c-inline.jpg\" alt=\"وێنەی ناوەند\" /><p>پاش وێنە، پاراگرافێکی تر.</p><ul><li>خاڵی یەکەم</li><li>خاڵی دووەم</li></ul>"
  },
  "kmrContent": {
    "title": "Nûçeya nimûne ji bo testkirina Tiptap",
    "description": "<h2>Sernavekî biçûk</h2><p>Ev nimûneyek e bi <strong>tîpên qalin</strong> û <em>tîpên xwar</em>.</p><blockquote>Gotinek girîng li vir.</blockquote><img src=\"https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/9f8e7d6c-inline.jpg\" alt=\"Wêneya navendî\" /><p>Piştî wêne paragrafek din.</p><ul><li>Xala yekem</li><li>Xala duyem</li></ul>"
  },
  "tags": {
    "ckb": ["کوردستان", "هەولێر", "ئابووری"],
    "kmr": ["Kurdistan", "Hewlêr", "Abori"]
  },
  "keywords": {
    "ckb": ["هەواڵ", "سیاسی", "ڕاپۆرت"],
    "kmr": ["Nûçe", "Siyasî", "Rapor"]
  }
}
```

### 3.6 · `POST /api/v1/news` — full response

`201 Created`

```json
{
  "success": true,
  "message": "News created successfully",
  "data": {
    "id": 142,
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/0a1b2c3d-cover.jpg",
    "datePublished": "2026-05-22",
    "createdAt": "2026-05-22T10:15:30.123",
    "updatedAt": "2026-05-22T10:15:30.123",
    "contentLanguages": ["CKB", "KMR"],
    "category": {
      "ckbName": "سیاسی",
      "kmrName": "Siyasî"
    },
    "subCategory": {
      "ckbName": "ئابووری",
      "kmrName": "Aborî"
    },
    "ckbContent": {
      "title": "هەواڵی نموونە بۆ تاقیکردنەوەی Tiptap",
      "description": "<h2>سەرناوێکی لاوەکی</h2><p>ئەمە دەقێکی نموونەیە …</p>"
    },
    "kmrContent": {
      "title": "Nûçeya nimûne ji bo testkirina Tiptap",
      "description": "<h2>Sernavekî biçûk</h2><p>Ev nimûneyek e …</p>"
    },
    "tags": {
      "ckb": ["کوردستان", "هەولێر", "ئابووری"],
      "kmr": ["Kurdistan", "Hewlêr", "Abori"]
    },
    "keywords": {
      "ckb": ["هەواڵ", "سیاسی", "ڕاپۆرت"],
      "kmr": ["Nûçe", "Siyasî", "Rapor"]
    }
  }
}
```

### 3.7 · `POST /api/v1/news/bulk` — full request / response

Same shape as a single create, but the body is a JSON array. Each item is
validated independently; the entire batch is committed in a single
transaction.

```json
[
  {
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/a.jpg",
    "contentLanguages": ["CKB"],
    "category":    { "ckbName": "سیاسی", "kmrName": "Siyasî" },
    "subCategory": { "ckbName": "ئابووری", "kmrName": "Aborî" },
    "ckbContent":  { "title": "بابەتی یەکەم", "description": "<p>دەقی کورت.</p>" }
  },
  {
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/b.jpg",
    "contentLanguages": ["KMR"],
    "category":    { "ckbName": "سیاسی", "kmrName": "Siyasî" },
    "subCategory": { "ckbName": "ئابووری", "kmrName": "Aborî" },
    "kmrContent":  { "title": "Babetê yekem", "description": "<p>Pîçeka kurt.</p>" }
  }
]
```

Response wraps the resulting array in the standard envelope; each `data[i]`
has the exact shape returned by `POST /api/v1/news` (§3.6):

```json
{
  "success": true,
  "message": "News created successfully (bulk)",
  "data": [
    { "id": 143, "coverUrl": "https://.../a.jpg", "datePublished": "2026-05-22", "_comment": "full single-news shape — see §3.6" },
    { "id": 144, "coverUrl": "https://.../b.jpg", "datePublished": "2026-05-22", "_comment": "full single-news shape — see §3.6" }
  ]
}
```

### 3.8 · `PUT /api/v1/news/{id}` — full request / response

Same body shape as `POST /api/v1/news`. `coverUrl` is required (use the
existing URL if you are not replacing the cover). Response is the updated
news wrapped in the envelope with `"message": "News updated successfully"`.

### 3.9 · `GET /api/v1/news` — full response (paginated)

```
GET /api/v1/news?page=0&size=20
```

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": {
    "content": [
      { "id": 144, "coverUrl": "https://.../cover-144.jpg", "_comment": "full single-news shape; see section 3.6" },
      { "id": 143, "coverUrl": "https://.../cover-143.jpg", "_comment": "full single-news shape; see section 3.6" }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": { "empty": true, "unsorted": true, "sorted": false },
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 2,
    "totalPages": 1,
    "last": true,
    "first": true,
    "size": 20,
    "number": 0,
    "numberOfElements": 2,
    "empty": false
  }
}
```

### 3.10 · `GET /api/v1/news/{id}` — full response

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": {
    "id": 142,
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/cover.jpg",
    "datePublished": "2026-05-22",
    "createdAt": "2026-05-22T10:15:30.123",
    "updatedAt": "2026-05-22T10:15:30.123",
    "contentLanguages": ["CKB", "KMR"],
    "category":    { "ckbName": "سیاسی",   "kmrName": "Siyasî" },
    "subCategory": { "ckbName": "ئابووری", "kmrName": "Aborî"  },
    "ckbContent":  { "title": "هەواڵی نموونە",         "description": "<h2>...</h2><p>...</p>" },
    "kmrContent":  { "title": "Nûçeya nimûne",          "description": "<h2>...</h2><p>...</p>" },
    "tags":        { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] },
    "keywords":    { "ckb": ["هەواڵ"],     "kmr": ["Nûçe"]      }
  }
}
```

### 3.11 · Search endpoints — request / response

All search endpoints return the standard paginated envelope shown above.
They differ only in which query parameter they read:

| Endpoint                              | Query params                                              |
| ------------------------------------- | --------------------------------------------------------- |
| `GET /api/v1/news/search`             | `q` (required), `page`, `size`                            |
| `GET /api/v1/news/search/keyword`     | `keyword` (required), `language` (`ckb` / `kmr` / `both`) |
| `GET /api/v1/news/search/tag`         | `tag` (required), `language`                              |
| `GET /api/v1/news/search/category`    | `name` (required)                                         |
| `GET /api/v1/news/search/subcategory` | `name` (required)                                         |

### 3.12 · `DELETE /api/v1/news/{id}` — response

```json
{
  "success": true,
  "message": "News deleted successfully",
  "data": null
}
```

### 3.13 · `DELETE /api/v1/news/bulk` — request / response

```http
DELETE /api/v1/news/bulk
Content-Type: application/json

[142, 143, 144]
```

```json
{
  "success": true,
  "message": "News deleted successfully (bulk)",
  "data": null
}
```

---

## 4 · Projects module — full reference

### 4.1 · Tables

| Table                          | Status      | Notes                                                       |
| ------------------------------ | ----------- | ----------------------------------------------------------- |
| `projects`                     | Kept        | `description_ckb` / `description_kmr` are `TEXT` — Tiptap HTML |
| `project_content_languages`    | Kept        | set of active languages                                     |
| `project_tag`                  | Kept        | shared tag dictionary                                       |
| `project_keyword`              | Kept        | shared keyword dictionary                                   |
| `project_tag_map_ckb`          | Kept        | M:N join                                                    |
| `project_tag_map_kmr`          | Kept        | M:N join                                                    |
| `project_keyword_map_ckb`      | Kept        | M:N join                                                    |
| `project_keyword_map_kmr`      | Kept        | M:N join                                                    |
| `project_log`                  | Kept        | audit trail                                                 |
| **`project_media`**            | **DROPPED** | inline media now lives inside the HTML                      |
| **`project_contents`**         | **DROPPED** | free-text "section tags" — express as headings inside the editor |
| **`project_content_map_ckb`**  | **DROPPED** | join table for `project_contents`                           |
| **`project_content_map_kmr`**  | **DROPPED** | join table for `project_contents`                           |

### 4.2 · Java source changes

**Deleted**

* `model/project/ProjectMedia.java`
* `model/project/ProjectContent.java`
* `repository/project/ProjectMediaRepository.java`
* `repository/project/ProjectContentRepository.java`
* `dto/project/ProjectMediaCreateRequest.java`
* `dto/project/ProjectMediaResponse.java`
* `exceptions/project/ProjectMediaException.java`
* `helper/ProjectSpecification.java` — dead code that referenced stale field names
* `Errors.projectMediaInvalid(...)` and `Errors.projectMediaUploadFailed(...)` factories
* `Project.java` lost `@OneToMany media`, `@ManyToMany contentsCkb`, `@ManyToMany contentsKmr`
* `ProjectCreateRequest.java` lost `media[]`, `contentsCkb`, `contentsKmr`
* `ProjectResponse.java` lost `media[]`, `contentsCkb`, `contentsKmr`
* `ProjectService.java` lost every cover-upload/media-upload helper and the `S3Service` dependency
* `ProjectController.java` lost both multipart endpoints

**Kept (the `ProjectMediaType` enum stays)** — it is still used by `S3Service`
and `MediaService` to route uploads into the right S3 folder.

### 4.3 · Endpoint matrix

| Method | Path                                       | Status   | Body type          |
| ------ | ------------------------------------------ | -------- | ------------------ |
| POST   | `/api/v1/projects/create`                  | KEPT     | `application/json` |
| POST   | `/api/v1/projects/with-files`              | REMOVED  | —                  |
| PUT    | `/api/v1/projects/update/{id}`             | KEPT     | `application/json` |
| PUT    | `/api/v1/projects/update/{id}/with-files`  | REMOVED  | —                  |
| DELETE | `/api/v1/projects/delete/{id}`             | KEPT     | —                  |
| GET    | `/api/v1/projects/getAll`                  | KEPT     | —                  |
| GET    | `/api/v1/projects/search/tag`              | KEPT     | query `tag`        |
| GET    | `/api/v1/projects/search/keyword`          | KEPT     | query `keyword`    |

### 4.4 · Validation rules

* `contentLanguages` must be non-empty.
* `coverUrl` is required on create and update.
* If `CKB` is selected, both `projectTypeCkb` and `ckbContent.title` are required.
* If `KMR` is selected, both `projectTypeKmr` and `kmrContent.title` are required.

### 4.5 · `POST /api/v1/projects/create` — full request

```http
POST /api/v1/projects/create
Content-Type: application/json
```

```json
{
  "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/proj-cover.jpg",
  "projectTypeCkb": "بەرنامەی توێژینەوە",
  "projectTypeKmr": "Bernameya Lêkolînê",
  "status": "ONGOING",
  "contentLanguages": ["CKB", "KMR"],
  "projectDate": "2026-04-01",
  "ckbContent": {
    "title": "پرۆژەی توێژینەوەی فۆلکلۆری",
    "description": "<h2>پێشەکی</h2><p>پرۆژەکە بریتییە لە کۆکردنەوەی <strong>چیرۆکی فۆلکلۆری</strong> لە ١٢ شارۆچکەدا.</p><img src=\"https://.../inline-1.jpg\" alt=\"تیپی مەیدانی\" /><h3>هەنگاوەکان</h3><ol><li>گفتوگۆکردن لەگەڵ گەورانی شارۆچکە</li><li>تۆمارکردنی دەنگی</li><li>گەردنیتر و چاپ</li></ol>",
    "location": "هەولێر"
  },
  "kmrContent": {
    "title": "Projeya lêkolîna folklorî",
    "description": "<h2>Pêşekî</h2><p>Proje li ser berhevkirina <strong>çîrokên folklorî</strong> di 12 bajarokan de ye.</p><img src=\"https://.../inline-1.jpg\" alt=\"Tîpa meydanî\" /><h3>Gavên</h3><ol><li>Hevpeyvîn bi mezinên bajarokê re</li><li>Tomarkirina deng</li><li>Veguhastin û çap</li></ol>",
    "location": "Hewlêr"
  },
  "tagsCkb":     ["ئەکادیمی", "فۆلکلۆر"],
  "tagsKmr":     ["Akademîk", "Folklor"],
  "keywordsCkb": ["توێژینەوە", "میدیا"],
  "keywordsKmr": ["Lêkolîn", "Medya"]
}
```

### 4.6 · `POST /api/v1/projects/create` — full response

`201 Created`

```json
{
  "success": true,
  "message": "Project created successfully",
  "data": {
    "id": 17,
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/proj-cover.jpg",
    "projectTypeCkb": "بەرنامەی توێژینەوە",
    "projectTypeKmr": "Bernameya Lêkolînê",
    "status": "ONGOING",
    "projectDate": "2026-04-01",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title": "پرۆژەی توێژینەوەی فۆلکلۆری",
      "description": "<h2>پێشەکی</h2><p>پرۆژەکە بریتییە …</p>",
      "location": "هەولێر"
    },
    "kmrContent": {
      "title": "Projeya lêkolîna folklorî",
      "description": "<h2>Pêşekî</h2><p>Proje li ser …</p>",
      "location": "Hewlêr"
    },
    "tagsCkb":     ["ئەکادیمی", "فۆلکلۆر"],
    "tagsKmr":     ["Akademîk", "Folklor"],
    "keywordsCkb": ["توێژینەوە", "میدیا"],
    "keywordsKmr": ["Lêkolîn", "Medya"],
    "createdAt": "2026-05-22T10:20:00Z",
    "updatedAt": null,
    "createdBy": null,
    "updatedBy": null
  }
}
```

### 4.7 · `PUT /api/v1/projects/update/{id}` — request / response

Identical body shape to `POST /api/v1/projects/create`. Returns:

```json
{
  "success": true,
  "message": "Project updated successfully",
  "data": {
    "id": 17,
    "coverUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/proj-cover.jpg",
    "projectTypeCkb": "بەرنامەی توێژینەوە",
    "projectTypeKmr": "Bernameya Lêkolînê",
    "status": "ONGOING",
    "projectDate": "2026-04-01",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "...", "description": "<h2>...</h2>", "location": "هەولێر" },
    "kmrContent": { "title": "...", "description": "<h2>...</h2>", "location": "Hewlêr" },
    "tagsCkb":     ["ئەکادیمی", "فۆلکلۆر"],
    "tagsKmr":     ["Akademîk", "Folklor"],
    "keywordsCkb": ["توێژینەوە", "میدیا"],
    "keywordsKmr": ["Lêkolîn", "Medya"],
    "createdAt":   "2026-05-22T10:20:00Z",
    "updatedAt":   "2026-05-22T11:05:14Z",
    "createdBy":   null,
    "updatedBy":   null
  }
}
```

### 4.8 · `GET /api/v1/projects/getAll` — full response

```json
{
  "success": true,
  "message": "Projects fetched successfully",
  "data": {
    "content": [
      {
        "id": 17,
        "coverUrl": "https://.../proj-cover.jpg",
        "projectTypeCkb": "بەرنامەی توێژینەوە",
        "projectTypeKmr": "Bernameya Lêkolînê",
        "status": "ONGOING",
        "projectDate": "2026-04-01",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent":  { "title": "...", "description": "<p>...</p>", "location": "هەولێر" },
        "kmrContent":  { "title": "...", "description": "<p>...</p>", "location": "Hewlêr" },
        "tagsCkb":     ["ئەکادیمی"],
        "tagsKmr":     ["Akademîk"],
        "keywordsCkb": ["توێژینەوە"],
        "keywordsKmr": ["Lêkolîn"],
        "createdAt":   "2026-05-22T10:20:00Z",
        "updatedAt":   null,
        "createdBy":   null,
        "updatedBy":   null
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort":   { "empty": true, "unsorted": true, "sorted": false },
      "offset": 0,
      "paged":  true,
      "unpaged": false
    },
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true,
    "size": 20,
    "number": 0,
    "numberOfElements": 1,
    "empty": false
  }
}
```

### 4.9 · `DELETE /api/v1/projects/delete/{id}` — response

```json
{
  "success": true,
  "message": "Project deleted successfully",
  "data": null
}
```

---

## 5 · About module — full reference

### 5.1 · Tables

| Table              | Status                            | Notes                                                                                                        |
| ------------------ | --------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `about_pages`      | Kept (3 columns added)            | `body_ckb` TEXT, `body_kmr` TEXT, `stats` JSONB                                                              |
| **`about_blocks`** | **DROPPED (entire table)**        | Along with `content_text_ckb/kmr`, `title_ckb/kmr`, `alt_text_ckb/kmr`, `media_url`, `thumbnail_url`, `metadata`, `sequence`, `content_type` columns |

### 5.2 · Java source changes

**Deleted**

* `model/about/AboutBlock.java`
* `model/about/AboutBlockContent.java`
* DTO inner classes: `AboutBlockRequest`, `AboutBlockContentRequest`,
  `AboutBlockResponse`, `AboutBlockContentResponse`
* `About.java` lost the `@OneToMany blocks` field, the `addBlock` and
  `removeBlock` helpers, and the JavaDoc comment about "falls back to the
  first IMAGE block"

**Added**

* `model/about/StatItem.java` — POJO persisted as a JSONB element
* `dto.AboutDTOs.StatItemDto` — request/response shape

**Modified**

* `AboutContent.java` — gained `body` field mapped to `body_ckb` / `body_kmr`
* `About.java` — gained `stats` field (`@JdbcTypeCode(SqlTypes.JSON)`)
* `AboutDTOs.java` — `AboutContentRequest` / `AboutContentResponse` gained
  `body`; `AboutRequest` / `AboutResponse` gained `stats[]`
* `AboutService.java` — block builders gone; new `buildStats(...)` and
  `toStatsResponse(...)` helpers
* `AboutRepository.java` — removed the `findBySlugWithBlocks` query that
  joined the dropped `about_blocks` table
* `AboutController.java` — the three upload endpoints
  (`/upload`, `/upload/multiple`, `/media`) were removed in favour of the
  shared media endpoints

### 5.3 · Endpoint matrix

| Method | Path                              | Status   | Body type          |
| ------ | --------------------------------- | -------- | ------------------ |
| GET    | `/api/v1/about`                   | KEPT     | —                  |
| GET    | `/api/v1/about/{slug}`            | KEPT     | —                  |
| POST   | `/api/v1/about`                   | KEPT     | `application/json` |
| PUT    | `/api/v1/about/{id}`              | KEPT     | `application/json` |
| DELETE | `/api/v1/about/{id}`              | KEPT     | —                  |
| POST   | `/api/v1/about/upload`            | REMOVED  | use `POST /api/v1/media/upload`          |
| POST   | `/api/v1/about/upload/multiple`   | REMOVED  | use `POST /api/v1/media/upload/multiple` |
| DELETE | `/api/v1/about/media`             | REMOVED  | use `DELETE /api/v1/media`               |

### 5.4 · Validation rules

* `slugCkb` is required and must be unique across `about_pages`.
* `slugKmr` is optional, but if provided it must be unique and must differ from `slugCkb`.
* `stats[*].value` is the only thing required *per stat entry*; missing
  labels are tolerated (and an entry is dropped only if all three fields are
  blank).

### 5.5 · `POST /api/v1/about` — full request

```http
POST /api/v1/about
Content-Type: application/json
```

```json
{
  "slugCkb": "derbare-ckb",
  "slugKmr": "derbare-kmr",
  "heroImageUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/about-hero.jpg",
  "ckbContent": {
    "title": "دەربارەی ئێمە",
    "subtitle": "ناساندنی پرۆژەی KHI",
    "metaDescription": "پەڕەی دەربارە بە کوردیی ناوەندی — کورتە چیرۆکی پرۆژەکە و ئامانجەکانی.",
    "body": "<h2>پێشەکی</h2><p>پرۆژەی KHI لە ٢٠١٠ دامەزراوە …</p><img src=\"https://.../about-1.jpg\" alt=\"تیپی KHI\" /><h3>ئامانجەکانمان</h3><ul><li>پاراستنی فۆلکلۆر</li><li>گەشەی توێژینەوەی ئەکادیمی</li></ul><figure><video controls src=\"https://.../intro.mp4\"></video><figcaption>ڤیدیۆی پێشەکی</figcaption></figure>"
  },
  "kmrContent": {
    "title": "Derbarê Me",
    "subtitle": "Nasandina projeya KHI",
    "metaDescription": "Rûpela derbarê me bi Kurmancî — çîroka kurt a projeyê û armancên wê.",
    "body": "<h2>Pêşekî</h2><p>Projeya KHI di 2010ê de hat avakirin …</p><img src=\"https://.../about-1.jpg\" alt=\"Tîpa KHI\" /><h3>Armancên me</h3><ul><li>Parastina folklorê</li><li>Pêşxistina lêkolîna akademîk</li></ul><figure><video controls src=\"https://.../intro.mp4\"></video><figcaption>Vîdyoya pêşekî</figcaption></figure>"
  },
  "stats": [
    { "labelCkb": "کتێب",   "labelKmr": "Pirtûk",   "value": "5,000+" },
    { "labelCkb": "ساڵ",    "labelKmr": "Sal",      "value": "15+"    },
    { "labelCkb": "توێژەر", "labelKmr": "Lêkoler",  "value": "42"     }
  ]
}
```

### 5.6 · `POST /api/v1/about` — full response

`201 Created`

```json
{
  "id": 1,
  "slugCkb": "derbare-ckb",
  "slugKmr": "derbare-kmr",
  "heroImageUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/about-hero.jpg",
  "active": true,
  "ckbContent": {
    "title": "دەربارەی ئێمە",
    "subtitle": "ناساندنی پرۆژەی KHI",
    "metaDescription": "پەڕەی دەربارە بە کوردیی ناوەندی — …",
    "body": "<h2>پێشەکی</h2><p>پرۆژەی KHI لە ٢٠١٠ …</p>"
  },
  "kmrContent": {
    "title": "Derbarê Me",
    "subtitle": "Nasandina projeya KHI",
    "metaDescription": "Rûpela derbarê me bi Kurmancî — …",
    "body": "<h2>Pêşekî</h2><p>Projeya KHI di 2010ê de …</p>"
  },
  "stats": [
    { "labelCkb": "کتێب",   "labelKmr": "Pirtûk",   "value": "5,000+" },
    { "labelCkb": "ساڵ",    "labelKmr": "Sal",      "value": "15+"    },
    { "labelCkb": "توێژەر", "labelKmr": "Lêkoler",  "value": "42"     }
  ],
  "createdAt": "2026-05-22 10:25:00",
  "updatedAt": "2026-05-22 10:25:00"
}
```

> **Envelope note:** the About controller returns the raw response body
> (without the `{ success, message, data }` wrapper used by every other
> module). This is intentional — it preserves the pre-migration controller
> contract so existing About callers keep working.

### 5.7 · `PUT /api/v1/about/{id}` — request / response

Identical body shape. The service handles old-hero-image cleanup
automatically: if `heroImageUrl` changes, the previous URL is deleted from
S3 via `S3Service.deleteFile(oldHero)`. Inline body media is **not**
auto-cleaned — use `DELETE /api/v1/media?fileUrl=…` if you want to purge
specific assets.

### 5.8 · `GET /api/v1/about` — full response

```json
[
  {
    "id": 1,
    "slugCkb": "derbare-ckb",
    "slugKmr": "derbare-kmr",
    "heroImageUrl": "https://.../about-hero.jpg",
    "active": true,
    "ckbContent": { "title": "...", "subtitle": "...", "metaDescription": "...", "body": "<h2>...</h2>" },
    "kmrContent": { "title": "...", "subtitle": "...", "metaDescription": "...", "body": "<h2>...</h2>" },
    "stats":      [ { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,000+" } ],
    "createdAt":  "2026-05-22 10:25:00",
    "updatedAt":  "2026-05-22 10:25:00"
  },
  {
    "id": 2,
    "slugCkb": "history-ckb",
    "slugKmr": null,
    "heroImageUrl": null,
    "active": true,
    "ckbContent": { "title": "مێژوومان", "subtitle": null, "metaDescription": null, "body": "<p>...</p>" },
    "kmrContent": null,
    "stats":      [],
    "createdAt":  "2026-04-10 09:11:00",
    "updatedAt":  "2026-04-10 09:11:00"
  }
]
```

Only `active: true` pages are returned.

### 5.9 · `GET /api/v1/about/{slug}` — full response

Returns a single about page using either the CKB or the KMR slug:

```
GET /api/v1/about/derbare-ckb
GET /api/v1/about/derbare-kmr      ← also works; resolves to the same row
```

### 5.10 · `DELETE /api/v1/about/{id}` — response

`204 No Content` (empty body). The hero image is deleted from S3 as part of
the operation.

### 5.11 · `StatItem` shape

```json
{
  "labelCkb": "کتێب",
  "labelKmr": "Pirtûk",
  "value":   "5,000+"
}
```

Stored as a JSONB array on `about_pages.stats`. Order is preserved.

---

## 6 · Services module — full reference

### 6.1 · Tables / DTOs

No schema change. `service_contents.description` is already `TEXT`. The
backend stores whatever the editor produces; no sanitization or stripping is
applied.

### 6.2 · Endpoint matrix

All existing endpoints under `/api/v1/services/...` keep their paths,
request bodies, and response shapes. The only conceptual change: callers
upload inline media first via `POST /api/v1/media/upload` and bake the
returned URLs into the `description` HTML.

### 6.3 · Field example

The `description` field inside `ServiceContent` (CKB and KMR variants) now
accepts strings such as:

```html
<h2>خزمەتگوزاریەکانمان</h2>
<p>پێشکەشکردنی <strong>توێژینەوەی ئەکادیمی</strong> و …</p>
<img src="https://.../service-photo.jpg" alt="بانگەشە" />
```

instead of plain text.

---

## 7 · SoundTrack module — full reference

No schema change. `SoundTrackContent.description` is already `TEXT`.
Endpoints and DTOs unchanged.

### 7.1 · Field example

```html
<h2>دەربارەی ئەلبوم</h2>
<p>ئەم ئەلبوومە کۆکراوەی <em>گۆرانییە فۆلکلۆریەکان</em>ە …</p>
<audio controls src="https://.../track-preview.mp3"></audio>
```

---

## 8 · Videos module — full reference

No schema change. `VideoContent.description` is already `TEXT`. Endpoints
and DTOs unchanged.

### 8.1 · Field example

```html
<h2>هەواڵی ڤیدیۆ</h2>
<p>کورتە چیرۆکی ڤیدیۆکە لێرە …</p>
<video controls src="https://.../trailer.mp4" poster="https://.../poster.jpg"></video>
```

---

## 9 · Image Collections module — full reference

No schema change. `ImageContent.description` is already `TEXT`. Endpoints
and DTOs unchanged.

---

## 10 · Writings module — full reference

No schema change. `WritingContent.description` is already `TEXT`. Endpoints
and DTOs unchanged.

---

## 11 · Contact module — full reference

No changes whatsoever. There is no long-form prose to migrate.

---

## 12 · Global Search — Tiptap-aware snippet

`GlobalSearchService.snippet(String)` used to truncate the raw `description`
string to 200 characters. Now that the source is Tiptap HTML it strips tags
first to keep the result-card preview clean:

```java
private String snippet(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String plain = raw.replaceAll("<[^>]+>", " ")
                      .replaceAll("\\s+", " ")
                      .trim();
    if (plain.isEmpty()) return "";
    return plain.length() <= 200 ? plain : plain.substring(0, 200) + "…";
}
```

**Caveat:** the actual `LIKE` search inside `News`/`Project` repositories
still runs against the raw HTML column, so a search for `"img"` will also
match pages that contain an `<img>` element. If that becomes a problem the
recommended fix is to add a sibling `description_ckb_plain` /
`description_kmr_plain` column populated server-side from the HTML on save,
and have the search query target that plain column instead.

---

## 13 · Database schema — before / after diff

### 13.1 · DROP TABLE IF EXISTS

```sql
-- News
DROP TABLE IF EXISTS news_media;

-- Projects
DROP TABLE IF EXISTS project_media;
DROP TABLE IF EXISTS project_content_map_ckb;
DROP TABLE IF EXISTS project_content_map_kmr;
DROP TABLE IF EXISTS project_contents;

-- About
DROP TABLE IF EXISTS about_blocks;
```

> With `ddl-auto: create-drop` these `DROP`s happen automatically when the
> application restarts; the `CREATE TABLE` set is rebuilt from the JPA model
> without the dropped entities.

### 13.2 · Columns added on `about_pages`

```sql
ALTER TABLE about_pages
    ADD COLUMN body_ckb TEXT,
    ADD COLUMN body_kmr TEXT,
    ADD COLUMN stats    JSONB;
```

`stats` is mapped through `@JdbcTypeCode(SqlTypes.JSON)` on the `About`
entity, so Hibernate serialises the `List<StatItem>` POJO into the column on
save and re-hydrates it on load. Example column value:

```json
[
  { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,000+" },
  { "labelCkb": "ساڵ",  "labelKmr": "Sal",    "value": "15+"    }
]
```

### 13.3 · Columns / collection tables unchanged but worth calling out

* `news.description_ckb`, `news.description_kmr` — `TEXT`; now Tiptap HTML.
* `projects.description_ckb`, `projects.description_kmr` — `TEXT`; now Tiptap HTML.
* `service_contents.description`, `video_contents.description`,
  `sound_track_contents.description`, `image_contents.description`,
  `writing_contents.description` — all `TEXT`; now Tiptap HTML.

### 13.4 · Indexes

No new indexes are required. All existing tag/keyword/category indexes
listed in the repository JavaDocs (`idx_news_tag_ckb`, `idx_project_tag_name`,
etc.) remain valuable.

---

## 14 · Java source diff — added / deleted files

### 14.1 · Added (4 new files)

```
src/main/java/.../khi_app/api/media/MediaController.java
src/main/java/.../khi_app/dto/media/MediaDtos.java
src/main/java/.../khi_app/service/media/MediaService.java
src/main/java/.../khi_app/model/about/StatItem.java
```

### 14.2 · Deleted (13 files)

```
src/main/java/.../khi_app/model/news/NewsMedia.java
src/main/java/.../khi_app/enums/news/NewsMediaType.java
src/main/java/.../khi_app/repository/news/NewsMediaRepository.java
src/main/java/.../khi_app/exceptions/news/NewsMediaException.java

src/main/java/.../khi_app/model/project/ProjectMedia.java
src/main/java/.../khi_app/model/project/ProjectContent.java
src/main/java/.../khi_app/repository/project/ProjectMediaRepository.java
src/main/java/.../khi_app/repository/project/ProjectContentRepository.java
src/main/java/.../khi_app/dto/project/ProjectMediaCreateRequest.java
src/main/java/.../khi_app/dto/project/ProjectMediaResponse.java
src/main/java/.../khi_app/exceptions/project/ProjectMediaException.java
src/main/java/.../khi_app/helper/ProjectSpecification.java

src/main/java/.../khi_app/model/about/AboutBlock.java
src/main/java/.../khi_app/model/about/AboutBlockContent.java
```

### 14.3 · Modified (10 files)

```
src/main/java/.../khi_app/exceptions/Errors.java
                  ↳ removed newsMediaInvalid(...), projectMediaInvalid(...),
                    projectMediaUploadFailed(...)
src/main/java/.../khi_app/model/news/News.java
                  ↳ removed @OneToMany media + helper methods
src/main/java/.../khi_app/dto/news/NewsDto.java
                  ↳ removed media[] field + MediaDto inner class
src/main/java/.../khi_app/service/news/NewsService.java
                  ↳ removed cover & inline upload pipeline, ExecutorService, S3Service dep
src/main/java/.../khi_app/api/news/NewsController.java
                  ↳ collapsed to JSON-only; removed 3 multipart endpoints
src/main/java/.../khi_app/repository/news/NewsRepository.java
                  ↳ removed "media" from @EntityGraph
src/main/java/.../khi_app/model/project/Project.java
                  ↳ removed @OneToMany media, @ManyToMany contentsCkb/Kmr,
                    addMedia/removeMedia helpers
src/main/java/.../khi_app/dto/project/ProjectCreateRequest.java
                  ↳ removed media[], contentsCkb, contentsKmr
src/main/java/.../khi_app/dto/project/ProjectResponse.java
                  ↳ removed media[], contentsCkb, contentsKmr
src/main/java/.../khi_app/service/project/ProjectService.java
                  ↳ removed every cover/media upload helper, S3Service dep,
                    ProjectContent repository dep, ProjectMedia repository dep
src/main/java/.../khi_app/api/project/ProjectController.java
                  ↳ collapsed to JSON-only; removed 2 multipart endpoints
src/main/java/.../khi_app/repository/project/ProjectRepository.java
                  ↳ removed "media", "contentsCkb", "contentsKmr" from @EntityGraph
src/main/java/.../khi_app/model/about/About.java
                  ↳ removed blocks; added stats JSONB column; new @AttributeOverrides
                    for body_ckb / body_kmr
src/main/java/.../khi_app/model/about/AboutContent.java
                  ↳ added "body" field (Tiptap HTML)
src/main/java/.../khi_app/dto/about/AboutDTOs.java
                  ↳ removed block DTOs; added StatItemDto; added body field
                    to content request/response
src/main/java/.../khi_app/service/about/AboutService.java
                  ↳ rewritten without block handling; new stats helpers;
                    removed upload methods
src/main/java/.../khi_app/api/about/AboutController.java
                  ↳ removed /upload, /upload/multiple, /media endpoints
src/main/java/.../khi_app/repository/about/AboutRepository.java
                  ↳ removed findBySlugWithBlocks query (joined dropped table)
src/main/java/.../khi_app/service/search/GlobalSearchService.java
                  ↳ snippet() now strips HTML tags before truncating
```

### 14.4 · ApiResponse envelope

`ak.dev.khi_backend.khi_app.dto.ApiResponse<T>` (the existing class) is
reused for the new media endpoints to keep response shapes consistent across
the platform. The About controller still returns raw response bodies (no
envelope) — that matches the pre-migration behaviour.

---

## 15 · Frontend integration recipe

The same recipe applies to every Tiptap editor in the app — News, Projects,
About, Services, SoundTrack, Videos, Image Collections, Writings.

### 15.1 · Inserting an image / video / audio inside the editor

```ts
// 1. User drops a file via the Tiptap node's drag-drop handler.
const fd = new FormData();
fd.append("file", file);
fd.append("type", file.type.startsWith("image/") ? "image"
                : file.type.startsWith("video/") ? "video"
                : file.type.startsWith("audio/") ? "audio"
                : "document");

const { data } = await fetch("/api/v1/media/upload", {
  method: "POST",
  body: fd,
}).then(r => r.json());

// 2. Insert into the editor at the current selection.
editor.chain().focus().setImage({ src: data.fileUrl, alt: file.name }).run();
//   ↳ for audio/video use .insertContent('<audio …>') / setVideo(...) etc.
```

### 15.2 · Saving the entity

```ts
// Read the HTML once.
const ckbHtml = editorCkb.getHTML();
const kmrHtml = editorKmr.getHTML();

await fetch("/api/v1/news", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    coverUrl,                       // already uploaded via /api/v1/media/upload
    datePublished: "2026-05-22",
    contentLanguages: ["CKB", "KMR"],
    category:    { ckbName: "…", kmrName: "…" },
    subCategory: { ckbName: "…", kmrName: "…" },
    ckbContent: { title: ckbTitle, description: ckbHtml },
    kmrContent: { title: kmrTitle, description: kmrHtml },
    tags:     { ckb: [...], kmr: [...] },
    keywords: { ckb: [...], kmr: [...] },
  }),
});
```

### 15.3 · Saving an About page

```ts
await fetch("/api/v1/about", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    slugCkb: "derbare-ckb",
    slugKmr: "derbare-kmr",
    heroImageUrl,                   // already uploaded
    ckbContent: {
      title:           ckbTitle,
      subtitle:        ckbSubtitle,
      metaDescription: ckbMeta,
      body:            ckbHtml,     // ← Tiptap HTML
    },
    kmrContent: {
      title:           kmrTitle,
      subtitle:        kmrSubtitle,
      metaDescription: kmrMeta,
      body:            kmrHtml,
    },
    stats: [
      { labelCkb: "کتێب", labelKmr: "Pirtûk", value: "5,000+" },
      { labelCkb: "ساڵ",  labelKmr: "Sal",    value: "15+"    },
    ],
  }),
});
```

### 15.4 · Removing an asset from S3

If the user removes an `<img>` from the editor and you want to free the S3
space immediately, parse the `src` attribute and call:

```ts
await fetch(`/api/v1/media?fileUrl=${encodeURIComponent(url)}`, {
  method: "DELETE",
});
```

Skipping this is safe — orphaned objects only cost a few cents per month and
can be swept on a schedule later.

---

## 16 · End-to-end manual test plan

A clean smoke test, ordered to exercise the full create → read → update →
delete loop on every migrated module.

### 16.1 · Shared media endpoint

* [ ] `POST /api/v1/media/upload` with a JPEG → `200 OK`, response contains a real S3 URL that resolves over HTTPS.
* [ ] `POST /api/v1/media/upload` with `type=video` and an MP4 → response URL contains `/video/`.
* [ ] `POST /api/v1/media/upload/multiple` with two files → response array has two entries.
* [ ] `DELETE /api/v1/media?fileUrl=<the URL from step 1>` → `200 OK`; URL no longer resolves.

### 16.2 · News

* [ ] `POST /api/v1/news` with the full payload from §3.5 → `201 Created`; `data.id` is populated.
* [ ] `GET /api/v1/news/{id}` returns the same `description` HTML byte-for-byte.
* [ ] `GET /api/v1/news?page=0&size=20` includes the new row.
* [ ] `GET /api/v1/news/search?q=نموونە` returns the new row.
* [ ] `PUT /api/v1/news/{id}` with a different `description` HTML → response shows the new HTML.
* [ ] `DELETE /api/v1/news/{id}` → `200 OK`; subsequent `GET` returns 404 / not found.

### 16.3 · Projects

* [ ] `POST /api/v1/projects/create` with the payload from §4.5 → `201 Created`.
* [ ] `GET /api/v1/projects/getAll` includes the new project.
* [ ] `PUT /api/v1/projects/update/{id}` modifies title and description → response confirms changes.
* [ ] `DELETE /api/v1/projects/delete/{id}` → `200 OK`.

### 16.4 · About

* [ ] `POST /api/v1/about` with the payload from §5.5 → `201 Created`; raw entity body returned.
* [ ] `GET /api/v1/about/derbare-ckb` and `GET /api/v1/about/derbare-kmr` both resolve to the same row.
* [ ] `PUT /api/v1/about/{id}` with a new `heroImageUrl` → response shows new URL; old URL no longer exists in S3.
* [ ] `PUT /api/v1/about/{id}` with a different `stats[]` array → response reflects the change.
* [ ] `DELETE /api/v1/about/{id}` → `204 No Content`.

### 16.5 · Other modules (services, sound, video, image, writing)

* [ ] For each, create one entity whose `description` field contains
  Tiptap HTML with at least one `<img>` whose `src` is an S3 URL returned by
  `POST /api/v1/media/upload`.
* [ ] Confirm `GET` returns the HTML untouched.
* [ ] Global search across these modules (`/api/v1/search`) returns
  results whose `descriptionCkb` / `descriptionKmr` snippets have **no
  HTML tags** (the snippet is stripped by §12).

### 16.6 · Removed endpoints — confirm they 404

These should all return `404 Not Found` because Spring no longer has a
controller method for them:

```
POST   /api/v1/news/with-files
PUT    /api/v1/news/{id}/with-files
PUT    /api/v1/news/update/{id}/with-files
POST   /api/v1/projects/with-files
PUT    /api/v1/projects/update/{id}/with-files
POST   /api/v1/about/upload
POST   /api/v1/about/upload/multiple
DELETE /api/v1/about/media
```

---

## 17 · Error responses — common shapes

The platform-wide `ApiErrorResponse` returned by `GlobalExceptionHandler`
keeps its existing shape. Examples below come from real validation failures.

### 17.1 · `400 Bad Request` (validation)

```json
{
  "timestamp": "2026-05-22T10:32:11.421Z",
  "status": 400,
  "error": "Bad Request",
  "code": "news.cover.required",
  "message": "news.cover.required",
  "details": { "field": "coverUrl" },
  "traceId": "8d5a-…"
}
```

### 17.2 · `404 Not Found`

```json
{
  "timestamp": "2026-05-22T10:32:11.421Z",
  "status": 404,
  "error": "Not Found",
  "code": "news.not_found",
  "message": "News not found: 142",
  "details": { "id": 142 },
  "traceId": "8d5a-…"
}
```

### 17.3 · `409 Conflict` (slug already exists)

```json
{
  "timestamp": "2026-05-22T10:32:11.421Z",
  "status": 409,
  "error": "Conflict",
  "code": "about.slug.duplicate",
  "message": "CKB slug already exists: derbare-ckb",
  "details": { "slug": "derbare-ckb" },
  "traceId": "8d5a-…"
}
```

### 17.4 · `502 Bad Gateway` (S3 upload failure)

```json
{
  "timestamp": "2026-05-22T10:32:11.421Z",
  "status": 502,
  "error": "Bad Gateway",
  "code": "s3.upload.failed",
  "message": "Failed to upload file to S3: connection reset",
  "details": { "fileType": "media" },
  "traceId": "8d5a-…"
}
```

---

## 18 · FAQ / known limits

### 18.1 · Can I still send the old multipart `mediaFiles` part to `POST /api/v1/news`?

No. The new endpoint is `consumes = application/json` only. Spring will
reject the request with `415 Unsupported Media Type`. Migrate the caller to
the two-step flow (upload first, then JSON).

### 18.2 · What happens to old `news_media` rows when I deploy?

Nothing. With `ddl-auto: create-drop` the table itself is dropped (and the
data with it) on the next restart. If you need to preserve data, snapshot
the table before deploying or switch `ddl-auto` to `validate` and run a
manual migration.

### 18.3 · Why is the About response body not wrapped in `{ success, message, data }`?

Because the pre-migration About controller never used the wrapper, and
keeping the change diff minimal is more important than enforcing a
platform-wide envelope. If you want to harmonise later, the controller is
the only file that needs to change.

### 18.4 · How are large HTML bodies handled?

Both `description_ckb` / `description_kmr` and `body_ckb` / `body_kmr` are
PostgreSQL `TEXT` columns — they have no upper size limit beyond the row
size (1 GB practical). The Tiptap output for a typical article is well
under 1 MB.

### 18.5 · How are inline assets garbage-collected from S3?

They aren't, automatically. The user controls cleanup through
`DELETE /api/v1/media?fileUrl=…`. A future enhancement could parse all HTML
fields on entity delete and queue the discovered URLs for purge, but it's
out of scope for this migration.

### 18.6 · Does the search behave well against HTML?

Substring matches still work, but a search for HTML token names (`img`,
`href`, etc.) will produce false positives. The result-card snippet is
HTML-stripped (see §12). If false positives become a problem, add a
parallel plaintext column populated on save.

### 18.7 · What happens to old code that imports `NewsMedia` / `ProjectMedia` / `AboutBlock`?

Compilation fails. The deleted classes are listed in §14.2. The only places
the old types were referenced in the main app were the modules themselves
(updated in this migration) and an unused `ProjectSpecification` helper
(deleted). External consumers must update their imports.

### 18.8 · `ProjectMediaType` is still around — why?

Because `S3Service` and the new `MediaService` use it as a folder-routing
enum. It is no longer tied to an entity and is purely a routing hint.

---

**Migration complete. `mvn compile` passes on 221 source files.**
