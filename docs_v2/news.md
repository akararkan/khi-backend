# News Module

> Bilingual (CKB / KMR) news articles with category/subcategory taxonomy, multi-axis search, Tiptap bodies, and audit logs. Public reads, role-gated writes.

## Table of Contents
- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — News](#02--data-model--news)
- [03 · Data Model — NewsContent](#03--data-model--newscontent)
- [04 · Data Model — NewsCategory & NewsSubCategory](#04--data-model--newscategory--newssubcategory)
- [05 · Data Model — NewsAuditLog](#05--data-model--newsauditlog)
- [06 · Enums](#06--enums)
- [07 · Authentication & Roles](#07--authentication--roles)
- [08 · Public API](#08--public-api)
- [09 · Internal API](#09--internal-api)
- [10 · DTO Reference](#10--dto-reference)
- [11 · Response Envelope](#11--response-envelope)
- [12 · Error Responses](#12--error-responses)
- [13 · Notes](#13--notes)

---

## 01 · Module Overview

The News module exposes a Tiptap-aware, bilingual (Sorani / Kurmanji) news API. All endpoints accept and emit `application/json` — multipart endpoints have been removed. The frontend uploads cover images and any inline rich-text media first via `POST /api/v1/media/upload`, then sends the resulting URLs inside the JSON payload (and inside Tiptap HTML for inline media).

- **Base path:** `/api/v1/news`
- **Content type (writes):** `application/json` (no multipart)
- **Cache:** read endpoints are cached under the `news` Spring cache; writes evict the entire region (`allEntries = true`)
- **Audit:** every create / update / delete writes a `NewsAuditLog` row

### Endpoint Summary

| # | Method | Path | Handler | Auth | Description |
|---|--------|------|---------|------|-------------|
| 1 | POST   | `/api/v1/news`                          | `createNews`        | EMPLOYEE+ | Create a single news article |
| 2 | POST   | `/api/v1/news/bulk`                     | `createNewsBulk`    | EMPLOYEE+ | Create many news articles in one transaction |
| 3 | GET    | `/api/v1/news`                          | `getAllNews`        | PUBLIC    | Paged list of all news (base path) |
| 4 | GET    | `/api/v1/news/`                         | `getAllNews`        | PUBLIC    | Paged list (alt mapping with trailing slash) |
| 5 | GET    | `/api/v1/news/all`                      | `getAllNews`        | PUBLIC    | Paged list (alt mapping `/all`) |
| 6 | GET    | `/api/v1/news/{id}`                     | `getNewsById`       | PUBLIC    | Fetch a single news article by id |
| 7 | GET    | `/api/v1/news/search`                   | `globalSearch`      | PUBLIC    | Cross-field free-text search (`q`) |
| 8 | GET    | `/api/v1/news/search/keyword`           | `searchByKeyword`   | PUBLIC    | Search by keyword, optionally per language |
| 9 | GET    | `/api/v1/news/search/tag`               | `searchByTag`       | PUBLIC    | Search by tag, optionally per language |
| 10 | GET   | `/api/v1/news/search/category`          | `searchByCategory`  | PUBLIC    | Filter by category name |
| 11 | GET   | `/api/v1/news/search/subcategory`       | `searchBySubCategory` | PUBLIC  | Filter by subcategory name |
| 12 | PUT   | `/api/v1/news/{id}`                     | `updateNews`        | EMPLOYEE+ | Partial / full update of a news article |
| 13 | DELETE| `/api/v1/news/{id}`                     | `deleteNews`        | ADMIN+    | Delete a news article |
| 14 | DELETE| `/api/v1/news/delete/{id}`              | `deleteNewsAlt`     | ADMIN+    | Alias for delete (legacy path) |
| 15 | DELETE| `/api/v1/news/bulk`                     | `deleteNewsBulk`    | ADMIN+    | Delete many news articles by id list |

---

## 02 · Data Model — News

Entity class: `ak.dev.khi_backend.khi_app.model.news.News`
Table: `news`

Indexes:
- `idx_news_date_published` on `datePublished DESC`
- `idx_news_created_at` on `createdAt DESC`

| Field | Java type | Column / Mapping | Notes |
|-------|-----------|------------------|-------|
| `id` | `Long` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `coverUrl` | `String` | `@Column(name = "cover_url", length = 1024)` | Card cover asset URL (image, video, or audio). Required at create time |
| `coverMediaType` | `MediaKind` | `@Enumerated(EnumType.STRING) @Column(name = "cover_media_type", length = 16)` | Discriminator for `coverUrl`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | `String` | `@Column(name = "cover_thumbnail_url", length = 1024)` | Optional poster (VIDEO) or cover art (AUDIO) URL |
| `mediaGallery` | `List<MediaItem>` | `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "media_gallery", columnDefinition = "jsonb")` | Mixed-type gallery rendered beside the cover, stored as JSONB |
| `datePublished` | `LocalDate` | `@Column(name = "date_published")` | Publication date; defaults to `LocalDate.now()` if null |
| `createdAt` | `LocalDateTime` | `@Column(name = "created_at", nullable = false, updatable = false)` | Set by `@PrePersist` |
| `updatedAt` | `LocalDateTime` | `@Column(name = "updated_at", nullable = false)` | Set by `@PrePersist` and `@PreUpdate` |
| `contentLanguages` | `Set<Language>` | `@ElementCollection(fetch = FetchType.LAZY) @CollectionTable(name = "news_content_languages", joinColumns = @JoinColumn(name = "news_id")) @Enumerated(EnumType.STRING) @Column(name = "language", nullable = false, length = 10) @BatchSize(size = 50)` | Which languages this news supports (subset of `{CKB, KMR}`) |
| `ckbContent` | `NewsContent` (embeddable) | `@Embedded @AttributeOverrides({ @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 250)), @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")) })` | Sorani title + Tiptap HTML body |
| `kmrContent` | `NewsContent` (embeddable) | `@Embedded @AttributeOverrides({ @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 250)), @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")) })` | Kurmanji title + Tiptap HTML body |
| `tagsCkb` | `Set<String>` | `@ElementCollection(fetch = FetchType.LAZY) @CollectionTable(name = "news_tags_ckb", joinColumns = @JoinColumn(name = "news_id")) @Column(name = "tag_ckb", nullable = false, length = 80) @BatchSize(size = 50)` | Sorani tags |
| `tagsKmr` | `Set<String>` | `@ElementCollection(fetch = FetchType.LAZY) @CollectionTable(name = "news_tags_kmr", joinColumns = @JoinColumn(name = "news_id")) @Column(name = "tag_kmr", nullable = false, length = 80) @BatchSize(size = 50)` | Kurmanji tags |
| `keywordsCkb` | `Set<String>` | `@ElementCollection(fetch = FetchType.LAZY) @CollectionTable(name = "news_keywords_ckb", joinColumns = @JoinColumn(name = "news_id")) @Column(name = "keyword_ckb", nullable = false, length = 120) @BatchSize(size = 50)` | Sorani keywords (SEO / search) |
| `keywordsKmr` | `Set<String>` | `@ElementCollection(fetch = FetchType.LAZY) @CollectionTable(name = "news_keywords_kmr", joinColumns = @JoinColumn(name = "news_id")) @Column(name = "keyword_kmr", nullable = false, length = 120) @BatchSize(size = 50)` | Kurmanji keywords (SEO / search) |
| `category` | `NewsCategory` | `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)` | Required category reference |
| `subCategory` | `NewsSubCategory` | `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sub_category_id", nullable = false)` | Required subcategory reference |

Lifecycle callbacks:

```
@PrePersist  → sets createdAt = updatedAt = now(); datePublished = LocalDate.now() when null
@PreUpdate   → sets updatedAt = now()
```

> The legacy `news_media` join table was dropped. Inline images, audio, video and embedded HTML now live inside the Tiptap HTML stored in `description_ckb` and `description_kmr`. Uploads still go through `POST /api/v1/media/upload`.

---

## 03 · Data Model — NewsContent

Embeddable class: `ak.dev.khi_backend.khi_app.model.news.NewsContent`

Used twice on `News` — once for CKB (Sorani) and once for KMR (Kurmanji). Column names are overridden at the embedding site (see the `@AttributeOverrides` block on `News.ckbContent` / `News.kmrContent`).

| Field | Java type | Column annotation | Notes |
|-------|-----------|-------------------|-------|
| `title` | `String` | `@Column(name = "title", length = 250)` | Title text. Overridden to `title_ckb` / `title_kmr` on `News` |
| `description` | `String` | `@Column(name = "description", columnDefinition = "TEXT")` | Tiptap HTML body. Overridden to `description_ckb` / `description_kmr` on `News` |

---

## 04 · Data Model — NewsCategory & NewsSubCategory

### NewsCategory

Entity class: `ak.dev.khi_backend.khi_app.model.news.NewsCategory`
Table: `news_categories`
Class-level `@BatchSize(size = 50)` — Hibernate batch-loads up to 50 categories per IN-query.

Indexes:
- `idx_news_category_name_ckb` on `name_ckb`

| Field | Java type | Column / Mapping | Notes |
|-------|-----------|------------------|-------|
| `id` | `Long` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `nameCkb` | `String` | `@Column(name = "name_ckb", nullable = false, unique = true, length = 120)` | Sorani name. Unique — acts as the natural identifier |
| `nameKmr` | `String` | `@Column(name = "name_kmr", nullable = false, length = 120)` | Kurmanji name |
| `subCategories` | `List<NewsSubCategory>` | `@OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true) @BatchSize(size = 50)` | Children. Cascaded delete |

### NewsSubCategory

Entity class: `ak.dev.khi_backend.khi_app.model.news.NewsSubCategory`
Table: `news_sub_categories`
Class-level `@BatchSize(size = 50)`.

Constraints:
- `@UniqueConstraint(columnNames = {"category_id", "name_ckb"})` — Sorani name is unique within a parent category

Indexes:
- `idx_news_sub_category_name_ckb` on `name_ckb`

| Field | Java type | Column / Mapping | Notes |
|-------|-----------|------------------|-------|
| `id` | `Long` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `nameCkb` | `String` | `@Column(name = "name_ckb", nullable = false, length = 120)` | Sorani name, unique within `category_id` |
| `nameKmr` | `String` | `@Column(name = "name_kmr", nullable = false, length = 120)` | Kurmanji name |
| `category` | `NewsCategory` | `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)` | Parent category reference |

Parent/child relationship: a `NewsCategory` owns many `NewsSubCategory` rows via the `category_id` FK. Categories cascade delete to subcategories (`CascadeType.ALL, orphanRemoval = true`). The service layer creates categories/subcategories on demand via `getOrCreateCategory` / `getOrCreateSubCategory` (lookup by `nameCkb`; if found and the Kurmanji translation changed, the row is updated; otherwise a new row is inserted).

---

## 05 · Data Model — NewsAuditLog

Entity class: `ak.dev.khi_backend.khi_app.model.news.NewsAuditLog`
Table: `news_audit_logs`

| Field | Java type | Column / Mapping | Notes |
|-------|-----------|------------------|-------|
| `id` | `Long` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `newsId` | `Long` | `@Column(name = "news_id", nullable = false)` | Foreign reference to the affected `News.id` |
| `action` | `String` | `@Column(nullable = false, length = 20)` | One of `CREATE` / `UPDATE` / `DELETE` (stored as string) |
| `performedBy` | `String` | `@Column(length = 150)` | Actor id / name. The current service writes the literal `"system"` |
| `note` | `String` | `@Column(columnDefinition = "TEXT")` | Optional description (e.g. `"News created"`, `"News bulk deleted"`) |
| `actionTime` | `LocalDateTime` | (no `@Column` overrides) | When the action happened. Set to `now()` in `@PrePersist` if null |
| `createdAt` | `LocalDateTime` | `@Column(nullable = false, updatable = false)` | Set by `@PrePersist` |
| `updatedAt` | `LocalDateTime` | `@Column(nullable = false)` | Set by `@PrePersist` and `@PreUpdate` |

Lifecycle:

```
@PrePersist → createdAt = updatedAt = now(); actionTime = now() if null
@PreUpdate  → updatedAt = now()
```

---

## 06 · Enums

The package `ak.dev.khi_backend.khi_app.enums.news` is empty. The News module uses two shared enums from `ak.dev.khi_backend.khi_app.enums`:

### Language

```java
public enum Language {
    CKB,  // Kurdish Central (Sorani)
    KMR;  // Kurdish Kurmanji
}
```

| Value | Meaning |
|-------|---------|
| `CKB` | Kurdish Central (Sorani) |
| `KMR` | Kurdish Kurmanji |

Jackson is wired to be case-insensitive on input via `@JsonCreator` (`Language.valueOf(value.trim().toUpperCase())`) and to emit the enum name unchanged on output via `@JsonValue`.

### MediaKind

```java
public enum MediaKind {
    IMAGE,
    VIDEO,
    AUDIO
}
```

| Value | Meaning |
|-------|---------|
| `IMAGE` | Rendered with `<img>` |
| `VIDEO` | Rendered with `<video>`; `thumbnailUrl` used as poster |
| `AUDIO` | Rendered with `<audio>`; `thumbnailUrl` used as cover art |

Used by `News.coverMediaType` and by every `MediaItem` inside `News.mediaGallery`.

---

## 07 · Authentication & Roles

| Method | Path pattern | Required role |
|--------|--------------|---------------|
| GET    | `/api/v1/news/**` | PUBLIC (no auth) |
| POST   | `/api/v1/news/**` | EMPLOYEE, ADMIN, or SUPER_ADMIN |
| PUT    | `/api/v1/news/**` | EMPLOYEE, ADMIN, or SUPER_ADMIN |
| DELETE | `/api/v1/news/**` | ADMIN or SUPER_ADMIN |

Authenticated endpoints expect a bearer token in the `Authorization: Bearer <jwt>` header.

---

## 08 · Public API

All public endpoints are HTTP GET, do not require authentication, and return `ApiResponse<T>` where `T` is either a `NewsDto` or a Spring Data `Page<NewsDto>`.

### 8.1 List news

```
GET /api/v1/news
GET /api/v1/news/
GET /api/v1/news/all
```

All three mappings are aliases of the same handler (`getAllNews`).

**Query parameters**

| Name | Type | Default | Description |
|------|------|---------|-------------|
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"News fetched successfully"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news?page=0&size=20"
```

**Sample JSON response**

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": {
    "content": [
      {
        "id": 142,
        "coverUrl": "https://cdn.example.com/news/142/cover.jpg",
        "coverMediaType": "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery": [],
        "datePublished": "2026-05-30",
        "createdAt": "2026-05-30T09:12:44",
        "updatedAt": "2026-05-30T09:12:44",
        "contentLanguages": ["CKB", "KMR"],
        "category": { "ckbName": "سیاسەت", "kmrName": "Siyaset" },
        "subCategory": { "ckbName": "هەڵبژاردن", "kmrName": "Hilbijartin" },
        "ckbContent": {
          "title": "ئەنجامی هەڵبژاردنی پەرلەمانی هەرێم",
          "description": "<p>ئەنجامەکانی فەرمی بڵاوکرانەوە...</p>"
        },
        "kmrContent": {
          "title": "Encamên hilbijartinên parlamenê yên Herêmê",
          "description": "<p>Encamên fermî hatin weşandin...</p>"
        },
        "tags": { "ckb": ["هەڵبژاردن"], "kmr": ["Hilbijartin"] },
        "keywords": { "ckb": ["پەرلەمان"], "kmr": ["Parlemen"] }
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

### 8.2 Get news by id

```
GET /api/v1/news/{id}
```

**Path parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | Long | News id |

**Response:** `ApiResponse<NewsDto>` — message: `"News fetched successfully"`.

If the id does not exist the service raises `Errors.newsNotFound(id)` (see [Error Responses](#12--error-responses)).

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/142"
```

**Sample JSON response**

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": {
    "id": 142,
    "coverUrl": "https://cdn.example.com/news/142/cover.jpg",
    "coverMediaType": "IMAGE",
    "mediaGallery": [],
    "datePublished": "2026-05-30",
    "contentLanguages": ["CKB", "KMR"],
    "category": { "ckbName": "سیاسەت", "kmrName": "Siyaset" },
    "subCategory": { "ckbName": "هەڵبژاردن", "kmrName": "Hilbijartin" },
    "ckbContent": {
      "title": "ئەنجامی هەڵبژاردنی پەرلەمانی هەرێم",
      "description": "<p>ئەنجامەکانی فەرمی بڵاوکرانەوە...</p>"
    },
    "kmrContent": {
      "title": "Encamên hilbijartinên parlamenê yên Herêmê",
      "description": "<p>Encamên fermî hatin weşandin...</p>"
    },
    "tags": { "ckb": ["هەڵبژاردن"], "kmr": ["Hilbijartin"] },
    "keywords": { "ckb": ["پەرلەمان"], "kmr": ["Parlemen"] }
  }
}
```

### 8.3 Global search

```
GET /api/v1/news/search
```

Free-text search across all indexed fields (titles, descriptions, tags, keywords, category names). Implemented by `NewsRepository.findIdsByGlobalSearch`.

**Query parameters**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `q` | String | yes | — | Search term. Trimmed; blank values return `keyword.required` error |
| `page` | int | no | `0` | Zero-based page index |
| `size` | int | no | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"Global search completed"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/search?q=%D9%87%DB%95%DA%B5%D8%A8%DA%98%D8%A7%D8%B1%D8%AF%D9%86&page=0&size=20"
```

### 8.4 Search by keyword

```
GET /api/v1/news/search/keyword
```

**Query parameters**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `keyword` | String | yes | — | Keyword to look up in `news_keywords_ckb` / `news_keywords_kmr` |
| `language` | String | no | `both` | One of `both`, `ckb`, `kmr` (case-insensitive). `both` searches both tables |
| `page` | int | no | `0` | Zero-based page index |
| `size` | int | no | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"Search by keyword completed"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/search/keyword?keyword=Parlemen&language=kmr&page=0&size=20"
```

### 8.5 Search by tag

```
GET /api/v1/news/search/tag
```

**Query parameters**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `tag` | String | yes | — | Tag to look up in `news_tags_ckb` / `news_tags_kmr` |
| `language` | String | no | `both` | One of `both`, `ckb`, `kmr` (case-insensitive) |
| `page` | int | no | `0` | Zero-based page index |
| `size` | int | no | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"Search by tag completed"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/search/tag?tag=%D9%87%DB%95%DA%B5%D8%A8%DA%98%D8%A7%D8%B1%D8%AF%D9%86&language=ckb"
```

### 8.6 Search by category

```
GET /api/v1/news/search/category
```

Filters news whose category matches the given name (matched by repository query `findIdsByCategory`).

**Query parameters**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `name` | String | yes | — | Category name (Sorani or Kurmanji). Trimmed |
| `page` | int | no | `0` | Zero-based page index |
| `size` | int | no | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"Search by category completed"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/search/category?name=Siyaset&page=0&size=20"
```

### 8.7 Search by subcategory

```
GET /api/v1/news/search/subcategory
```

**Query parameters**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `name` | String | yes | — | Subcategory name (Sorani or Kurmanji). Trimmed |
| `page` | int | no | `0` | Zero-based page index |
| `size` | int | no | `20` | Page size |

**Response:** `ApiResponse<Page<NewsDto>>` — message: `"Search by subcategory completed"`.

**cURL**

```bash
curl -s "https://api.example.com/api/v1/news/search/subcategory?name=Hilbijartin"
```

---

## 09 · Internal API

All write endpoints require a JWT and one of the listed roles. Bodies are `application/json`.

### 9.1 Create news

```
POST /api/v1/news
```

- **Auth:** EMPLOYEE, ADMIN, or SUPER_ADMIN
- **Consumes:** `application/json`
- **Body:** `NewsDto`
- **HTTP status:** `201 Created`
- **Response:** `ApiResponse<NewsDto>` — message: `"News created successfully"`

**Server-side validation (from `NewsService.validate(dto, true)`):**

- request body must not be null
- `contentLanguages` must be non-empty
- `coverUrl` must be non-blank
- `category.ckbName` and `category.kmrName` must be non-blank
- `subCategory.ckbName` and `subCategory.kmrName` must be non-blank
- if `CKB` in `contentLanguages` → `ckbContent.title` must be non-blank
- if `KMR` in `contentLanguages` → `kmrContent.title` must be non-blank

The Tiptap HTML in `ckbContent.description` and `kmrContent.description` is rewritten by `TiptapHtmlProcessor.process(...)` before persistence.

**cURL**

```bash
curl -s -X POST "https://api.example.com/api/v1/news" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "coverUrl": "https://cdn.example.com/news/cover/2026-05-30.jpg",
  "coverMediaType": "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery": [
    {
      "url": "https://cdn.example.com/news/inline/photo1.jpg",
      "kind": "IMAGE",
      "thumbnailUrl": null,
      "captionCkb": "وێنەی یەکەم لە کۆبوونەوەکە",
      "captionKmr": "Wêneya yekem ji civînê",
      "sortOrder": 0
    }
  ],
  "datePublished": "2026-05-30",
  "contentLanguages": ["CKB", "KMR"],
  "category":    { "ckbName": "سیاسەت",   "kmrName": "Siyaset" },
  "subCategory": { "ckbName": "هەڵبژاردن", "kmrName": "Hilbijartin" },
  "ckbContent": {
    "title": "ئەنجامی هەڵبژاردنی پەرلەمانی هەرێم",
    "description": "<p>ئەنجامەکانی فەرمی بڵاوکرانەوە...</p>"
  },
  "kmrContent": {
    "title": "Encamên hilbijartinên parlamenê yên Herêmê",
    "description": "<p>Encamên fermî hatin weşandin...</p>"
  },
  "tags":     { "ckb": ["هەڵبژاردن", "پەرلەمان"], "kmr": ["Hilbijartin", "Parlemen"] },
  "keywords": { "ckb": ["هەرێم"],                "kmr": ["Herêm"] }
}
JSON
```

**Sample JSON response**

```json
{
  "success": true,
  "message": "News created successfully",
  "data": {
    "id": 187,
    "coverUrl": "https://cdn.example.com/news/cover/2026-05-30.jpg",
    "coverMediaType": "IMAGE",
    "mediaGallery": [
      {
        "url": "https://cdn.example.com/news/inline/photo1.jpg",
        "kind": "IMAGE",
        "thumbnailUrl": null,
        "captionCkb": "وێنەی یەکەم لە کۆبوونەوەکە",
        "captionKmr": "Wêneya yekem ji civînê",
        "sortOrder": 0
      }
    ],
    "datePublished": "2026-05-30",
    "createdAt": "2026-05-30T10:01:02",
    "updatedAt": "2026-05-30T10:01:02",
    "contentLanguages": ["CKB", "KMR"],
    "category":    { "ckbName": "سیاسەت",   "kmrName": "Siyaset" },
    "subCategory": { "ckbName": "هەڵبژاردن", "kmrName": "Hilbijartin" },
    "ckbContent": { "title": "ئەنجامی هەڵبژاردنی پەرلەمانی هەرێم", "description": "<p>...</p>" },
    "kmrContent": { "title": "Encamên hilbijartinên parlamenê yên Herêmê", "description": "<p>...</p>" },
    "tags":     { "ckb": ["هەڵبژاردن", "پەرلەمان"], "kmr": ["Hilbijartin", "Parlemen"] },
    "keywords": { "ckb": ["هەرێم"],                "kmr": ["Herêm"] }
  }
}
```

### 9.2 Create news (bulk)

```
POST /api/v1/news/bulk
```

- **Auth:** EMPLOYEE, ADMIN, or SUPER_ADMIN
- **Consumes:** `application/json`
- **Body:** `List<NewsDto>`
- **HTTP status:** `201 Created`
- **Response:** `ApiResponse<List<NewsDto>>` — message: `"News created successfully (bulk)"`

Service behaviour (from `NewsService.addNewsBulk`):

- a null or empty list raises `error.validation` (`field=list`, `message="News list is empty"`)
- every element is validated up-front with the same rules as single create (`validate(dto, true)`)
- if any element fails validation the whole operation aborts before any insert
- inserts happen inside one `transactionTemplate.execute(...)`; if anything throws after validation the entire transaction rolls back (all-or-nothing)
- one `NewsAuditLog` row is written per persisted item with `action = "CREATE"` and `note = "News bulk created"`

**cURL**

```bash
curl -s -X POST "https://api.example.com/api/v1/news/bulk" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
[
  {
    "coverUrl": "https://cdn.example.com/news/cover/n1.jpg",
    "coverMediaType": "IMAGE",
    "datePublished": "2026-05-29",
    "contentLanguages": ["CKB", "KMR"],
    "category":    { "ckbName": "ئابووری", "kmrName": "Aborî" },
    "subCategory": { "ckbName": "بازرگانی", "kmrName": "Bazirganî" },
    "ckbContent":  { "title": "بازاڕی نەوت", "description": "<p>...</p>" },
    "kmrContent":  { "title": "Bazara petrolê", "description": "<p>...</p>" },
    "tags":     { "ckb": ["نەوت"], "kmr": ["Petrol"] },
    "keywords": { "ckb": ["بازاڕ"], "kmr": ["Bazar"] }
  },
  {
    "coverUrl": "https://cdn.example.com/news/cover/n2.jpg",
    "coverMediaType": "VIDEO",
    "coverThumbnailUrl": "https://cdn.example.com/news/cover/n2-poster.jpg",
    "datePublished": "2026-05-29",
    "contentLanguages": ["KMR"],
    "category":    { "ckbName": "وەرزش", "kmrName": "Werzîş" },
    "subCategory": { "ckbName": "تۆپی پێ", "kmrName": "Futbol" },
    "kmrContent":  { "title": "Encamên hefta dawî", "description": "<p>...</p>" },
    "tags":     { "ckb": [], "kmr": ["Futbol"] },
    "keywords": { "ckb": [], "kmr": ["Werzîş"] }
  }
]
JSON
```

### 9.3 Update news

```
PUT /api/v1/news/{id}
```

- **Auth:** EMPLOYEE, ADMIN, or SUPER_ADMIN
- **Consumes:** `application/json`
- **Body:** `NewsDto`
- **Response:** `ApiResponse<NewsDto>` — message: `"News updated successfully"`

Path parameter `id` is the target news id. The service runs `validate(dto, false)` (cover is **not** mandatory in the body, but if the existing news already has no `coverUrl` and the body doesn't supply one a `news.cover.required` validation error is raised).

Field-level behaviour (from `NewsService.updateNews`):

| DTO field | Behaviour |
|-----------|-----------|
| `coverUrl` | Replaced when non-blank; otherwise must already exist on the entity |
| `coverMediaType` | Replaced when non-null |
| `coverThumbnailUrl` | Replaced (set to null when blank) when the body field is non-null |
| `mediaGallery` | Replaced wholesale when non-null (rebuilt via `buildGallery`) |
| `datePublished` | Replaced when non-null |
| `category` | When non-null, resolved/created via `getOrCreateCategory` |
| `subCategory` | When non-null, resolved/created via `getOrCreateSubCategory` under the (possibly updated) category |
| `contentLanguages` | Always replaced with the supplied set |
| `ckbContent` / `kmrContent` | Re-applied via `applyContentByLanguages` — language not in `contentLanguages` clears the corresponding content + tags + keywords |
| `tags` (BilingualSet) | When the CKB or KMR sub-set is non-null, that side is cleared and replaced |
| `keywords` (BilingualSet) | When the CKB or KMR sub-set is non-null, that side is cleared and replaced |

A `NewsAuditLog` with `action = "UPDATE"` and `note = "News updated"` is written.

**cURL**

```bash
curl -s -X PUT "https://api.example.com/api/v1/news/187" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "contentLanguages": ["CKB", "KMR"],
  "category":    { "ckbName": "سیاسەت",   "kmrName": "Siyaset" },
  "subCategory": { "ckbName": "هەڵبژاردن", "kmrName": "Hilbijartin" },
  "ckbContent":  { "title": "نوێکراوە: ئەنجامەکان", "description": "<p>زانیاری زیاتر زیادکرا...</p>" },
  "kmrContent":  { "title": "Nûvekirin: Encam",      "description": "<p>Zêdetir agahî hatin zêdekirin...</p>" },
  "tags":     { "ckb": ["هەڵبژاردن", "نوێکراوە"], "kmr": ["Hilbijartin", "Nûvekirin"] },
  "keywords": { "ckb": ["هەرێم"],                  "kmr": ["Herêm"] }
}
JSON
```

### 9.4 Delete news

```
DELETE /api/v1/news/{id}
```

- **Auth:** ADMIN or SUPER_ADMIN
- **Response:** `ApiResponse<Void>` — message: `"News deleted successfully"`

A `NewsAuditLog` with `action = "DELETE"` and `note = "News deleted"` is written before the row is removed.

**cURL**

```bash
curl -s -X DELETE "https://api.example.com/api/v1/news/187" \
  -H "Authorization: Bearer $JWT"
```

**Sample JSON response**

```json
{
  "success": true,
  "message": "News deleted successfully",
  "data": null
}
```

### 9.5 Delete news (alt path)

```
DELETE /api/v1/news/delete/{id}
```

- **Auth:** ADMIN or SUPER_ADMIN
- Identical behaviour to `DELETE /api/v1/news/{id}` — the handler `deleteNewsAlt` delegates to the same `newsService.deleteNews(id)`. Kept as a legacy alias.
- **Response:** `ApiResponse<Void>` — message: `"News deleted successfully"`

**cURL**

```bash
curl -s -X DELETE "https://api.example.com/api/v1/news/delete/187" \
  -H "Authorization: Bearer $JWT"
```

### 9.6 Delete news (bulk)

```
DELETE /api/v1/news/bulk
```

- **Auth:** ADMIN or SUPER_ADMIN
- **Consumes:** `application/json`
- **Body:** `List<Long>` — list of news ids to delete
- **Response:** `ApiResponse<Void>` — message: `"News deleted successfully (bulk)"`

Service behaviour (from `NewsService.deleteNewsBulk`):

- null or empty list raises `error.validation` (`field=newsIds`)
- the service calls `newsRepository.findAllById(newsIds)`; if **none** of the requested ids exist a `news.not_found` error is raised with the offending ids
- ids that match cause one `NewsAuditLog` per deletion (`action = "DELETE"`, `note = "News bulk deleted"`)
- ids that do **not** match any persisted row are silently ignored (as long as at least one matches) — the matching subset is deleted

The whole deletion runs inside one transaction (`transactionTemplate.executeWithoutResult`).

**cURL**

```bash
curl -s -X DELETE "https://api.example.com/api/v1/news/bulk" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '[187, 188, 189]'
```

---

## 10 · DTO Reference

DTO class: `ak.dev.khi_backend.khi_app.dto.news.NewsDto`
Annotations: `@Data @NoArgsConstructor @AllArgsConstructor @Builder @JsonIgnoreProperties(ignoreUnknown = true)`

There are **no bean-validation annotations** (`@NotNull`, `@NotBlank`, `@Size`, etc.) on `NewsDto`. All validation lives in `NewsService.validate(...)`.

### Top-level fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Server-assigned id (ignored on create) |
| `coverUrl` | `String` | URL of the card cover asset (image / video / audio). Required on create. Max 1024 chars on the entity |
| `coverMediaType` | `MediaKind` | Discriminator for `coverUrl`. Defaults to `IMAGE` on the entity if null |
| `coverThumbnailUrl` | `String` | Optional poster (VIDEO) or cover art (AUDIO) URL for the card cover |
| `mediaGallery` | `List<MediaItem>` | Mixed-type gallery (images / videos / audios) rendered beside the cover. Defaults to empty list via `@Builder.Default` |
| `datePublished` | `LocalDate` | Publication date. If null at create time the entity defaults to `LocalDate.now()` |
| `createdAt` | `LocalDateTime` | Response-only; populated by the entity lifecycle |
| `updatedAt` | `LocalDateTime` | Response-only; populated by the entity lifecycle |
| `contentLanguages` | `Set<Language>` | Subset of `{CKB, KMR}` indicating which language blocks are populated. Defaults to empty set via `@Builder.Default`. Must be non-empty on create / update |
| `category` | `NewsDto.CategoryDto` | Required — Sorani + Kurmanji name of the category |
| `subCategory` | `NewsDto.SubCategoryDto` | Required — Sorani + Kurmanji name of the subcategory |
| `ckbContent` | `NewsDto.LanguageContentDto` | Required if `CKB` is in `contentLanguages` |
| `kmrContent` | `NewsDto.LanguageContentDto` | Required if `KMR` is in `contentLanguages` |
| `tags` | `NewsDto.BilingualSet` | Bilingual tags |
| `keywords` | `NewsDto.BilingualSet` | Bilingual SEO / search keywords |

### Nested DTOs

#### `NewsDto.CategoryDto`

| Field | Type | Description |
|-------|------|-------------|
| `ckbName` | `String` | Sorani category name. Required, non-blank |
| `kmrName` | `String` | Kurmanji category name. Required, non-blank |

#### `NewsDto.SubCategoryDto`

| Field | Type | Description |
|-------|------|-------------|
| `ckbName` | `String` | Sorani subcategory name. Required, non-blank |
| `kmrName` | `String` | Kurmanji subcategory name. Required, non-blank |

#### `NewsDto.LanguageContentDto`

| Field | Type | Description |
|-------|------|-------------|
| `title` | `String` | Title. Required if its language is in `contentLanguages`. Max 250 chars on the entity column |
| `description` | `String` | Tiptap HTML produced by the editor. Rewritten by `TiptapHtmlProcessor` before persistence. Stored as `TEXT` |

#### `NewsDto.BilingualSet`

| Field | Type | Description |
|-------|------|-------------|
| `ckb` | `Set<String>` | Sorani values. Defaults to empty set via `@Builder.Default`. Trimmed; blanks dropped |
| `kmr` | `Set<String>` | Kurmanji values. Defaults to empty set via `@Builder.Default`. Trimmed; blanks dropped |

#### `MediaItem` (used by `mediaGallery`)

| Field | Type | Description |
|-------|------|-------------|
| `url` | `String` | S3 / CDN URL of the asset (required) |
| `kind` | `MediaKind` | `IMAGE` / `VIDEO` / `AUDIO`. Defaults to `IMAGE` server-side if null |
| `thumbnailUrl` | `String` | Optional poster (VIDEO) or cover art (AUDIO); ignored for `IMAGE` |
| `captionCkb` | `String` | Sorani caption shown under the asset |
| `captionKmr` | `String` | Kurmanji caption shown under the asset |
| `sortOrder` | `Integer` | Display order inside the gallery (ascending). Server fills in the input index if null |

---

## 11 · Response Envelope

Every News endpoint returns `ResponseEntity<ApiResponse<T>>`:

```json
{
  "success": true,
  "message": "News fetched successfully",
  "data": { /* T */ }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | `true` for the success factory used by every News handler (`ApiResponse.success(...)`) |
| `message` | string | Human-readable message — controller-defined per endpoint |
| `data` | T \| null | Payload. `null` for delete endpoints |

### Spring Data `Page<T>` shape

When `T` is `Page<NewsDto>` (list / search endpoints) the `data` object has the canonical Spring Data shape:

```json
{
  "content": [ /* NewsDto[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": false, "unsorted": true, "empty": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 137,
  "totalPages": 7,
  "last": false,
  "first": true,
  "number": 0,
  "numberOfElements": 20,
  "size": 20,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "empty": false
}
```

---

## 12 · Error Responses

Validation and not-found errors surface as a bilingual `ApiErrorResponse` envelope produced by the central `Errors` helper / `BadRequestException`.

Indicative shape:

```json
{
  "success": false,
  "code": "NEWS_NOT_FOUND",
  "messageCkb": "هەواڵەکە نەدۆزرایەوە",
  "messageKmr": "Nûçeya peyda nebû",
  "details": { "id": 187 }
}
```

### Common error codes raised by the News module

| Code | When |
|------|------|
| `NEWS_NOT_FOUND` | `getNewsById`, `updateNews`, `deleteNews` when the id does not resolve (`Errors.newsNotFound(id)`) |
| `NEWS_VALIDATION` | Any business-rule violation thrown by `Errors.newsValidation(...)` — missing cover, missing languages, missing category / subcategory, missing required title |
| `NEWS_CONFLICT` | Conflicting taxonomy / uniqueness violations surfaced by the central error handler |
| `NEWS_MEDIA_INVALID` | Media payload rejected by the central error handler (e.g. malformed gallery entries) |
| `VALIDATION_ERROR` | Generic validation envelope used by `BadRequestException` (`error.validation`, `keyword.required`, `tag.required`, `news.category.required`, `news.subcategory.required`, `news.not_found` with id list, …) |
| `UNAUTHORIZED` | Missing / expired JWT on POST / PUT / DELETE |
| `FORBIDDEN` | Authenticated user lacks the required role |

### Specific validation messages emitted by `NewsService`

| Trigger | Message key | Detail map |
|---------|-------------|------------|
| Body is null | `error.validation` | `field=body`, `message=Request body is required` |
| `contentLanguages` empty | `news.languages.required` | `field=contentLanguages` |
| `coverUrl` blank (create or required-on-update path) | `news.cover.required` | `field=coverUrl` |
| `category` missing or names blank | `news.category.required` | `field=category` |
| `subCategory` missing or names blank | `news.subcategory.required` | `field=subCategory` |
| CKB selected but `ckbContent.title` blank | `news.ckb.title.required` | `field=ckbContent.title` |
| KMR selected but `kmrContent.title` blank | `news.kmr.title.required` | `field=kmrContent.title` |
| Bulk create — empty list | `error.validation` | `field=list`, `message=News list is empty` |
| Update — null id | `error.validation` | `field=id`, `message=News id is required` |
| Delete — null id | `error.validation` | `field=id`, `message=News id is required for delete` |
| Bulk delete — null / empty list | `error.validation` | `field=newsIds`, `message=News id list is empty` |
| Bulk delete — no ids matched | `news.not_found` | `ids=<list>` |
| `globalSearch` — blank `q` | `keyword.required` | `message=Search keyword is required` |
| `searchByKeyword` — blank keyword | `keyword.required` | `message=Search keyword is required` |
| `searchByTag` — blank tag | `tag.required` | `message=Search tag is required` |
| `searchByCategory` — blank `name` | `news.category.required` | `field=category` |
| `searchBySubCategory` — blank `name` | `news.subcategory.required` | `field=subCategory` |

---

## 13 · Notes

- **Inline media** for the article body is uploaded separately through `POST /api/v1/media/upload`; the returned URLs are baked into the Tiptap HTML stored in `ckbContent.description` and `kmrContent.description`. The News endpoints themselves never accept multipart.
- **Cover media** flow is identical — frontend uploads first, then sends the resulting URL as `coverUrl` (and optionally `coverThumbnailUrl`). Use `coverMediaType` to mark it as `IMAGE`, `VIDEO`, or `AUDIO`.
- **`language` query parameter** accepts `both` (default), `ckb`, or `kmr` — comparison is case-insensitive (`"ckb".equalsIgnoreCase(language)`). Any value other than `ckb` / `kmr` falls back to the cross-language repository query (effectively `both`).
- **Bulk create (`POST /api/v1/news/bulk`)** is fail-fast and all-or-nothing: every DTO is validated up front, and the inserts plus audit-log writes run inside a single `transactionTemplate.execute(...)`. A failure on any element aborts the whole batch.
- **Bulk delete (`DELETE /api/v1/news/bulk`)** is best-effort within a single transaction: it deletes whatever subset of the supplied ids actually exists. Only if **none** of the ids match does it raise `news.not_found`. Unknown ids in a partially-matching list are silently skipped.
- **Duplicate delete paths** (`DELETE /api/v1/news/{id}` and `DELETE /api/v1/news/delete/{id}`) both call the exact same `newsService.deleteNews(id)`. The `/delete/{id}` form is retained as a legacy alias.
- **Caching:** all read endpoints are annotated with `@Cacheable(value = "news", ...)`; every create / update / delete (single or bulk) is annotated with `@CacheEvict(value = "news", allEntries = true)`, so any write flushes the whole cache region.
- **Category / subcategory bootstrap:** writes do not require pre-existing taxonomy rows. `getOrCreateCategory` and `getOrCreateSubCategory` create missing rows on demand (looked up by Sorani name) and refresh the Kurmanji translation if it changed.
- **Performance:** the entity uses `@BatchSize(size = 50)` on all `@ElementCollection`s and on `NewsCategory` / `NewsSubCategory` so a page of 20 news fires a small fixed number of IN-queries instead of N+1.
- **Auditing:** every successful create / update / delete writes a `NewsAuditLog` row with `performedBy = "system"` (hard-coded in `NewsService.buildAuditLog`).
