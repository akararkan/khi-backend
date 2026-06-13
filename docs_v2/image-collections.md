# Image Collections Module

> Bilingual (CKB / KMR) image collections with multipart cover uploads, album items, hover overlays, topic taxonomy, audit logs. Public reads, role-gated writes.

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — ImageCollection](#02--data-model--imagecollection)
- [03 · Data Model — ImageContent](#03--data-model--imagecontent)
- [04 · Data Model — ImageAlbumItem](#04--data-model--imagealbumitem)
- [05 · Data Model — ImageCollectionLog](#05--data-model--imagecollectionlog)
- [06 · Enums](#06--enums)
- [07 · Authentication & Roles](#07--authentication--roles)
- [08 · Public API](#08--public-api)
- [09 · Internal API](#09--internal-api)
- [10 · DTO Reference](#10--dto-reference)
- [11 · Multipart Layout](#11--multipart-layout)
- [12 · Response Envelope](#12--response-envelope)
- [13 · Error Responses](#13--error-responses)
- [14 · Notes](#14--notes)

---

## 01 · Module Overview

- **Base path:** `/api/v1/image-collections`
- **Controller:** `ImageCollectionController`
- **Service:** `ImageCollectionService`
- **Two create modes:**
  - `POST /api/v1/image-collections` — `multipart/form-data` (with file uploads)
  - `POST /api/v1/image-collections/json` — `application/json` (URL-only sources, no file uploads)
- **Update mode:** `PUT /api/v1/image-collections/{id}` — `multipart/form-data` only
- **Topic taxonomy:** Topics are stored in `publishment_topics` table, scoped by `entityType = "IMAGE"`. They can be referenced by id or created inline via `newTopic`.
- **Collection types:** `SINGLE`, `GALLERY`, `PHOTO_STORY` — each enforces a different minimum image count.
- **Bilingual content:** Independent CKB (Sorani) and KMR (Kurmanji) content blocks (title, description, location, collectedBy), plus bilingual tag/keyword sets and bilingual album item captions/descriptions.
- **Three cover slots:** `ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl` — each can be uploaded as a multipart file or supplied as an external URL.
- **Auto-extracted album metadata:** When an image is uploaded as a multipart file, the service auto-extracts `widthPx`, `heightPx`, `fileSizeBytes`, `mimeType` using `ImageIO`. External / embed URLs receive `null` for these fields.
- **Audit logs:** Every `CREATE`, `UPDATE`, `DELETE` writes a row to `image_collection_logs`.
- **Caching:** Read endpoints are `@Cacheable("imageCollections")`. Write endpoints `@CacheEvict(allEntries = true)`.

### Endpoint Summary

| Method | Path                                   | Content-Type             | Auth         | Purpose                              |
| ------ | -------------------------------------- | ------------------------ | ------------ | ------------------------------------ |
| GET    | `/api/v1/image-collections`            | —                        | Public       | Paginated list                       |
| GET    | `/api/v1/image-collections/{id}`       | —                        | Public       | Fetch one by id                      |
| GET    | `/api/v1/image-collections/topics`     | —                        | Public       | List `IMAGE`-typed topics            |
| POST   | `/api/v1/image-collections`            | `multipart/form-data`    | EMPLOYEE+    | Create (with file uploads)           |
| POST   | `/api/v1/image-collections/json`       | `application/json`       | EMPLOYEE+    | Create (URL-only sources)            |
| PUT    | `/api/v1/image-collections/{id}`       | `multipart/form-data`    | EMPLOYEE+    | Update                               |
| DELETE | `/api/v1/image-collections/{id}`       | —                        | ADMIN+       | Delete                               |

---

## 02 · Data Model — ImageCollection

**Table:** `image_collections`

**Indexes:**

- `idx_img_collection_type` on `collection_type`
- `idx_img_topic` on `topic_id`
- `idx_img_title_ckb` on `title_ckb`
- `idx_img_title_kmr` on `title_kmr`
- `idx_img_publishment_date` on `publishment_date`
- `idx_img_created_at` on `created_at`
- `idx_img_updated_at` on `updated_at`

### Fields

| Field              | Java Type                  | DB Column / Mapping                                                                              | Notes                                                                            |
| ------------------ | -------------------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| `id`               | `Long`                     | `id` — `@Id @GeneratedValue(IDENTITY)`                                                            | Primary key.                                                                     |
| `collectionType`   | `ImageCollectionType`      | `@Enumerated(STRING) @Column(name="collection_type", nullable=false, length=20)`                  | `SINGLE`, `GALLERY`, `PHOTO_STORY`.                                              |
| `ckbCoverUrl`      | `String`                   | `@Column(name="ckb_cover_url", columnDefinition="TEXT")`                                          | Sorani cover (uploaded or external URL).                                         |
| `kmrCoverUrl`      | `String`                   | `@Column(name="kmr_cover_url", columnDefinition="TEXT")`                                          | Kurmanji cover.                                                                  |
| `hoverCoverUrl`    | `String`                   | `@Column(name="hover_cover_url", columnDefinition="TEXT")`                                        | Hover overlay shown on mouse-over.                                               |
| `topic`            | `PublishmentTopic`         | `@ManyToOne(fetch=LAZY) @JoinColumn(name="topic_id")`                                             | Optional; nullable. FK `publishment_topics(id)`.                                 |
| `ckbContent`       | `ImageContent` (Embedded)  | `title_ckb`, `description_ckb`, `location_ckb`, `collected_by_ckb`                                | Sorani content block (see § 03).                                                 |
| `kmrContent`       | `ImageContent` (Embedded)  | `title_kmr`, `description_kmr`, `location_kmr`, `collected_by_kmr`                                | Kurmanji content block.                                                          |
| `imageAlbum`       | `List<ImageAlbumItem>`     | `@OneToMany(mappedBy="imageCollection", cascade=ALL, orphanRemoval=true, fetch=LAZY)` + `@BatchSize(50)` + `@OrderBy("sortOrder ASC")` | Album items ordered by `sortOrder`.                                              |
| `publishmentDate`  | `LocalDate`                | `@Column(name="publishment_date")`                                                                | Publication date.                                                                |
| `contentLanguages` | `Set<Language>`            | `@ElementCollection(fetch=LAZY) @CollectionTable("image_collection_languages") @BatchSize(50)`     | Stored as STRING enum (`length=10`).                                             |
| `tagsCkb`          | `Set<String>`              | `@ElementCollection(fetch=LAZY) @CollectionTable("image_tags_ckb")` — col `tag_ckb` len 100        | CKB tags.                                                                        |
| `tagsKmr`          | `Set<String>`              | `@ElementCollection(fetch=LAZY) @CollectionTable("image_tags_kmr")` — col `tag_kmr` len 100        | KMR tags.                                                                        |
| `keywordsCkb`      | `Set<String>`              | `@ElementCollection(fetch=LAZY) @CollectionTable("image_keywords_ckb")` — col `keyword_ckb` len 150 | CKB keywords.                                                                    |
| `keywordsKmr`      | `Set<String>`              | `@ElementCollection(fetch=LAZY) @CollectionTable("image_keywords_kmr")` — col `keyword_kmr` len 150 | KMR keywords.                                                                    |
| `createdAt`        | `LocalDateTime`            | `@Column(name="created_at", nullable=false, updatable=false)`                                     | Set by `@PrePersist`.                                                            |
| `updatedAt`        | `LocalDateTime`            | `@Column(name="updated_at", nullable=false)`                                                      | Updated by `@PrePersist` / `@PreUpdate`.                                         |

**Helper:** `getAnyCoverUrl()` returns the first non-blank of `ckbCoverUrl → kmrCoverUrl → hoverCoverUrl`, else `null`.

---

## 03 · Data Model — ImageContent

`@Embeddable` value type used twice on `ImageCollection` (CKB + KMR blocks). Column overrides applied at the embedding site.

| Field         | Java Type | Default Column Mapping (overridden per-language) |
| ------------- | --------- | ------------------------------------------------ |
| `title`       | `String`  | `@Column(length=300)`                            |
| `description` | `String`  | `@Column(columnDefinition="TEXT")`               |
| `location`    | `String`  | `@Column(length=250)`                            |
| `collectedBy` | `String`  | `@Column(length=250)`                            |

Overrides used on `ImageCollection`:

- CKB → `title_ckb(300)`, `description_ckb(TEXT)`, `location_ckb(250)`, `collected_by_ckb(250)`
- KMR → `title_kmr(300)`, `description_kmr(TEXT)`, `location_kmr(250)`, `collected_by_kmr(250)`

---

## 04 · Data Model — ImageAlbumItem

**Table:** `image_album_items`

**Indexes:**

- `idx_album_item_collection_id` on `image_collection_id`

### Fields

| Field             | Java Type         | DB Column / Mapping                                              | Notes                                                                                  |
| ----------------- | ----------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `id`              | `Long`            | `@Id @GeneratedValue(IDENTITY)`                                  | Primary key.                                                                           |
| `imageUrl`        | `String`          | `@Column(name="image_url", nullable=true, columnDefinition="TEXT")` | S3 / uploaded URL (optional).                                                          |
| `externalUrl`     | `String`          | `@Column(name="external_url", columnDefinition="TEXT")`          | External page / direct link (Flickr, Unsplash, etc.).                                  |
| `embedUrl`        | `String`          | `@Column(name="embed_url", columnDefinition="TEXT")`             | Iframe-ready embed URL.                                                                |
| `captionCkb`      | `String`          | `@Column(name="caption_ckb", length=500)`                        | Short CKB caption.                                                                     |
| `captionKmr`      | `String`          | `@Column(name="caption_kmr", length=500)`                        | Short KMR caption.                                                                     |
| `descriptionCkb`  | `String`          | `@Column(name="description_ckb", columnDefinition="TEXT")`       | Detailed CKB description (Tiptap HTML processed).                                      |
| `descriptionKmr`  | `String`          | `@Column(name="description_kmr", columnDefinition="TEXT")`       | Detailed KMR description (Tiptap HTML processed).                                      |
| `sortOrder`       | `Integer`         | `@Column(name="sort_order")`                                     | 0-based display order.                                                                 |
| `fileSizeBytes`   | `Long`            | `@Column(name="file_size_bytes")`                                | Auto-extracted on upload.                                                              |
| `widthPx`         | `Integer`         | `@Column(name="width_px")`                                       | Auto-extracted on upload via `ImageIO`.                                                |
| `heightPx`        | `Integer`         | `@Column(name="height_px")`                                      | Auto-extracted on upload via `ImageIO`.                                                |
| `mimeType`        | `String`          | `@Column(name="mime_type", length=50)`                           | Auto-extracted from multipart content-type.                                            |
| `imageCollection` | `ImageCollection` | `@ManyToOne(fetch=LAZY) @JoinColumn(name="image_collection_id", nullable=false)` | Owning collection.                                                                     |

### Transient (calculated) helpers

| Method                  | Returns   | Description                                                                                  |
| ----------------------- | --------- | -------------------------------------------------------------------------------------------- |
| `getAspectRatio()`      | `Double`  | `width / height` — null if dimensions unavailable.                                           |
| `isPortrait()`          | `Boolean` | `height > width`.                                                                            |
| `isLandscape()`         | `Boolean` | `width > height`.                                                                            |
| `getHumanReadableSize()`| `String`  | Formatted size — e.g. `"2.4 MB"`, `"850 KB"`, `"512 B"`.                                     |

---

## 05 · Data Model — ImageCollectionLog

**Table:** `image_collection_logs`

**Indexes:**

- `idx_img_log_collection_id` on `image_collection_id`
- `idx_img_log_action` on `action`
- `idx_img_log_timestamp` on `timestamp`

### Fields

| Field               | Java Type       | DB Column / Mapping                                       | Notes                                       |
| ------------------- | --------------- | --------------------------------------------------------- | ------------------------------------------- |
| `id`                | `Long`          | `@Id @GeneratedValue(IDENTITY)`                           | Primary key.                                |
| `imageCollectionId` | `Long`          | `@Column(name="image_collection_id")`                     | FK-style reference (no JPA association).    |
| `collectionTitle`   | `String`        | `@Column(name="collection_title", length=300)`            | Snapshot of CKB title (fallback KMR / id).  |
| `action`            | `String`        | `@Column(name="action", nullable=false, length=30)`       | `CREATE`, `UPDATE`, `DELETE`.               |
| `details`           | `String`        | `@Column(name="details", columnDefinition="TEXT")`        | Free-text CKB summary.                      |
| `performedBy`       | `String`        | `@Column(name="performed_by", length=150)`                | Service writes `"system"`.                  |
| `timestamp`         | `LocalDateTime` | `@Column(name="timestamp", nullable=false)`               | Defaulted in `@PrePersist`.                 |

---

## 06 · Enums

### `ImageCollectionType`

Package: `ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType`

| Value         | Meaning / Constraint                                              |
| ------------- | ----------------------------------------------------------------- |
| `SINGLE`      | Exactly one image (`count != 1` → validation error).              |
| `GALLERY`     | At least 1 image (`count < 1` → validation error).                |
| `PHOTO_STORY` | Sequential steps; at least 2 images (`count < 2` → error).        |

### `Language`

Package: `ak.dev.khi_backend.khi_app.enums.Language`

| Value | Meaning                  |
| ----- | ------------------------ |
| `CKB` | Kurdish Central (Sorani) |
| `KMR` | Kurdish Kurmanji         |

JSON serialization: enum name as-is (`"CKB"`, `"KMR"`). `@JsonCreator` accepts case-insensitive input.

---

## 07 · Authentication & Roles

Resolved from `SecurityConfig`:

| Method | Path Pattern                              | Auth Required | Allowed Roles                       |
| ------ | ----------------------------------------- | ------------- | ----------------------------------- |
| GET    | `/api/v1/image-collections/**`            | No            | Public                              |
| POST   | `/api/v1/image-collections/**`            | Yes           | `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`  |
| PUT    | `/api/v1/image-collections/**`            | Yes           | `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`  |
| DELETE | `/api/v1/image-collections/**`            | Yes           | `ADMIN`, `SUPER_ADMIN`              |

Authorization is provided as a Bearer JWT in the `Authorization` header for non-public routes:

```
Authorization: Bearer <jwt>
```

---

## 08 · Public API

### GET `/api/v1/image-collections`

Paginated list of image collections.

**Query parameters**

| Name   | Type    | Default | Description                |
| ------ | ------- | ------- | -------------------------- |
| `page` | integer | `0`     | Zero-based page index.     |
| `size` | integer | `20`    | Page size.                 |

**Response:** `ApiResponse<Page<Response>>` (see § 10 and § 12).

**Example**

```bash
curl -X GET "https://api.example.com/api/v1/image-collections?page=0&size=20"
```

```json
{
  "success": true,
  "message": "Image collections fetched successfully",
  "data": {
    "content": [
      {
        "id": 17,
        "collectionType": "GALLERY",
        "ckbCoverUrl": "https://cdn.example.com/img/17-ckb-cover.jpg",
        "kmrCoverUrl": "https://cdn.example.com/img/17-kmr-cover.jpg",
        "hoverCoverUrl": null,
        "topicId": 4,
        "topicNameCkb": "مێژووی هەولێر",
        "topicNameKmr": "Dîroka Hewlêrê",
        "publishmentDate": "2026-04-12",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "گەلەری وێنەکانی شاری هەولێر",
          "description": "<p>کۆمەڵەیەک لە وێنە مێژووییەکانی شاری هەولێر.</p>",
          "location": "هەولێر، کوردستان",
          "collectedBy": "تیمی نووسەرانی KHI"
        },
        "kmrContent": {
          "title": "Galeriya wêneyên bajarê Hewlêrê",
          "description": "<p>Komeleyek ji wêneyên dîrokî yên bajarê Hewlêrê.</p>",
          "location": "Hewlêr, Kurdistan",
          "collectedBy": "Tîma nivîskarên KHI"
        },
        "tags": { "ckb": ["مێژوو", "هەولێر"], "kmr": ["Dîrok", "Hewlêr"] },
        "keywords": { "ckb": ["شار", "کۆن"], "kmr": ["Bajar", "Kevn"] },
        "imageAlbum": [
          {
            "id": 88,
            "imageUrl": "https://cdn.example.com/img/88.jpg",
            "captionCkb": "قەڵای هەولێر",
            "captionKmr": "Kelaya Hewlêrê",
            "sortOrder": 0,
            "fileSizeBytes": 245760,
            "widthPx": 1920,
            "heightPx": 1080,
            "mimeType": "image/jpeg",
            "aspectRatio": 1.7777777777777777,
            "humanReadableSize": "240.0 KB"
          }
        ],
        "createdAt": "2026-04-12T10:14:33",
        "updatedAt": "2026-04-12T10:14:33"
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

---

### GET `/api/v1/image-collections/{id}`

Fetch a single image collection by id.

**Path parameters**

| Name | Type | Description           |
| ---- | ---- | --------------------- |
| `id` | Long | Image collection id.  |

**Response:** `ApiResponse<Response>` — see § 10.

**Example**

```bash
curl -X GET "https://api.example.com/api/v1/image-collections/17"
```

```json
{
  "success": true,
  "message": "Image collection fetched successfully",
  "data": {
    "id": 17,
    "collectionType": "GALLERY",
    "ckbCoverUrl": "https://cdn.example.com/img/17-ckb-cover.jpg",
    "topicId": 4,
    "topicNameCkb": "مێژووی هەولێر",
    "topicNameKmr": "Dîroka Hewlêrê",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "گەلەری وێنەکانی شاری هەولێر" },
    "imageAlbum": [],
    "createdAt": "2026-04-12T10:14:33",
    "updatedAt": "2026-04-12T10:14:33"
  }
}
```

If not found → `404 IMAGE_NOT_FOUND`.

---

### GET `/api/v1/image-collections/topics`

Returns all `PublishmentTopic` rows with `entityType = "IMAGE"`, projected to a minimal autocomplete shape. Mirrors `SoundTrackController` `/topics`.

**Response:** `ApiResponse<List<Map<String, Object>>>` — each element has `id`, `nameCkb`, `nameKmr` (empty strings, never null, when names are absent).

**Example**

```bash
curl -X GET "https://api.example.com/api/v1/image-collections/topics"
```

```json
{
  "success": true,
  "message": "IMAGE topics fetched successfully",
  "data": [
    { "id": 4, "nameCkb": "مێژووی هەولێر", "nameKmr": "Dîroka Hewlêrê" },
    { "id": 7, "nameCkb": "جلوبەرگی کوردی", "nameKmr": "Cil û bergên Kurdî" }
  ]
}
```

---

## 09 · Internal API

All write endpoints require a JWT Bearer token.

---

### POST `/api/v1/image-collections`

Create an image collection with multipart file uploads.

- **Content-Type:** `multipart/form-data`
- **Roles:** `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`
- **HTTP status on success:** `201 Created`

#### Multipart parts

| Part              | Required | Type                  | Description                                                                                                                                                       |
| ----------------- | -------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data`            | yes      | JSON string           | Serialized `CreateRequest` (see § 10).                                                                                                                            |
| `ckbCoverImage`   | no       | file                  | Sorani cover image. Uploaded to S3; sets `ckbCoverUrl`.                                                                                                           |
| `kmrCoverImage`   | no       | file                  | Kurmanji cover image. Uploaded to S3; sets `kmrCoverUrl`.                                                                                                         |
| `hoverCoverImage` | no       | file                  | Hover overlay image. Uploaded to S3; sets `hoverCoverUrl`.                                                                                                        |
| `images`          | no       | file[] (repeated)     | Album image files. Each non-empty file replaces the `imageUrl` of the corresponding `imageAlbum[i]` entry (see § 11). Metadata (width/height/size/mime) auto-extracted. |

#### Validation rules (server-side)

- `collectionType` is required.
- `contentLanguages` must be non-empty.
- Cover requirement: at least one of `ckbCoverImage` (file) or any of `ckbCoverUrl` / `kmrCoverUrl` / `hoverCoverUrl` (URL) must be present, otherwise `IMAGE_VALIDATION` with field `ckbCoverImage | ckbCoverUrl | kmrCoverUrl | hoverCoverUrl`.
- Album item count vs. type:
  - `SINGLE` → exactly 1 item.
  - `GALLERY` → at least 1 item.
  - `PHOTO_STORY` → at least 2 items.
- If `newTopic` is supplied, at least one of `nameCkb` / `nameKmr` must be non-blank.
- If `topicId` is supplied, the referenced topic must exist and have `entityType = "IMAGE"`.

#### Example

```bash
curl -X POST "https://api.example.com/api/v1/image-collections" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
    "collectionType":"GALLERY",
    "publishmentDate":"2026-05-31",
    "contentLanguages":["CKB","KMR"],
    "topicId":4,
    "ckbContent":{
      "title":"گەلەری وێنەکانی شاری هەولێر",
      "description":"<p>کۆمەڵەیەک لە وێنە مێژووییەکانی شاری هەولێر.</p>",
      "location":"هەولێر، کوردستان",
      "collectedBy":"تیمی KHI"
    },
    "kmrContent":{
      "title":"Galeriya wêneyên bajarê Hewlêrê",
      "description":"<p>Komeleyek ji wêneyên dîrokî yên bajarê Hewlêrê.</p>",
      "location":"Hewlêr, Kurdistan",
      "collectedBy":"Tîma KHI"
    },
    "tags":{"ckb":["مێژوو","هەولێر"],"kmr":["Dîrok","Hewlêr"]},
    "keywords":{"ckb":["شار","کۆن"],"kmr":["Bajar","Kevn"]},
    "imageAlbum":[
      {"captionCkb":"قەڵای هەولێر","captionKmr":"Kelaya Hewlêrê","sortOrder":0},
      {"captionCkb":"بازاڕی قەیسەری","captionKmr":"Bazara Qeyserî","sortOrder":1}
    ]
  };type=application/json' \
  -F "ckbCoverImage=@./covers/ckb-cover.jpg" \
  -F "kmrCoverImage=@./covers/kmr-cover.jpg" \
  -F "hoverCoverImage=@./covers/hover.png" \
  -F "images=@./album/img1.jpg" \
  -F "images=@./album/img2.jpg"
```

**Response (201):**

```json
{
  "success": true,
  "message": "Image collection created successfully",
  "data": {
    "id": 42,
    "collectionType": "GALLERY",
    "ckbCoverUrl": "https://s3.example.com/khi/img/42-ckb-cover.jpg",
    "kmrCoverUrl": "https://s3.example.com/khi/img/42-kmr-cover.jpg",
    "hoverCoverUrl": "https://s3.example.com/khi/img/42-hover.png",
    "topicId": 4,
    "topicNameCkb": "مێژووی هەولێر",
    "topicNameKmr": "Dîroka Hewlêrê",
    "publishmentDate": "2026-05-31",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title": "گەلەری وێنەکانی شاری هەولێر",
      "description": "<p>کۆمەڵەیەک لە وێنە مێژووییەکانی شاری هەولێر.</p>",
      "location": "هەولێر، کوردستان",
      "collectedBy": "تیمی KHI"
    },
    "kmrContent": {
      "title": "Galeriya wêneyên bajarê Hewlêrê",
      "description": "<p>Komeleyek ji wêneyên dîrokî yên bajarê Hewlêrê.</p>",
      "location": "Hewlêr, Kurdistan",
      "collectedBy": "Tîma KHI"
    },
    "tags": { "ckb": ["مێژوو", "هەولێر"], "kmr": ["Dîrok", "Hewlêr"] },
    "keywords": { "ckb": ["شار", "کۆن"], "kmr": ["Bajar", "Kevn"] },
    "imageAlbum": [
      {
        "id": 101,
        "imageUrl": "https://s3.example.com/khi/img/album/101.jpg",
        "captionCkb": "قەڵای هەولێر",
        "captionKmr": "Kelaya Hewlêrê",
        "sortOrder": 0,
        "fileSizeBytes": 524288,
        "widthPx": 2048,
        "heightPx": 1365,
        "mimeType": "image/jpeg",
        "aspectRatio": 1.5003663003663004,
        "humanReadableSize": "512.0 KB"
      },
      {
        "id": 102,
        "imageUrl": "https://s3.example.com/khi/img/album/102.jpg",
        "captionCkb": "بازاڕی قەیسەری",
        "captionKmr": "Bazara Qeyserî",
        "sortOrder": 1,
        "fileSizeBytes": 314572,
        "widthPx": 1600,
        "heightPx": 1067,
        "mimeType": "image/jpeg",
        "aspectRatio": 1.4995313964386117,
        "humanReadableSize": "307.2 KB"
      }
    ],
    "createdAt": "2026-05-31T11:02:18",
    "updatedAt": "2026-05-31T11:02:18"
  }
}
```

---

### POST `/api/v1/image-collections/json`

Create an image collection from URL-only sources (no file uploads). Internally calls the same `service.create(...)` method with all multipart args set to `null`.

- **Content-Type:** `application/json`
- **Roles:** `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`
- **HTTP status on success:** `201 Created`

#### Body

`CreateRequest` (see § 10), validated with `@Valid`.

#### Example

```bash
curl -X POST "https://api.example.com/api/v1/image-collections/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionType": "SINGLE",
    "publishmentDate": "2026-05-31",
    "contentLanguages": ["CKB"],
    "ckbCoverUrl": "https://cdn.example.com/cover-ckb.jpg",
    "newTopic": { "nameCkb": "وێنە مێژووییەکان", "nameKmr": "Wêneyên dîrokî" },
    "ckbContent": {
      "title": "وێنەی مزگەوتی گەورەی هەولێر",
      "description": "<p>وێنەیەکی کۆن لە ١٩٣٠ەکانەوە.</p>",
      "location": "هەولێر",
      "collectedBy": "ئەرشیفی شار"
    },
    "tags": { "ckb": ["مزگەوت", "هەولێر"] },
    "imageAlbum": [
      {
        "externalUrl": "https://example.com/mosque.jpg",
        "captionCkb": "مزگەوتی گەورە",
        "sortOrder": 0
      }
    ]
  }'
```

**Response:** identical envelope to multipart create (`201 Created`, `ApiResponse<Response>`).

---

### PUT `/api/v1/image-collections/{id}`

Update an image collection. Multipart-only — there is no JSON-only update endpoint.

- **Content-Type:** `multipart/form-data`
- **Roles:** `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN`
- **HTTP status on success:** `200 OK`

#### Path parameters

| Name | Type | Description          |
| ---- | ---- | -------------------- |
| `id` | Long | Image collection id. |

#### Multipart parts

| Part              | Required | Type              | Description                                                                                                                                                                                                  |
| ----------------- | -------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `data`            | yes      | JSON string       | Serialized `UpdateRequest` (see § 10). Use `clearTopic=true` to detach the current topic.                                                                                                                     |
| `ckbCoverImage`   | no       | file              | Replaces `ckbCoverUrl` when present; otherwise `ckbCoverUrl` in `data` is honoured.                                                                                                                          |
| `kmrCoverImage`   | no       | file              | Same rule for `kmrCoverUrl`.                                                                                                                                                                                  |
| `hoverCoverImage` | no       | file              | Same rule for `hoverCoverUrl`.                                                                                                                                                                                |
| `images`          | no       | file[] (repeated) | Album files. If `images` has any non-empty file OR `imageAlbum` is non-null in `data`, the existing album is **replaced entirely** with the newly built items.                                               |

#### Partial update semantics

- Only fields explicitly present in `data` (non-null / non-blank) are changed.
- `contentLanguages`: `null` keeps existing; non-null replaces.
- `tags` / `keywords`: each sub-set (`ckb` or `kmr`) is cleared and replaced only if non-null.
- Album: `imageAlbum == null && no file uploads` → keep existing. Otherwise → replace entirely.
- Topic resolution:
  - `clearTopic = true` → topic detached (set to `null`).
  - else if `topicId != null` or `newTopic != null` → resolve / create.
  - else → keep existing.

#### Example

```bash
curl -X PUT "https://api.example.com/api/v1/image-collections/42" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={
    "collectionType":"GALLERY",
    "publishmentDate":"2026-06-01",
    "ckbContent":{"title":"گەلەری نوێی هەولێر"},
    "clearTopic":false,
    "topicId":7,
    "imageAlbum":[
      {"captionCkb":"وێنەی نوێ","sortOrder":0}
    ]
  };type=application/json' \
  -F "ckbCoverImage=@./covers/new-ckb.jpg" \
  -F "images=@./album/new.jpg"
```

**Response:** `200 OK` with `ApiResponse<Response>` (same shape as create).

---

### DELETE `/api/v1/image-collections/{id}`

Delete an image collection (cascades to `imageAlbum` via `orphanRemoval = true`).

- **Roles:** `ADMIN`, `SUPER_ADMIN`
- **HTTP status on success:** `200 OK`

```bash
curl -X DELETE "https://api.example.com/api/v1/image-collections/42" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "success": true,
  "message": "Image collection deleted successfully",
  "data": null
}
```

---

## 10 · DTO Reference

All DTOs live in `ImageCollectionDTO` (a final container class).

### `ImageCollectionDTO.LanguageContentDto`

| Field         | Type     | Validation | Notes                  |
| ------------- | -------- | ---------- | ---------------------- |
| `title`       | `String` | —          | Plain text.            |
| `description` | `String` | —          | Tiptap HTML processed. |
| `topic`       | `String` | —          | Free-text topic name.  |
| `location`    | `String` | —          | Plain text.            |
| `collectedBy` | `String` | —          | Plain text.            |

### `ImageCollectionDTO.BilingualSet`

| Field | Type          | Notes                |
| ----- | ------------- | -------------------- |
| `ckb` | `Set<String>` | CKB tags / keywords. |
| `kmr` | `Set<String>` | KMR tags / keywords. |

### `ImageCollectionDTO.InlineTopicRequest`

`@JsonInclude(NON_NULL)`

| Field     | Type     | Validation | Notes                                                       |
| --------- | -------- | ---------- | ----------------------------------------------------------- |
| `nameCkb` | `String` | —          | At least one of CKB / KMR must be non-blank (service-level). |
| `nameKmr` | `String` | —          |                                                             |

### `ImageCollectionDTO.ImageItemDto`

`@JsonInclude(NON_NULL)`

| Field               | Type      | Validation | Notes                                                                   |
| ------------------- | --------- | ---------- | ----------------------------------------------------------------------- |
| `id`                | `Long`    | —          | Response-only; not sent by frontend on create/update.                   |
| `imageUrl`          | `String`  | —          | Direct S3 / CDN URL (used when no upload file substitutes).             |
| `externalUrl`       | `String`  | —          | External page link.                                                     |
| `embedUrl`          | `String`  | —          | Iframe-ready embed URL.                                                 |
| `captionCkb`        | `String`  | —          | CKB caption.                                                            |
| `captionKmr`        | `String`  | —          | KMR caption.                                                            |
| `descriptionCkb`    | `String`  | —          | Tiptap HTML processed on persist.                                       |
| `descriptionKmr`    | `String`  | —          | Tiptap HTML processed on persist.                                       |
| `sortOrder`         | `Integer` | —          | 0-based; if omitted, service uses position index.                       |
| `fileSizeBytes`     | `Long`    | —          | Response-only (auto-extracted on upload).                               |
| `widthPx`           | `Integer` | —          | Response-only (auto-extracted on upload).                               |
| `heightPx`          | `Integer` | —          | Response-only (auto-extracted on upload).                               |
| `mimeType`          | `String`  | —          | Response-only (auto-extracted on upload).                               |
| `aspectRatio`       | `Double`  | —          | Response-only, calculated.                                              |
| `humanReadableSize` | `String`  | —          | Response-only, formatted (`"2.4 MB"`, `"850 KB"`, etc.).                |

### `ImageCollectionDTO.CreateRequest`

| Field              | Type                       | Validation                                                          | Notes                                                                        |
| ------------------ | -------------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `collectionType`   | `ImageCollectionType`      | `@NotNull(message = "collectionType is required")`                  | Enum.                                                                        |
| `ckbCoverUrl`      | `String`                   | —                                                                   | Fallback if no `ckbCoverImage` file uploaded.                                |
| `kmrCoverUrl`      | `String`                   | —                                                                   | Fallback if no `kmrCoverImage` file uploaded.                                |
| `hoverCoverUrl`    | `String`                   | —                                                                   | Fallback if no `hoverCoverImage` file uploaded.                              |
| `topicId`          | `Long`                     | —                                                                   | Must reference an existing `IMAGE` topic.                                    |
| `newTopic`         | `InlineTopicRequest`       | —                                                                   | Mutually exclusive with `topicId`; creates a new IMAGE topic.                |
| `publishmentDate`  | `LocalDate`                | —                                                                   | Optional.                                                                    |
| `contentLanguages` | `Set<Language>`            | `@NotNull(message = "At least one content language is required")`   | Service additionally rejects empty sets.                                     |
| `ckbContent`       | `LanguageContentDto`       | —                                                                   | Persisted only if `CKB` is in `contentLanguages`.                            |
| `kmrContent`       | `LanguageContentDto`       | —                                                                   | Persisted only if `KMR` is in `contentLanguages`.                            |
| `tags`             | `BilingualSet`             | —                                                                   | Bilingual.                                                                   |
| `keywords`         | `BilingualSet`             | —                                                                   | Bilingual.                                                                   |
| `imageAlbum`       | `List<ImageItemDto>`       | —                                                                   | URL-source album items. Combined with `images` files (see § 11).             |

### `ImageCollectionDTO.UpdateRequest`

| Field              | Type                       | Validation | Notes                                                                                |
| ------------------ | -------------------------- | ---------- | ------------------------------------------------------------------------------------ |
| `collectionType`   | `ImageCollectionType`      | —          | Optional; null keeps existing.                                                       |
| `ckbCoverUrl`      | `String`                   | —          | Honoured only if no `ckbCoverImage` file present.                                    |
| `kmrCoverUrl`      | `String`                   | —          | Same rule.                                                                           |
| `hoverCoverUrl`    | `String`                   | —          | Same rule.                                                                           |
| `topicId`          | `Long`                     | —          | Resolve and attach an existing IMAGE topic.                                          |
| `newTopic`         | `InlineTopicRequest`       | —          | Inline create.                                                                       |
| `clearTopic`       | `boolean`                  | —          | When `true`, detaches current topic (overrides `topicId` / `newTopic`).              |
| `publishmentDate`  | `LocalDate`                | —          | Optional.                                                                            |
| `contentLanguages` | `Set<Language>`            | —          | `null` keeps existing; non-null replaces.                                            |
| `ckbContent`       | `LanguageContentDto`       | —          | Persisted only if CKB is in (merged) languages.                                      |
| `kmrContent`       | `LanguageContentDto`       | —          | Persisted only if KMR is in (merged) languages.                                      |
| `tags`             | `BilingualSet`             | —          | Per-language replacement when sub-set is non-null.                                   |
| `keywords`         | `BilingualSet`             | —          | Per-language replacement when sub-set is non-null.                                   |
| `imageAlbum`       | `List<ImageItemDto>`       | —          | `null` AND no file uploads → keep existing; otherwise album replaced entirely.       |

### `ImageCollectionDTO.Response`

`@JsonInclude(NON_NULL)`

| Field              | Type                       | Notes                                                                |
| ------------------ | -------------------------- | -------------------------------------------------------------------- |
| `id`               | `Long`                     | Server-assigned id.                                                  |
| `collectionType`   | `ImageCollectionType`      |                                                                      |
| `ckbCoverUrl`      | `String`                   | Resolved S3 / CDN URL.                                               |
| `kmrCoverUrl`      | `String`                   |                                                                      |
| `hoverCoverUrl`    | `String`                   |                                                                      |
| `topicId`          | `Long`                     | `null` if no topic attached.                                         |
| `topicNameCkb`     | `String`                   |                                                                      |
| `topicNameKmr`     | `String`                   |                                                                      |
| `publishmentDate`  | `LocalDate`                |                                                                      |
| `contentLanguages` | `Set<Language>`            | Copied into a plain `LinkedHashSet` to avoid `LazyInitException`.    |
| `ckbContent`       | `LanguageContentDto`       | `null` if no CKB content.                                            |
| `kmrContent`       | `LanguageContentDto`       | `null` if no KMR content.                                            |
| `tags`             | `BilingualSet`             | Always present (sub-sets may be empty).                              |
| `keywords`         | `BilingualSet`             | Always present.                                                      |
| `imageAlbum`       | `List<ImageItemDto>`       | Sorted by `sortOrder`; includes auto metadata + calculated fields.   |
| `createdAt`        | `LocalDateTime`            |                                                                      |
| `updatedAt`        | `LocalDateTime`            |                                                                      |

---

## 11 · Multipart Layout

### Form-data → DTO mapping

| Form-data part    | Repeated | Maps to / Effect                                                                                                |
| ----------------- | -------- | --------------------------------------------------------------------------------------------------------------- |
| `data`            | no       | JSON body deserialized into `CreateRequest` / `UpdateRequest`.                                                  |
| `ckbCoverImage`   | no       | Single file. Uploaded to S3; sets `ckbCoverUrl`. Takes precedence over `data.ckbCoverUrl`.                      |
| `kmrCoverImage`   | no       | Single file. Same rule for `kmrCoverUrl`.                                                                       |
| `hoverCoverImage` | no       | Single file. Same rule for `hoverCoverUrl`.                                                                     |
| `images`          | yes      | Album files. Each non-empty file substitutes the `imageUrl` of the corresponding `imageAlbum[i]` slot.          |

### `images` ↔ `imageAlbum` pairing rule

The service builds `max = max(fileCount, dtoCount)` album items. For each position `i`:

1. If the next non-empty multipart file is available, it is uploaded → `imageUrl` is set on slot `i`; `externalUrl` and `embedUrl` are cleared; metadata is auto-extracted.
2. Otherwise the `imageAlbum[i]` DTO must supply at least one of `imageUrl` / `externalUrl` / `embedUrl`, otherwise validation fails with `image.source.required`.

Caption / description / `sortOrder` always come from the DTO slot (`imageAlbum[i]`), regardless of whether a file is uploaded for that slot.

Album item count is then validated against `collectionType`:

| Type          | Required count |
| ------------- | -------------- |
| `SINGLE`      | exactly 1      |
| `GALLERY`     | ≥ 1            |
| `PHOTO_STORY` | ≥ 2            |

---

## 12 · Response Envelope

All endpoints return `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Image collection created successfully",
  "data": { /* T */ }
}
```

| Field     | Type      | Notes                              |
| --------- | --------- | ---------------------------------- |
| `success` | `boolean` | `true` for 2xx responses.          |
| `message` | `string`  | Human-readable status message.     |
| `data`    | `T`       | Payload; may be `null` for delete. |

### Spring Data `Page<T>` shape

```json
{
  "content": [ /* T[] */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "empty": true, "sorted": false, "unsorted": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "number": 0,
  "numberOfElements": 1,
  "size": 20,
  "sort": { "empty": true, "sorted": false, "unsorted": true },
  "empty": false
}
```

---

## 13 · Error Responses

Errors use the bilingual `ApiErrorResponse` envelope. Schematic shape:

```json
{
  "success": false,
  "code": "IMAGE_VALIDATION",
  "messageCkb": "...",
  "messageKmr": "...",
  "details": { "field": "..." },
  "status": 400,
  "timestamp": "2026-05-31T11:02:18Z",
  "path": "/api/v1/image-collections"
}
```

### Common codes raised by this module

| Code                | HTTP | Where it originates                                                                                                                                                  |
| ------------------- | ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `IMAGE_NOT_FOUND`   | 404  | `Errors.imageNotFound(id)` from `getById` / `update` / `delete`.                                                                                                     |
| `IMAGE_VALIDATION`  | 400  | `validateCreate` (cover / type / languages), album count rules, topic checks (`topic.type.mismatch`), missing image source (`image.source.required`), missing `id`.   |
| `IMAGE_CONFLICT`    | 409  | Reserved for state conflicts.                                                                                                                                        |
| `IMAGE_MEDIA_INVALID` | 415 / 422 | Bad media / unsupported format paths.                                                                                                                            |
| `image.media_upload_failed` | 500 | `Errors.imageStorageFailed(...)` wrapping `IOException` from S3 upload (in create / update).                                                                  |
| `PAYLOAD_TOO_LARGE` | 413  | Cover or album file exceeds configured multipart size limit.                                                                                                         |
| `VALIDATION_ERROR`  | 400  | Bean-validation failures on JSON create (e.g. `@NotNull collectionType`, `@NotNull contentLanguages`).                                                                |
| `UNAUTHORIZED`      | 401  | Missing / invalid JWT on a write endpoint.                                                                                                                           |
| `FORBIDDEN`         | 403  | Authenticated but role insufficient (e.g. `EMPLOYEE` calling `DELETE`).                                                                                              |

### Example — validation (cover missing)

```json
{
  "success": false,
  "code": "IMAGE_VALIDATION",
  "messageCkb": "بەرگی وێنە پێویستە",
  "messageKmr": "Bergê wêneyê pêwîst e",
  "details": {
    "field": "ckbCoverImage | ckbCoverUrl | kmrCoverUrl | hoverCoverUrl"
  },
  "status": 400,
  "path": "/api/v1/image-collections"
}
```

### Example — not found

```json
{
  "success": false,
  "code": "IMAGE_NOT_FOUND",
  "messageCkb": "کۆمەڵەی وێنە نەدۆزرایەوە",
  "messageKmr": "Komeleya wêneyê nehat dîtin",
  "details": { "id": 42 },
  "status": 404,
  "path": "/api/v1/image-collections/42"
}
```

---

## 14 · Notes

- The two create paths (`POST /` multipart, `POST /json`) both call the same `ImageCollectionService.create(...)` method — the JSON endpoint simply passes `null` for all four multipart arguments.
- The update path is **multipart-only**. There is no `PUT /api/v1/image-collections/{id}/json`.
- The `/topics` endpoint mirrors `SoundTrackController`'s `/topics` and is sourced from `PublishmentTopicRepository.findByEntityType("IMAGE")`. Topics for other publishment types (`VIDEO`, `SOUND`, `WRITING`) are stored in the same table but filtered out by `entityType`.
- Tiptap-rich description fields (`LanguageContentDto.description`, `ImageItemDto.descriptionCkb`, `ImageItemDto.descriptionKmr`) are passed through `TiptapHtmlProcessor.process(...)` before persistence — embedded images / sanitization rules of that processor apply.
- Album item metadata (`widthPx`, `heightPx`, `fileSizeBytes`, `mimeType`) is auto-extracted only for **uploaded multipart files**. External / embed URLs receive `null` for all four fields; `aspectRatio` and `humanReadableSize` are calculated on the fly in the response.
- Album items are sorted in the response by `sortOrder ASC` (matches the `@OrderBy` on the entity).
- All collections on `ImageCollection` (languages, tags, keywords, album, topic) use `LAZY` fetch + `@BatchSize(50)` to avoid Cartesian explosion on paginated reads. The service uses a two-phase id-page → hydrate strategy.
- Write operations evict the entire `imageCollections` cache (`@CacheEvict(allEntries = true)`).
- Audit logs are best-effort: a failure inside `createLog(...)` is logged as a warning but does not abort the request.
- The `Response` DTO is `@JsonInclude(NON_NULL)`, so absent fields (e.g. `kmrContent` when only CKB is present) are omitted from the JSON payload.
