# KHI Backend — Image Collection API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 7 Endpoints · Multipart + JSON · Paginated · Cached · Tiptap-aware Descriptions

Complete documentation for all image collection management endpoints — create, update, delete, list, and topics — including bilingual content, multi-slot cover images, album items with auto-extracted metadata, topic management, per-type album validation, enums, DTOs, and full request/response examples.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Enums](#03--enums)
- [04 · Authentication, Caching & Performance Notes](#04--authentication-caching--performance-notes)
- [05 · Create Image Collection](#05--create-image-collection)
  - `POST /` (multipart)
  - `POST /json`
- [06 · Update Image Collection](#06--update-image-collection) — `PUT /{id}` (multipart)
- [07 · Read](#07--read)
  - `GET /` (getAll)
  - `GET /{id}`
- [08 · Delete](#08--delete) — `DELETE /{id}`
- [09 · Topics](#09--topics) — `GET /topics`
- [10 · DTO Reference](#10--dto-reference)
- [11 · Error Responses](#11--error-responses)
- [12 · Change Log — Old vs. New](#12--change-log--old-vs-new)

---

## 01 · Overview

The Image Collection module manages bilingual photo/image publishments for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each collection carries:

- A **collection type** (`SINGLE`, `GALLERY`, or `PHOTO_STORY`) — enforces an album-size rule
- **Three cover image slots** — CKB cover, KMR cover, and a hover overlay
- **Bilingual embedded content** per language (title, description as **Tiptap HTML**, location, collectedBy)
- An **image album** of ordered items — uploaded files, external URLs, or embed links, with **auto-extracted metadata** (dimensions, file size, MIME type, aspect ratio)
- **Bilingual tags and keywords**
- An optional **topic** (existing by ID, created inline, or cleared on update)
- A full **audit trail** via `ImageCollectionLog`

### Base URL

```
/api/v1/image-collections

# All endpoints require a valid JWT
Authorization: Bearer eyJhbGci...
# OR
Cookie: auth_token=eyJhbGci...
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create collection — `multipart/form-data` (files + JSON) |
| `POST` | `/json` | Create collection — `application/json` (URL-only sources) |
| `PUT` | `/{id}` | Update collection — `multipart/form-data` (partial-merge) |
| `GET` | `/` | Paginated list of all image collections |
| `GET` | `/{id}` | Fetch a single image collection by ID |
| `DELETE` | `/{id}` | Delete a single image collection |
| `GET` | `/topics` | List all `IMAGE` topics for autocomplete |

> ℹ️ The service layer also exposes `getByType`, `searchByTag`, `searchByKeyword`, `globalSearch`, and `getByTopic` methods (all `@Cacheable`), but **they are not yet wired into the controller**. They're ready to be exposed when the frontend needs them.

---

## 02 · Data Models

Three JPA entities + one embeddable make up the image collection module. `ImageCollection` is the aggregate root. All lazy collections use `@BatchSize(50)` to eliminate N+1 queries.

### ImageCollection — `image_collections`

Aggregate root. Manages `@PrePersist` / `@PreUpdate` lifecycle hooks for `createdAt` and `updatedAt`. Uses `@BatchSize(50)` on all element collections and the `imageAlbum` relationship.

**DB indexes:** `idx_img_collection_type`, `idx_img_topic`, `idx_img_title_ckb`, `idx_img_title_kmr`, `idx_img_publishment_date`, `idx_img_created_at`, `idx_img_updated_at`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `collectionType` | `collection_type` | VARCHAR(20) | NOT NULL | `SINGLE` \| `GALLERY` \| `PHOTO_STORY` |
| `ckbCoverUrl` | `ckb_cover_url` | TEXT | NULLABLE | Sorani cover — S3 uploaded or external URL |
| `kmrCoverUrl` | `kmr_cover_url` | TEXT | NULLABLE | Kurmanji cover — S3 uploaded or external URL |
| `hoverCoverUrl` | `hover_cover_url` | TEXT | NULLABLE | Hover overlay image — shown on mouse-over in card views |
| `topic` | `topic_id` | FK → publishment_topics | NULLABLE | Associated topic. `ON DELETE SET NULL`. LAZY + class-level `@BatchSize(50)` on `PublishmentTopic` |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani: `title`, `description` (**Tiptap HTML**), `location`, `collectedBy` |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji: `title`, `description` (**Tiptap HTML**), `location`, `collectedBy` |
| `imageAlbum` | — | @OneToMany (LAZY) | NULLABLE | Ordered list of `ImageAlbumItem`. Cascade ALL + orphanRemoval, `@OrderBy("sortOrder ASC")` |
| `publishmentDate` | `publishment_date` | DATE | NULLABLE | Publication date |
| `contentLanguages` | image_collection_languages | @ElementCollection (LAZY) | NOT NULL | `Set<Language>` — at least one required |
| `tagsCkb` | image_tags_ckb | @ElementCollection (LAZY) | NULLABLE | `Set<String>` (max length 100) |
| `tagsKmr` | image_tags_kmr | @ElementCollection (LAZY) | NULLABLE | `Set<String>` (max length 100) |
| `keywordsCkb` | image_keywords_ckb | @ElementCollection (LAZY) | NULLABLE | `Set<String>` (max length 150) |
| `keywordsKmr` | image_keywords_kmr | @ElementCollection (LAZY) | NULLABLE | `Set<String>` (max length 150) |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist`. `LocalDateTime` |
| `updatedAt` | `updated_at` | TIMESTAMP | NOT NULL | Set on `@PrePersist` and `@PreUpdate`. `LocalDateTime` |

> ℹ️ **Cover URL fallback:** `getAnyCoverUrl()` returns the first non-blank URL in priority order: `ckbCoverUrl` → `kmrCoverUrl` → `hoverCoverUrl`. Useful for thumbnails, logs, and notifications.

> ⚠️ **DB Migration (one-time, only when upgrading from the old single-cover schema):**
>
> ```sql
> ALTER TABLE image_collections RENAME COLUMN cover_url TO ckb_cover_url;
> ALTER TABLE image_collections ALTER COLUMN ckb_cover_url DROP NOT NULL;
> ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS kmr_cover_url   TEXT;
> ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS hover_cover_url TEXT;
> ALTER TABLE image_collections ADD COLUMN IF NOT EXISTS topic_id BIGINT;
> ALTER TABLE image_collections
>     ADD CONSTRAINT fk_img_coll_topic
>     FOREIGN KEY (topic_id) REFERENCES publishment_topics(id) ON DELETE SET NULL;
> ```

### ImageContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `ImageCollection`.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(300) | Collection title in this language |
| `description` | `description_ckb` | `description_kmr` | TEXT | **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| `location` | `location_ckb` | `location_kmr` | VARCHAR(250) | Where the photos were taken |
| `collectedBy` | `collected_by_ckb` | `collected_by_kmr` | VARCHAR(250) | Photographer / collector credit |

### ImageAlbumItem — `image_album_items`

Each row represents a single image inside a collection's album. Supports uploaded files, external links, and embed codes — all three can coexist on a single item. **DB index:** `idx_album_item_collection_id`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `imageUrl` | `image_url` | TEXT | NULLABLE | S3 / CDN uploaded file URL |
| `externalUrl` | `external_url` | TEXT | NULLABLE | Third-party page link (Flickr, Unsplash, etc.) |
| `embedUrl` | `embed_url` | TEXT | NULLABLE | iframe-embeddable URL |
| `captionCkb` | `caption_ckb` | VARCHAR(500) | NULLABLE | Short image caption in Sorani |
| `captionKmr` | `caption_kmr` | VARCHAR(500) | NULLABLE | Short image caption in Kurmanji |
| `descriptionCkb` | `description_ckb` | TEXT | NULLABLE | Long description in Sorani |
| `descriptionKmr` | `description_kmr` | TEXT | NULLABLE | Long description in Kurmanji |
| `sortOrder` | `sort_order` | INT | NULLABLE | Display position (0-based). Album is ordered `ASC` |
| `fileSizeBytes` | `file_size_bytes` | BIGINT | NULLABLE | Auto-detected on upload |
| `widthPx` | `width_px` | INT | NULLABLE | Auto-detected on upload |
| `heightPx` | `height_px` | INT | NULLABLE | Auto-detected on upload |
| `mimeType` | `mime_type` | VARCHAR(50) | NULLABLE | Auto-detected on upload (e.g., `image/jpeg`) |
| `imageCollection` | `image_collection_id` | FK → image_collections | NOT NULL | Parent collection — lazy loaded |

**Transient (computed) helpers on `ImageAlbumItem`:**

| Method | Return Type | Description |
| --- | --- | --- |
| `getAspectRatio()` | `Double` | `widthPx / heightPx`. `null` if dimensions missing |
| `isPortrait()` | `Boolean` | `true` when `heightPx > widthPx` |
| `isLandscape()` | `Boolean` | `true` when `widthPx > heightPx` |
| `getHumanReadableSize()` | `String` | e.g., `"2.4 MB"`, `"850 KB"`, `"512 B"` |

### ImageCollectionLog — `image_collection_logs`

Append-only audit log. Stores `imageCollectionId` as a plain column (not a FK) so log entries are retained even after a collection is deleted.

**DB indexes:** `idx_img_log_collection_id`, `idx_img_log_action`, `idx_img_log_timestamp`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment |
| `imageCollectionId` | `image_collection_id` | BIGINT (column) | NULLABLE | ID of the collection — retained after deletion |
| `collectionTitle` | `collection_title` | VARCHAR(300) | NULLABLE | Snapshot of title at time of action |
| `action` | `action` | VARCHAR(30) | NOT NULL | e.g., `CREATE`, `UPDATE`, `DELETE` |
| `details` | `details` | TEXT | NULLABLE | Human-readable description of the change |
| `performedBy` | `performed_by` | VARCHAR(150) | NULLABLE | Acting principal |
| `timestamp` | `timestamp` | TIMESTAMP | NOT NULL | Defaults to `LocalDateTime.now()` on `@PrePersist` |

---

## 03 · Enums

### ImageCollectionType

| Value | Description | Album-size rule |
| --- | --- | --- |
| `SINGLE` | Exactly one image | **count must be 1** |
| `GALLERY` | Multiple images (album) | **count ≥ 1** |
| `PHOTO_STORY` | Sequential narrative / process | **count ≥ 2** |

> ⚠️ The album-size rule is enforced by the service. Violating it returns `400` with one of: `imageCollection.single.invalid`, `imageCollection.gallery.invalid`, `imageCollection.photoStory.invalid`.

### Language

| Value | Description |
| --- | --- |
| `CKB` | Sorani Kurdish |
| `KMR` | Kurmanji Kurdish |

---

## 04 · Authentication, Caching & Performance Notes

> ℹ️ **All image collection endpoints require authentication.** Every request must carry a valid JWT — either via `Authorization: Bearer <token>` header or the `auth_token` HttpOnly cookie.

> ⚡ **Caching:** All read endpoints (`getAll`, `getById`-adjacent service methods) and the service-layer search/filter methods are `@Cacheable(value="imageCollections")`. Every write (`create`, `update`, `delete`) does `@CacheEvict(value="imageCollections", allEntries=true)`.

> ⚡ **N+1 Protection — `@BatchSize(50)` strategy:** For a page of 20 collections, Hibernate fires approximately 8 focused `IN`-queries instead of one Cartesian monster join:
>
> | Query | Target |
> | --- | --- |
> | Q1 | `image_collections` — base rows |
> | Q2 | `image_collection_languages` |
> | Q3 | `image_tags_ckb` |
> | Q4 | `image_tags_kmr` |
> | Q5 | `image_keywords_ckb` |
> | Q6 | `image_keywords_kmr` |
> | Q7 | `image_album_items` (ordered `sortOrder ASC`) |
> | Q8 | `publishment_topics` (via class-level `@BatchSize` on `PublishmentTopic`) |

> 📝 **Tiptap HTML pipeline:** the `description` fields of both `ckbContent` and `kmrContent` are processed through `TiptapHtmlProcessor.process(...)` on save. Inline media uploaded via `POST /api/v1/media/upload` can be baked into the description body.

---

## 05 · Create Image Collection

### `POST /api/v1/image-collections` — Multipart

🔒 **Auth Required** · `Content-Type: multipart/form-data`

Create a new image collection. The JSON payload goes in the `data` part; cover images and album image files are uploaded as additional multipart parts. **At least one cover** must be provided — either as a file part or as a URL inside `data`.

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON String | **Yes** | Full `CreateRequest` as a JSON string |
| `ckbCoverImage` | File (image/*) | No* | Sorani cover image file. Overrides `ckbCoverUrl` in JSON |
| `kmrCoverImage` | File (image/*) | No* | Kurmanji cover image file. Overrides `kmrCoverUrl` in JSON |
| `hoverCoverImage` | File (image/*) | No* | Hover overlay image file. Overrides `hoverCoverUrl` in JSON |
| `images` | File[] (image/*) | No | Album image files (repeatable). Appended to album items from JSON |

> *At least one cover **source** must be present (either a file part or a `*CoverUrl` in JSON), otherwise the server returns `400 imageCollection.cover.required`.

### `data` JSON Part — Full Example (Both Languages, All Fields, Album)

```json
{
  "collectionType":  "GALLERY",
  "ckbCoverUrl":     null,
  "kmrCoverUrl":     null,
  "hoverCoverUrl":   null,
  "topicId":         3,
  "publishmentDate": "2026-04-11",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "وێنەکانی کوردستان",
    "description": "<p>کۆمەڵێک وێنەی ناوچەی کوردستان لە دەمی بەهار</p>",
    "location":    "سلێمانی، کوردستان",
    "collectedBy": "ئەحمەد کەریم"
  },
  "kmrContent": {
    "title":       "Wêneyên Kurdistanê",
    "description": "<p>Koleksiyoneke wêneyên herêma Kurdistanê di demsala biharê de</p>",
    "location":    "Silêmanî, Kurdistanê",
    "collectedBy": "Ehmed Kerîm"
  },
  "tags":     { "ckb": ["کوردستان", "سروشت", "بەهار"], "kmr": ["Kurdistan", "xweza", "bihar"] },
  "keywords": { "ckb": ["وێنەکێشی", "کوردستان"],       "kmr": ["wênekêşî", "Kurdistan"] },
  "imageAlbum": [
    {
      "imageUrl":       "https://cdn.khi.iq/images/mountain-001.jpg",
      "captionCkb":     "چیاکانی کوردستان",
      "captionKmr":     "Çiyayên Kurdistanê",
      "descriptionCkb": "دیمەنی چیاکان لە وەختی بەهار",
      "descriptionKmr": "Dîmena çiyan di demsala biharê de",
      "sortOrder":      0
    },
    {
      "externalUrl": "https://flickr.com/photos/khi/photo-002",
      "embedUrl":    "https://flickr.com/photos/khi/photo-002/embed",
      "captionCkb":  "دەریاچەی دوکان",
      "captionKmr":  "Deryaçeya Dûkên",
      "sortOrder":   1
    }
  ]
}
```

### `data` JSON Part — CKB-Only, Inline Topic Creation, SINGLE Type

```json
{
  "collectionType":  "SINGLE",
  "ckbCoverUrl":     "https://cdn.khi.iq/images/single-cover.jpg",
  "newTopic":        { "nameCkb": "مێژوو", "nameKmr": "Dîrok" },
  "publishmentDate": "2026-04-11",
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title":       "گەڕانەوە بۆ مێژووی کوردستان",
    "description": "<p>وێنەیەکی کلاسیکی نوێکردنەوە</p>",
    "location":    "ئەربیل",
    "collectedBy": "ئەرشیفی کهی"
  },
  "tags":     { "ckb": ["مێژوو", "کلاسیک"], "kmr": [] },
  "keywords": { "ckb": ["ئەرشیف", "کوردستان"], "kmr": [] },
  "imageAlbum": [
    { "imageUrl": "https://cdn.khi.iq/images/archive-001.jpg", "sortOrder": 0 }
  ]
}
```

> ⚠️ **For `SINGLE`:** the album must contain **exactly one** item (either an uploaded file or one `imageAlbum[]` entry). Sending zero items or more than one returns `400 imageCollection.single.invalid`.

### Request · curl Example (File Covers + File Album Images)

```bash
curl -X POST https://api.khi.iq/api/v1/image-collections \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "collectionType":"GALLERY",
    "topicId":3,
    "publishmentDate":"2026-04-11",
    "contentLanguages":["CKB","KMR"],
    "ckbContent":{"title":"وێنەکانی کوردستان","description":"<p>کۆمەڵێک وێنە</p>","location":"سلێمانی","collectedBy":"ئەحمەد"},
    "kmrContent":{"title":"Wêneyên Kurdistanê","description":"<p>Koleksiyon</p>","location":"Silêmanî","collectedBy":"Ehmed"},
    "tags":{"ckb":["کوردستان","بەهار"],"kmr":["Kurdistan","bihar"]},
    "keywords":{"ckb":["وێنەکێشی"],"kmr":["wênekêşî"]}
  };type=application/json' \
  -F "ckbCoverImage=@ckb-cover.jpg;type=image/jpeg" \
  -F "kmrCoverImage=@kmr-cover.jpg;type=image/jpeg" \
  -F "hoverCoverImage=@hover.jpg;type=image/jpeg" \
  -F "images=@photo1.jpg;type=image/jpeg" \
  -F "images=@photo2.jpg;type=image/jpeg"
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "Image collection created successfully",
  "data": {
    "id":             42,
    "collectionType": "GALLERY",
    "ckbCoverUrl":    "https://cdn.khi.iq/images/collections/42/ckb-cover.jpg",
    "kmrCoverUrl":    "https://cdn.khi.iq/images/collections/42/kmr-cover.jpg",
    "hoverCoverUrl":  "https://cdn.khi.iq/images/collections/42/hover.jpg",
    "topicId":        3,
    "topicNameCkb":   "سروشت",
    "topicNameKmr":   "Xweza",
    "publishmentDate": "2026-04-11",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title":       "وێنەکانی کوردستان",
      "description": "<p>کۆمەڵێک وێنەی ناوچەی کوردستان لە دەمی بەهار</p>",
      "location":    "سلێمانی، کوردستان",
      "collectedBy": "ئەحمەد کەریم"
    },
    "kmrContent": {
      "title":       "Wêneyên Kurdistanê",
      "description": "<p>Koleksiyoneke wêneyên herêma Kurdistanê di demsala biharê de</p>",
      "location":    "Silêmanî, Kurdistanê",
      "collectedBy": "Ehmed Kerîm"
    },
    "tags":     { "ckb": ["کوردستان", "سروشت", "بەهار"], "kmr": ["Kurdistan", "xweza", "bihar"] },
    "keywords": { "ckb": ["وێنەکێشی", "کوردستان"],       "kmr": ["wênekêşî", "Kurdistan"] },
    "imageAlbum": [
      {
        "id":               101,
        "imageUrl":         "https://cdn.khi.iq/images/collections/42/photo1.jpg",
        "captionCkb":       null,
        "captionKmr":       null,
        "sortOrder":        0,
        "fileSizeBytes":    2516582,
        "widthPx":          3840,
        "heightPx":         2160,
        "mimeType":         "image/jpeg",
        "aspectRatio":      1.7777777777777777,
        "humanReadableSize": "2.4 MB"
      },
      {
        "id":               102,
        "imageUrl":         "https://cdn.khi.iq/images/collections/42/photo2.jpg",
        "captionCkb":       null,
        "captionKmr":       null,
        "sortOrder":        1,
        "fileSizeBytes":    921600,
        "widthPx":          1920,
        "heightPx":         1080,
        "mimeType":         "image/jpeg",
        "aspectRatio":      1.7777777777777777,
        "humanReadableSize": "900.0 KB"
      }
    ],
    "createdAt": "2026-04-11T21:30:00",
    "updatedAt": "2026-04-11T21:30:00"
  }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `error.validation` | `data` JSON is null or invalid |
| `400` | `imageCollection.type.required` | `collectionType` is null |
| `400` | `imageCollection.languages.required` | `contentLanguages` is empty or null |
| `400` | `imageCollection.cover.required` | No cover file AND no `*CoverUrl` in JSON |
| `400` | `imageCollection.single.invalid` | `SINGLE` type but album count ≠ 1 |
| `400` | `imageCollection.gallery.invalid` | `GALLERY` type but album count < 1 |
| `400` | `imageCollection.photoStory.invalid` | `PHOTO_STORY` type but album count < 2 |
| `400` | `image.source.required` | An album item has none of `imageUrl` / `externalUrl` / `embedUrl` |
| `400` | `topic.type.mismatch` | `topicId` points to a topic that is not an `IMAGE` topic |
| `500` | `image.media_upload_failed` | S3 / storage failure while uploading any file |
| `401` | — | Missing or expired JWT |

---

### `POST /api/v1/image-collections/json`

🔒 **Auth Required** · `Content-Type: application/json`

Create a new image collection using a plain JSON body — no file uploads. All image sources must be provided as URLs (`ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl`, `imageAlbum[].imageUrl`, `externalUrl`, or `embedUrl`). Body is validated with Bean Validation (`@Valid`).

### Request Body — `CreateRequest`

```json
{
  "collectionType":  "PHOTO_STORY",
  "ckbCoverUrl":     "https://cdn.khi.iq/images/story-cover-ckb.jpg",
  "kmrCoverUrl":     "https://cdn.khi.iq/images/story-cover-kmr.jpg",
  "hoverCoverUrl":   "https://cdn.khi.iq/images/story-hover.jpg",
  "topicId":         7,
  "publishmentDate": "2026-04-10",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "چیرۆکی وێنەکانی ئاژەڵانی کوردستان",
    "description": "<p>زنجیرەیەک وێنە کە چیرۆکی ژیانی ئاژەڵان دەگێڕێتەوە</p>",
    "location":    "دێرەلووک، دهۆک",
    "collectedBy": "سنووری وێنەکێشان"
  },
  "kmrContent": {
    "title":       "Çîroka Wêneyên Ajalan li Kurdistanê",
    "description": "<p>Rêzek wêne ku jiyana ajalan vedibêje</p>",
    "location":    "Dêrelouk, Duhok",
    "collectedBy": "Koma Wênekêşan"
  },
  "tags":     { "ckb": ["ئاژەڵ", "سروشت", "دهۆک"], "kmr": ["ajal", "xweza", "Duhok"] },
  "keywords": { "ckb": ["وێنەکێشی", "ئاژەڵناسی"],   "kmr": ["wênekêşî", "ajalnasî"] },
  "imageAlbum": [
    { "imageUrl":    "https://cdn.khi.iq/images/wildlife-001.jpg", "captionCkb": "ئاژەڵ لە شاخەکان", "captionKmr": "Ajal li çiyan", "sortOrder": 0 },
    { "imageUrl":    "https://cdn.khi.iq/images/wildlife-002.jpg", "captionCkb": "دیمەنی دەشتی",     "captionKmr": "Dîmena deştê",  "sortOrder": 1 },
    { "externalUrl": "https://flickr.com/photos/khi/wildlife-003", "captionCkb": "ئاواتەی پەرتووک",   "captionKmr": "Hêviya xweşikbûnê", "sortOrder": 2 }
  ]
}
```

### Response · 201 Created

Same shape as `POST /` above. Items sourced from URLs only will have **null metadata** (`fileSizeBytes`, `widthPx`, `heightPx`, `mimeType`, `aspectRatio`, `humanReadableSize`) because dimensions/MIME are only auto-extracted during actual file uploads.

### Error Responses

Same set as `POST /` — plus standard Bean Validation envelopes for `@NotNull` failures on `collectionType` and `contentLanguages`.

---

## 06 · Update Image Collection

### `PUT /api/v1/image-collections/{id}`

🔒 **Auth Required** · `Content-Type: multipart/form-data`

**Partial-merge** update. Only fields present in the `data` JSON are applied — null/omitted fields keep their existing values. To clear the topic, send `"clearTopic": true`. To replace the album, send a new `imageAlbum` array; send `null` (or omit the field) to keep the existing album.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | ID of the collection to update |

### Form Parts

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | JSON String | **Yes** | `UpdateRequest` as a JSON string |
| `ckbCoverImage` | File (image/*) | No | Replaces the Sorani cover image |
| `kmrCoverImage` | File (image/*) | No | Replaces the Kurmanji cover image |
| `hoverCoverImage` | File (image/*) | No | Replaces the hover overlay image |
| `images` | File[] (image/*) | No | New album image files. Used alongside or instead of `imageAlbum` JSON items |

### Update Semantics

| Field | Behaviour when present | Behaviour when omitted / null |
| --- | --- | --- |
| `collectionType` | Replaces the type (album-size rule re-validated) | Existing type is kept |
| `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` | Replaces the URL (if no matching file part is uploaded) | Existing URL is kept |
| `*CoverImage` file part | Uploads and replaces the URL | Existing URL is kept |
| `topicId` | Assigns existing topic by ID (must be `IMAGE` type) | Existing topic is kept |
| `newTopic` | Creates and assigns a new inline topic | Existing topic is kept |
| `clearTopic: true` | Removes the topic association | No-op |
| `publishmentDate` | Replaces the date | Existing date is kept |
| `contentLanguages` | Replaces the language set | Existing set is kept |
| `ckbContent` / `kmrContent` | Replaces that block | Existing block is kept |
| `tags` / `keywords` | Replaces the entire set | Existing sets are kept |
| `imageAlbum` (non-null) | **Replaces** the entire album | — |
| `imageAlbum` (null/omitted) | — | Existing album is kept |

### `data` JSON Part — Update Type, Cover URL, and Album

```json
{
  "collectionType":   "GALLERY",
  "ckbCoverUrl":      "https://cdn.khi.iq/images/new-ckb-cover.jpg",
  "publishmentDate":  "2026-04-12",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title":       "وێنەکانی نوێی کوردستان",
    "description": "<p>نوێکراوەوە</p>",
    "location":    "هەولێر",
    "collectedBy": "ئەرشیفی کهی"
  },
  "kmrContent": {
    "title":       "Wêneyên Nû yên Kurdistanê",
    "description": "<p>Nûvekirî</p>",
    "location":    "Hewlêr",
    "collectedBy": "Arşîva KHI"
  },
  "tags":     { "ckb": ["کوردستان", "هەولێر"], "kmr": ["Kurdistan", "Hewlêr"] },
  "keywords": { "ckb": ["وێنەکێشی"],            "kmr": ["wênekêşî"] },
  "imageAlbum": [
    { "imageUrl": "https://cdn.khi.iq/images/new-photo-001.jpg", "captionCkb": "دیمەنی نوێ", "captionKmr": "Dîmena nû", "sortOrder": 0 }
  ]
}
```

### `data` JSON Part — Topic actions

```json
{ "clearTopic": true }
```
```json
{ "topicId": 5 }
```
```json
{ "newTopic": { "nameCkb": "شەقام", "nameKmr": "Çand" } }
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/image-collections/42 \
  -H "Authorization: Bearer eyJhbGci..." \
  -F 'data={
    "collectionType":"GALLERY",
    "contentLanguages":["CKB","KMR"],
    "ckbContent":{"title":"وێنەکانی نوێی کوردستان","description":"<p>نوێکراوەوە</p>"},
    "kmrContent":{"title":"Wêneyên Nû yên Kurdistanê","description":"<p>Nûvekirî</p>"},
    "tags":{"ckb":["کوردستان"],"kmr":["Kurdistan"]}
  };type=application/json' \
  -F "ckbCoverImage=@new-cover.jpg;type=image/jpeg"
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Image collection updated successfully",
  "data": { /* full Response — same shape as create */ }
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `400` | `imageCollection.type.required` | New `collectionType` is null when present |
| `400` | `imageCollection.single.invalid` / `.gallery.invalid` / `.photoStory.invalid` | Album-size rule violated for the chosen (or kept) type |
| `400` | `image.source.required` | An album item has none of `imageUrl` / `externalUrl` / `embedUrl` |
| `400` | `topic.type.mismatch` | `topicId` points to a non-IMAGE topic |
| `400` | `error.validation` | Other validation failures |
| `401` | — | Missing or expired JWT |
| `404` | `imageCollection.not.found` | Collection with given `id` does not exist |
| `500` | `image.media_upload_failed` | S3 / storage failure while uploading any file |

---

## 07 · Read

### `GET /api/v1/image-collections` — getAll

🔒 **Auth Required** · `@Cacheable("imageCollections")`

Returns a paginated list of all image collections. Each page item includes the full response shape with bilingual content, cover URLs, topic, album, tags, and keywords.

### Query Parameters

| Param | Type | Default | Description |
| --- | --- | --- | --- |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Items per page |

### Request

```
GET /api/v1/image-collections?page=0&size=20
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Image collections fetched successfully",
  "data": {
    "content": [
      { /* Response — see DTO Reference */ }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize":   20,
      "sort":       { "sorted": true, "unsorted": false, "empty": false }
    },
    "totalElements":    87,
    "totalPages":       5,
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

### `GET /api/v1/image-collections/{id}`

🔒 **Auth Required**

Fetch a single image collection by its primary key. Returns the complete response shape including all album items with auto-extracted metadata.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the collection |

### Response · 200 OK

Same shape as one element from `getAll`.

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `imageCollection.not.found` | Collection with given `id` does not exist |

---

## 08 · Delete

### `DELETE /api/v1/image-collections/{id}`

🔒 **Auth Required**

Permanently delete an image collection and all its associated album items (cascade delete). The `ImageCollectionLog` entry for the deletion is retained — the log stores `imageCollectionId` as a plain column, not a FK, so log history survives the deletion.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the collection to delete |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Image collection deleted successfully",
  "data":    null
}
```

### Error Responses

| Status | Error Key | Description |
| --- | --- | --- |
| `401` | — | Missing or expired JWT |
| `404` | `imageCollection.not.found` | Collection with given `id` does not exist |

---

## 09 · Topics

### `GET /api/v1/image-collections/topics`

🔒 **Auth Required**

Returns all `PublishmentTopic` records with `entityType = "IMAGE"`. Designed for frontend autocomplete/dropdown when creating or editing an image collection. Returns the minimum fields needed: `id`, `nameCkb`, and `nameKmr`. Null name values are returned as empty strings.

### Request

```
GET /api/v1/image-collections/topics
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "IMAGE topics fetched successfully",
  "data": [
    { "id": 1, "nameCkb": "سیاسی",   "nameKmr": "Siyasî" },
    { "id": 2, "nameCkb": "ئابووری", "nameKmr": "Aborî" },
    { "id": 3, "nameCkb": "سروشت",   "nameKmr": "Xweza" },
    { "id": 7, "nameCkb": "مێژوو",   "nameKmr": "Dîrok" }
  ]
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT |

---

## 10 · DTO Reference

### CreateRequest

Sent as a JSON string in the `data` multipart part (or as the request body for `/json`).

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `collectionType` | `ImageCollectionType` | **Yes** | `@NotNull` · `SINGLE` \| `GALLERY` \| `PHOTO_STORY` |
| `ckbCoverUrl` | String | No | Sorani cover URL. Used if no `ckbCoverImage` file part is uploaded |
| `kmrCoverUrl` | String | No | Kurmanji cover URL. Used if no `kmrCoverImage` file part is uploaded |
| `hoverCoverUrl` | String | No | Hover overlay URL. Used if no `hoverCoverImage` file part is uploaded |
| `topicId` | Long | No | ID of an existing `PublishmentTopic` with `entityType = "IMAGE"` |
| `newTopic` | `InlineTopicRequest` | No | Creates and assigns a new topic inline. Ignored if `topicId` is set |
| `publishmentDate` | `LocalDate` | No | ISO-8601 date, e.g., `"2026-04-11"` |
| `contentLanguages` | `Set<Language>` | **Yes** | `@NotNull` · At least one of `CKB`, `KMR` |
| `ckbContent` | `LanguageContentDto` | No* | Used when CKB is in `contentLanguages` |
| `kmrContent` | `LanguageContentDto` | No* | Used when KMR is in `contentLanguages` |
| `tags` | `BilingualSet` | No | Bilingual tag sets |
| `keywords` | `BilingualSet` | No | Bilingual keyword sets |
| `imageAlbum` | `List<ImageItemDto>` | No | Album items with URL-based sources. File-uploaded items are appended by the service |

### UpdateRequest

All fields are optional — partial-merge.

| Field | Type | Description |
| --- | --- | --- |
| `collectionType` | `ImageCollectionType` | Replaces the collection type |
| `ckbCoverUrl` | String | Replaces the Sorani cover URL (if no file part uploaded) |
| `kmrCoverUrl` | String | Replaces the Kurmanji cover URL (if no file part uploaded) |
| `hoverCoverUrl` | String | Replaces the hover cover URL (if no file part uploaded) |
| `topicId` | Long | Assigns an existing topic by ID |
| `newTopic` | `InlineTopicRequest` | Creates and assigns a new topic inline |
| `clearTopic` | boolean | `true` removes the current topic association. Default `false` |
| `publishmentDate` | `LocalDate` | Replaces the publication date |
| `contentLanguages` | `Set<Language>` | Replaces the content language set |
| `ckbContent` | `LanguageContentDto` | Replaces Sorani content block |
| `kmrContent` | `LanguageContentDto` | Replaces Kurmanji content block |
| `tags` | `BilingualSet` | Replaces the entire tag set |
| `keywords` | `BilingualSet` | Replaces the entire keyword set |
| `imageAlbum` | `List<ImageItemDto>` | `null` = keep existing album. Non-null = replace entire album |

### Response

Returned by all write endpoints and both read endpoints. `@JsonInclude(NON_NULL)` is applied.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `collectionType` | `ImageCollectionType` | `SINGLE` \| `GALLERY` \| `PHOTO_STORY` |
| `ckbCoverUrl` | String | Sorani cover image URL |
| `kmrCoverUrl` | String | Kurmanji cover image URL |
| `hoverCoverUrl` | String | Hover overlay image URL |
| `topicId` | Long | ID of the assigned topic, or `null` |
| `topicNameCkb` | String | Topic name in Sorani, or `null` |
| `topicNameKmr` | String | Topic name in Kurmanji, or `null` |
| `publishmentDate` | `LocalDate` | ISO-8601 date |
| `contentLanguages` | `Set<Language>` | Active languages |
| `ckbContent` | `LanguageContentDto` | Sorani content block |
| `kmrContent` | `LanguageContentDto` | Kurmanji content block |
| `tags` | `BilingualSet` | Bilingual tag sets |
| `keywords` | `BilingualSet` | Bilingual keyword sets |
| `imageAlbum` | `List<ImageItemDto>` | Album items ordered by `sortOrder ASC`. Includes metadata |
| `createdAt` | `LocalDateTime` | ISO-8601 local datetime |
| `updatedAt` | `LocalDateTime` | ISO-8601 local datetime |

### LanguageContentDto

Used inside `ckbContent` / `kmrContent` on both requests and responses.

| Field | Type | Request | Response | Description |
| --- | --- | --- | --- | --- |
| `title` | String | Optional | ✅ | Collection title (max 300) |
| `description` | String | Optional | ✅ | **Tiptap HTML** — processed by `TiptapHtmlProcessor` on save |
| `topic` | String | Optional | ✅ | Free-text topic name **inside the content block** (distinct from the `topicId` FK relation) |
| `location` | String | Optional | ✅ | Where the photos were taken (max 250) |
| `collectedBy` | String | Optional | ✅ | Photographer / collector credit (max 250) |

> ℹ️ A content block is treated as "empty" (and stored as null) when `title`, `description`, `location`, and `collectedBy` are all blank.

### ImageItemDto

Used in `imageAlbum` on both requests and responses. `@JsonInclude(NON_NULL)` is applied.

| Field | Type | Request | Response | Description |
| --- | --- | --- | --- | --- |
| `id` | Long | — | ✅ | DB primary key of the album item |
| `imageUrl` | String | Optional* | ✅ | S3 / CDN URL of the uploaded image |
| `externalUrl` | String | Optional* | ✅ | Third-party page link (Flickr, Unsplash, etc.) |
| `embedUrl` | String | Optional* | ✅ | iframe-embeddable URL |
| `captionCkb` | String | Optional | ✅ | Short image caption in Sorani (max 500) |
| `captionKmr` | String | Optional | ✅ | Short image caption in Kurmanji (max 500) |
| `descriptionCkb` | String | Optional | ✅ | Long description in Sorani |
| `descriptionKmr` | String | Optional | ✅ | Long description in Kurmanji |
| `sortOrder` | Integer | Optional | ✅ | Display order (0-based). Album is sorted `ASC` |
| `fileSizeBytes` | Long | — | ✅ | Auto-extracted on upload. `null` for URL-only items |
| `widthPx` | Integer | — | ✅ | Auto-extracted on upload |
| `heightPx` | Integer | — | ✅ | Auto-extracted on upload |
| `mimeType` | String | — | ✅ | Auto-detected (e.g., `image/jpeg`) |
| `aspectRatio` | Double | — | ✅ | Computed transient: `widthPx / heightPx`. `null` if dimensions missing |
| `humanReadableSize` | String | — | ✅ | Computed transient: e.g., `"2.4 MB"`, `"850 KB"` |

> *At least one of `imageUrl`, `externalUrl`, or `embedUrl` must be present on each album item, otherwise the server returns `400 image.source.required`.

> ℹ️ **Metadata fields** (`fileSizeBytes`, `widthPx`, `heightPx`, `mimeType`, `aspectRatio`, `humanReadableSize`) are **only populated for items uploaded as binary files**. Items sourced from `imageUrl` / `externalUrl` / `embedUrl` in the JSON will have `null` for all metadata fields.

### InlineTopicRequest

Used in `CreateRequest.newTopic` / `UpdateRequest.newTopic`. `@JsonInclude(NON_NULL)` is applied.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `nameCkb` | String | **Yes** | Sorani name for the new topic |
| `nameKmr` | String | **Yes** | Kurmanji name for the new topic |

### BilingualSet

Used in `tags` and `keywords`.

| Field | Type | Description |
| --- | --- | --- |
| `ckb` | `Set<String>` | CKB (Sorani) tag/keyword strings |
| `kmr` | `Set<String>` | KMR (Kurmanji) tag/keyword strings |

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
| `201 Created` | New image collection saved successfully |
| `200 OK` | Update, delete, or read succeeded |
| `400 Bad Request` | Validation error or business rule violation |
| `401 Unauthorized` | JWT is missing, expired, or blacklisted |
| `403 Forbidden` | Account locked, disabled, or insufficient role |
| `404 Not Found` | Image collection with given id does not exist |
| `500 Internal Error` | Unexpected server failure or storage failure |

### Validation Error Body — `400 Bad Request`

```json
{
  "timestamp": "2026-04-11T21:30:00",
  "status":    400,
  "errors": [
    {
      "field":   "collectionType",
      "message": "collectionType is required"
    },
    {
      "field":   "contentLanguages",
      "message": "At least one content language is required"
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
  "message":   "imageCollection.cover.required"
}
```

### Common Business Error Keys

| Error Key | Trigger |
| --- | --- |
| `error.validation` | Generic validation envelope (null body, etc.) |
| `imageCollection.type.required` | `collectionType` is null |
| `imageCollection.languages.required` | `contentLanguages` is empty or null |
| `imageCollection.cover.required` | No cover file AND no `*CoverUrl` in JSON |
| `imageCollection.single.invalid` | `SINGLE` type but album count ≠ 1 |
| `imageCollection.gallery.invalid` | `GALLERY` type but album count < 1 |
| `imageCollection.photoStory.invalid` | `PHOTO_STORY` type but album count < 2 |
| `imageCollection.not.found` | No collection found for the given `id` |
| `image.source.required` | An album item has no `imageUrl`, `externalUrl`, or `embedUrl` |
| `topic.type.mismatch` | `topicId` provided but topic is not an `IMAGE` topic |
| `image.media_upload_failed` | S3 / storage failure during upload |
| `tag.required` | (service-only) `searchByTag` called with blank `tag` |
| `keyword.required` | (service-only) `searchByKeyword` / `globalSearch` called with blank input |

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
  "message":   "imageCollection.not.found"
}
```

> ℹ️ All `createdAt` and `updatedAt` fields in the `Response` DTO are `LocalDateTime`.

---

## 12 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `POST /api/v1/image-collections` | Multipart create | Multipart create | ⚪ Unchanged |
| `POST /api/v1/image-collections/json` | JSON create | JSON create | ⚪ Unchanged |
| `PUT /api/v1/image-collections/{id}` | Multipart update | Multipart update — **partial-merge** semantics formalized | 🟡 Behaviour clarified |
| `GET /api/v1/image-collections` | Paginated list | Paginated list — now `@Cacheable("imageCollections")` | 🟡 Behaviour clarified |
| `GET /api/v1/image-collections/{id}` | Get by id | Get by id | ⚪ Unchanged |
| `DELETE /api/v1/image-collections/{id}` | Delete | Delete | ⚪ Unchanged |
| `GET /api/v1/image-collections/topics` | IMAGE topics | IMAGE topics | ⚪ Unchanged |

**Endpoint count:** The old doc header advertised **8 endpoints** but its summary table only listed **7**; the controller exposes **7**. The new doc reflects the actual 7.

> ℹ️ The service layer also exposes `getByType`, `searchByTag`, `searchByKeyword`, `globalSearch`, and `getByTopic` (all `@Cacheable`), but they are **not yet wired into the controller**. They can be exposed when the frontend needs them.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `image_collections` DB indexes | Not documented | 🟢 Documented: `idx_img_collection_type`, `idx_img_topic`, `idx_img_title_ckb`, `idx_img_title_kmr`, `idx_img_publishment_date`, `idx_img_created_at`, `idx_img_updated_at` |
| `image_album_items` DB index | Not documented | 🟢 Documented: `idx_album_item_collection_id` |
| `image_collection_logs` DB indexes | Not documented | 🟢 Documented: `idx_img_log_collection_id`, `idx_img_log_action`, `idx_img_log_timestamp` |
| `imageAlbum` ordering | Said "ordered" generically | 🟢 Clarified: `@OrderBy("sortOrder ASC")` is enforced by JPA |
| Three cover slots | Already present | ⚪ Unchanged |
| Topic FK with `ON DELETE SET NULL` | Present | ⚪ Unchanged |
| Auto-extracted metadata on `ImageAlbumItem` | Present | ⚪ Unchanged |
| `description_ckb` / `description_kmr` content type | Plain TEXT | 🟡 Now processed as **Tiptap HTML** by `TiptapHtmlProcessor` on save |

### C) DTO comparison

| Item | Old | New |
| --- | --- | --- |
| `CreateRequest` shape | Same | ⚪ Unchanged |
| `UpdateRequest` shape | Same (partial-merge) | ⚪ Unchanged — semantics now formalized in this doc |
| `Response` shape | Same | ⚪ Unchanged |
| `LanguageContentDto.description` | Plain text | 🟡 Now **Tiptap HTML** (processed on save) |
| `LanguageContentDto.topic` (free-text) | Documented | ⚪ Unchanged (still present, distinct from `topicId` relation) |
| `ImageItemDto` metadata fields | Documented as response-only | ⚪ Unchanged |
| `@JsonInclude(NON_NULL)` on `Response`/`ImageItemDto`/`InlineTopicRequest` | Not explicit | 🟢 Documented |

### D) Validation / error-key comparison

| Old error key | New error key | Change |
| --- | --- | --- |
| `image.collection.type.required` | `imageCollection.type.required` | 🟡 **Renamed** (camelCase) |
| `image.collection.languages.required` | `imageCollection.languages.required` | 🟡 **Renamed** (camelCase) |
| `image.collection.ckb.title.required` | — | 🔴 **Removed** — title is no longer required per language; album-size rules drive validation instead |
| `image.collection.kmr.title.required` | — | 🔴 **Removed** — same as above |
| `image.collection.not.found` | `imageCollection.not.found` | 🟡 **Renamed** (camelCase) |
| `image.collection.topic.not.found` | `topic.type.mismatch` | 🟡 **Renamed** — service now distinguishes "topic missing" from "topic exists but isn't IMAGE type" |
| `image.album.item.no.source` | `image.source.required` | 🟡 **Renamed** |
| — | `imageCollection.cover.required` | 🟢 **Added** — explicit "no cover file AND no `*CoverUrl`" error |
| — | `imageCollection.single.invalid` | 🟢 **Added** — `SINGLE` must have exactly 1 image |
| — | `imageCollection.gallery.invalid` | 🟢 **Added** — `GALLERY` must have ≥ 1 image |
| — | `imageCollection.photoStory.invalid` | 🟢 **Added** — `PHOTO_STORY` must have ≥ 2 images |
| — | `image.media_upload_failed` | 🟢 **Added** — wraps S3/storage failures (`500`) |
| — | `error.validation` | 🟢 **Added** — generic validation envelope |
| — | `tag.required` / `keyword.required` | 🟢 **Added** — service-layer search guards (not yet exposed by controller) |

### E) Caching & performance

| Item | Old | New |
| --- | --- | --- |
| `@Cacheable("imageCollections")` on reads | Not documented | 🟢 Documented — `getAll`, `getByType`, `searchByTag`, `searchByKeyword`, `globalSearch`, `getByTopic` |
| `@CacheEvict(allEntries=true)` on writes | Not documented | 🟢 Documented — `create`, `update`, `delete` |
| `@BatchSize(50)` strategy on all lazy collections | Documented (~8 IN-queries per page) | ⚪ Unchanged |

### F) Summary

- 🟢 **Added** (validation rules): per-type album-count validation (`SINGLE` = 1, `GALLERY` ≥ 1, `PHOTO_STORY` ≥ 2); explicit `imageCollection.cover.required`; `image.media_upload_failed` for storage errors; `topic.type.mismatch` instead of the generic "topic not found".
- 🟢 **Added** (operational): `@Cacheable("imageCollections")` on reads / `@CacheEvict(allEntries=true)` on writes; comprehensive DB indexes on `image_collections`, `image_album_items`, and `image_collection_logs`; service-layer search/filter methods ready to be exposed (`getByType`, `searchByTag`, `searchByKeyword`, `globalSearch`, `getByTopic`).
- 🟡 **Changed:** `description_ckb` / `description_kmr` are now **Tiptap HTML** (processed by `TiptapHtmlProcessor` on save); error keys renamed from `image.collection.*` to `imageCollection.*` (camelCase); album item ordering formalized via `@OrderBy("sortOrder ASC")`; per-language title-required errors **removed** in favour of type-based album-count validation.
- 🔴 **Removed:** `image.collection.ckb.title.required`, `image.collection.kmr.title.required` (title is no longer hard-required when a language is selected — the album-size rule is the binding constraint now).
- ⚪ **Unchanged:** All seven endpoints, three cover slots (`ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl`), JSONB-free schema (album is a real table here, unlike News/Project), topic FK with `ON DELETE SET NULL`, audit log with retained `imageCollectionId`, `MediaItem`-style helpers on `ImageAlbumItem` (transient `aspectRatio`, `isPortrait`, `isLandscape`, `getHumanReadableSize`).
