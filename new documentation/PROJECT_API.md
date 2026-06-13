# KHI Backend — Project API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 6 Endpoints · JSON Only · Tiptap HTML · Paginated · Cached

Complete documentation for all project management endpoints — create, update, delete, list, and search — including bilingual Tiptap content, JSONB media gallery, cover discriminators, enums, DTOs, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Media Pipeline (Tiptap)](#02--media-pipeline-tiptap)
- [03 · Data Models](#03--data-models)
- [04 · Enums](#04--enums)
- [05 · Authentication](#05--authentication)
- [06 · Create Project](#06--create-project) — `POST /create`
- [07 · Update Project](#07--update-project) — `PUT /update/{id}`
- [08 · Delete Project](#08--delete-project) — `DELETE /delete/{id}`
- [09 · Read & Search](#09--read--search)
  - `GET /getAll`
  - `GET /search/tag`
  - `GET /search/keyword`
- [10 · DTO Reference](#10--dto-reference)
- [11 · Error Responses](#11--error-responses)
- [12 · Change Log — Old vs. New](#12--change-log--old-vs-new)

---

## 01 · Overview

The Project module manages research/publication projects with full bilingual support (CKB Sorani and KMR Kurmanji). Each project carries embedded text content (now rich **Tiptap HTML**), a cover asset (image, video, or audio), an optional cover thumbnail, a JSONB media gallery, associated tags/keywords per language, and a full audit trail via `AuditableEntity`.

### Base URL

```
/api/v1/projects

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/create` | Create project — JSON only |
| `PUT` | `/update/{id}` | Update project — JSON only |
| `DELETE` | `/delete/{id}` | Permanently delete a project |
| `GET` | `/getAll` | Paginated list of all projects |
| `GET` | `/search/tag` | Search projects by tag name |
| `GET` | `/search/keyword` | Search projects by keyword name |

> ⚠️ **Multipart endpoints removed.** The previous `POST /with-files` and `PUT /update/{id}/with-files` endpoints have been deleted. All project endpoints are now pure `application/json`. See [Section 02 · Media Pipeline](#02--media-pipeline-tiptap) for the new upload flow.

---

## 02 · Media Pipeline (Tiptap)

The Project module has migrated to a **Tiptap-based content pipeline**. Binary files are no longer uploaded as part of the project request body.

### How it works

1. **Frontend uploads** each cover/gallery/inline asset first via:
   ```
   POST /api/v1/media/upload
   ```
2. The upload endpoint returns a stored URL (S3 or CDN).
3. The frontend then sends those URLs back inside the project JSON:
   - **Cover** → `coverUrl` (top-level) + `coverMediaType` + optional `coverThumbnailUrl`
   - **Gallery items** → `mediaGallery[]` array of `MediaItem`
   - **Inline media** → baked directly into the **Tiptap HTML** stored in `ckbContent.description` / `kmrContent.description`
4. The backend `TiptapHtmlProcessor` runs on the HTML descriptions during create/update.

### What this replaces

- ❌ `project_media` table — **dropped**
- ❌ `media[]` request array — **dropped**
- ❌ `contentsCkb` / `contentsKmr` free-text content tags — **dropped** (use editor headings instead)
- ❌ Multipart `cover` / `media` form parts — **dropped**

---

## 03 · Data Models

Five JPA entities power the project module. `Project` is the aggregate root; `ProjectContentBlock` is embedded; tags/keywords are related via `@ManyToMany`; logs via FK with `ON DELETE CASCADE`. The media gallery is stored as a **JSONB** column (`List<MediaItem>`), not a join table.

### Project — `projects`

Aggregate root. Extends `AuditableEntity` (createdAt, updatedAt, createdBy, updatedBy). Uses `@BatchSize(50)` on all lazy collections to eliminate N+1 queries.

**DB indexes:** `idx_projects_type_ckb`, `idx_projects_type_kmr`, `idx_projects_status`, `idx_projects_date`.

| Field | DB Type | Constraint | Description |
| --- | --- | --- | --- |
| `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `coverUrl` | VARCHAR(1024) | NULLABLE | Cover asset URL (image, video, or audio) |
| `coverMediaType` | ENUM(STRING, 16) | NULLABLE | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | VARCHAR(1024) | NULLABLE | Poster (VIDEO) or cover art (AUDIO) for the card cover |
| `mediaGallery` | **JSONB** | NULLABLE | Ordered `List<MediaItem>` — mixed image/video/audio gallery beside cover |
| `ckbContent` | EMBEDDED | NULLABLE | Sorani CKB: `title`, `description` (**Tiptap HTML**), `location` |
| `kmrContent` | EMBEDDED | NULLABLE | Kurmanji KMR: `title`, `description` (**Tiptap HTML**), `location` |
| `projectTypeCkb` | VARCHAR(128) | NULLABLE | Project category label in CKB (required when CKB is in `contentLanguages`) |
| `projectTypeKmr` | VARCHAR(128) | NULLABLE | Project category label in KMR (required when KMR is in `contentLanguages`) |
| `status` | ENUM(STRING, 32) | NOT NULL | `ONGOING` \| `COMPLETED`. Default: `ONGOING` |
| `contentLanguages` | @ElementCollection | NOT NULL | `Set<Language>` — at least one required |
| `tagsCkb / tagsKmr` | @ManyToMany | NULLABLE | Per-language `ProjectTag` associations |
| `keywordsCkb / keywordsKmr` | @ManyToMany | NULLABLE | Per-language `ProjectKeyword` associations |
| `projectDate` | DATE | NULLABLE | Publication / activity date |
| `createdAt / updatedAt` | TIMESTAMP | NULLABLE | ISO-8601 UTC — from `AuditableEntity` |
| `createdBy / updatedBy` | VARCHAR | NULLABLE | Username principal — from `AuditableEntity` |

> ⚠️ The `project_media` table and the `ProjectContent` entity (with its `contentsCkb` / `contentsKmr` collections) **no longer exist** on `Project`.

### ProjectContentBlock — `@Embeddable`

| Field | DB Column (CKB) | DB Column (KMR) | Type |
| --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(255) |
| `description` | `description_ckb` | `description_kmr` | TEXT — **Tiptap HTML** |
| `location` | `location_ckb` | `location_kmr` | VARCHAR(255) |

### MediaItem — JSONB gallery element (not a JPA entity)

Stored as one element of the `media_gallery` JSONB column on `Project`. Plain POJO — no DB ID; ordering is derived from `sortOrder` and array position.

| Field | Type | Description |
| --- | --- | --- |
| `url` | String | S3 / CDN URL of the asset (**required**) |
| `kind` | `MediaKind` | `IMAGE` \| `VIDEO` \| `AUDIO` — drives the player on the frontend |
| `thumbnailUrl` | String | Optional poster (VIDEO) or cover art (AUDIO); ignored for IMAGE |
| `captionCkb` | String | Sorani caption shown under the asset |
| `captionKmr` | String | Kurmanji caption shown under the asset |
| `sortOrder` | Integer | Display order inside the gallery (ascending). Auto-assigned by index if null |

### ProjectTag / ProjectKeyword

| Entity | Table | Unique Constraint | Fields |
| --- | --- | --- | --- |
| `ProjectTag` | `project_tags` | `uq_project_tags_name` (name) | `id` (PK), `name` VARCHAR(128) |
| `ProjectKeyword` | `project_keywords` | `uq_project_keywords_name` (name) | `id` (PK), `name` VARCHAR(191) |

> ℹ️ The previous `ProjectContent` entity (`project_contents` table) **no longer exists**.

### ProjectLog — `project_log`

Append-only audit log. FK uses `@OnDelete(CASCADE)` so deleting a project automatically purges its logs.

**DB indexes:** `idx_project_log_project_id`, `idx_project_log_action`, `idx_project_log_created_at`.

| Field | Type | Description |
| --- | --- | --- |
| `id` | BIGINT PK | Auto-increment |
| `project` | FK (CASCADE, NOT NULL) | Parent project — deleted with project |
| `action` | VARCHAR(50) | `CREATE` \| `UPDATE` \| `DELETE` \| … |
| `fieldName` | VARCHAR(50) | Changed field — `SUMMARY` for general entries |
| `oldValue / newValue` | TEXT | Before and after values for the change |
| `createdAt` | TIMESTAMP | When the log entry was created |

---

## 04 · Enums

### ProjectStatus

| Value | Description |
| --- | --- |
| `ONGOING` | Project is active and in progress (default) — بەردەوام |
| `COMPLETED` | Project has been finished — تەواو |

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

### ProjectMediaType *(legacy enum — still defined, no longer used by request/response)*

| Value | Description |
| --- | --- |
| `IMAGE`, `VIDEO`, `AUDIO`, `DOCUMENT`, `PDF`, `TEXT` | Legacy values retained from the pre-Tiptap pipeline |

> ℹ️ Project requests / responses now use `MediaKind` for cover and gallery items. Inline document / PDF / text content lives **inside the Tiptap HTML**, not as a typed media row.

---

## 05 · Authentication

> ℹ️ **All project endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> ⚠️ **N+1 Protection:** All collection fields (`tagsCkb/Kmr`, `keywordsCkb/Kmr`, `contentLanguages`) use `@BatchSize(50)`. Read endpoints use **two-phase loading** — first an ID-only page query, then a single batch hydration — so list/search responses are bounded regardless of page size.

> 🚀 **Caching:** All read endpoints (`getAll`, `search/tag`, `search/keyword`) are `@Cacheable` under the `projects` cache. Every write (`create`, `update`, `delete`) does `@CacheEvict(allEntries = true)`.

---

## 06 · Create Project

### `POST /api/v1/projects/create`

🔒 **Auth Required**

Create a new project using a plain JSON body. **Cover URL is required** — upload the cover file first via `POST /api/v1/media/upload`, then pass the returned URL here.

### Request Body — ProjectCreateRequest

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `coverUrl` | String | **Yes** | `@Size(max=1024)` · pre-uploaded URL |
| `coverMediaType` | `MediaKind` | No | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | String | No | `@Size(max=1024)` · poster (VIDEO) / cover art (AUDIO) |
| `mediaGallery` | `List<MediaItem>` | No | Mixed gallery — see [DTO Reference](#10--dto-reference) |
| `projectTypeCkb` | String | Conditional | `@Size(max=128)` · **required when CKB is in `contentLanguages`** |
| `projectTypeKmr` | String | Conditional | `@Size(max=128)` · **required when KMR is in `contentLanguages`** |
| `status` | `ProjectStatus` | No | `ONGOING` \| `COMPLETED`. Defaults to `ONGOING` |
| `contentLanguages` | `Set<Language>` | **Yes** | `@NotEmpty` — at least one: `CKB`, `KMR` |
| `projectDate` | `LocalDate` | No | ISO-8601 date string e.g. `"2026-04-11"` |
| `ckbContent` | `ContentBlock` | Conditional | `{ title, description, location }` — **`title` required when CKB selected**. `description` accepts **Tiptap HTML** |
| `kmrContent` | `ContentBlock` | Conditional | `{ title, description, location }` — **`title` required when KMR selected**. `description` accepts **Tiptap HTML** |
| `tagsCkb / tagsKmr` | `List<String>` | No | Tag names — looked up or created by service (case-insensitive) |
| `keywordsCkb / keywordsKmr` | `List<String>` | No | Keyword names — looked up or created (case-insensitive) |

> ⛔ **Removed fields** (return a 400 if old clients send them):
> - `media[]` — use Tiptap HTML in `description` + `mediaGallery[]` for the standalone gallery
> - `contentsCkb` / `contentsKmr` — use editor headings inside the Tiptap HTML

### Request JSON

```json
{
  "coverUrl":          "https://cdn.khi.iq/projects/cover-001.jpg",
  "coverMediaType":    "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery": [
    {
      "url":          "https://cdn.khi.iq/projects/gallery-001.jpg",
      "kind":         "IMAGE",
      "thumbnailUrl": null,
      "captionCkb":   "وێنەی سەرەکی",
      "captionKmr":   "Wêneya sereke",
      "sortOrder":    0
    },
    {
      "url":          "https://cdn.khi.iq/projects/clip.mp4",
      "kind":         "VIDEO",
      "thumbnailUrl": "https://cdn.khi.iq/projects/clip-poster.jpg",
      "captionCkb":   "کلیپی بەڵگەنامەیی",
      "captionKmr":   "Klîpa belgefilmî",
      "sortOrder":    1
    }
  ],
  "projectTypeCkb":  "توێژینەوە",
  "projectTypeKmr":  "Lêkolîn",
  "status":          "ONGOING",
  "contentLanguages": ["CKB", "KMR"],
  "projectDate":     "2026-04-11",
  "ckbContent": {
    "title":       "توێژینەوەی مێژووی کوردستان",
    "description": "<h2>پێشەکی</h2><p>وردبینی لەسەر مێژووی کوردستان</p><img src=\"https://cdn.khi.iq/projects/inline-1.jpg\" />",
    "location":    "سلێمانی"
  },
  "kmrContent": {
    "title":       "Lêkolîna Dîroka Kurdistanê",
    "description": "<h2>Pêşgotin</h2><p>Vekolînek hûr a dîroka Kurdistanê</p>",
    "location":    "Silêmanî"
  },
  "tagsCkb":     ["مێژوو", "کوردستان"],
  "tagsKmr":     ["dîrok", "Kurdistan"],
  "keywordsCkb": ["توێژینەوە"],
  "keywordsKmr": ["lêkolîn"]
}
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "Project created successfully",
  "data": {
    "id":                1,
    "coverUrl":          "https://cdn.khi.iq/projects/cover-001.jpg",
    "coverMediaType":    "IMAGE",
    "coverThumbnailUrl": null,
    "mediaGallery": [
      {
        "url":          "https://cdn.khi.iq/projects/gallery-001.jpg",
        "kind":         "IMAGE",
        "thumbnailUrl": null,
        "captionCkb":   "وێنەی سەرەکی",
        "captionKmr":   "Wêneya sereke",
        "sortOrder":    0
      },
      {
        "url":          "https://cdn.khi.iq/projects/clip.mp4",
        "kind":         "VIDEO",
        "thumbnailUrl": "https://cdn.khi.iq/projects/clip-poster.jpg",
        "captionCkb":   "کلیپی بەڵگەنامەیی",
        "captionKmr":   "Klîpa belgefilmî",
        "sortOrder":    1
      }
    ],
    "projectTypeCkb":   "توێژینەوە",
    "projectTypeKmr":   "Lêkolîn",
    "status":           "ONGOING",
    "projectDate":      "2026-04-11",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title":       "توێژینەوەی مێژووی کوردستان",
      "description": "<h2>پێشەکی</h2><p>وردبینی لەسەر مێژووی کوردستان</p><img src=\"https://cdn.khi.iq/projects/inline-1.jpg\" />",
      "location":    "سلێمانی"
    },
    "kmrContent": {
      "title":       "Lêkolîna Dîroka Kurdistanê",
      "description": "<h2>Pêşgotin</h2><p>Vekolînek hûr a dîroka Kurdistanê</p>",
      "location":    "Silêmanî"
    },
    "tagsCkb":     ["مێژوو", "کوردستان"],
    "tagsKmr":     ["dîrok", "Kurdistan"],
    "keywordsCkb": ["توێژینەوە"],
    "keywordsKmr": ["lêkolîn"],
    "createdAt":   "2026-04-11T21:30:00Z",
    "updatedAt":   null,
    "createdBy":   "akar_dev",
    "updatedBy":   null
  }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `project.request_required` | Request body is missing |
| `400` | `project.languages_required` | `contentLanguages` is empty — must supply at least one language |
| `400` | `project.cover_required` | `coverUrl` is missing or blank |
| `400` | `project.ckb_type_required` | CKB selected but `projectTypeCkb` is blank |
| `400` | `project.kmr_type_required` | KMR selected but `projectTypeKmr` is blank |
| `400` | `project.ckb_title_required` | CKB selected but `ckbContent.title` is blank |
| `400` | `project.kmr_title_required` | KMR selected but `kmrContent.title` is blank |
| `400` | — | `@Size`/enum validation failure on any field |
| `400` | `project.conflict` | DB integrity violation (e.g. duplicate unique constraint) |
| `401` | — | Missing or expired JWT token |
| `500` | `project.create_failed` | Unexpected server error during create |

---

## 07 · Update Project

### `PUT /api/v1/projects/update/{id}`

🔒 **Auth Required**

Replace a project's data using a JSON body. The entire object is replaced — pass all fields you want to keep, omit/null to clear. Same request shape as `POST /create`.

> ⚠️ **`coverUrl` is required on update too.** If you are not replacing the cover, send back the existing `coverUrl` value you got from `GET /getAll` or the original create response.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the project to update |

### Request Body

Identical to `ProjectCreateRequest`. See Section 06 for the full field list.

```json
{
  "coverUrl":          "https://cdn.khi.iq/projects/cover-001.jpg",
  "coverMediaType":    "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery":      [],
  "status":            "COMPLETED",
  "contentLanguages":  ["CKB"],
  "projectTypeCkb":    "توێژینەوە",
  "ckbContent": {
    "title":       "Updated Title in CKB",
    "description": "<p>Updated Tiptap HTML body…</p>",
    "location":    "سلێمانی"
  },
  "tagsCkb":    ["مێژوو", "نوێ"],
  "keywordsCkb": ["توێژینەوە"]
}
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Project updated successfully",
  "data": { /* full ProjectResponse — same shape as create */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | various | Same validation errors as `POST /create` |
| `400` | `project.conflict` | DB integrity violation |
| `401` | — | Missing or expired JWT |
| `404` | `project.not_found` | Project with given id not found |
| `500` | `project.update_failed` | Unexpected server error during update |

---

## 08 · Delete Project

### `DELETE /api/v1/projects/delete/{id}`

🔒 **Auth Required**

Permanently delete a project and all its associated data — tag/keyword associations, language collection, JSONB media gallery, and audit logs. Uses `@OnDelete(CASCADE)` on the project_log FK; tag/keyword maps are removed by JPA cascade.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the project to delete |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Project deleted successfully",
  "data":    null
}
```

> ⛔ **This action is irreversible.** All project data, gallery items, language associations, tags, keywords, and audit logs are permanently removed. No soft-delete exists.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `project.not_found` | Project with given id not found |
| `500` | `project.delete_failed` | Unexpected server error during delete |

---

## 09 · Read & Search

### `GET /api/v1/projects/getAll`

🔒 **Auth Required** · `@Cacheable("projects")`

Return a paginated list of all projects, **ordered by `id DESC`** (newest first). Uses a two-phase load: an ID-only page query, then a single batch hydration. Lazy collections are batch-loaded thanks to `@BatchSize(50)`.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Items per page |

### Request

```
GET /api/v1/projects/getAll?page=0&size=10
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK — `Page<ProjectResponse>`

```json
{
  "success": true,
  "message": "Projects fetched successfully",
  "data": {
    "content": [
      {
        "id":                5,
        "coverUrl":          "https://cdn.khi.iq/projects/cover-005.jpg",
        "coverMediaType":    "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery":      [],
        "status":            "COMPLETED",
        "projectDate":       "2026-03-01",
        "contentLanguages":  ["CKB"],
        "projectTypeCkb":    "توێژینەوە",
        "ckbContent": {
          "title":       "…",
          "description": "<p>Tiptap HTML…</p>",
          "location":    "…"
        },
        "tagsCkb":     ["مێژوو"],
        "keywordsCkb": ["توێژینەوە"],
        "createdAt":   "2026-03-01T10:00:00Z",
        "createdBy":   "akar_dev"
      }
    ],
    "totalElements": 42,
    "totalPages":    5,
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

### `GET /api/v1/projects/search/tag`

🔒 **Auth Required** · `@Cacheable("projects")`

Search projects by tag name across both `tagsCkb` and `tagsKmr` using a **case-insensitive partial match** (`LIKE %tag%`). Returns a paginated result ordered by `id DESC`.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `tag` | String | **Yes** | Tag name to search. URL-encode non-ASCII characters |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request · Latin Tag

```
GET /api/v1/projects/search/tag?tag=dîrok&page=0&size=10
Authorization: Bearer eyJhbGci...
```

### Request · Kurdish Tag (URL-encoded)

```
GET /api/v1/projects/search/tag?tag=%D9%85%DB%8E%DA%98%D9%88%D9%88
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Search by tag completed",
  "data": { /* Page<ProjectResponse> — same shape as getAll */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `tag.required` | `tag` param is missing or blank |
| `401` | — | Missing or expired JWT |

---

### `GET /api/v1/projects/search/keyword`

🔒 **Auth Required** · `@Cacheable("projects")`

Search projects by keyword name across both `keywordsCkb` and `keywordsKmr` using a **case-insensitive partial match** (`LIKE %keyword%`). Returns a paginated result ordered by `id DESC`.

### Query Parameters

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | String | **Yes** | Keyword name to search. URL-encode non-ASCII characters |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

### Request

```
GET /api/v1/projects/search/keyword?keyword=lêkolîn&page=0&size=20
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Search by keyword completed",
  "data": { /* Page<ProjectResponse> — same shape as getAll */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `keyword.required` | `keyword` param is missing or blank |
| `401` | — | Missing or expired JWT |

---

## 10 · DTO Reference

### ProjectCreateRequest

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `coverUrl` | String | **Yes** | `@Size(max=1024)` · pre-uploaded URL |
| `coverMediaType` | `MediaKind` | No | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `coverThumbnailUrl` | String | No | `@Size(max=1024)` |
| `mediaGallery` | `List<MediaItem>` | No | Mixed-type gallery |
| `projectTypeCkb` | String | Conditional | `@Size(max=128)` · required when CKB selected |
| `projectTypeKmr` | String | Conditional | `@Size(max=128)` · required when KMR selected |
| `status` | `ProjectStatus` | No | Defaults to `ONGOING` |
| `contentLanguages` | `Set<Language>` | **Yes** | `@NotEmpty` |
| `projectDate` | `LocalDate` | No | ISO-8601 |
| `ckbContent` | `ProjectContentBlock` | Conditional | `title` required when CKB selected. `description` = Tiptap HTML |
| `kmrContent` | `ProjectContentBlock` | Conditional | `title` required when KMR selected. `description` = Tiptap HTML |
| `tagsCkb / tagsKmr` | `List<String>` | No | Looked up case-insensitive or created |
| `keywordsCkb / keywordsKmr` | `List<String>` | No | Looked up case-insensitive or created |

### MediaItem (request + response)

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `url` | String | **Yes** | S3 / CDN URL — items with blank `url` are filtered out |
| `kind` | `MediaKind` | No | `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` |
| `thumbnailUrl` | String | No | Poster (VIDEO) / cover art (AUDIO) |
| `captionCkb` | String | No | Sorani caption |
| `captionKmr` | String | No | Kurmanji caption |
| `sortOrder` | Integer | No | Display order ASC. Auto-assigned by index if null |

### ProjectContentBlock (request + entity)

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Project title in this language (max 255) |
| `description` | String | **Tiptap HTML** body — processed by `TiptapHtmlProcessor` on save |
| `location` | String | Geographic location label (max 255) |

### ProjectResponse

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | Project primary key |
| `coverUrl` | String | Cover asset URL |
| `coverMediaType` | `MediaKind` | `IMAGE` \| `VIDEO` \| `AUDIO`. Always present (defaults to `IMAGE`) |
| `coverThumbnailUrl` | String | Optional poster / cover art |
| `mediaGallery` | `List<MediaItem>` | Ordered gallery items (sortOrder ASC). Empty list when none |
| `projectTypeCkb / projectTypeKmr` | String | Bilingual project category label |
| `status` | `ProjectStatus` | `ONGOING` \| `COMPLETED` |
| `projectDate` | `LocalDate` | ISO-8601 date string |
| `contentLanguages` | `Set<Language>` | Languages this project is published in |
| `ckbContent / kmrContent` | `ProjectContentBlockDto` | `{ title, description (Tiptap HTML), location }` per language |
| `tagsCkb / tagsKmr` | `List<String>` | Tag names per language |
| `keywordsCkb / keywordsKmr` | `List<String>` | Keyword names per language |
| `createdAt / updatedAt` | `Instant` | ISO-8601 UTC timestamps |
| `createdBy / updatedBy` | String | Username of the acting principal |

> ⛔ **Removed from `ProjectResponse`:** `media[]`, `contentsCkb`, `contentsKmr`.

### ProjectResponse.ProjectContentBlockDto

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Project title in this language |
| `description` | String | **Tiptap HTML** output (inline media URLs already point to S3) |
| `location` | String | Geographic location label |

### ApiResponse&lt;T&gt;

All endpoints return this wrapper. `data` is omitted on failure due to `@JsonInclude(NON_NULL)`.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure (NON_NULL) |

---

## 11 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New project saved successfully |
| `200 OK` | Update, delete, or read succeeded |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | Project id does not exist |
| `500 Internal Error` | Unexpected server failure — check logs |

### Error Key Reference (from `ProjectValidationException` / service)

| Key | Trigger |
| --- | --- |
| `project.request_required` | Request body is null |
| `project.languages_required` | `contentLanguages` is null or empty |
| `project.cover_required` | `coverUrl` is null or blank |
| `project.ckb_type_required` | CKB selected but `projectTypeCkb` is blank |
| `project.kmr_type_required` | KMR selected but `projectTypeKmr` is blank |
| `project.ckb_title_required` | CKB selected but `ckbContent.title` is blank |
| `project.kmr_title_required` | KMR selected but `kmrContent.title` is blank |
| `project.not_found` | No project with the given id |
| `project.conflict` | DB integrity violation (unique constraint, FK, etc.) |
| `project.create_failed` | Unexpected error during create |
| `project.update_failed` | Unexpected error during update |
| `project.delete_failed` | Unexpected error during delete |
| `tag.required` | `tag` query param is blank in `search/tag` |
| `keyword.required` | `keyword` query param is blank in `search/keyword` |

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    400,
  "errors": [
    {
      "field":   "contentLanguages",
      "message": "At least one content language is required"
    },
    {
      "field":   "coverUrl",
      "message": "Cover URL must not exceed 1024 characters"
    }
  ]
}
```

### Business Rule Error Body — `400 project.cover_required`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    400,
  "error":     "Bad Request",
  "message":   "project.cover_required"
}
```

### Auth Error Body — `401 Unauthorized`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Authentication token is missing or expired"
}
```

### Not Found Error Body — `404`

```json
{
  "timestamp": "2026-04-11T21:30:00Z",
  "status":    404,
  "error":     "Not Found",
  "message":   "Project not found with id: 99"
}
```

> ℹ️ All `timestamp` values are returned in **ISO-8601 UTC** format. All `Instant` fields in `ProjectResponse` are also UTC.

---

## 12 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /api/v1/projects/create` | JSON-only create | JSON-only create | ⚪ Unchanged |
| `POST /api/v1/projects/with-files` | Multipart create (cover + media files) | — | 🔴 **Removed** |
| `PUT /api/v1/projects/update/{id}` | JSON-only update | JSON-only update | ⚪ Unchanged |
| `PUT /api/v1/projects/update/{id}/with-files` | Multipart update | — | 🔴 **Removed** |
| `DELETE /api/v1/projects/delete/{id}` | Delete | Delete | ⚪ Unchanged |
| `GET /api/v1/projects/getAll` | Paginated list | Paginated list — now `@Cacheable`, two-phase load, `id DESC` ordering | 🟡 Behaviour clarified |
| `GET /api/v1/projects/search/tag` | Search by tag | Case-insensitive partial-match search by tag — now `@Cacheable` | 🟡 Behaviour clarified |
| `GET /api/v1/projects/search/keyword` | Search by keyword | Case-insensitive partial-match search by keyword — now `@Cacheable` | 🟡 Behaviour clarified |

**Endpoint count:** 8 → **6** (2 multipart endpoints removed). A service-layer `globalSearch(q)` exists but is not yet exposed by the controller.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `project_media` table | Existed | 🔴 **Dropped** |
| `ProjectMedia` entity (`type`, `url`, `externalUrl`, `embedUrl`, `caption`, `sortOrder`) | Existed | 🔴 **Dropped** |
| `ProjectContent` entity + `project_contents` table | Existed | 🔴 **Dropped** |
| `contentsCkb` / `contentsKmr` collections on `Project` | Existed | 🔴 **Dropped** |
| Cover discriminator | None — `coverUrl` only | 🟢 `coverMediaType` (`MediaKind`) + `coverThumbnailUrl` |
| Media gallery storage | `@OneToMany ProjectMedia` join table | 🟢 `mediaGallery` JSONB `List<MediaItem>` on `projects` |
| Gallery item shape | `ProjectMedia` with `type`/`url`/`externalUrl`/`embedUrl`/`caption` | 🟢 `MediaItem`: `url` + `kind` + `thumbnailUrl` + `captionCkb` + `captionKmr` + `sortOrder` |
| Bilingual captions on gallery | No (single `caption`) | 🟢 Yes (`captionCkb`, `captionKmr`) |
| `description_ckb` / `description_kmr` content type | Plain TEXT | 🟢 **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| DB indexes on `projects` | (unspecified) | 🟢 `idx_projects_type_ckb`, `idx_projects_type_kmr`, `idx_projects_status`, `idx_projects_date` |
| Default list ordering | (unspecified) | 🟢 `id DESC` (newest first) |
| `ProjectLog` FK behaviour | `ON DELETE CASCADE` | ⚪ Unchanged |
| Tag / keyword entities | `ProjectTag`, `ProjectKeyword` (`@ManyToMany`) | ⚪ Unchanged |
| `ProjectMediaType` enum | Used by request/response | 🟡 Still defined in code (legacy) but **no longer used** by request/response — replaced by `MediaKind` |

### C) DTO comparison

| Field | Old `ProjectCreateRequest` / `ProjectResponse` | New |
| --- | --- | --- |
| `coverUrl` | Optional (could be uploaded via `/with-files`) | **Required on create AND update** |
| `coverMediaType` | — | 🟢 Added (`MediaKind`, defaults to `IMAGE`) |
| `coverThumbnailUrl` | — | 🟢 Added |
| `mediaGallery` | — | 🟢 Added (`List<MediaItem>`) |
| `media` | `List<ProjectMediaCreateRequest>` / `List<ProjectMediaResponse>` (with `mediaType`, `url`, `externalUrl`, `embedUrl`, `caption`, `sortOrder`, `textBody`) | 🔴 **Removed** |
| `contentsCkb` / `contentsKmr` | `List<String>` content-type names | 🔴 **Removed** |
| `projectTypeCkb` / `projectTypeKmr` | Optional | 🟡 Now **conditionally required**: required when that language is in `contentLanguages` |
| `ckbContent.title` / `kmrContent.title` | Optional | 🟡 Now **conditionally required**: required when that language is in `contentLanguages` |
| `ckbContent.description` / `kmrContent.description` | Plain text | 🟡 Now **Tiptap HTML** |
| `ProjectMediaCreateRequest` / `ProjectMediaResponse` | Existed | 🔴 **Removed** |

### D) Validation / error-key comparison

| Error key | Old | New |
| --- | --- | --- |
| `project.cover_required` | Only when neither file nor `coverUrl` provided in `/with-files` | Whenever `coverUrl` is blank on create or update |
| `project.languages_required` | Implied by `@NotEmpty` | 🟢 Explicit, surfaced as a labelled error key |
| `project.request_required` | — | 🟢 Added — null request body |
| `project.ckb_type_required` | — | 🟢 Added — CKB selected but `projectTypeCkb` blank |
| `project.kmr_type_required` | — | 🟢 Added — KMR selected but `projectTypeKmr` blank |
| `project.ckb_title_required` | — | 🟢 Added — CKB selected but `ckbContent.title` blank |
| `project.kmr_title_required` | — | 🟢 Added — KMR selected but `kmrContent.title` blank |
| `project.not_found` | Implicit `404` | 🟢 Explicit, surfaced as a labelled error key |
| `project.conflict` | — | 🟢 Added — wraps `DataIntegrityViolationException` |
| `project.create_failed` / `project.update_failed` / `project.delete_failed` | — | 🟢 Added — wrap unexpected server errors with a trace id |
| `tag.required` / `keyword.required` | Existed | ⚪ Unchanged |

### E) Request/upload flow comparison

| Step | Old | New |
| --- | --- | --- |
| 1. Upload cover binary | Multipart `cover` part on `/with-files` | `POST /api/v1/media/upload` first, then send URL in `coverUrl` |
| 2. Upload gallery binaries | Multipart `media` parts on `/with-files` | `POST /api/v1/media/upload` first, then send URLs in `mediaGallery[]` |
| 3. Inline images inside the body | Sent as separate `ProjectMedia` rows | Uploaded individually via `/media/upload`, URLs baked into Tiptap HTML in `description` |
| 4. PDFs / documents | Sent as `ProjectMedia` with `PDF`/`DOCUMENT` type | Embedded inside Tiptap HTML — there is no document gallery row |

### F) Performance & caching

| Item | Old | New |
| --- | --- | --- |
| `@BatchSize(50)` on lazy collections | Applied | ⚪ Unchanged (now on a slimmer set — media/contents are gone) |
| Read endpoints `@Cacheable("projects")` | (unspecified) | 🟢 All read/search endpoints are cacheable |
| Write endpoints `@CacheEvict(allEntries=true)` | (unspecified) | 🟢 All writes evict the entire cache |
| Two-phase load (IDs → batch hydrate) | (unspecified) | 🟢 Now used by `getAll` and both `search/*` endpoints |

### G) Summary

- 🔴 **Removed:** `POST /with-files`, `PUT /update/{id}/with-files`, `ProjectMedia` entity + table, `ProjectContent` entity + table, `media[]` and `contentsCkb`/`contentsKmr` from DTOs, `ProjectMediaCreateRequest`, `ProjectMediaResponse`.
- 🟢 **Added:** `coverMediaType`, `coverThumbnailUrl`, `mediaGallery` (JSONB), `MediaItem`, Tiptap HTML descriptions, caching, two-phase reads, labelled error keys, conditional title/type validation, DB indexes on `projects`.
- 🟡 **Changed:** `coverUrl` is now required; descriptions are now Tiptap HTML; gallery captions are bilingual; `ProjectMediaType` enum is deprecated in favour of `MediaKind`.
- ⚪ **Unchanged:** `POST /create`, `PUT /update/{id}`, `DELETE /delete/{id}`, `GET /getAll`, `GET /search/tag`, `GET /search/keyword`, tag/keyword tables, `ProjectLog` cascade behaviour, `Language` and `ProjectStatus` enums.
