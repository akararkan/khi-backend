# KHI Backend — Tiptap & Media Pipeline (Complete Reference, All Entities)

> **Status:** Applied · `./mvnw compile` passes cleanly.
> **Stack:** Spring Boot 3 · Java 21 · PostgreSQL · AWS S3 · JPA/Hibernate

This is the single source of truth for how every public-facing module
stores its rich-text content and the media that lives inside it.

## 0 · The big rule

**About, Contact, and Service no longer accept standalone media uploads.**

Every visual asset that used to be a hero image / hero video / hero audio
/ cover image / gallery item / per-file media row on those three entities
is now embedded **inline inside the bilingual Tiptap description HTML** of
the same row, as one of:

- `<img src="https://…s3…">`
- `<video controls src="…">` / `<video><source src="…"></video>`
- `<audio controls src="…">` (voice memos, music, podcasts)
- `<a href="https://…s3…">file.pdf</a>` (PDFs, DOCX, ZIP — any "other file")

`TiptapHtmlProcessor.process(html)` is called inside every service that
builds or merges a description / body. It scans both `src=` and `href=`
data URIs, uploads decoded payloads to S3, and rewrites the attribute —
so the database only ever stores S3 URLs, never raw binaries.

News and Project still keep their separate `coverUrl` / `coverMediaType`
/ `coverThumbnailUrl` / `mediaGallery` because their public listing card
needs an out-of-body asset. Sound, Video, Image Collection, and Writing
keep their dedicated cover URL fields and (where applicable) structured
file-row tables for cataloguing technical metadata (codec, bitrate,
duration, …). In every module, inline body / description media still
flows through `TiptapHtmlProcessor`.

| Kind                     | Tag the editor emits                              | S3 folder  |
| ------------------------ | ------------------------------------------------- | ---------- |
| Image                    | `<img src=…>`                                     | `images/`  |
| Video                    | `<video src=…>` / `<source src=…>`                | `video/`   |
| Voice / audio            | `<audio src=…>` / `<source src=…>`                | `audio/`   |
| Document / other file    | `<a href=…>` (PDF, DOCX, XLSX, ZIP, TXT, …)       | `files/`   |

---

## 1 · Shared media upload — `POST /api/v1/media/upload`

A single neutral endpoint used by **every** Tiptap editor across the
platform. The frontend uploads each file once, then bakes the returned
`fileUrl` into the editor HTML.

### Request — `multipart/form-data`

| Part   | Type   | Required | Description |
| ------ | ------ | -------- | ----------- |
| `file` | File   | Yes      | Any media file (image / video / audio / document / other). |
| `type` | String | No       | Folder hint: `image`, `audio`, `video`, `document`, `gallery`. Default `image`. |

### Response — `200 OK`

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "fileUrl": "https://<bucket>.s3.<region>.amazonaws.com/khi-web-folders/images/<uuid>-photo.jpg",
    "fileName": "photo.jpg",
    "fileSize": 204800,
    "contentType": "image/jpeg"
  }
}
```

### Bulk variant — `POST /api/v1/media/upload/multiple`

Same shape, multipart part `files` is a list.

### Cleanup — `DELETE /api/v1/media?fileUrl=…`

Removes a previously uploaded asset from S3.

### S3 layout

```
<bucket>/<baseFolder=khi-web-folders>/
    images/<uuid>-<sanitized-name>
    video/<uuid>-<sanitized-name>
    audio/<uuid>-<sanitized-name>
    files/<uuid>-<sanitized-name>
```

| Layer      | File |
| ---------- | --- |
| Controller | `khi_app/api/media/MediaController.java` |
| Service    | `khi_app/service/media/MediaService.java` |
| DTOs       | `khi_app/dto/media/MediaDtos.java` |

---

## 2 · The base64 → S3 rewriter — `TiptapHtmlProcessor`

`src/main/java/ak/dev/khi_backend/khi_app/service/media/TiptapHtmlProcessor.java`

The frontend **should** call `/api/v1/media/upload` and paste the returned
URL into the editor before submit — but it doesn't always. When a Tiptap
user drag-drops, pastes, or screenshot-attaches a file, the editor emits a
`data:` URI directly. The processor catches those and rewrites them.

### What it scans

Two regular expressions, run sequentially over the inbound HTML:

| Pattern         | Matches on                                       | Used for                          |
| --------------- | ------------------------------------------------ | --------------------------------- |
| `src="data:…"`  | `<img>`, `<video>`, `<audio>`, `<source>`        | Image / video / voice payloads    |
| `href="data:…"` | `<a>`                                            | Documents and any "other file"    |

Single or double quotes both work; the rewriter preserves the original
quote style.

### For each match

1. Base64-decodes the payload.
2. Picks an S3 folder from the MIME type:
   `image/* → images/`, `video/* → video/`, `audio/* → audio/`,
   anything else → `files/`.
3. Uploads via `S3Service.upload(bytes, filename, mime, ProjectMediaType)`.
4. Replaces the matched attribute with `src="https://…s3…"` (or
   `href="https://…s3…"`).

### Guarantees

- **Idempotent** — HTML already containing only S3 URLs is returned
  unchanged (early-out on `!html.contains("data:")`).
- **Resilient** — a malformed base64 payload or an S3 failure on one
  asset is logged and the original attribute is left in place; the save
  succeeds for the rest of the document.
- **Null-safe / blank-safe** — `null` and empty strings pass through.
- **MIME → extension table** maps `jpeg`, `png`, `gif`, `webp`, `svg`,
  `bmp`, `avif`, `mp4`, `webm`, `mov`, `mp3`, `wav`, `ogg`, `weba`,
  `aac`, `flac`, `m4a`, `pdf`, `doc`, `docx`, `xls`, `xlsx`, `ppt`,
  `pptx`, `zip`, `rar`, `7z`, `txt`, `csv`, `json`. Anything else falls
  back to the substring after `/`.

### Recommended Tiptap extensions

| Extension                     | What it produces                                       |
| ----------------------------- | ------------------------------------------------------ |
| `@tiptap/extension-image`     | `<img src=…>` (paste, drag-drop)                       |
| custom `Video` node           | `<video controls src=…>` or `<video><source …></video>` |
| custom `Audio` node           | `<audio controls src=…>` (voice memos, music, podcasts) |
| custom `FileLink` node        | `<a href=… download>name.ext</a>` (PDFs, docs, ZIPs)   |
| `@tiptap/extension-link`      | external links                                         |
| `@tiptap/extension-table`     | inline tables (optional)                               |

### Where it runs

| Module           | Field(s) processed                                                   |
| ---------------- | -------------------------------------------------------------------- |
| About            | `ckbContent.body`, `kmrContent.body`                                 |
| Contact          | `ckbContent.description`, `kmrContent.description`                   |
| Service          | each `serviceContent.description` (CKB / KMR)                        |
| News             | `ckbContent.description`, `kmrContent.description`                   |
| Projects         | `ckbContent.description`, `kmrContent.description`                   |
| Sound            | `soundTrackContent.description` (CKB / KMR)                          |
| Video            | `videoContent.description` + every `VideoClipItem.descriptionCkb/Kmr` |
| Image Collection | `imageContent.description` + every `ImageAlbumItem.descriptionCkb/Kmr` |
| Writing          | `writingContent.description` (CKB / KMR)                             |

GET paths do **not** re-process — the stored HTML is already clean.

---

## 3 · About module — entirely Tiptap-driven

About is the simplest media model in the codebase: there is no media
model. Everything lives in the bilingual Tiptap body.

### Schema — `about_pages`

| Column                          | Type             | Notes                                  |
| ------------------------------- | ---------------- | -------------------------------------- |
| `id`                            | BIGSERIAL        | PK                                     |
| `slug_ckb`                      | VARCHAR(200)     | unique, required                       |
| `slug_kmr`                      | VARCHAR(200)     | unique, nullable                       |
| `active`                        | BOOLEAN          | default `true`                         |
| `display_order`                 | INT              | default `0`                            |
| `title_ckb` / `_kmr`            | VARCHAR(300)     | per-language title                     |
| `subtitle_ckb` / `_kmr`         | VARCHAR(500)     | per-language subtitle                  |
| `meta_description_ckb` / `_kmr` | VARCHAR(2500)    | per-language meta description          |
| `body_ckb`                      | TEXT             | **Tiptap HTML — the only media path**  |
| `body_kmr`                      | TEXT             | **Tiptap HTML — the only media path**  |
| `stats`                         | JSONB            | structured stats array                 |
| `created_at` / `updated_at`     | TIMESTAMP        |                                        |

**Removed columns**: `hero_image_url`, `hero_media_type`,
`hero_thumbnail_url`, `media_gallery`.

### Entity — `About`

`khi_app/model/about/About.java`

```
About
├─ slugCkb / slugKmr
├─ active, displayOrder
├─ ckbContent : AboutContent { title, subtitle, metaDescription, body }
├─ kmrContent : AboutContent { title, subtitle, metaDescription, body }
├─ stats      : List<StatItem>   ← JSONB
└─ createdAt / updatedAt
```

Embedded `StatItem`: `{ labelCkb, labelKmr, value }`.

### DTOs — `AboutDTOs`

- `AboutRequest`         — `slugCkb`, `slugKmr`, `ckbContent`, `kmrContent`, `stats`.
- `AboutContentRequest`  — `title`, `subtitle`, `metaDescription`, `body` (Tiptap HTML).
- `StatItemDto`          — `labelCkb`, `labelKmr`, `value`.
- `AboutResponse`        — same fields back + `id`, `active`, `createdAt`, `updatedAt`.
- `AboutContentResponse` — same as request side.

### Endpoints — `/api/v1/about`

| Method | Path             | Notes                       | Content-Type |
| ------ | ---------------- | --------------------------- | ------------ |
| GET    | `/`              | All active.                 | —            |
| GET    | `/{slug}`        | By slug (CKB or KMR).       | —            |
| POST   | `/`              | Create.                     | JSON         |
| PUT    | `/{id}`          | Update.                     | JSON         |
| DELETE | `/{id}`          | Hard delete.                | —            |

### Tiptap rewrite hook

`AboutService.buildAboutContent(AboutContentRequest)` calls
`tiptapHtmlProcessor.process(req.getBody())` on both create and update,
for both languages.

`AboutService` injects only `AboutRepository` and `TiptapHtmlProcessor`.
No `S3Service`, no gallery code, no hero cleanup.

---

## 4 · Contact module — gained a Tiptap description

Previously, Contact had only structured fields. The contact page now also
carries a bilingual Tiptap **description** per language, which is the
**only** place media is attached.

### Schema — `contact_pages`

| Column                          | Type           | Notes                                  |
| ------------------------------- | -------------- | -------------------------------------- |
| `id`                            | BIGSERIAL      | PK                                     |
| `slug_ckb`                      | VARCHAR(200)   | unique, required                       |
| `slug_kmr`                      | VARCHAR(200)   | unique, nullable                       |
| `active`                        | BOOLEAN        | default `true`                         |
| `display_order`                 | INT            | default `0`                            |
| `title_ckb` / `_kmr`            | VARCHAR(300)   |                                        |
| `subtitle_ckb` / `_kmr`         | VARCHAR(500)   |                                        |
| `address_ckb` / `_kmr`          | VARCHAR(500)   |                                        |
| `working_hours_ckb` / `_kmr`    | VARCHAR(300)   |                                        |
| `description_ckb`               | TEXT           | **Tiptap HTML — the only media path**  |
| `description_kmr`               | TEXT           | **Tiptap HTML — the only media path**  |
| `phone`, `secondary_phone`      | VARCHAR(60)    |                                        |
| `email`                         | VARCHAR(200)   |                                        |
| `map_embed_url`                 | TEXT           |                                        |
| `latitude`, `longitude`         | DOUBLE         |                                        |
| `created_at` / `updated_at`     | TIMESTAMP      |                                        |

**Added columns**: `description_ckb`, `description_kmr`.
**Removed columns**: `hero_image_url`, `hero_media_type`,
`hero_thumbnail_url`, `media_gallery`.

### Entity — `Contact`

```
Contact
├─ slugCkb / slugKmr
├─ ckbContent : ContactContent { title, subtitle, address, workingHours, description }
├─ kmrContent : ContactContent { title, subtitle, address, workingHours, description }
└─ phone, secondaryPhone, email, mapEmbedUrl, latitude, longitude
```

### DTOs — `ContactDTOs`

- `ContactRequest`        — slugs, bilingual content, contact details.
- `ContactContentRequest` — `title`, `subtitle`, `address`, `workingHours`, `description`.
- `ContactResponse`       — same fields back + `id`, `active`, `createdAt`, `updatedAt`.
- `ContactContentResponse` — mirrors request side.

### Endpoints — `/api/v1/contact`

| Method | Path                | Notes                                  | Content-Type |
| ------ | ------------------- | -------------------------------------- | ------------ |
| GET    | `/`                 | All (admin — includes inactive).       | —            |
| GET    | `/active`           | All active.                            | —            |
| GET    | `/{id}`             | By id.                                 | —            |
| GET    | `/slug/{slug}`      | By slug (CKB or KMR).                  | —            |
| POST   | `/`                 | Create.                                | JSON         |
| PUT    | `/{id}`             | Update.                                | JSON         |
| DELETE | `/{id}`             | Hard delete.                           | —            |

### Tiptap rewrite hook

`ContactService.buildContent(ContactContentRequest)` calls
`tiptapHtmlProcessor.process(req.getDescription())` on both create and
update, for both languages.

---

## 5 · Service module — single description path

Service keeps its separate-row bilingual content model (`service_contents`
joined by `language_code`), but the entire normalised media model
(`service_media_collections` + `service_media_files`) has been
**removed**.

### Schema

**`services`**

| Column                   | Type           | Notes                              |
| ------------------------ | -------------- | ---------------------------------- |
| `id`                     | BIGSERIAL      | PK                                 |
| `service_type`           | VARCHAR(100)   | required                           |
| `location`               | VARCHAR(200)   | nullable                           |
| `active`                 | BOOLEAN        | default `true`                     |
| `published_at`           | TIMESTAMP      | nullable                           |
| `created_at` / `_at`     | TIMESTAMP      |                                    |

**`service_contents`** (joined by `service_id`)

| Column            | Type          | Notes                                  |
| ----------------- | ------------- | -------------------------------------- |
| `id`              | BIGSERIAL     | PK                                     |
| `language_code`   | VARCHAR(10)   | `CKB` or `KMR`. Unique per `service_id`. |
| `title`           | VARCHAR(300)  | required                               |
| `description`     | TEXT          | **Tiptap HTML — the only media path**  |
| `service_id`      | BIGINT        | FK                                     |

**`service_audit_logs`** — unchanged audit history.

**Removed columns**: `services.cover_media_url`, `services.cover_media_type`,
`services.cover_thumbnail_url`.
**Dropped tables**: `service_media_collections`, `service_media_files`.

### Entity — `Service`

```
Service
├─ serviceType, location, active, publishedAt
└─ contents : Set<ServiceContent>
                ├─ languageCode ("CKB" | "KMR")
                ├─ title
                └─ description  ← Tiptap HTML
```

**Deleted classes** (removed from the repo):

- `model/service/ServiceMediaCollection.java`
- `model/service/ServiceMediaFile.java`
- `model/service/ServiceMediaFileContent.java`
- `repository/service/ServiceMediaCollectionRepository.java`
- `repository/service/ServiceMediaFileRepository.java`

### DTOs — `ServiceDTOs`

- `ServiceRequest`         — `serviceType`, `location`, `publishedAt`, `contents`.
- `ServiceContentRequest`  — `languageCode`, `title`, `description` (Tiptap HTML).
- `ServiceResponse`        — adds `id`, `active`, timestamps, `contents[]`.
- `ServiceContentResponse` — adds `id`.

**Removed DTOs**: `ServiceMediaCollectionRequest/Response`,
`ServiceMediaFileRequest/Response`, `FileContentRequest/Response`,
`CollectionUpsertRequest`, `FileAddRequest`, `UploadResponse`.

### Endpoints — `/api/v1/services`

| Method | Path                                   | Notes                            | Content-Type |
| ------ | -------------------------------------- | -------------------------------- | ------------ |
| GET    | `/`                                    | Active, paginated.               | —            |
| GET    | `/?type={t}`                           | Filter active by type.           | —            |
| GET    | `/all`                                 | Admin: all, paginated.           | —            |
| GET    | `/{id}`                                | Single service (full detail).    | —            |
| GET    | `/types`                               | Distinct service types.          | —            |
| GET    | `/search?q=…`                          | Global search.                   | —            |
| GET    | `/search/admin?q=…`                    | Admin search.                    | —            |
| POST   | `/`                                    | Create.                          | JSON         |
| PUT    | `/{id}`                                | Update.                          | JSON         |
| PATCH  | `/{id}/active?value=…`                 | Soft toggle.                     | —            |
| DELETE | `/{id}`                                | Hard delete.                     | —            |
| DELETE | `/bulk`                                | Bulk delete.                     | JSON (ids)   |

**Removed endpoints**: `with-files`, `/collections/*`, `/files/*`,
`/upload`, `/upload/multiple`, `DELETE /upload`.

### Tiptap rewrite hook

`ServiceService.buildContent(ServiceContentRequest)` calls
`tiptapHtmlProcessor.process(req.getDescription())` on every create and
update, for every language row.

`ServiceService` injects only `ServiceRepository`,
`ServiceAuditLogRepository`, `TiptapHtmlProcessor`.

---

## 6 · News module — cover + gallery + Tiptap

News keeps a standalone `coverUrl` / `coverMediaType` /
`coverThumbnailUrl` / `mediaGallery` because the public listing card
needs an out-of-body asset. Inside the body, media flows through
`TiptapHtmlProcessor`.

### Schema

**`news`**

| Column                  | Type             | Notes                                  |
| ----------------------- | ---------------- | -------------------------------------- |
| `id`                    | BIGSERIAL        | PK                                     |
| `cover_url`             | VARCHAR(1024)    | card cover URL (image/video/audio)     |
| `cover_media_type`      | VARCHAR(16)      | `MediaKind` enum, default `IMAGE`      |
| `cover_thumbnail_url`   | VARCHAR(1024)    | poster (VIDEO) / cover art (AUDIO)     |
| `media_gallery`         | JSONB            | list of `MediaItem` (mixed kinds)      |
| `date_published`        | DATE             |                                        |
| `title_ckb` / `_kmr`    | VARCHAR(250)     | embedded `NewsContent.title`           |
| `description_ckb`       | TEXT             | **Tiptap HTML**                        |
| `description_kmr`       | TEXT             | **Tiptap HTML**                        |
| `category_id`           | BIGINT           | FK NOT NULL → `news_categories`        |
| `sub_category_id`       | BIGINT           | FK NOT NULL → `news_sub_categories`    |
| `created_at` / `updated_at` | TIMESTAMP    |                                        |

Indexes: `idx_news_date_published(date_published DESC)`,
`idx_news_created_at(created_at DESC)`.

**Element-collection tables**

- `news_content_languages(news_id, language)` — CKB / KMR
- `news_tags_ckb(news_id, tag_ckb VARCHAR(80))`
- `news_tags_kmr(news_id, tag_kmr VARCHAR(80))`
- `news_keywords_ckb(news_id, keyword_ckb VARCHAR(120))`
- `news_keywords_kmr(news_id, keyword_kmr VARCHAR(120))`

**`news_categories`** — `id`, `name_ckb VARCHAR(120) UNIQUE`, `name_kmr VARCHAR(120)`.
**`news_sub_categories`** — `id`, `name_ckb`, `name_kmr`, `category_id`; unique `(category_id, name_ckb)`.

### Entity — `News`

```
News
├─ coverUrl, coverMediaType, coverThumbnailUrl
├─ mediaGallery : List<MediaItem>  ← JSONB
├─ datePublished
├─ contentLanguages : Set<Language>
├─ ckbContent : NewsContent { title, description }
├─ kmrContent : NewsContent { title, description }
├─ tagsCkb / tagsKmr     : Set<String>
├─ keywordsCkb / keywordsKmr : Set<String>
├─ category : NewsCategory     { nameCkb, nameKmr }
└─ subCategory : NewsSubCategory { nameCkb, nameKmr, category }
```

### DTOs — `NewsDto`

- Top-level: `id, coverUrl, coverMediaType, coverThumbnailUrl, mediaGallery, datePublished, createdAt, updatedAt, contentLanguages, category, subCategory, ckbContent, kmrContent, tags, keywords`.
- `CategoryDto`: `ckbName, kmrName`.
- `SubCategoryDto`: `ckbName, kmrName`.
- `LanguageContentDto`: `title, description` (Tiptap HTML).
- `BilingualSet`: `ckb: Set<String>, kmr: Set<String>`.

### Endpoints — `/api/v1/news`

| Method | Path                                                  | Notes                            | Content-Type |
| ------ | ----------------------------------------------------- | -------------------------------- | ------------ |
| POST   | `/`                                                   | Create.                          | JSON         |
| POST   | `/bulk`                                               | Bulk create.                     | JSON         |
| GET    | `/?page&size`                                         | Paged list.                      | —            |
| GET    | `/{id}`                                               | Get by id.                       | —            |
| GET    | `/search?q&page&size`                                 | Global search.                   | —            |
| GET    | `/search/keyword?keyword&language&page&size`          | Search by keyword (language=ckb/kmr/both). | — |
| GET    | `/search/tag?tag&language&page&size`                  | Search by tag.                   | —            |
| GET    | `/search/category?name&page&size`                     | Filter by category name.         | —            |
| GET    | `/search/subcategory?name&page&size`                  | Filter by sub-category name.     | —            |
| PUT    | `/{id}`                                               | Update.                          | JSON         |
| DELETE | `/{id}` / `/delete/{id}`                              | Delete.                          | —            |
| DELETE | `/bulk`                                               | Bulk delete (ids).               | JSON         |

### Tiptap rewrite hook

`NewsService.buildContent(NewsDto.LanguageContentDto)` runs
`tiptapHtmlProcessor.process(dto.getDescription())` on the embedded
`NewsContent.description` for both languages, called from
`applyContentByLanguages(...)` during create and update.

### Notables

- Tags + keywords are bilingual (`Set<String>` per language).
- Audit log via `NewsAuditLogRepository` (CREATE / UPDATE / DELETE rows
  with `performedBy="system"`).
- `@Cacheable("news")` on read endpoints, `@CacheEvict` on writes.
- Search modes: global, keyword (per language), tag (per language),
  category, sub-category.

---

## 7 · Projects module — cover + gallery + Tiptap

Projects also keep their separate cover + gallery for the listing card,
plus a normalised tag/keyword model with per-language reuse.

### Schema

**`projects`**

| Column                   | Type             | Notes                                  |
| ------------------------ | ---------------- | -------------------------------------- |
| `id`                     | BIGSERIAL        | PK                                     |
| `cover_url`              | VARCHAR(1024)    |                                        |
| `cover_media_type`       | VARCHAR(16)      | `MediaKind` enum, default `IMAGE`      |
| `cover_thumbnail_url`    | VARCHAR(1024)    |                                        |
| `media_gallery`          | JSONB            |                                        |
| `title_ckb` / `_kmr`     | VARCHAR(255)     | embedded                               |
| `description_ckb` / `_kmr` | TEXT           | **Tiptap HTML**                        |
| `location_ckb` / `_kmr`  | VARCHAR(255)     | embedded                               |
| `project_type_ckb` / `_kmr` | VARCHAR(128)  |                                        |
| `status`                 | VARCHAR(32)      | `ProjectStatus` enum, default `ONGOING` |
| `project_date`           | DATE             |                                        |
| audit columns            | via `AuditableEntity` (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`) |

Indexes: type ckb/kmr, status, project_date.

**Tag / keyword tables**

- `project_tags` — `id`, `name VARCHAR(128) UNIQUE NOT NULL`.
- `project_keywords` — `id`, `name VARCHAR(191) UNIQUE NOT NULL`.
- Map tables (per-language): `project_tag_map_ckb`, `project_tag_map_kmr`,
  `project_keyword_map_ckb`, `project_keyword_map_kmr`.
- `project_content_languages(project_id, language)`.

### Entity — `Project`

```
Project
├─ coverUrl, coverMediaType, coverThumbnailUrl, mediaGallery
├─ projectTypeCkb / projectTypeKmr
├─ status : ProjectStatus (ONGOING default)
├─ contentLanguages : Set<Language>
├─ ckbContent : ProjectContentBlock { title, description, location }
├─ kmrContent : ProjectContentBlock { title, description, location }
├─ tagsCkb / tagsKmr         : Set<ProjectTag>    (ManyToMany, name reused case-insensitively)
├─ keywordsCkb / keywordsKmr : Set<ProjectKeyword>(ManyToMany, name reused)
└─ projectDate
```

### DTOs

`ProjectCreateRequest`: `coverUrl, coverMediaType, coverThumbnailUrl,
mediaGallery, projectTypeCkb, projectTypeKmr, status, contentLanguages
(@NotEmpty), projectDate, ckbContent, kmrContent, tagsCkb, tagsKmr,
keywordsCkb, keywordsKmr`.

`ProjectResponse`: same + `id, contentLanguages, ckbContent, kmrContent
(ProjectContentBlockDto{title, description, location}), createdAt,
updatedAt, createdBy, updatedBy`.

### Endpoints — `/api/v1/projects`

| Method | Path                                  | Notes                | Content-Type |
| ------ | ------------------------------------- | -------------------- | ------------ |
| POST   | `/create`                             | Create.              | JSON         |
| PUT    | `/update/{id}`                        | Update.              | JSON         |
| DELETE | `/delete/{id}`                        | Delete.              | —            |
| GET    | `/getAll?page&size`                   | Paged list.          | —            |
| GET    | `/search/tag?tag&page&size`           | Search by tag.       | —            |
| GET    | `/search/keyword?keyword&page&size`   | Search by keyword.   | —            |

### Tiptap rewrite hook

`ProjectService.buildProject(...)` (CREATE) and `applyUpdate(...)`
(UPDATE) call `tiptapHtmlProcessor.process(...)` on
`ckbContent.description` and `kmrContent.description` of the embedded
`ProjectContentBlock`.

### Notables

- Tags / keywords normalised via `findByNameIgnoreCase` — duplicates
  collapse onto an existing row.
- `@Cacheable("projects")` on reads, `@CacheEvict` on writes.
- Audit history via `ProjectLog` (`action, fieldName, oldValue,
  newValue, createdAt`).

---

## 8 · Sound (SoundTrack) module — files + brochures + attachments + Tiptap

Sound keeps a structured media model because each audio file carries its
own technical metadata, plus per-track brochures and per-track-set
attachments. Description-level media still goes through Tiptap.

### Schema

**`sound_tracks`**

| Column                  | Type             | Notes                                  |
| ----------------------- | ---------------- | -------------------------------------- |
| `id`                    | BIGSERIAL        | PK                                     |
| `ckb_cover_url`         | VARCHAR(1000)    |                                        |
| `kmr_cover_url`         | VARCHAR(1000)    |                                        |
| `hover_cover_url`       | VARCHAR(1000)    | hover-state cover                      |
| `sound_type`            | VARCHAR(100)     | required, free text                    |
| `track_state`           | VARCHAR(10)      | `TrackState` enum: SINGLE / MULTI      |
| `is_album_of_memories`  | BOOLEAN          | default `false`                        |
| `topic_id`              | BIGINT           | FK → `publishment_topics`, nullable    |
| `title_ckb` / `_kmr`    | VARCHAR(200)     | embedded                               |
| `description_ckb` / `_kmr` | TEXT          | **Tiptap HTML**                        |
| `reader_name`           | VARCHAR(255)     |                                        |
| `terms`                 | VARCHAR(200)     |                                        |
| `is_institute_project`  | BOOLEAN          | required                               |
| `album_name`            | VARCHAR(300)     |                                        |
| `publishment_year`      | INT              |                                        |
| `cd_number`             | INT              |                                        |
| `total_tracks`          | INT              |                                        |
| `created_at` / `updated_at` | TIMESTAMP    |                                        |

**Element-collection tables**

- `sound_track_content_languages(sound_track_id, language)`
- `sound_track_locations(sound_track_id, location VARCHAR(255))`
- `sound_track_directors(sound_track_id, director_name VARCHAR(255))`
- `sound_track_keywords_ckb` / `_kmr`
- `sound_track_tags_ckb` / `_kmr`

**`sound_track_files`** — one row per audio file

| Column              | Type / Notes                                           |
| ------------------- | ------------------------------------------------------ |
| `id`                | PK                                                     |
| `sound_track_id`    | FK NOT NULL                                            |
| `file_url`          | VARCHAR(1200)                                          |
| `external_url`      | TEXT                                                   |
| `embed_url`         | TEXT                                                   |
| `title`             | VARCHAR(300)                                           |
| `file_type`         | VARCHAR(10) — `FileType` enum, NOT NULL                |
| `publishment_year`  | INT                                                    |
| `file_format`       | VARCHAR(50)                                            |
| `size_bytes`        | BIGINT NOT NULL                                        |
| `duration_seconds`  | BIGINT NOT NULL                                        |
| `bit_rate`          | VARCHAR(50)                                            |
| `sample_rate`       | VARCHAR(50)                                            |
| `audio_channel`     | VARCHAR(10) — `AudioChannel`: STEREO / MONO            |
| `form`              | VARCHAR(150)                                           |
| `genre`             | VARCHAR(100)                                           |
| `recording_venue`   | VARCHAR(500)                                           |

**`sound_track_brochures`** — per-audio-file scanned cover sheets

| Column                | Type / Notes                       |
| --------------------- | ---------------------------------- |
| `id`                  | PK                                 |
| `sound_track_file_id` | FK NOT NULL                        |
| `image_url`           | VARCHAR(1200) NOT NULL             |
| `caption`             | VARCHAR(300)                       |
| `brochure_order`      | INT                                |

**`sound_track_attachments`** — track-set-level downloads

| Column            | Type / Notes                                            |
| ----------------- | ------------------------------------------------------- |
| `id`              | PK                                                      |
| `sound_track_id`  | FK NOT NULL                                             |
| `file_url`        | VARCHAR(1200) NOT NULL                                  |
| `title`           | VARCHAR(300)                                            |
| `attachment_type` | VARCHAR(20) — `AttachmentType`: PDF / VIDEO / IMAGE / AUDIO / OTHER |
| `size_bytes`      | BIGINT NOT NULL                                         |
| `mime_type`       | VARCHAR(100)                                            |
| `attachment_order`| INT                                                     |

### Entity — `SoundTrack`

Embeds `SoundTrackContent { title, description }` once per language.

### DTOs — `SoundTrackDtos` (static container)

| DTO                        | Fields |
| -------------------------- | ------ |
| `LanguageContentDto`       | `title (≤200)`, `description (≤4000)` |
| `BilingualSet`             | `ckb / kmr : Set<String>` |
| `InlineTopicRequest`       | `nameCkb`, `nameKmr` |
| `TopicView`                | `id`, `nameCkb`, `nameKmr`, `createdAt` |
| `BrochureRequest`          | `imageUrl (≤1200)`, `caption (≤300)` |
| `BrochureResponse`         | `id`, `imageUrl`, `caption`, `brochureOrder` |
| `AttachmentRequest`        | `fileUrl`, `title`, `attachmentType` (default OTHER), `sizeBytes`, `mimeType` |
| `AttachmentResponse`       | `id`, `fileUrl`, `title`, `attachmentType`, `sizeBytes`, `mimeType`, `attachmentOrder` |
| `FileCreateRequest`        | `fileUrl`, `externalUrl`, `embedUrl`, `title`, `fileType` (@NotNull), `publishmentYear`, `sizeBytes`, `durationSeconds`, `bitRate`, `sampleRate`, `audioChannel`, `form`, `genre`, `recordingVenue`, `sortOrder`, `brochures: List<BrochureRequest>` |
| `FileResponse`             | same + `durationMinutes` |
| `CreateRequest`            | `soundType (@NotBlank)`, `trackState (@NotNull)`, `albumOfMemories`, `topicId`, `newTopic`, `contentLanguages (@NotEmpty)`, `ckbContent`, `kmrContent`, `locations`, `reader`, `directors`, `terms`, `thisProjectOfInstitute`, `tags`, `keywords`, `files`, `albumName`, `publishmentYear`, `cdNumber`, `totalTracks`, `attachments` |
| `UpdateRequest`            | same as Create + `clearTopic: boolean`; all fields optional |
| `Response`                 | `id`, `ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl`, `soundType`, `trackState`, `albumOfMemories`, `topicId`, `topicNameCkb`, `topicNameKmr`, `contentLanguages`, `ckbContent`, `kmrContent`, `locations`, `reader`, `directors`, `terms`, `thisProjectOfInstitute`, `tags`, `keywords`, `files`, `totalDurationSeconds`, `totalSizeBytes`, `albumName`, `publishmentYear`, `cdNumber`, `totalTracks`, `attachments`, `createdAt`, `updatedAt` |

### Endpoints — `/api/v1/sound-tracks`

| Method | Path                                  | Notes                                                   | Content-Type |
| ------ | ------------------------------------- | ------------------------------------------------------- | ------------ |
| POST   | `/`                                   | Create. Parts: `data`, `ckbCoverImage`, `kmrCoverImage`, `hoverCoverImage`, `audioFiles[]`, `brochureFiles[]`, `attachmentFiles[]` | multipart |
| PUT    | `/{id}`                               | Update. Same parts.                                     | multipart    |
| GET    | `/?page&size`                         | Paged list.                                             | —            |
| GET    | `/{id}`                               | Get by id.                                              | —            |
| DELETE | `/{id}`                               | Delete.                                                 | —            |
| GET    | `/by-state?state&page&size`           | Filter by `TrackState`.                                 | —            |
| GET    | `/by-sound-type?soundType&page&size`  | Filter by free-text type.                               | —            |
| GET    | `/by-topic?topicId&page&size`         | Filter by topic id.                                     | —            |
| GET    | `/album-of-memories?page&size`        | `albumOfMemories=true` only.                            | —            |
| GET    | `/search/tag?tag&page&size`           | Search by tag.                                          | —            |
| GET    | `/search/keyword?keyword&page&size`   | Search by keyword.                                      | —            |
| GET    | `/search?q&page&size`                 | Global search.                                          | —            |
| GET    | `/topics`                             | List SOUND topics (autocomplete).                       | —            |

### Tiptap rewrite hook

`SoundTrackService.buildContent(LanguageContentDto)` runs
`tiptapHtmlProcessor.process(trimOrNull(dto.getDescription()))` on the
embedded `SoundTrackContent.description`, invoked from
`applyContentByLanguages(...)` during create and update.

### Notables

- `TrackState`: SINGLE (one file) / MULTI (album / playlist).
  `albumOfMemories` only meaningful when MULTI.
- Attachment file types accepted: PDF, VIDEO, IMAGE, AUDIO, OTHER (any MIME).
- Topic via `PublishmentTopic(entityType="SOUND")`, nullable. UpdateRequest
  carries a `clearTopic` flag.

---

## 9 · Video module — film vs. clips + Tiptap

Video supports two top-level types (`FILM` with a single source, or
`VIDEO_CLIP` with a list of clips). Each video carries its bilingual
description, and clip items carry their own bilingual title +
description — all of which flow through Tiptap.

### Schema

**`videos`**

| Column                     | Type             | Notes                                  |
| -------------------------- | ---------------- | -------------------------------------- |
| `id`                       | BIGSERIAL        | PK                                     |
| `ckb_cover_url` / `kmr_cover_url` / `hover_cover_url` | VARCHAR(1000) | |
| `video_type`               | VARCHAR(20)      | `VideoType` enum: FILM / VIDEO_CLIP    |
| `is_album_of_memories`     | BOOLEAN          | default `false`                        |
| `topic_id`                 | BIGINT           | FK → `publishment_topics`              |
| `title_ckb` / `_kmr`       | VARCHAR(300)     | embedded `VideoContent.title`          |
| `description_ckb` / `_kmr` | TEXT             | **Tiptap HTML**                        |
| `location_ckb` / `_kmr`    | VARCHAR(250)     |                                        |
| `director_ckb` / `_kmr`    | VARCHAR(250)     |                                        |
| `producer_ckb` / `_kmr`    | VARCHAR(250)     |                                        |
| `source_url`               | TEXT             | FILM type                              |
| `source_external_url`      | TEXT             |                                        |
| `source_embed_url`         | TEXT             |                                        |
| `file_format`              | VARCHAR(20)      |                                        |
| `duration_seconds`         | INT              |                                        |
| `publishment_date`         | DATE             |                                        |
| `resolution`               | VARCHAR(20)      |                                        |
| `file_size_mb`             | DOUBLE           |                                        |
| `created_at` / `updated_at`| TIMESTAMP        |                                        |

**Element-collection tables**

- `video_content_languages(video_id, language)`
- `video_tags_ckb(video_id, tag_ckb VARCHAR(100))`
- `video_tags_kmr(video_id, tag_kmr VARCHAR(100))`
- `video_keywords_ckb(video_id, keyword_ckb VARCHAR(150))`
- `video_keywords_kmr(video_id, keyword_kmr VARCHAR(150))`

**`video_clip_items`** — only used by `VIDEO_CLIP` type

| Column                       | Type / Notes                       |
| ---------------------------- | ---------------------------------- |
| `id`                         | PK                                 |
| `video_id`                   | FK NOT NULL                        |
| `url`, `external_url`, `embed_url` | TEXT                         |
| `clip_number`                | INT (order)                        |
| `duration_seconds`           | INT                                |
| `resolution`                 | VARCHAR(20)                        |
| `file_format`                | VARCHAR(20)                        |
| `file_size_mb`               | DOUBLE                             |
| `title_ckb` / `_kmr`         | VARCHAR(300)                       |
| `description_ckb` / `_kmr`   | TEXT — **Tiptap HTML, per clip**   |

### Entity — `Video`

Embeds `VideoContent { title, description, location, director, producer }`
once per language. `VideoClipItem` rows live in their own table.

### DTOs — `VideoDTO` + mapper

- Top-level: `id, ckbCoverUrl, kmrCoverUrl, hoverCoverUrl, videoType,
  albumOfMemories, topicId, newTopic, clearTopic, topicNameCkb,
  topicNameKmr, contentLanguages, ckbContent, kmrContent, sourceUrl,
  sourceExternalUrl, sourceEmbedUrl, videoClipItems, fileFormat,
  durationSeconds, publishmentDate, resolution, fileSizeMb, tagsCkb,
  tagsKmr, keywordsCkb, keywordsKmr, createdAt, updatedAt`.
- `VideoContentDTO`: `title, description, location, director, producer`.
- `VideoClipItemDTO`: `id, url, externalUrl, embedUrl, clipNumber,
  durationSeconds, resolution, fileFormat, fileSizeMb, titleCkb,
  titleKmr, descriptionCkb, descriptionKmr`.
- `InlineTopicRequest`: `nameCkb, nameKmr`.
- `TopicView`: `id, nameCkb, nameKmr, createdAt`.
- `VideoMapper` — static `toEntity`, `updateEntity`, `toDTO`, `toLogDTO`.

### Endpoints — `/api/v1/videos`

| Method | Path                                  | Notes                                                              | Content-Type |
| ------ | ------------------------------------- | ------------------------------------------------------------------ | ------------ |
| POST   | `/`                                   | Create. Parts: `data`, `ckbCoverImage`, `kmrCoverImage`, `hoverImage`, `videoFile` | multipart |
| GET    | `/topics`                             | List VIDEO topics.                                                 | —            |
| POST   | `/topics?nameCkb&nameKmr`             | Create video topic.                                                | query params |
| DELETE | `/topics/{topicId}`                   | Delete topic.                                                      | —            |
| GET    | `/?page&size`                         | Paged list.                                                        | —            |
| GET    | `/{id}`                               | Get by id.                                                         | —            |
| GET    | `/search/tag?value&page&size`         | Search by tag.                                                     | —            |
| GET    | `/search/keyword?value&page&size`     | Search by keyword.                                                 | —            |
| PUT    | `/{id}`                               | Update.                                                            | multipart    |
| DELETE | `/{id}`                               | Delete.                                                            | —            |

### Tiptap rewrite hook

`VideoService.processTiptapHtml(Video)` runs
`tiptapHtmlProcessor.process(...)` on:

- `video.getCkbContent().getDescription()`
- `video.getKmrContent().getDescription()`
- every `VideoClipItem.descriptionCkb`
- every `VideoClipItem.descriptionKmr`

Called after `VideoMapper.toEntity(...)` (create) and after
`updateEntity(...) + buildAndAttachClipItems(...)` (update).

### Notables

- `VIDEO_CLIP` supports per-clip bilingual title + Tiptap description.
- `FILM` uses single `sourceUrl` / `sourceExternalUrl` / `sourceEmbedUrl`;
  clip list ignored.
- `albumOfMemories` only valid for `VIDEO_CLIP`.
- `VideoLog` audit entity (`videoId, videoTitle, action, details,
  performedBy, timestamp`).
- Shared topic via `PublishmentTopic(entityType="VIDEO")`.

---

## 10 · Image Collection module — album items + Tiptap

`ImageCollection` is the structured photo gallery — every `ImageAlbumItem`
has its own auto-extracted technical metadata AND its own bilingual
Tiptap description.

### Schema

**`image_collections`**

| Column                       | Type             | Notes                                  |
| ---------------------------- | ---------------- | -------------------------------------- |
| `id`                         | BIGSERIAL        | PK                                     |
| `collection_type`            | VARCHAR(20)      | `ImageCollectionType`: SINGLE / GALLERY / PHOTO_STORY |
| `ckb_cover_url` / `kmr_cover_url` / `hover_cover_url` | TEXT |   |
| `topic_id`                   | BIGINT           | FK → `publishment_topics` ON DELETE SET NULL |
| `title_ckb` / `_kmr`         | VARCHAR(300)     | embedded `ImageContent.title`          |
| `description_ckb` / `_kmr`   | TEXT             | **Tiptap HTML**                        |
| `location_ckb` / `_kmr`      | VARCHAR(250)     |                                        |
| `collected_by_ckb` / `_kmr`  | VARCHAR(250)     |                                        |
| `publishment_date`           | DATE             |                                        |
| `created_at` / `updated_at`  | TIMESTAMP        |                                        |

**Element-collection tables**

- `image_collection_languages(image_collection_id, language)`
- `image_tags_ckb` / `_kmr`
- `image_keywords_ckb` / `_kmr`

**`image_album_items`** — each photo / external link in the collection

| Column                       | Type / Notes                                |
| ---------------------------- | ------------------------------------------- |
| `id`                         | PK                                          |
| `image_collection_id`        | FK NOT NULL                                 |
| `image_url`                  | TEXT (S3, nullable for external entries)    |
| `external_url`, `embed_url`  | TEXT                                        |
| `caption_ckb` / `_kmr`       | VARCHAR(500)                                |
| `description_ckb` / `_kmr`   | TEXT — **Tiptap HTML, per item**            |
| `sort_order`                 | INT                                         |
| `file_size_bytes`            | BIGINT (auto-extracted on upload)           |
| `width_px` / `height_px`     | INT (auto)                                  |
| `mime_type`                  | VARCHAR(50)                                 |

Entity transient helpers: `getAspectRatio()`, `isPortrait()`,
`isLandscape()`, `getHumanReadableSize()`.

### Entity — `ImageCollection`

Embeds `ImageContent { title, description, location, collectedBy }`
twice (CKB / KMR). Children = `Set<ImageAlbumItem>`.

### DTOs — `ImageCollectionDTO`

| DTO                  | Fields |
| -------------------- | ------ |
| `LanguageContentDto` | `title`, `description`, `topic`, `location`, `collectedBy` |
| `BilingualSet`       | `ckb / kmr : Set<String>` |
| `InlineTopicRequest` | `nameCkb`, `nameKmr` |
| `ImageItemDto`       | `id`, `imageUrl`, `externalUrl`, `embedUrl`, `captionCkb`, `captionKmr`, `descriptionCkb`, `descriptionKmr`, `sortOrder`, `fileSizeBytes`, `widthPx`, `heightPx`, `mimeType`, `aspectRatio`, `humanReadableSize` |
| `CreateRequest`      | `collectionType` (@NotNull), `ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl`, `topicId`, `newTopic`, `publishmentDate`, `contentLanguages` (@NotNull), `ckbContent`, `kmrContent`, `tags`, `keywords`, `imageAlbum` |
| `UpdateRequest`      | same + `clearTopic: boolean`; all fields nullable |
| `Response`           | `id`, `collectionType`, three cover URLs, `topicId`, `topicNameCkb`, `topicNameKmr`, `publishmentDate`, `contentLanguages`, `ckbContent`, `kmrContent`, `tags`, `keywords`, `imageAlbum`, `createdAt`, `updatedAt` |

### Endpoints — `/api/v1/image-collections`

| Method | Path           | Notes                                                                                | Content-Type |
| ------ | -------------- | ------------------------------------------------------------------------------------ | ------------ |
| POST   | `/`            | Create with files. Parts: `data`, `ckbCoverImage`, `kmrCoverImage`, `hoverCoverImage`, `images[]` | multipart |
| POST   | `/json`        | Create (URL-only, no uploads).                                                       | JSON         |
| PUT    | `/{id}`        | Update.                                                                              | multipart    |
| GET    | `/?page&size`  | Paged list.                                                                          | —            |
| GET    | `/{id}`        | Get by id.                                                                           | —            |
| DELETE | `/{id}`        | Delete.                                                                              | —            |
| GET    | `/topics`      | List IMAGE topics.                                                                   | —            |

### Tiptap rewrite hook

`ImageCollectionService.buildContent(LanguageContentDto)` runs
`tiptapHtmlProcessor.process(...)` on the embedded `ImageContent.description`.
`buildAlbumItems(...)` also feeds each `ImageAlbumItem.descriptionCkb` /
`descriptionKmr` through the processor.

### Notables

- `ImageCollectionType` SINGLE (exactly 1 image) / GALLERY (≥1) /
  PHOTO_STORY (≥2). Enforced by `validateAlbumItemCount`.
- Per-item technical metadata (size / width / height / mime) is
  auto-extracted only for uploaded files; external/embed URL items leave
  these null.
- Topic via `PublishmentTopic(entityType="IMAGE")`; `clearTopic` flag.

---

## 11 · Writing module — books + series + Tiptap

Writing supports book series (parent / child links, ordering), multiple
genres per book, and per-language book files (PDF, EPUB, DOCX…).

### Schema

**`writings`**

| Column                      | Type             | Notes                                  |
| --------------------------- | ---------------- | -------------------------------------- |
| `id`                        | BIGSERIAL        | PK                                     |
| `ckb_cover_url` / `kmr_cover_url` / `hover_cover_url` | TEXT |       |
| `topic_id`                  | BIGINT           | FK → `publishment_topics`              |
| `series_id`                 | VARCHAR(100)     | grouping key (auto-assigned in `@PrePersist`) |
| `series_name`               | VARCHAR(300)     |                                        |
| `series_order`              | DOUBLE           |                                        |
| `parent_book_id`            | BIGINT           | FK → `writings` (self)                 |
| `series_total_books`        | INT              |                                        |
| `title_ckb` / `_kmr`        | VARCHAR(300)     | embedded                               |
| `description_ckb` / `_kmr`  | TEXT             | **Tiptap HTML**                        |
| `writer_ckb` / `_kmr`       | VARCHAR(200)     |                                        |
| `file_url_ckb` / `_kmr`     | VARCHAR(1000)    | the book file URL per language         |
| `file_format_ckb` / `_kmr`  | VARCHAR(20)      | `WritingFileFormat` enum (PDF / DOCX / EPUB / …) |
| `file_size_bytes_ckb` / `_kmr` | BIGINT        |                                        |
| `page_count_ckb` / `_kmr`   | INT              |                                        |
| `genre_ckb` / `_kmr`        | VARCHAR(150)     | free-text per language                 |
| `published_by_institute`    | BOOLEAN          | required                               |
| `created_at` / `updated_at` | TIMESTAMP        |                                        |

Indexes: `topic_id`, `published_by_institute`, `created_at`,
`updated_at`, `writer_ckb`, `writer_kmr`, `series_id`,
`(series_id, series_order)`, `parent_book_id`.

**Element-collection tables**

- `writing_content_languages(writing_id, language)`
- `writing_book_genres(writing_id, book_genre VARCHAR(30))` — `Set<BookGenre>`
- `writing_keywords_ckb` / `_kmr`
- `writing_tags_ckb` / `_kmr`

### Entity — `Writing`

Embeds `WritingContent { title, description, writer, fileUrl, fileFormat,
fileSizeBytes, pageCount, genre }` twice (CKB / KMR).

### DTOs — `WritingDtos`

| DTO                   | Fields |
| --------------------- | ------ |
| `LanguageContentDto`  | `title (≤300)`, `description (≤10000)`, `writer (≤200)`, `fileUrl (≤1000)`, `fileFormat`, `fileSizeBytes (@Min 0)`, `pageCount (@Min 1)`, `genre (≤150)` |
| `BilingualSet`        | `ckb / kmr` |
| `TopicPayload`        | `nameCkb`, `nameKmr` (≤300) |
| `TopicInfo`           | `id`, `nameCkb`, `nameKmr` |
| `SeriesInfoDto`       | `seriesId`, `seriesName`, `seriesOrder`, `parentBookId`, `totalBooks`, `isParent` |
| `SeriesBookSummary`   | `id`, `titleCkb`, `titleKmr`, `seriesOrder`, `createdAt` |
| `CreateRequest`       | `contentLanguages (@NotEmpty)`, `ckbCoverUrl`, `kmrCoverUrl`, `hoverCoverUrl` (each ≤2000), `ckbContent`, `kmrContent`, `topicId`, `newTopic`, `bookGenres (@NotEmpty Set<BookGenre>)`, `publishedByInstitute`, `tags`, `keywords`, `seriesId (≤100)`, `seriesName (≤300)`, `seriesOrder (@Min 0)`, `parentBookId` |
| `UpdateRequest`       | all fields optional + `clearTopic: Boolean`; `bookGenres = null` means unchanged |
| `Response`            | `id`, `contentLanguages`, three cover URLs, `ckbContent`, `kmrContent`, `topic (TopicInfo)`, `bookGenres`, `publishedByInstitute`, `tags`, `keywords`, `seriesInfo`, `createdAt`, `updatedAt` |
| `SeriesResponse`      | `seriesId`, `seriesName`, `totalBooks`, `books: List<SeriesBookSummary>` |
| `LinkToSeriesRequest` | `bookId (@NotNull)`, `parentBookId (@NotNull)`, `seriesOrder (@NotNull @Min 1)`, `seriesName (≤300)` |
| `SearchRequest`       | `bookGenres`, `instituteOnly`, `writer`, `language`, `seriesId`, `seriesParentsOnly` |

### Endpoints — `/api/v1/writings`

| Method | Path                                              | Notes                                                                 | Content-Type |
| ------ | ------------------------------------------------- | --------------------------------------------------------------------- | ------------ |
| POST   | `/`                                               | Create. Parts: `data`, `ckbCoverImage`, `kmrCoverImage`, `hoverCoverImage`, `ckbBookFile`, `kmrBookFile` | multipart |
| GET    | `/?page&size`                                     | Paged list (sorted createdAt DESC).                                   | —            |
| GET    | `/{id}`                                           | Get by id.                                                            | —            |
| PUT    | `/{id}`                                           | Update.                                                               | multipart    |
| DELETE | `/{id}`                                           | Delete.                                                               | —            |
| GET    | `/series/parents?page&size`                       | Series parents only.                                                  | —            |
| POST   | `/series/link`                                    | Link a book into a series.                                            | JSON         |
| GET    | `/series/{seriesId}`                              | Books in a series.                                                    | —            |
| GET    | `/search/writer?name&language&page&size`          | Search by writer.                                                     | —            |
| GET    | `/search/tag?tag&language&page&size`              | Search by tag.                                                        | —            |
| GET    | `/search/keyword?keyword&language&page&size`      | Search by keyword.                                                    | —            |
| GET    | `/topics`                                         | List WRITING topics.                                                  | —            |

### Tiptap rewrite hook

`WritingService.buildContent(LanguageContentDto, String fileUrl)` runs
`tiptapHtmlProcessor.process(trimOrNull(dto.getDescription()))`.
`mergeContent(...)` re-runs it on update when description is non-null.

### Notables

- `Set<BookGenre>` — multi-genre per book, ≥1 required.
- `WritingFileFormat` enum on each language's `fileFormat` column.
- Book file types accepted: per-language `ckbBookFile` and `kmrBookFile`
  multipart parts → stored at `WritingContent.fileUrl` (`file_url_ckb` /
  `file_url_kmr`).
- Series: self-referencing parent/child via `parent_book_id`, ordering
  via `series_order`, grouping via `series_id` (auto-assigned in
  `@PrePersist` if null: `"series-" + ms`).
- Topic via `PublishmentTopic(entityType="WRITING")`; `clearTopic` flag.

---

## 12 · PublishmentTopic — shared topic registry

Used by Sound, Video, Image Collection, and Writing for typed
auto-complete-style topic grouping.

### Schema — `publishment_topics`

| Column        | Type           | Notes                                            |
| ------------- | -------------- | ------------------------------------------------ |
| `id`          | BIGSERIAL      | PK                                               |
| `entity_type` | VARCHAR(20)    | "VIDEO" / "SOUND" / "IMAGE" / "WRITING"          |
| `name_ckb`    | VARCHAR(300)   |                                                  |
| `name_kmr`    | VARCHAR(300)   |                                                  |
| `created_at`  | TIMESTAMP      | not updatable                                    |
| `updated_at`  | TIMESTAMP      |                                                  |

Indexes: `idx_topic_entity_type`, `idx_topic_name_ckb(entity_type,
name_ckb)`, `idx_topic_name_kmr(entity_type, name_kmr)`. Class-level
`@BatchSize(50)`.

### Endpoints — `/api/v1/topics`

| Method | Path                       | Notes                                                  | Content-Type |
| ------ | -------------------------- | ------------------------------------------------------ | ------------ |
| GET    | `/{entityType}`            | List all topics for a type.                            | —            |
| GET    | `/{entityType}/{id}`       | Get single topic.                                      | —            |
| POST   | `/{entityType}`            | Create topic. Body: `{ nameCkb, nameKmr }`.            | JSON         |
| PUT    | `/{id}`                    | Update topic. Body: `{ nameCkb, nameKmr }`.            | JSON         |
| DELETE | `/{id}`                    | Delete.                                                | —            |

Shortcut endpoints on other controllers backed by the same registry:

- `GET /api/v1/sound-tracks/topics` (entityType = "SOUND")
- `GET /api/v1/videos/topics`, `POST`, `DELETE` (entityType = "VIDEO")
- `GET /api/v1/image-collections/topics` (entityType = "IMAGE")
- `GET /api/v1/writings/topics` (entityType = "WRITING")

### Notables

- `ManyToOne(LAZY)` from `SoundTrack`, `Video`, `ImageCollection`, and
  `Writing` via `topic_id` FK, nullable. Image FK is `ON DELETE SET NULL`.
- `entity_type` is a free-text discriminator (not a JPA enum).
- `@BatchSize(50)` lets Hibernate batch-load topics for a page in one
  IN-query.

---

## End-to-End Flow

```
[ Frontend Tiptap editor ]
     │
     │  user drops an image / audio / video / pdf
     │  → POST /api/v1/media/upload (type=image|video|audio|document)
     │  ← { fileUrl: "https://…s3…/<folder>/<uuid>-foo.<ext>" }
     │  → editor inserts:
     │       <img|video|audio src="https://…s3…">   (media)
     │       <a href="https://…s3…">foo.pdf</a>     (file)
     │
     │  (fallback) user pastes / drag-drops a screenshot or file
     │  → editor produces  src="data:..."  or  href="data:..."
     │
     ▼
POST/PUT  /api/v1/<module>
     {
       …structured fields…,
       ckbContent: { …, body|description: "<p>…<img src=…><a href=…>doc.pdf</a></p>" },
       kmrContent: { …, body|description: "<p>…</p>" }
     }
     │
     ▼
[ Service layer ]
     │  builds the embeddable / content row, e.g.
     │
     │      NewsContent.builder()
     │          .title(…)
     │          .description(tiptapHtmlProcessor.process(dto.getDescription()))
     │          .build();
     │
     │      VideoService.processTiptapHtml(video)
     │          → rewrites video + every clip's bilingual description
     │
     ▼
[ TiptapHtmlProcessor.process(html) ]
     │  • Fast path: no "data:" → return unchanged.
     │  • Match every src="data:<mime>;base64,…"   (<img>/<video>/<audio>/<source>)
     │  • Match every href="data:<mime>;base64,…"  (<a>)
     │  • For each one: decode → S3Service.upload(...)
     │                  → replace attribute with the public URL
     │
     ▼
[ Persisted in Postgres ]
     description / body columns contain only S3 URLs — no inline binaries.
     mediaGallery (where it exists) holds normalised MediaItem JSONB.

GET /api/v1/<module>/{id}
     ▼
   toResponse(...) returns the stored HTML verbatim — the frontend renders
   the S3 URLs directly. The browser fetches each image / video / audio /
   PDF from S3 via its public URL.
```

---

## JSON Examples — every Tiptap-driven module

### About — `POST /api/v1/about`

```json
{
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",
  "ckbContent": {
    "title": "دەربارەی ئێمە",
    "subtitle": "زانیاری گشتی",
    "metaDescription": "…",
    "body": "<h1>دەربارە</h1><p>…</p><img src=\"https://…/images/team.jpg\"><video controls src=\"https://…/video/intro.mp4\"></video><audio controls src=\"https://…/audio/voice.mp3\"></audio><p><a href=\"https://…/files/brochure.pdf\" download>پەڕەی ناساندن</a></p>"
  },
  "kmrContent": { "title": "Derbarê me", "subtitle": "…", "metaDescription": "…", "body": "<p>…</p>" },
  "stats": [
    { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,000+" }
  ]
}
```

### Contact — `POST /api/v1/contact`

```json
{
  "slugCkb": "پەیوەندی",
  "slugKmr": "peywendi",
  "ckbContent": {
    "title": "پەیوەندیمان پێوە بکە",
    "subtitle": "ئامادە بەخزمەتت",
    "address": "هەولێر",
    "workingHours": "٩:٠٠–١٧:٠٠",
    "description": "<p>بەخێرهاتن</p><img src=\"https://…/images/lobby.jpg\"><video controls src=\"https://…/video/office-tour.mp4\"></video><p><a href=\"https://…/files/directions.pdf\">ڕێگاکان</a></p>"
  },
  "kmrContent": { "title": "Têkilî", "subtitle": "…", "address": "Hewlêr", "workingHours": "09:00–17:00", "description": "<p>…</p>" },
  "phone": "+964 770 000 0000",
  "email": "info@khi.iq",
  "mapEmbedUrl": "https://www.google.com/maps/embed?…",
  "latitude":  36.19,
  "longitude": 44.01
}
```

### Service — `POST /api/v1/services`

```json
{
  "serviceType": "Architecture",
  "location": "Erbil",
  "publishedAt": "2026-05-22 14:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title": "خزمەتگوزاری بیناسازی",
      "description": "<p>دەستپێک</p><img src=\"https://…/images/blueprint.jpg\"><video controls src=\"https://…/video/showreel.mp4\"></video><audio controls src=\"https://…/audio/intro.mp3\"></audio><p><a href=\"https://…/files/portfolio.pdf\">پۆرتفۆلیۆ</a></p>"
    },
    { "languageCode": "KMR", "title": "Karûbarê mîmarî", "description": "<p>…</p>" }
  ]
}
```

### News — `POST /api/v1/news`

```json
{
  "coverUrl":          "https://…/video/breaking.mp4",
  "coverMediaType":    "VIDEO",
  "coverThumbnailUrl": "https://…/images/breaking-poster.jpg",
  "mediaGallery": [
    { "url": "https://…/images/site.jpg", "kind": "IMAGE", "sortOrder": 0 },
    { "url": "https://…/audio/quote.mp3", "kind": "AUDIO",
      "thumbnailUrl": "https://…/images/speaker.jpg",
      "captionCkb": "گوتە", "captionKmr": "Gotin", "sortOrder": 1 }
  ],
  "datePublished": "2026-05-22",
  "contentLanguages": ["CKB", "KMR"],
  "category":    { "ckbName": "هەواڵ", "kmrName": "Nûçe" },
  "subCategory": { "ckbName": "کلتوور", "kmrName": "Çand" },
  "ckbContent":  { "title": "ناونیشانی هەواڵ", "description": "<p>دەقی هەواڵ</p><img src=\"https://…/images/photo.jpg\"><p>کۆتایی</p>" },
  "kmrContent":  { "title": "Sernavê nûçeyê",  "description": "<p>Deqa nûçeyê</p>" },
  "tags":     { "ckb": ["هۆنراوە"], "kmr": ["Helbest"] },
  "keywords": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] }
}
```

### Projects — `POST /api/v1/projects/create`

```json
{
  "coverUrl":          "https://…/audio/theme.mp3",
  "coverMediaType":    "AUDIO",
  "coverThumbnailUrl": "https://…/images/theme-art.jpg",
  "mediaGallery": [
    { "url": "https://…/images/site-plan.jpg", "kind": "IMAGE", "sortOrder": 0 },
    { "url": "https://…/video/site-walkthrough.mp4", "kind": "VIDEO",
      "thumbnailUrl": "https://…/images/site-poster.jpg", "sortOrder": 1 }
  ],
  "projectTypeCkb": "بیناسازی",
  "projectTypeKmr": "Mîmarî",
  "status": "ONGOING",
  "projectDate": "2026-04-01",
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "پرۆژەی نوێ",
    "description": "<h2>پێشەکی</h2><p>وردەکاری</p><img src=\"https://…/images/site-plan.jpg\"><p><a href=\"https://…/files/spec.pdf\">پلانی تەواو</a></p>",
    "location": "هەولێر"
  },
  "kmrContent": { "title": "Projeya nû", "description": "<p>…</p>", "location": "Hewlêr" },
  "tagsCkb":     ["بیناسازی"],
  "tagsKmr":     ["Mîmarî"],
  "keywordsCkb": ["پرۆژە"],
  "keywordsKmr": ["Proje"]
}
```

### Sound — `POST /api/v1/sound-tracks` (multipart)

JSON `data` part:

```json
{
  "soundType": "Music",
  "trackState": "MULTI",
  "albumOfMemories": true,
  "topicId": 7,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": { "title": "ئەلبوومی نوێ", "description": "<p>پوختە</p><img src=\"https://…/images/album.jpg\"><p>کۆتایی</p>" },
  "kmrContent": { "title": "Albûma nû",    "description": "<p>Kurte</p>" },
  "locations": ["هەولێر"],
  "reader": "ئاکار",
  "directors": ["د. سامی"],
  "thisProjectOfInstitute": true,
  "tags":     { "ckb": ["مۆسیقا"], "kmr": ["Muzîk"] },
  "keywords": { "ckb": ["گۆرانی"], "kmr": ["Stran"] },
  "files": [
    {
      "title": "Track 1",
      "fileType": "AUDIO",
      "sizeBytes": 5242880,
      "durationSeconds": 240,
      "audioChannel": "STEREO",
      "form": "Folk", "genre": "Traditional",
      "sortOrder": 0,
      "brochures": [ { "imageUrl": "https://…/images/sleeve.jpg", "caption": "ڕووکار" } ]
    }
  ],
  "albumName": "ئەلبوومی یەکەم",
  "publishmentYear": 2026,
  "cdNumber": 1,
  "totalTracks": 10,
  "attachments": [
    { "fileUrl": "https://…/files/lyrics.pdf", "title": "Lyrics", "attachmentType": "PDF", "sizeBytes": 100000, "mimeType": "application/pdf" }
  ]
}
```

### Video — `POST /api/v1/videos` (multipart, JSON `data` part)

```json
{
  "videoType": "VIDEO_CLIP",
  "albumOfMemories": true,
  "topicId": 12,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": { "title": "فیلمی نوێ",  "description": "<p>پوختە</p><img src=\"https://…/images/still-1.jpg\"><p>کۆتایی</p>", "location": "هەولێر", "director": "د. ئاکار", "producer": "KHI" },
  "kmrContent": { "title": "Fîlma nû",   "description": "<p>Kurte</p>", "location": "Hewlêr", "director": "Dr. Akar", "producer": "KHI" },
  "videoClipItems": [
    {
      "clipNumber": 1,
      "durationSeconds": 60,
      "resolution": "1920x1080",
      "fileFormat": "mp4",
      "fileSizeMb": 12.4,
      "titleCkb": "بەشی یەکەم",
      "titleKmr": "Beşa yekem",
      "descriptionCkb": "<p>وردەکاری بەشی یەکەم</p><img src=\"https://…/images/clip-1.jpg\">",
      "descriptionKmr": "<p>Hûrgilî beşa yekem</p>"
    }
  ],
  "publishmentDate": "2026-05-22",
  "tagsCkb": ["فیلم"], "tagsKmr": ["Fîlm"],
  "keywordsCkb": ["دۆکیومێنتاری"], "keywordsKmr": ["Belgefîlm"]
}
```

### Image Collection — `POST /api/v1/image-collections` (multipart, JSON `data` part)

```json
{
  "collectionType": "GALLERY",
  "topicId": 3,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "گەلەری وێنە",
    "description": "<p>زنجیرە وێنە</p><img src=\"https://…/images/intro.jpg\"><p>کۆتایی</p>",
    "location": "هەولێر",
    "collectedBy": "KHI"
  },
  "kmrContent": { "title": "Galeriya wêneyan", "description": "<p>Rêzewêne</p>", "location": "Hewlêr", "collectedBy": "KHI" },
  "publishmentDate": "2026-05-22",
  "tags":     { "ckb": ["وێنە"], "kmr": ["Wêne"] },
  "keywords": { "ckb": ["مێژوو"], "kmr": ["Dîrok"] },
  "imageAlbum": [
    {
      "sortOrder": 0,
      "captionCkb": "وێنە ١",
      "captionKmr": "Wêne 1",
      "descriptionCkb": "<p>وردەکاری وێنە ١</p><img src=\"https://…/images/detail-1.jpg\">",
      "descriptionKmr": "<p>Hûrgilî wêne 1</p>"
    }
  ]
}
```

### Writing — `POST /api/v1/writings` (multipart, JSON `data` part)

```json
{
  "contentLanguages": ["CKB", "KMR"],
  "ckbCoverUrl":  "https://…/images/book-cover-ckb.jpg",
  "kmrCoverUrl":  "https://…/images/book-cover-kmr.jpg",
  "hoverCoverUrl":"https://…/images/book-cover-hover.jpg",
  "ckbContent": {
    "title": "نووسینی نوێ",
    "description": "<h2>پێشەکی</h2><p>…</p><img src=\"https://…/images/fig-1.jpg\"><p><a href=\"https://…/files/sample.pdf\">نموونە</a></p>",
    "writer": "ئاکار",
    "fileFormat": "PDF",
    "pageCount": 240,
    "genre": "هۆنراوە"
  },
  "kmrContent": { "title": "Nivîs", "description": "<p>…</p>", "writer": "Akar", "fileFormat": "PDF", "pageCount": 240, "genre": "Helbest" },
  "topicId": 5,
  "bookGenres": ["NOVEL", "POETRY"],
  "publishedByInstitute": true,
  "tags":     { "ckb": ["شیعر"], "kmr": ["Helbest"] },
  "keywords": { "ckb": ["کوردی"], "kmr": ["Kurdî"] },
  "seriesName": "نووسینی نوێ",
  "seriesOrder": 1
}
```

---

## Error Modes and Edge Cases

| Scenario                                          | Behaviour                                                       |
| ------------------------------------------------- | --------------------------------------------------------------- |
| `body` / `description` is `null` or empty         | Returned as-is. Hibernate stores `null`.                        |
| HTML has no `data:` URIs                          | Returned unchanged (early-out before regex compilation).        |
| Single `data:` URI is malformed base64            | Logged at `WARN`, original attribute kept, save still succeeds. |
| S3 upload fails for one inline asset              | Logged at `ERROR`, original attribute kept, save succeeds.      |
| Multiple `data:` URIs in one description          | All processed in document order; one pass, one upload each.     |
| Mixed `src=` and `href=` data URIs in same doc    | Both passes run; `src` first, then `href`.                      |
| Same image referenced twice                       | Uploaded twice (filename uses `System.nanoTime()`).             |
| MIME unknown (`application/octet-stream`)         | Folder falls back to `files/`, extension to suffix after `/` or `bin`. |
| `coverMediaType` is `null` on News / Project      | Server defaults to `IMAGE`.                                     |
| `MediaItem.kind` is `null` in News/Project gallery | Normalised to `IMAGE`.                                          |
| News / Project gallery item URL blank             | Dropped by `buildGallery(...)`; never stored.                   |
| Update changes a News/Project cover or gallery URL | Old S3 object is deleted (per-module update path).             |
| Delete on News / Project                          | Cover + gallery item S3 objects are removed.                    |
| Inline Tiptap S3 asset is removed from HTML       | Lazy orphan; not auto-cleaned (admin uses `DELETE /api/v1/media`). |

---

## Summary Table — All Modules

| Module           | Out-of-body media          | Inline media via Tiptap | Tiptap rewrite hook                                  | Multipart endpoints? |
| ---------------- | -------------------------- | ----------------------- | ---------------------------------------------------- | -------------------- |
| About            | ❌                          | ✅ body                  | `AboutService.buildAboutContent`                     | ❌ JSON only          |
| Contact          | ❌                          | ✅ description           | `ContactService.buildContent`                        | ❌ JSON only          |
| Service          | ❌                          | ✅ description           | `ServiceService.buildContent`                        | ❌ JSON only          |
| News             | ✅ cover + gallery          | ✅ description           | `NewsService.buildContent`                           | ❌ JSON only          |
| Projects         | ✅ cover + gallery          | ✅ description           | `ProjectService.buildProject` + `applyUpdate`        | ❌ JSON only          |
| Sound            | ✅ covers + files + brochures + attachments | ✅ description | `SoundTrackService.buildContent`                  | ✅ create / update    |
| Video            | ✅ covers + source / clips  | ✅ description + per-clip| `VideoService.processTiptapHtml`                     | ✅ create / update    |
| Image Collection | ✅ covers + album items     | ✅ description + per-item| `ImageCollectionService.buildContent` + album items | ✅ create / update    |
| Writing          | ✅ covers + per-lang books  | ✅ description           | `WritingService.buildContent` + `mergeContent`       | ✅ create / update    |

**New DB columns this round**: `contact_pages.description_ckb`,
`contact_pages.description_kmr`.

**Dropped DB columns this round**:

- `about_pages`: `hero_image_url`, `hero_media_type`, `hero_thumbnail_url`, `media_gallery`.
- `contact_pages`: `hero_image_url`, `hero_media_type`, `hero_thumbnail_url`, `media_gallery`.
- `services`: `cover_media_url`, `cover_media_type`, `cover_thumbnail_url`.

**Dropped tables**: `service_media_collections`, `service_media_files`.

**Deleted Java classes**:

- `model/service/ServiceMediaCollection.java`
- `model/service/ServiceMediaFile.java`
- `model/service/ServiceMediaFileContent.java`
- `repository/service/ServiceMediaCollectionRepository.java`
- `repository/service/ServiceMediaFileRepository.java`

---

## Key Files

| Concern | File |
| --- | --- |
| Shared upload controller | `khi_app/api/media/MediaController.java` |
| Shared upload service | `khi_app/service/media/MediaService.java` |
| Upload DTOs | `khi_app/dto/media/MediaDtos.java` |
| **Base64 → S3 rewriter (src + href)** | `khi_app/service/media/TiptapHtmlProcessor.java` |
| Low-level S3 | `khi_app/service/S3Service.java` |
| `MediaKind` enum (News / Project only) | `khi_app/enums/MediaKind.java` |
| `MediaItem` POJO (News / Project galleries) | `khi_app/model/media/MediaItem.java` |
| About | `khi_app/model/about/{About,AboutContent,StatItem}.java`, `khi_app/dto/about/AboutDTOs.java`, `khi_app/service/about/AboutService.java`, `khi_app/api/about/AboutController.java` |
| Contact | `khi_app/model/contact/{Contact,ContactContent}.java`, `khi_app/dto/contact/ContactDTOs.java`, `khi_app/service/contact/ContactService.java`, `khi_app/api/contact/ContactController.java` |
| Service | `khi_app/model/service/{Service,ServiceContent,ServiceAuditLog}.java`, `khi_app/dto/service/ServiceDTOs.java`, `khi_app/service/service/ServiceService.java`, `khi_app/api/service/ServiceController.java` |
| News | `khi_app/model/news/{News,NewsContent,NewsCategory,NewsSubCategory,NewsAuditLog}.java`, `khi_app/dto/news/NewsDto.java`, `khi_app/service/news/NewsService.java`, `khi_app/api/news/NewsController.java` |
| Projects | `khi_app/model/project/{Project,ProjectContentBlock,ProjectTag,ProjectKeyword,ProjectLog}.java`, `khi_app/dto/project/{ProjectCreateRequest,ProjectResponse}.java`, `khi_app/service/project/ProjectService.java`, `khi_app/api/project/ProjectController.java` |
| Sound | `khi_app/model/publishment/sound/{SoundTrack,SoundTrackContent,SoundTrackFile,SoundTrackBrochure,SoundTrackAttachment,SoundTrackLog}.java`, `khi_app/dto/publishment/sound/SoundTrackDtos.java`, `khi_app/service/publishment/sound/SoundTrackService.java`, `khi_app/api/publishment/sound/SoundTrackController.java` |
| Video | `khi_app/model/publishment/video/{Video,VideoContent,VideoClipItem,VideoType,VideoLog}.java`, `khi_app/dto/publishment/video/{VideoDTO,VideoMapper,VideoLogDTO}.java`, `khi_app/service/publishment/video/VideoService.java`, `khi_app/api/publishment/video/VideoController.java` |
| Image Collection | `khi_app/model/publishment/image/{ImageCollection,ImageContent,ImageAlbumItem,ImageCollectionLog}.java`, `khi_app/dto/publishment/image/ImageCollectionDTO.java`, `khi_app/service/publishment/image/ImageCollectionService.java`, `khi_app/api/publishment/image/ImageCollectionController.java` |
| Writing | `khi_app/model/publishment/writing/{Writing,WritingContent,WritingLog}.java`, `khi_app/dto/publishment/writing/WritingDtos.java`, `khi_app/service/publishment/writing/WritingService.java`, `khi_app/api/publishment/writing/WritingController.java` |
| PublishmentTopic | `khi_app/model/publishment/topic/PublishmentTopic.java`, `khi_app/api/publishment/topic/PublishmentTopicController.java` |

---

## Migration Order (for replays / new environments)

1. About column drops: `hero_image_url`, `hero_media_type`,
   `hero_thumbnail_url`, `media_gallery`.
2. Contact: drop the same four columns; then
   `ALTER TABLE contact_pages ADD COLUMN description_ckb TEXT,
   ADD COLUMN description_kmr TEXT;`
3. Service: `DROP TABLE service_media_files;
   DROP TABLE service_media_collections;
   ALTER TABLE services DROP COLUMN cover_media_url,
   DROP COLUMN cover_media_type, DROP COLUMN cover_thumbnail_url;`
4. In dev these are auto-applied by Hibernate on the next boot.
5. Deploy the backend with the new `TiptapHtmlProcessor` (which now also
   rewrites `href="data:..."` for documents) and the three rewritten
   modules.
6. Migrate any historic plain-text descriptions into Tiptap HTML if
   needed (wrap in `<p>…</p>`). Existing inline `<img>` / `<video>` /
   `<audio>` tags already work as-is.

---

## Mock Data Seeder — `MockDataSeeder`

A bundled bilingual (Sorani + Kurmanji) seeder lives at
`khi_app/seed/MockDataSeeder.java`. It populates every public-facing
module with realistic content and points at well-known public CDNs for
media so the full stack can be exercised without manual data entry.

### What it inserts

| Module           | Rows | Notes                                                                                                |
| ---------------- | ---- | ---------------------------------------------------------------------------------------------------- |
| About            | 3    | Includes bilingual `body` (Tiptap HTML with inline `<img>` / `<video>` / `<audio>` / `<a>`) + stats. |
| Contact          | 3    | Erbil / Sulaymaniyah / Duhok offices, bilingual `description` with media.                            |
| Service          | 4    | Training / Event / Program / Workshop — each with CKB+KMR `ServiceContent`.                          |
| News             | 5    | Across 3 categories (Culture / History / Literature) and 4 sub-categories.                           |
| Project          | 4    | Mixed `ProjectStatus` (ONGOING / COMPLETED), tags + keywords.                                        |
| PublishmentTopic | 12   | Topics for SOUND / VIDEO / IMAGE / WRITING.                                                          |
| SoundTrack       | 3    | SINGLE folk anthem + MULTI album-of-memories with 3 files & a brochure + a religious SINGLE.        |
| Video            | 3    | One FILM + one VIDEO_CLIP (3 clips) + one interview FILM.                                            |
| ImageCollection  | 3    | GALLERY (4 items) + SINGLE + PHOTO_STORY (5 items).                                                  |
| Writing          | 4    | Two series-linked books + a linguistics textbook + a novel.                                          |

### Media URLs used

- Images — `images.unsplash.com` direct photo URLs.
- Videos — `commondatastorage.googleapis.com/gtv-videos-bucket/sample/*.mp4` (Big Buck Bunny, Sintel, Tears of Steel …).
- Audio  — `soundhelix.com/examples/mp3/SoundHelix-Song-*.mp3` (royalty-free).
- PDF    — `w3.org/.../dummy.pdf` and a public sample PDF.

### How to enable

The seeder is gated on `app.seed.enabled=true` and is **off by default**.

```bash
# Via command-line flag
./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.seed.enabled=true

# Or via environment variable
export APP_SEED_ENABLED=true
./mvnw spring-boot:run
```

### Idempotency

Each per-entity seed method calls `repository.count()` first; if the
table already has rows the section is skipped. So restarting the app
with the flag still enabled is safe — no duplicates will be inserted.

To re-seed, truncate the relevant tables (or drop the DB and let
`hibernate.ddl-auto=update` re-create them on the next boot) and run
again.

### Error isolation

Every per-entity block is wrapped in its own `try/catch` so that a
failure in one module (e.g. a unique-constraint clash on a slug) doesn't
block the rest from inserting.

---

## Cross-cutting Tiptap-Hook Summary

| Service                          | Method(s)                                        | Field(s) processed |
| -------------------------------- | ------------------------------------------------ | ------------------ |
| `AboutService`                   | `buildAboutContent`                              | `AboutContent.body` (ckb + kmr) |
| `ContactService`                 | `buildContent`                                   | `ContactContent.description` (ckb + kmr) |
| `ServiceService`                 | `buildContent`                                   | each `ServiceContent.description` row |
| `NewsService`                    | `buildContent` (called from `applyContentByLanguages`) | `NewsContent.description` (ckb + kmr) |
| `ProjectService`                 | `buildProject` + `applyUpdate`                   | `ProjectContentBlock.description` (ckb + kmr) |
| `SoundTrackService`              | `buildContent` (via `applyContentByLanguages`)   | `SoundTrackContent.description` (ckb + kmr) |
| `VideoService`                   | `processTiptapHtml(Video)`                       | `VideoContent.description` (ckb + kmr) + every `VideoClipItem.descriptionCkb/Kmr` |
| `ImageCollectionService`         | `buildContent` + `buildAlbumItems`               | `ImageContent.description` (ckb + kmr) + every `ImageAlbumItem.descriptionCkb/Kmr` |
| `WritingService`                 | `buildContent` + `mergeContent`                  | `WritingContent.description` (ckb + kmr) |

All modules share the same `TiptapHtmlProcessor` bean
(`ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor`).
