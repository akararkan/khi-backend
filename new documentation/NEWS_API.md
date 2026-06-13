# KHI Backend — News API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 13 Endpoints · JSON Only · Tiptap HTML · Paginated · Cached

Complete documentation for all news management endpoints — create, update, delete, list, and search — including bilingual Tiptap content, JSONB media gallery, cover discriminators, category/subcategory handling, enums, DTOs, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Media Pipeline (Tiptap)](#02--media-pipeline-tiptap)
- [03 · Data Models](#03--data-models)
- [04 · Enums](#04--enums)
- [05 · Authentication, Caching & Read Strategy](#05--authentication-caching--read-strategy)
- [06 · Create News](#06--create-news)
  - `POST /` (JSON)
  - `POST /bulk` (JSON)
- [07 · Update News](#07--update-news) — `PUT /{id}` (JSON)
- [08 · Delete News](#08--delete-news)
  - `DELETE /{id}`
  - `DELETE /delete/{id}`
  - `DELETE /bulk`
- [09 · Read & Search](#09--read--search)
  - `GET /` (getAll)
  - `GET /{id}`
  - `GET /search`
  - `GET /search/tag`
  - `GET /search/keyword`
  - `GET /search/category`
  - `GET /search/subcategory`
- [10 · DTO Reference](#10--dto-reference)
- [11 · Error Responses](#11--error-responses)
- [12 · Change Log — Old vs. New](#12--change-log--old-vs-new)

---

## 01 · Overview

The News module manages bilingual news articles with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each news item carries embedded text content per language (now rich **Tiptap HTML**), a cover asset (image, video, or audio), an optional cover thumbnail, a JSONB media gallery, category and subcategory classification, bilingual tags and keywords, and a full audit trail via `NewsAuditLog`.

### Base URL

```
/api/v1/news

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create news — **JSON only** |
| `POST` | `/bulk` | Bulk create news — JSON array |
| `PUT` | `/{id}` | Update news — **JSON only** |
| `DELETE` | `/{id}` | Delete a single news item |
| `DELETE` | `/delete/{id}` | Alias for `DELETE /{id}` |
| `DELETE` | `/bulk` | Bulk delete news by id list |
| `GET` | `/` or `""` or `/all` | Paginated list of all news |
| `GET` | `/{id}` | Fetch a single news item by id |
| `GET` | `/search` | Global full-text search (title + description + tags + keywords) |
| `GET` | `/search/tag` | Search by tag name (language-aware) |
| `GET` | `/search/keyword` | Search by keyword name (language-aware) |
| `GET` | `/search/category` | Search by category name (CKB or KMR, partial match) |
| `GET` | `/search/subcategory` | Search by subcategory name (CKB or KMR, partial match) |

> ⚠️ **Multipart endpoints removed.** `POST /with-files`, `PUT /{id}/with-files`, and `PUT /update/{id}/with-files` are gone. `POST /` and `PUT /{id}` are now pure `application/json`. See [Section 02 · Media Pipeline](#02--media-pipeline-tiptap) for the new upload flow.

---

## 02 · Media Pipeline (Tiptap)

The News module has migrated to a **Tiptap-based content pipeline**. Binary files are no longer uploaded as part of the news request body.

### How it works

1. **Frontend uploads** each cover/gallery/inline asset first via:
   ```
   POST /api/v1/media/upload
   ```
2. The upload endpoint returns a stored URL (S3 or CDN).
3. The frontend then sends those URLs back inside the news JSON:
   - **Cover** → `coverUrl` (top-level) + `coverMediaType` + optional `coverThumbnailUrl`
   - **Gallery items** → `mediaGallery[]` array of `MediaItem`
   - **Inline media** → baked directly into the **Tiptap HTML** stored in `ckbContent.description` / `kmrContent.description`
4. The backend `TiptapHtmlProcessor` runs on the HTML descriptions during create/update.

### What this replaces

- ❌ `news_media` table — **dropped**
- ❌ `NewsMedia` entity — **dropped**
- ❌ `NewsMediaType` enum — **dropped**
- ❌ `media[]` request/response array (with `type`, `url`, `externalUrl`, `embedUrl`, `sortOrder`) — **dropped**
- ❌ Multipart `coverImage` / `mediaFiles` / `cover` / `media` form parts — **dropped**

---

## 03 · Data Models

Five JPA entities power the news module. `News` is the aggregate root; `NewsContent` is embedded; categories live in their own tables; the media gallery is stored as a **JSONB** column (`List<MediaItem>`).

### News — `news`

Aggregate root. Manages `@PrePersist` / `@PreUpdate` lifecycle hooks for `createdAt` and `updatedAt`. Uses `@BatchSize(50)` on every lazy collection.

**DB indexes:** `idx_news_date_published` (DESC), `idx_news_created_at` (DESC).

| Field | DB Type | Constraint | Description |
| --- | --- | --- | --- |
| `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `coverUrl` | VARCHAR(1024) | NULLABLE | Cover asset URL (image, video, or audio) |
| `coverMediaType` | ENUM(STRING, 16) | NULLABLE | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | VARCHAR(1024) | NULLABLE | Poster (VIDEO) or cover art (AUDIO) for the card cover |
| `mediaGallery` | **JSONB** | NULLABLE | Ordered `List<MediaItem>` — mixed image/video/audio gallery beside cover |
| `datePublished` | DATE | NULLABLE | Publication date. Defaults to today on `@PrePersist` if null |
| `createdAt` | TIMESTAMP | NOT NULL | Set on `@PrePersist`. `LocalDateTime` |
| `updatedAt` | TIMESTAMP | NOT NULL | Set on `@PrePersist` and `@PreUpdate`. `LocalDateTime` |
| `contentLanguages` | @ElementCollection (LAZY) | NOT NULL | `Set<Language>` — at least one required. Table `news_content_languages` |
| `ckbContent` | EMBEDDED | NULLABLE | Sorani CKB: `title`, `description` (**Tiptap HTML**) |
| `kmrContent` | EMBEDDED | NULLABLE | Kurmanji KMR: `title`, `description` (**Tiptap HTML**) |
| `tagsCkb / tagsKmr` | @ElementCollection (LAZY) | NULLABLE | `Set<String>` — separate tables `news_tags_ckb`, `news_tags_kmr` |
| `keywordsCkb / keywordsKmr` | @ElementCollection (LAZY) | NULLABLE | `Set<String>` — separate tables `news_keywords_ckb`, `news_keywords_kmr` |
| `category` | FK → news_categories | NOT NULL | Bilingual category (LAZY, `@BatchSize(50)` on entity class) |
| `subCategory` | FK → news_sub_categories | NOT NULL | Bilingual subcategory (LAZY, `@BatchSize(50)` on entity class) |

> ⚠️ The `news_media` table and the `NewsMedia` JPA entity **no longer exist**.

### NewsContent — `@Embeddable`

| Field | DB Column (CKB) | DB Column (KMR) | Type |
| --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(250) |
| `description` | `description_ckb` | `description_kmr` | TEXT — **Tiptap HTML** |

### MediaItem — JSONB gallery element (not a JPA entity)

Stored as one element of the `media_gallery` JSONB column on `News`. Plain POJO — no DB id; ordering is derived from `sortOrder` and array position.

| Field | Type | Description |
| --- | --- | --- |
| `url` | String | S3 / CDN URL of the asset (**required**; items with blank `url` are filtered out) |
| `kind` | `MediaKind` | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `thumbnailUrl` | String | Optional poster (VIDEO) or cover art (AUDIO); ignored for IMAGE |
| `captionCkb` | String | Sorani caption shown under the asset |
| `captionKmr` | String | Kurmanji caption shown under the asset |
| `sortOrder` | Integer | Display order inside the gallery (ascending). Auto-assigned by index if null |

### NewsCategory — `news_categories`

**Class-level `@BatchSize(50)`.** DB index `idx_news_category_name_ckb`.

| Field | DB Type | Constraint | Description |
| --- | --- | --- | --- |
| `id` | BIGINT | PK / AUTO | Auto-increment |
| `nameCkb` | VARCHAR(120) | **UNIQUE / NOT NULL** | Sorani name — used as the unique lookup key |
| `nameKmr` | VARCHAR(120) | NOT NULL | Kurmanji name |
| `subCategories` | @OneToMany | NULLABLE | Child subcategories. Cascade ALL + orphanRemoval |

> ℹ️ Categories are **looked up or created** by the service using `nameCkb` as the unique key. If a category with the same `nameCkb` already exists but has a different `nameKmr`, the `nameKmr` is updated automatically.

### NewsSubCategory — `news_sub_categories`

**Class-level `@BatchSize(50)`.** Unique constraint `(category_id, name_ckb)`. DB index `idx_news_sub_category_name_ckb`.

| Field | DB Type | Constraint | Description |
| --- | --- | --- | --- |
| `id` | BIGINT | PK / AUTO | Auto-increment |
| `nameCkb` | VARCHAR(120) | NOT NULL | Sorani name — unique per parent category |
| `nameKmr` | VARCHAR(120) | NOT NULL | Kurmanji name |
| `category` | FK → news_categories | NOT NULL | Parent category (LAZY) |

### NewsAuditLog — `news_audit_logs`

Append-only audit log. Stores `newsId` as a plain column (not a FK) so logs are retained even after a news item is deleted. `@PrePersist` initializes `createdAt`, `updatedAt`, and (if null) `actionTime`. `@PreUpdate` refreshes `updatedAt`.

| Field | Type | Description |
| --- | --- | --- |
| `id` | BIGINT PK | Auto-increment |
| `newsId` | BIGINT (column) | ID of the news item — retained after deletion |
| `action` | VARCHAR(20) | `CREATE` \| `UPDATE` \| `DELETE` |
| `performedBy` | VARCHAR(150) | Acting principal (currently `"system"`) |
| `note` | TEXT | Human-readable description of the action |
| `actionTime` | TIMESTAMP | When the action occurred (defaults to `createdAt`) |
| `createdAt` | TIMESTAMP (NOT NULL) | Set on `@PrePersist` |
| `updatedAt` | TIMESTAMP (NOT NULL) | Set on `@PrePersist` and `@PreUpdate` |

---

## 04 · Enums

### MediaKind (cover + gallery items)

| Value | Description |
| --- | --- |
| `IMAGE` | Rendered with `<img>` |
| `VIDEO` | Rendered with `<video>` — `thumbnailUrl` used as poster |
| `AUDIO` | Rendered with `<audio>` — `thumbnailUrl` used as cover art |

### Language

| Value | Description |
| --- | --- |
| `CKB` | Sorani Kurdish |
| `KMR` | Kurmanji Kurdish |

> ⛔ **`NewsMediaType` enum removed.** Old values (`IMAGE`, `VIDEO`, `AUDIO`, `DOCUMENT`) are no longer used by the news module. Documents/PDFs/text live inside the Tiptap HTML; images/videos/audios use the unified `MediaKind`.

---

## 05 · Authentication, Caching & Read Strategy

> ℹ️ **All news endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> ⚡ **Caching:** All read and search operations are `@Cacheable(value="news")`. Every write (`addNews`, `addNewsBulk`, `updateNews`, `deleteNews`, `deleteNewsBulk`) does `@CacheEvict(allEntries=true)`.

> ⚠️ **N+1 Protection:** All collection fields on `News` use `@BatchSize(50)`. `NewsCategory` and `NewsSubCategory` carry `@BatchSize(50)` at the **entity class** level. Reads use a **two-phase load** — Phase 1 returns IDs only (index scan); Phase 2 batch-hydrates the page in a few IN-queries — so list/search response cost is bounded regardless of page size.

> 📐 **Default ordering:** All list/search endpoints order by `datePublished DESC, createdAt DESC` (newest first).

---

## 06 · Create News

### `POST /api/v1/news`

🔒 **Auth Required** · `Content-Type: application/json`

Create a single news item. **Cover URL is required** — upload the cover file first via `POST /api/v1/media/upload`, then pass the returned URL here. All inline images/videos/audio go inside the Tiptap HTML body of `ckbContent.description` / `kmrContent.description`.

### Request Body — NewsDto

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| `coverUrl` | String | **Yes** | Pre-uploaded cover URL |
| `coverMediaType` | `MediaKind` | No | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | String | No | Poster (VIDEO) / cover art (AUDIO) |
| `mediaGallery` | `List<MediaItem>` | No | Mixed gallery — see [DTO Reference](#10--dto-reference) |
| `datePublished` | `LocalDate` | No | Defaults to today on `@PrePersist` if null |
| `contentLanguages` | `Set<Language>` | **Yes** | At least one of `CKB`, `KMR` |
| `category` | `CategoryDto` | **Yes** | Both `ckbName` and `kmrName` required |
| `subCategory` | `SubCategoryDto` | **Yes** | Both `ckbName` and `kmrName` required |
| `ckbContent` | `LanguageContentDto` | Conditional | `title` required when CKB selected; `description` accepts Tiptap HTML |
| `kmrContent` | `LanguageContentDto` | Conditional | `title` required when KMR selected; `description` accepts Tiptap HTML |
| `tags` | `BilingualSet` | No | `{ ckb: Set<String>, kmr: Set<String> }` |
| `keywords` | `BilingualSet` | No | `{ ckb: Set<String>, kmr: Set<String> }` |

> ⛔ **Removed fields** (silently ignored — DTO has `@JsonIgnoreProperties(ignoreUnknown = true)`):
> - `media[]` (with `type`, `url`, `externalUrl`, `embedUrl`, `sortOrder`) — use Tiptap HTML in `description` + `mediaGallery[]` for the standalone gallery.

### Request JSON

```json
{
  "coverUrl":          "https://cdn.khi.iq/news/cover-001.jpg",
  "coverMediaType":    "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery": [
    {
      "url":          "https://cdn.khi.iq/news/gallery-001.jpg",
      "kind":         "IMAGE",
      "thumbnailUrl": null,
      "captionCkb":   "وێنەی سەرەکی",
      "captionKmr":   "Wêneya sereke",
      "sortOrder":    0
    },
    {
      "url":          "https://cdn.khi.iq/news/clip.mp4",
      "kind":         "VIDEO",
      "thumbnailUrl": "https://cdn.khi.iq/news/clip-poster.jpg",
      "captionCkb":   "ڤیدیۆی ڕاپۆرت",
      "captionKmr":   "Vîdyoya rapor",
      "sortOrder":    1
    }
  ],
  "datePublished":     "2026-04-11",
  "contentLanguages":  ["CKB", "KMR"],
  "category": {
    "ckbName": "سیاسی",
    "kmrName": "Siyasî"
  },
  "subCategory": {
    "ckbName": "ناوخۆ",
    "kmrName": "Navxweyî"
  },
  "ckbContent": {
    "title":       "سەردێڕی هەواڵەکە بەزمانی کوردی سۆرانی",
    "description": "<h2>پێشەکی</h2><p>وردبینی هەواڵەکە</p><img src=\"https://cdn.khi.iq/news/inline-1.jpg\" />"
  },
  "kmrContent": {
    "title":       "Sernivîsa Nûçeyê bi Kurdî Kurmancî",
    "description": "<h2>Pêşgotin</h2><p>Vekolîna nûçeyê</p>"
  },
  "tags": {
    "ckb": ["سیاسی", "کوردستان", "ناوخۆ"],
    "kmr": ["siyasî", "Kurdistan", "navxweyî"]
  },
  "keywords": {
    "ckb": ["هەرێم", "پەرلەمان"],
    "kmr": ["herêm", "parleman"]
  }
}
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "News created successfully",
  "data": {
    "id":                1,
    "coverUrl":          "https://cdn.khi.iq/news/cover-001.jpg",
    "coverMediaType":    "IMAGE",
    "coverThumbnailUrl": null,
    "mediaGallery": [
      {
        "url":          "https://cdn.khi.iq/news/gallery-001.jpg",
        "kind":         "IMAGE",
        "thumbnailUrl": null,
        "captionCkb":   "وێنەی سەرەکی",
        "captionKmr":   "Wêneya sereke",
        "sortOrder":    0
      },
      {
        "url":          "https://cdn.khi.iq/news/clip.mp4",
        "kind":         "VIDEO",
        "thumbnailUrl": "https://cdn.khi.iq/news/clip-poster.jpg",
        "captionCkb":   "ڤیدیۆی ڕاپۆرت",
        "captionKmr":   "Vîdyoya rapor",
        "sortOrder":    1
      }
    ],
    "datePublished":    "2026-04-11",
    "createdAt":        "2026-04-11T21:30:00",
    "updatedAt":        "2026-04-11T21:30:00",
    "contentLanguages": ["CKB", "KMR"],
    "category":    { "ckbName": "سیاسی", "kmrName": "Siyasî" },
    "subCategory": { "ckbName": "ناوخۆ", "kmrName": "Navxweyî" },
    "ckbContent": {
      "title":       "سەردێڕی هەواڵەکە بەزمانی کوردی سۆرانی",
      "description": "<h2>پێشەکی</h2><p>وردبینی هەواڵەکە</p><img src=\"https://cdn.khi.iq/news/inline-1.jpg\" />"
    },
    "kmrContent": {
      "title":       "Sernivîsa Nûçeyê bi Kurdî Kurmancî",
      "description": "<h2>Pêşgotin</h2><p>Vekolîna nûçeyê</p>"
    },
    "tags":     { "ckb": ["سیاسی", "کوردستان", "ناوخۆ"], "kmr": ["siyasî", "Kurdistan", "navxweyî"] },
    "keywords": { "ckb": ["هەرێم", "پەرلەمان"],          "kmr": ["herêm", "parleman"] }
  }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | Request body is null |
| `400` | `news.languages.required` | `contentLanguages` is empty |
| `400` | `news.cover.required` | `coverUrl` is missing or blank |
| `400` | `news.category.required` | Category missing or either bilingual name is blank |
| `400` | `news.subcategory.required` | SubCategory missing or either bilingual name is blank |
| `400` | `news.ckb.title.required` | CKB selected but `ckbContent.title` is blank |
| `400` | `news.kmr.title.required` | KMR selected but `kmrContent.title` is blank |
| `401` | — | Missing or expired JWT token |

---

### `POST /api/v1/news/bulk`

🔒 **Auth Required** · `Content-Type: application/json`

Create multiple news items in a single request using a JSON array. All items are validated **first**; if any fails, none are saved. Each item must include a `coverUrl`.

### Request Body — `List<NewsDto>`

```json
[
  {
    "coverUrl":         "https://cdn.khi.iq/news/cover-001.jpg",
    "coverMediaType":   "IMAGE",
    "datePublished":    "2026-04-11",
    "contentLanguages": ["CKB"],
    "category":    { "ckbName": "ئابووری", "kmrName": "Aborî" },
    "subCategory": { "ckbName": "بازار",   "kmrName": "Bazar" },
    "ckbContent": {
      "title":       "بازاری نەوت لە ئاستی جیهاندا",
      "description": "<p>بەهای نەوت لە بازاری جیهاندا بەرزبووەوە</p>"
    },
    "tags":     { "ckb": ["نەوت", "ئابووری"], "kmr": [] },
    "keywords": { "ckb": ["نەوت"],            "kmr": [] }
  },
  {
    "coverUrl":         "https://cdn.khi.iq/news/cover-002.jpg",
    "coverMediaType":   "IMAGE",
    "datePublished":    "2026-04-10",
    "contentLanguages": ["CKB", "KMR"],
    "category":    { "ckbName": "سیاسی", "kmrName": "Siyasî" },
    "subCategory": { "ckbName": "دیپلۆماسی", "kmrName": "Dîplomasi" },
    "ckbContent": {
      "title":       "چارەسەری دیپلۆماسی بۆ کێشەی ئاو",
      "description": "<p>لێکدانەوەی ئەنجامەکانی دانوستان</p>"
    },
    "kmrContent": {
      "title":       "Çareseriya Dîplomasiyê ji bo Pirsgirêka Avê",
      "description": "<p>Şirovekirina encamên danûstandinan</p>"
    },
    "tags":     { "ckb": ["دیپلۆماسی"], "kmr": ["dîplomasi"] },
    "keywords": { "ckb": ["پەیوەندی دەرەوە"], "kmr": ["têkiliyên derve"] }
  }
]
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "News created successfully (bulk)",
  "data": [
    { /* full NewsDto for item 1 */ },
    { /* full NewsDto for item 2 */ }
  ]
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | Request list is null or empty |
| `400` | `news.cover.required` | Any item is missing `coverUrl` |
| `400` | various | Any item fails the per-item validation rules above |
| `401` | — | Missing or expired JWT |

---

## 07 · Update News

### `PUT /api/v1/news/{id}`

🔒 **Auth Required** · `Content-Type: application/json`

Update an existing news item using a plain JSON body. Update semantics are **partial-merge per field** (see below).

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the news item to update |

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `coverUrl` | Replaces existing cover | Existing cover is **kept** (must already exist, otherwise `400 news.cover.required`) |
| `coverMediaType` | Replaces existing value | Existing value is kept |
| `coverThumbnailUrl` | Replaces existing value (blank → null) | Existing value is kept |
| `mediaGallery` | Replaces entire gallery. Sending `[]` clears it | Existing gallery is kept |
| `datePublished` | Replaces existing date | Existing date is kept |
| `category` | Replaces existing category (looked-up / created) | Existing category is kept |
| `subCategory` | Replaces existing subcategory | Existing subcategory is kept |
| `contentLanguages` | Replaces the language set | Cleared (treated as empty — title checks apply) |
| `ckbContent` / `kmrContent` | Replaces the block. If the language is no longer in `contentLanguages`, the block is set to `null` and its tags/keywords are cleared | — |
| `tags.ckb` / `tags.kmr` | Replaces that side of the set | That side is kept |
| `keywords.ckb` / `keywords.kmr` | Replaces that side of the set | That side is kept |

> ⚠️ **`coverUrl` rule:** if you omit `coverUrl` AND the entity has no existing `coverUrl`, the server returns `400 news.cover.required`. In practice always send the existing URL back if you are not replacing it.

### Request JSON

```json
{
  "coverUrl":          "https://cdn.khi.iq/news/cover-updated.jpg",
  "coverMediaType":    "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery":      [],
  "datePublished":     "2026-04-12",
  "contentLanguages":  ["CKB", "KMR"],
  "category":    { "ckbName": "سیاسی", "kmrName": "Siyasî" },
  "subCategory": { "ckbName": "ناوخۆ", "kmrName": "Navxweyî" },
  "ckbContent": {
    "title":       "سەردێڕی نوێکراوە بەزمانی سۆرانی",
    "description": "<p>وردبینی نوێکراوی</p>"
  },
  "kmrContent": {
    "title":       "Sernivîsa Nûkirî bi Kurmancî",
    "description": "<p>Vekolîna nûkirî</p>"
  },
  "tags":     { "ckb": ["نوێ"], "kmr": ["nû"] },
  "keywords": { "ckb": ["نوێکردنەوە"], "kmr": ["nûkirî"] }
}
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "News updated successfully",
  "data": { /* full NewsDto — same shape as create */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | `id` path variable is null |
| `400` | `news.cover.required` | `coverUrl` is missing and entity has no existing cover |
| `400` | various | Validation rules from `POST /` (languages, category, subcategory, titles) |
| `401` | — | Missing or expired JWT |
| `404` | `news.not_found` | News item with given `id` not found |

---

## 08 · Delete News

### `DELETE /api/v1/news/{id}`

🔒 **Auth Required**

Permanently delete a news item along with its embedded content, bilingual tag/keyword collections, language collection, and JSONB media gallery. An audit log entry with action `DELETE` is written **before** the row is removed; the `newsId` is preserved in `news_audit_logs` even after the parent row is gone.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the news item to delete |

### Response · 200 OK

```json
{
  "success": true,
  "message": "News deleted successfully",
  "data":    null
}
```

> ⛔ **This action is irreversible.** All media gallery items, bilingual collections, and embedded content are permanently removed. Categories and subcategories are **not** deleted — they are shared across news items.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | `id` path variable is null |
| `400` | `news.not_found` | News item with given `id` not found |
| `401` | — | Missing or expired JWT |

---

### `DELETE /api/v1/news/delete/{id}`

🔒 **Auth Required**

Alias for `DELETE /{id}` — identical behaviour and response.

---

### `DELETE /api/v1/news/bulk`

🔒 **Auth Required** · `Content-Type: application/json`

Delete multiple news items in one request. An audit log entry is written for each deleted item **before** the rows are removed.

### Request Body — `List<Long>`

```json
[1, 2, 3, 42, 99]
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "News deleted successfully (bulk)",
  "data":    null
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | `newsIds` list is null or empty |
| `400` | `news.not_found` | None of the provided IDs were found |
| `401` | — | Missing or expired JWT |

---

## 09 · Read & Search

### `GET /api/v1/news` — getAll

🔒 **Auth Required** · `@Cacheable("news")`

Return a paginated list of all news items ordered by `datePublished DESC, createdAt DESC`. Uses the two-phase load described in [Section 05](#05--authentication-caching--read-strategy).

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Items per page |

### Request

```
GET /api/v1/news?page=0&size=10
Authorization: Bearer eyJhbGci...

# Also routed at:
GET /api/v1/news/
GET /api/v1/news/all
```

### Response · 200 OK — `Page<NewsDto>`

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": {
    "content": [
      {
        "id":                5,
        "coverUrl":          "https://cdn.khi.iq/news/cover-005.jpg",
        "coverMediaType":    "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery":      [],
        "datePublished":     "2026-04-10",
        "createdAt":         "2026-04-10T08:00:00",
        "updatedAt":         "2026-04-10T08:00:00",
        "contentLanguages":  ["CKB"],
        "category":     { "ckbName": "سیاسی", "kmrName": "Siyasî" },
        "subCategory":  { "ckbName": "ناوخۆ", "kmrName": "Navxweyî" },
        "ckbContent": {
          "title":       "سەردێڕی هەواڵ",
          "description": "<p>وردبینی هەواڵ</p>"
        },
        "kmrContent": null,
        "tags":     { "ckb": ["سیاسی", "کوردستان"], "kmr": [] },
        "keywords": { "ckb": ["هەرێم"],             "kmr": [] }
      }
    ],
    "totalElements": 84,
    "totalPages":    9,
    "size":          10,
    "number":        0,
    "first":         true,
    "last":          false,
    "empty":         false
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/news/{id}`

🔒 **Auth Required**

Fetch a single news item by its primary key. Loads the full entity graph via `findByIdWithGraph` (`contentLanguages`, tags, keywords, category, subCategory).

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the news item |

### Response · 200 OK

Same shape as one element of `GET /` `content`.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `news.not_found` | News item with given `id` not found |

---

### `GET /api/v1/news/search`

🔒 **Auth Required** · `@Cacheable("news")`

Global **case-insensitive partial-match** search across `title_ckb`, `title_kmr`, `description_ckb`, `description_kmr`, `tagsCkb`, `tagsKmr`, `keywordsCkb`, `keywordsKmr` — all in a single SQL query. Ideal for a unified search box.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `q` | String | **Yes** | Search term. URL-encode non-ASCII |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request

```
GET /api/v1/news/search?q=کوردستان&page=0&size=20
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Global search completed",
  "data": { /* Page<NewsDto> — same shape as getAll */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `keyword.required` | `q` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/news/search/tag`

🔒 **Auth Required** · `@Cacheable("news")`

Case-insensitive partial-match (`LIKE %tag%`) search on tag sets with an optional `language` scope.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `tag` | String | **Yes** | Tag name. URL-encode non-ASCII |
| `language` | String | No | `ckb` \| `kmr` \| `both` (default) — picks the JPQL variant |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request

```
GET /api/v1/news/search/tag?tag=سیاسی&language=ckb&page=0&size=10
GET /api/v1/news/search/tag?tag=dîrok&language=both
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `tag.required` | `tag` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/news/search/keyword`

🔒 **Auth Required** · `@Cacheable("news")`

Mirrors `/search/tag` — case-insensitive partial-match on keyword sets, optionally scoped by language.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | String | **Yes** | Keyword name |
| `language` | String | No | `ckb` \| `kmr` \| `both` (default) |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `keyword.required` | `keyword` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/news/search/category`

🔒 **Auth Required** · `@Cacheable("news")` · **NEW endpoint**

Case-insensitive partial-match search on `category.nameCkb` OR `category.nameKmr`.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | String | **Yes** | Category name (CKB or KMR). URL-encode non-ASCII |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request

```
GET /api/v1/news/search/category?name=سیاسی&page=0&size=20
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `news.category.required` | `name` is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/news/search/subcategory`

🔒 **Auth Required** · `@Cacheable("news")` · **NEW endpoint**

Case-insensitive partial-match search on `subCategory.nameCkb` OR `subCategory.nameKmr`.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | String | **Yes** | SubCategory name (CKB or KMR). URL-encode non-ASCII |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request

```
GET /api/v1/news/search/subcategory?name=ناوخۆ&page=0&size=20
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `news.subcategory.required` | `name` is missing or blank |
| `401` | — | Missing or expired JWT |

---

## 10 · DTO Reference

### NewsDto (Request & Response)

`NewsDto` is used as both the inbound request shape (now a plain JSON body) and the outbound response shape. Unknown JSON fields are silently ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`).

| Field | Type | Request | Response | Description |
| --- | --- | --- | --- | --- |
| `id` | Long | — | ✅ | DB primary key |
| `coverUrl` | String | **Required (create)** / optional (update with existing cover) | ✅ | Pre-uploaded cover URL |
| `coverMediaType` | `MediaKind` | Optional | ✅ | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | String | Optional | ✅ | Optional poster / cover art |
| `mediaGallery` | `List<MediaItem>` | Optional | ✅ | Ordered gallery items (sortOrder ASC) |
| `datePublished` | `LocalDate` | Optional | ✅ | Defaults to today on create |
| `createdAt` | `LocalDateTime` | — | ✅ | Server-set on `@PrePersist` |
| `updatedAt` | `LocalDateTime` | — | ✅ | Server-set on `@PrePersist` / `@PreUpdate` |
| `contentLanguages` | `Set<Language>` | **Required** | ✅ | At least one of `CKB`, `KMR` |
| `category` | `CategoryDto` | **Required** | ✅ | Both names required |
| `subCategory` | `SubCategoryDto` | **Required** | ✅ | Both names required |
| `ckbContent` | `LanguageContentDto` | Required if CKB active | ✅ | `{ title, description }` — `description` = Tiptap HTML |
| `kmrContent` | `LanguageContentDto` | Required if KMR active | ✅ | `{ title, description }` — `description` = Tiptap HTML |
| `tags` | `BilingualSet` | Optional | ✅ | `{ ckb: Set<String>, kmr: Set<String> }` |
| `keywords` | `BilingualSet` | Optional | ✅ | `{ ckb: Set<String>, kmr: Set<String> }` |

> ⛔ **Removed:** the old `media` field of type `List<MediaDto>` with `type` / `externalUrl` / `embedUrl`. Use `mediaGallery` (homogeneous `MediaItem` POJO) and bake inline media into Tiptap HTML.

### NewsDto.CategoryDto

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ckbName` | String | **Yes** | Sorani name — unique lookup key |
| `kmrName` | String | **Yes** | Kurmanji name |

### NewsDto.SubCategoryDto

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ckbName` | String | **Yes** | Sorani name — unique per parent category |
| `kmrName` | String | **Yes** | Kurmanji name |

### NewsDto.LanguageContentDto

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | String | **Yes** when its language is in `contentLanguages` | Article title in this language (max 250) |
| `description` | String | No | **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |

### NewsDto.BilingualSet

| Field | Type | Description |
| --- | --- | --- |
| `ckb` | Set<String> | CKB tag/keyword strings. Empty set if none |
| `kmr` | Set<String> | KMR tag/keyword strings. Empty set if none |

### MediaItem (request + response)

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `url` | String | **Yes** | S3 / CDN URL — items with blank `url` are filtered out |
| `kind` | `MediaKind` | No | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `thumbnailUrl` | String | No | Poster (VIDEO) / cover art (AUDIO) |
| `captionCkb` | String | No | Sorani caption |
| `captionKmr` | String | No | Kurmanji caption |
| `sortOrder` | Integer | No | Display order ASC. Auto-assigned by index if null |

### ApiResponse&lt;T&gt;

All endpoints return this wrapper. `data` is omitted on failure due to `@JsonInclude(NON_NULL)`.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure |

---

## 11 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New news item(s) saved successfully |
| `200 OK` | Update, delete, or read succeeded |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | News item with given id does not exist |
| `500 Internal Error` | Unexpected server failure — check logs |

### Common Business Error Keys

| Error Key | Trigger |
| --- | --- |
| `error.validation` | Generic validation envelope (null body, null id, empty list, etc.) |
| `news.cover.required` | `coverUrl` missing on create, or missing on update with no existing cover |
| `news.languages.required` | `contentLanguages` is empty or null |
| `news.category.required` | Category missing or either bilingual name is blank |
| `news.subcategory.required` | SubCategory missing or either bilingual name is blank |
| `news.ckb.title.required` | CKB active but `ckbContent.title` is blank |
| `news.kmr.title.required` | KMR active but `kmrContent.title` is blank |
| `news.not_found` | News item with the given id does not exist |
| `tag.required` | `/search/tag` called with blank `tag` |
| `keyword.required` | `/search/keyword` or `/search` called with blank param |

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "errors": [
    {
      "field":   "contentLanguages",
      "message": "At least one content language is required"
    },
    {
      "field":   "category",
      "message": "Category with both bilingual names is required"
    }
  ]
}
```

### Business Rule Error Body — `400`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "error":     "Bad Request",
  "message":   "news.cover.required"
}
```

### Auth Error Body — `401`

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
  "message":   "News not found with id: 99"
}
```

> ℹ️ All `createdAt` and `updatedAt` fields on `NewsDto` are `LocalDateTime`.

---

## 12 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /api/v1/news` | Multipart (`news` + `coverImage` + `mediaFiles`) | **JSON only** | 🟡 Changed |
| `POST /api/v1/news/with-files` | Flexible multipart (`news`/`data`, `cover`/`coverImage`, …) | — | 🔴 **Removed** |
| `POST /api/v1/news/bulk` | JSON array | JSON array | ⚪ Unchanged |
| `PUT /api/v1/news/{id}` | Multipart | **JSON only** (partial-merge semantics) | 🟡 Changed |
| `PUT /api/v1/news/{id}/with-files` | Flexible multipart | — | 🔴 **Removed** |
| `PUT /api/v1/news/update/{id}/with-files` | Alias of `/{id}/with-files` | — | 🔴 **Removed** |
| `DELETE /api/v1/news/{id}` | Delete | Delete | ⚪ Unchanged |
| `DELETE /api/v1/news/delete/{id}` | Alias | Alias | ⚪ Unchanged |
| `DELETE /api/v1/news/bulk` | Bulk delete | Bulk delete | ⚪ Unchanged |
| `GET /api/v1/news` / `/all` | Paginated list | Paginated list (also routed at `""`) | ⚪ Unchanged |
| `GET /api/v1/news/{id}` | By id | By id | ⚪ Unchanged |
| `GET /api/v1/news/search?q=…` | Global search | Global search | ⚪ Unchanged |
| `GET /api/v1/news/search/tag` | By tag + language | By tag + language | ⚪ Unchanged |
| `GET /api/v1/news/search/keyword` | By keyword + language | By keyword + language | ⚪ Unchanged |
| `GET /api/v1/news/search/category` | — | Partial match on category name | 🟢 **Added** |
| `GET /api/v1/news/search/subcategory` | — | Partial match on subcategory name | 🟢 **Added** |

**Endpoint count:** 14 → **13** (3 removed, 2 added, 2 changed from multipart to JSON).

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `news_media` table | Existed | 🔴 **Dropped** |
| `NewsMedia` entity | Existed (FK to news, `type`, `url`, `externalUrl`, `embedUrl`, `sortOrder`, `createdAt`) | 🔴 **Dropped** |
| `NewsMediaType` enum (`IMAGE`/`VIDEO`/`AUDIO`/`DOCUMENT`) | Existed | 🔴 **Dropped** |
| Cover discriminator | None — `coverUrl` only | 🟢 `coverMediaType` (`MediaKind`) + `coverThumbnailUrl` |
| Media gallery storage | `@OneToMany NewsMedia` join table | 🟢 `mediaGallery` JSONB `List<MediaItem>` on `news` |
| Gallery item shape | `NewsMedia` with `type`/`url`/`externalUrl`/`embedUrl` | 🟢 `MediaItem`: `url` + `kind` + `thumbnailUrl` + `captionCkb` + `captionKmr` + `sortOrder` |
| Bilingual captions on gallery | No | 🟢 Yes (`captionCkb`, `captionKmr`) |
| `description_ckb` / `description_kmr` content type | Plain TEXT | 🟢 **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| DB indexes on `news` | (unspecified) | 🟢 `idx_news_date_published DESC`, `idx_news_created_at DESC` |
| Default list ordering | (unspecified) | 🟢 `datePublished DESC, createdAt DESC` |
| `NewsAuditLog` columns | `id`, `newsId`, `action`, `performedBy`, `note`, `actionTime` | 🟢 Adds `createdAt` (NOT NULL) and `updatedAt` (NOT NULL); `actionTime` defaults to `createdAt` |
| Tags / keywords storage | `@ElementCollection<String>` per language | ⚪ Unchanged |
| Category / SubCategory entities | Same get-or-create semantics | ⚪ Unchanged |

### C) DTO comparison

| Field | Old `NewsDto` | New `NewsDto` |
| --- | --- | --- |
| `coverUrl` | Optional (could rely on `coverImage` file part) | **Required on create** |
| `coverMediaType` | — | 🟢 Added (`MediaKind`, defaults to `IMAGE`) |
| `coverThumbnailUrl` | — | 🟢 Added |
| `mediaGallery` | — | 🟢 Added (`List<MediaItem>`) |
| `media` | `List<MediaDto>` (`type`, `url`, `externalUrl`, `embedUrl`, `sortOrder`) | 🔴 **Removed** |
| `tags` / `keywords` | `BilingualSet` | ⚪ Unchanged |
| `category` / `subCategory` | Required, both names | ⚪ Unchanged |
| `ckbContent.description` / `kmrContent.description` | Plain text | 🟡 Now **Tiptap HTML** |
| `@JsonIgnoreProperties(ignoreUnknown = true)` | Yes | ⚪ Unchanged — old `media[]` payloads from legacy clients are silently dropped |

### D) Request/upload flow comparison

| Step | Old | New |
| --- | --- | --- |
| 1. Upload cover binary | Multipart `coverImage` / `cover` part | `POST /api/v1/media/upload` first, then send URL in `coverUrl` |
| 2. Upload gallery binaries | Multipart `mediaFiles` / `media` parts | `POST /api/v1/media/upload` first, then send URLs in `mediaGallery[]` |
| 3. Inline images inside the article | Sent as separate `NewsMedia` rows with `IMAGE` type | Uploaded individually via `/media/upload`, URLs baked into Tiptap HTML in `description` |
| 4. PDFs / documents | Sent as `NewsMedia` rows with `DOCUMENT` type | Embedded inside Tiptap HTML — there is no document gallery row |
| 5. Update semantics | Replace-all if `media` provided, else keep | **Partial-merge per field** — see [Section 07](#07--update-news) |

### E) Endpoint-level summary

- 🔴 **Removed:** `POST /with-files`, `PUT /{id}/with-files`, `PUT /update/{id}/with-files`.
- 🟢 **Added:** `GET /search/category`, `GET /search/subcategory`.
- 🟡 **Changed (still same URL):** `POST /` and `PUT /{id}` are now JSON-only.
- ⚪ **Unchanged:** `POST /bulk`, all `DELETE` endpoints, `GET /`/`/all`, `GET /{id}`, `GET /search`, `GET /search/tag`, `GET /search/keyword`.
