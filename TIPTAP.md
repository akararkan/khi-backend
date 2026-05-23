# KHI Backend — Tiptap as the Single Media Pipeline (Complete Reference)

> **Status:** Applied · `./mvnw compile` passes cleanly.
> **Stack:** Spring Boot 3 · Java 21 · PostgreSQL · AWS S3 · JPA/Hibernate

This is the single source of truth for how rich-text content and the media
that lives inside it are persisted across the platform.

## 0 · The big rule

**About, Contact, and Service no longer accept standalone media uploads.**

Every visual asset that used to be a hero image, hero video, hero audio,
cover image, gallery item, or per-file media row on those three entities
is now embedded **inline inside the bilingual Tiptap description HTML** of
the same row, as one of:

- `<img src="https://…s3…">`
- `<video controls src="…"> <source src="…" …></video>`
- `<audio controls src="…">`
- `<a href="https://…s3…">file.pdf</a>` (for documents and "other files")

The backend then runs `TiptapHtmlProcessor.process(html)` on every save,
which hoists any inline base64 payload to S3 and rewrites the attribute to
the resulting public URL — so the database only ever stores S3 URLs,
never raw binaries.

This applies to **all media kinds** the Tiptap editor can carry:

| Kind                     | Tag the editor produces                        | S3 folder  |
| ------------------------ | ---------------------------------------------- | ---------- |
| Image                    | `<img src=…>`                                  | `images/`  |
| Video                    | `<video src=…>` / `<source src=…>`             | `video/`   |
| Voice / audio            | `<audio src=…>` / `<source src=…>`             | `audio/`   |
| Document / other file    | `<a href=…>` (PDF, DOCX, ZIP, TXT, anything)   | `files/`   |

News and Project still keep their separate `coverUrl` / `coverMediaType`
/ `coverThumbnailUrl` / `mediaGallery` because their public listing card
needs an out-of-body asset. Everything else has moved into the description.

---

## 1 · Shared media upload — `POST /api/v1/media/upload`

Used by every Tiptap editor across the platform. The frontend uploads each
file once, then inserts the returned `fileUrl` into the editor.

### Request — `multipart/form-data`

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | File | Yes | Any media file (image / video / audio / document / other). |
| `type` | String | No | Folder hint: `image`, `audio`, `video`, `document`, `gallery`. Default `image`. |

The `type` part only controls the S3 folder; the editor decides which tag
to emit (`<img>` / `<video>` / `<audio>` / `<a>`).

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

Same shape, multipart part `files` is a list. Useful for drag-and-drop or
"insert multiple images" flows.

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

| Layer | File |
| --- | --- |
| Controller | `khi_app/api/media/MediaController.java` |
| Service | `khi_app/service/media/MediaService.java` |
| DTOs | `khi_app/dto/media/MediaDtos.java` |

---

## 2 · The base64 → S3 rewriter — `TiptapHtmlProcessor`

`src/main/java/ak/dev/khi_backend/khi_app/service/media/TiptapHtmlProcessor.java`

The frontend **should** call `/api/v1/media/upload` and paste the returned
URL into the editor before submit — but it doesn't always. When a Tiptap
user drag-drops, pastes, or screenshot-attaches a file, the editor emits a
`data:` URI directly:

```html
<img src="data:image/png;base64,iVBORw0KGgoAAAA…">
<video><source src="data:video/mp4;base64,…"></video>
<audio src="data:audio/mpeg;base64,…"></audio>
<a href="data:application/pdf;base64,JVBERi0…">policy.pdf</a>
```

If we stored these as-is, the description columns would explode, the CDN
could not cache, and read performance would tank. So the backend intercepts
every description / body write, scans the HTML, extracts every base64
payload, uploads it to S3, and rewrites the attribute to the resulting
public URL **before persisting**.

### What it scans

Two regular expressions, run sequentially over the inbound HTML:

| Pattern              | Matches                                                  | Used for                          |
| -------------------- | -------------------------------------------------------- | --------------------------------- |
| `src="data:…"`       | `<img>`, `<video>`, `<audio>`, `<source>`                | Image / video / voice payloads    |
| `href="data:…"`      | `<a>`                                                    | Documents and any "other file"    |

Single or double quotes both work; the rewriter preserves the original
quote style.

### For each match

1. Base64-decodes the payload.
2. Picks an S3 folder from the MIME type:
   `image/* → images/`, `video/* → video/`, `audio/* → audio/`,
   anything else → `files/`.
3. Uploads via `S3Service.upload(bytes, filename, mime, ProjectMediaType)`.
4. Replaces the matched attribute with `src="https://…s3…"` (or `href=…`).

### Guarantees

- **Idempotent** — HTML that already contains only S3 URLs is returned
  unchanged (early-out on `!html.contains("data:")`).
- **Resilient** — a malformed base64 payload or an S3 failure on **one**
  asset is logged and the original attribute is left in place; the save
  still succeeds for the rest of the document.
- **Null-safe / blank-safe** — `null` and empty strings pass through.
- **MIME → extension table** explicitly maps `jpeg`, `png`, `gif`, `webp`,
  `svg`, `bmp`, `avif`, `mp4`, `webm`, `mov`, `mp3`, `wav`, `ogg`, `weba`,
  `aac`, `flac`, `m4a`, `pdf`, `doc`, `docx`, `xls`, `xlsx`, `ppt`, `pptx`,
  `zip`, `rar`, `7z`, `txt`, `csv`, `json`. Anything else falls back to
  the substring after `/`.

### Recommended Tiptap extensions

| Extension                     | What it produces                                    |
| ----------------------------- | --------------------------------------------------- |
| `@tiptap/extension-image`     | `<img src=…>` (paste, drag-drop)                    |
| custom `Video` node           | `<video controls src=…>` or `<video><source …></video>` |
| custom `Audio` node           | `<audio controls src=…>` (voice memos, music, etc.) |
| custom `FileLink` node        | `<a href=… download>name.ext</a>` (PDFs, docs)      |
| `@tiptap/extension-link`      | external links                                      |
| `@tiptap/extension-table`     | inline tables (optional)                            |

The backend only cares that the resulting HTML uses standard
`<img>` / `<video>` / `<audio>` / `<source>` / `<a>` tags. Inline base64
on any of those is hoisted to S3 on save.

### Where it runs

| Module           | Field rewritten                                                       |
| ---------------- | --------------------------------------------------------------------- |
| About            | `ckbContent.body`, `kmrContent.body`                                  |
| Contact          | `ckbContent.description`, `kmrContent.description`                    |
| Service          | each `serviceContent.description` (CKB / KMR)                         |
| News             | `ckbContent.description`, `kmrContent.description`                    |
| Projects         | `ckbContent.description`, `kmrContent.description`                    |
| Sound            | `soundTrackContent.description`                                       |
| Video            | entity + every `VideoClipItem.descriptionCkb` / `descriptionKmr`      |
| Image Collection | collection-level description + every `ImageAlbumItem.description*`   |
| Writing          | `ckbContent.description`, `kmrContent.description`                    |

GET paths do **not** re-process — the stored HTML is already clean.

---

## 3 · About module — entirely Tiptap-driven

About is now the simplest media model in the codebase: there is no media
model. Everything lives in the bilingual Tiptap body.

### Schema

| Column                  | Type             | Notes                                  |
| ----------------------- | ---------------- | -------------------------------------- |
| `id`                    | BIGSERIAL        | PK                                     |
| `slug_ckb`              | VARCHAR(200)     | unique, required                       |
| `slug_kmr`              | VARCHAR(200)     | unique, nullable                       |
| `active`                | BOOLEAN          | default `true`                         |
| `display_order`         | INT              | default `0`                            |
| `title_ckb` / `_kmr`    | VARCHAR(300)     | per-language title                     |
| `subtitle_ckb` / `_kmr` | VARCHAR(500)     | per-language subtitle                  |
| `meta_description_ckb` / `_kmr` | VARCHAR(2500) | per-language meta description |
| `body_ckb`              | TEXT             | **Tiptap HTML — the only media path** |
| `body_kmr`              | TEXT             | **Tiptap HTML — the only media path** |
| `stats`                 | JSONB            | structured stats array                 |
| `created_at` / `updated_at` | TIMESTAMP    |                                        |

**Removed columns** (from the prior multi-type media model):
`hero_image_url`, `hero_media_type`, `hero_thumbnail_url`, `media_gallery`.

### Entities

`khi_app/model/about/About.java`

```
About
├─ slugCkb / slugKmr
├─ ckbContent : AboutContent { title, subtitle, metaDescription, body }
├─ kmrContent : AboutContent { title, subtitle, metaDescription, body }
└─ stats      : List<StatItem>   ← still JSONB
```

No `heroImageUrl`, no `heroMediaType`, no `heroThumbnailUrl`, no
`mediaGallery`. All media tags live inside `body`.

### DTOs

`khi_app/dto/about/AboutDTOs.java`

- `AboutRequest`         — `slugCkb`, `slugKmr`, `ckbContent`, `kmrContent`, `stats`.
- `AboutContentRequest`  — `title`, `subtitle`, `metaDescription`, `body` (Tiptap HTML).
- `StatItemDto`          — `labelCkb`, `labelKmr`, `value`.
- `AboutResponse`        — same fields back.
- `AboutContentResponse` — same fields back.

Notably absent: every hero / thumbnail / gallery field, and the legacy
`UploadResponse` (clients use the shared `/api/v1/media/upload`).

### Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| GET    | `/api/v1/about`        | All active. |
| GET    | `/api/v1/about/{slug}` | By slug (CKB or KMR). |
| POST   | `/api/v1/about`        | Create. JSON body — no multipart. |
| PUT    | `/api/v1/about/{id}`   | Update. JSON body — no multipart. |
| DELETE | `/api/v1/about/{id}`   | Hard delete. |

There is **no longer** a `POST /api/v1/about/upload` or
`DELETE /api/v1/about/media` endpoint — use the shared
`/api/v1/media/upload` and `/api/v1/media` paths.

### Tiptap rewrite hook

`AboutService.buildAboutContent(AboutContentRequest)` calls
`tiptapHtmlProcessor.process(req.getBody())` on both create and update,
for both languages.

### Service layer

`AboutService` injects only `AboutRepository` and `TiptapHtmlProcessor`.
There is no `S3Service` injection, no `buildGallery(...)`, no per-update
S3 cleanup, and no hero / thumbnail bookkeeping.

---

## 4 · Contact module — gained a Tiptap description

Previously, Contact had only structured fields (title, subtitle, address,
working hours). The contact page now also carries a bilingual Tiptap
**description** per language, which is the **only** place media is
attached.

### Schema

| Column                          | Type           | Notes                              |
| ------------------------------- | -------------- | ---------------------------------- |
| `id`                            | BIGSERIAL      | PK                                 |
| `slug_ckb`                      | VARCHAR(200)   | unique, required                   |
| `slug_kmr`                      | VARCHAR(200)   | unique, nullable                   |
| `active`                        | BOOLEAN        | default `true`                     |
| `display_order`                 | INT            | default `0`                        |
| `title_ckb` / `_kmr`            | VARCHAR(300)   |                                    |
| `subtitle_ckb` / `_kmr`         | VARCHAR(500)   |                                    |
| `address_ckb` / `_kmr`          | VARCHAR(500)   |                                    |
| `working_hours_ckb` / `_kmr`    | VARCHAR(300)   |                                    |
| `description_ckb`               | TEXT           | **Tiptap HTML — the only media path** |
| `description_kmr`               | TEXT           | **Tiptap HTML — the only media path** |
| `phone`, `secondary_phone`      | VARCHAR(60)    |                                    |
| `email`                         | VARCHAR(200)   |                                    |
| `map_embed_url`                 | TEXT           |                                    |
| `latitude`, `longitude`         | DOUBLE         |                                    |
| `created_at` / `updated_at`     | TIMESTAMP      |                                    |

**Added columns**: `description_ckb`, `description_kmr`.
**Removed columns**: `hero_image_url`, `hero_media_type`,
`hero_thumbnail_url`, `media_gallery`.

### Entities

`khi_app/model/contact/Contact.java`

```
Contact
├─ slugCkb / slugKmr
├─ ckbContent : ContactContent { title, subtitle, address, workingHours, description }
├─ kmrContent : ContactContent { title, subtitle, address, workingHours, description }
└─ phone, secondaryPhone, email, mapEmbedUrl, latitude, longitude
```

`description` is Tiptap HTML; everything else is plain text.

### DTOs

`khi_app/dto/contact/ContactDTOs.java`

- `ContactRequest`        — slugs, bilingual content (with `description`), contact details.
- `ContactContentRequest` — `title`, `subtitle`, `address`, `workingHours`, `description`.
- `ContactResponse`       — same fields back.
- `ContactContentResponse` — same fields back.

Notably absent: every hero / thumbnail / gallery field, and the legacy
`UploadResponse`.

### Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| GET    | `/api/v1/contact`              | All (admin — includes inactive). |
| GET    | `/api/v1/contact/active`       | All active. |
| GET    | `/api/v1/contact/{id}`         | By id. |
| GET    | `/api/v1/contact/slug/{slug}`  | By slug (CKB or KMR). |
| POST   | `/api/v1/contact`              | Create. JSON. |
| PUT    | `/api/v1/contact/{id}`         | Update. JSON. |
| DELETE | `/api/v1/contact/{id}`         | Hard delete. |

There is **no longer** a `POST /api/v1/contact/upload` or
`DELETE /api/v1/contact/media` endpoint.

### Tiptap rewrite hook

`ContactService.buildContent(ContactContentRequest)` calls
`tiptapHtmlProcessor.process(req.getDescription())` on both create and
update, for both languages.

### Service layer

`ContactService` injects only `ContactRepository` and
`TiptapHtmlProcessor`. There is no `S3Service` injection, no
`buildGallery`, no hero / thumbnail bookkeeping, no `uploadMedia` / 
`deleteMedia` methods.

---

## 5 · Service module — single description path

Service still keeps its separate-row bilingual content model
(`service_contents` joined by `language_code`), but the entire normalised
media model (`service_media_collections` + `service_media_files`) has
been **removed**. The bilingual `description` on each `ServiceContent`
row is now the only place media is attached to a service.

### Schema

| Column                      | Type          | Notes                                 |
| --------------------------- | ------------- | ------------------------------------- |
| `services.id`               | BIGSERIAL     | PK                                    |
| `services.service_type`     | VARCHAR(100)  | required                              |
| `services.location`         | VARCHAR(200)  | nullable                              |
| `services.active`           | BOOLEAN       | default `true`                        |
| `services.published_at`     | TIMESTAMP     | nullable                              |
| `services.created_at` / `_at` | TIMESTAMP   |                                       |
| `service_contents.id`       | BIGSERIAL     | PK                                    |
| `service_contents.language_code` | VARCHAR(10) | `CKB` or `KMR`                       |
| `service_contents.title`    | VARCHAR(300)  |                                       |
| `service_contents.description` | TEXT       | **Tiptap HTML — the only media path** |
| `service_contents.service_id`  | BIGINT     | FK                                    |
| `service_audit_logs.*`      | …             | unchanged                             |

**Removed columns**: `services.cover_media_url`, `services.cover_media_type`,
`services.cover_thumbnail_url`.

**Dropped tables**: `service_media_collections`, `service_media_files`.

### Entities

```
Service
├─ serviceType, location, active, publishedAt, timestamps
└─ contents : Set<ServiceContent>
                ├─ languageCode ("CKB" | "KMR")
                ├─ title
                └─ description  ← Tiptap HTML
```

**Deleted classes** (Java files removed from the repo):

- `khi_app/model/service/ServiceMediaCollection.java`
- `khi_app/model/service/ServiceMediaFile.java`
- `khi_app/model/service/ServiceMediaFileContent.java`
- `khi_app/repository/service/ServiceMediaCollectionRepository.java`
- `khi_app/repository/service/ServiceMediaFileRepository.java`

### DTOs

`khi_app/dto/service/ServiceDTOs.java`

- `ServiceRequest`         — `serviceType`, `location`, `publishedAt`, `contents`.
- `ServiceContentRequest`  — `languageCode`, `title`, `description` (Tiptap HTML).
- `ServiceResponse`        — `id`, `serviceType`, `location`, `active`, `publishedAt`, `contents[]`, timestamps.
- `ServiceContentResponse` — `id`, `languageCode`, `title`, `description`.

**Removed DTOs**:
`ServiceMediaCollectionRequest`, `ServiceMediaCollectionResponse`,
`ServiceMediaFileRequest`, `ServiceMediaFileResponse`,
`FileContentRequest`, `FileContentResponse`,
`CollectionUpsertRequest`, `FileAddRequest`,
`UploadResponse` (clients use the shared `/api/v1/media/upload`).

**Removed fields on `ServiceRequest`/`Response`**:
`coverMediaUrl`, `coverMediaType`, `coverThumbnailUrl`,
`mediaCollections`.

### Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| GET    | `/api/v1/services`                       | Active, paginated. |
| GET    | `/api/v1/services?type={t}`              | Filter active by type. |
| GET    | `/api/v1/services/all`                   | Admin: all, paginated. |
| GET    | `/api/v1/services/{id}`                  | Single service (full detail). |
| GET    | `/api/v1/services/types`                 | Distinct service types. |
| GET    | `/api/v1/services/search?q=…`            | Global search. |
| GET    | `/api/v1/services/search/admin?q=…`      | Admin search. |
| POST   | `/api/v1/services`                       | Create (JSON only). |
| PUT    | `/api/v1/services/{id}`                  | Update (JSON only). |
| PATCH  | `/api/v1/services/{id}/active?value=…`   | Soft toggle. |
| DELETE | `/api/v1/services/{id}`                  | Hard delete. |
| DELETE | `/api/v1/services/bulk`                  | Bulk delete. |

**Removed endpoints**:

- `POST   /api/v1/services/with-files`
- `PUT    /api/v1/services/{id}/with-files`
- `POST   /api/v1/services/{serviceId}/collections`
- `PUT    /api/v1/services/{serviceId}/collections/{colId}`
- `DELETE /api/v1/services/{serviceId}/collections/{colId}`
- `POST   /api/v1/services/collections/{colId}/files`
- `DELETE /api/v1/services/files/{fileId}`
- `POST   /api/v1/services/upload`
- `POST   /api/v1/services/upload/multiple`
- `DELETE /api/v1/services/upload?fileUrl=…`

### Tiptap rewrite hook

`ServiceService.buildContent(ServiceContentRequest)` calls
`tiptapHtmlProcessor.process(req.getDescription())` on every create and
update, for every language row.

### Service layer

`ServiceService` injects only:

- `ServiceRepository`
- `ServiceAuditLogRepository`
- `TiptapHtmlProcessor`

`S3Service` and `MediaMetadataExtractor` are no longer dependencies of the
Service module. Per-update / per-delete S3 orphan cleanup, parallel file
uploads, collection / file management, and the multipart entry points are
all gone.

---

## 6 · News module (unchanged from prior migration)

News still has a standalone `coverUrl` / `coverMediaType` /
`coverThumbnailUrl` / `mediaGallery` (it needs out-of-body assets on the
listing card). Inside the body, media still flows through
`TiptapHtmlProcessor` exactly as before. No code changed for this module
in this round.

See the prior section in git history for details.

---

## 7 · Projects module (unchanged from prior migration)

Projects also keep their separate cover + gallery for the listing card.
Inline media still goes through `TiptapHtmlProcessor`. No code changed in
this round.

---

## 8 · Sound / Video / Image / Writing modules

Unchanged. All four modules already used `TiptapHtmlProcessor` as the
single entry-point for description / clip / item HTML.

---

## End-to-End Flow (About, Contact, Service)

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
     │  (fallback) user pastes / drag-drops content
     │  → editor produces  src="data:..."  or  href="data:..."
     │
     ▼
POST/PUT  /api/v1/{about|contact|services}
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
     │      AboutContent.builder()
     │          .title(…)
     │          .body(tiptapHtmlProcessor.process(req.getBody()))
     │          .build();
     │
     │      ContactContent.builder()
     │          .description(tiptapHtmlProcessor.process(req.getDescription()))
     │          ……
     │
     │      ServiceContent.builder()
     │          .description(tiptapHtmlProcessor.process(req.getDescription()))
     │          ……
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
   about_pages.body_ckb / body_kmr
   contact_pages.description_ckb / description_kmr
   service_contents.description
       contain only S3 URLs — no base64 payloads, no inline binaries.

GET /api/v1/{about|contact|services}/{id}
     ▼
   toResponse(...) returns the stored HTML verbatim — the frontend renders
   the S3 URLs directly. The browser fetches each image / video / audio /
   PDF from S3 via its public URL.
```

---

## JSON Examples

### About — `POST /api/v1/about`

```json
{
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",
  "ckbContent": {
    "title": "دەربارەی ئێمە",
    "subtitle": "زانیاری گشتی",
    "metaDescription": "…",
    "body": "<h1>دەربارە</h1><p>…</p><img src=\"https://…/images/team.jpg\" alt=\"team\"><video controls src=\"https://…/video/intro.mp4\"></video><audio controls src=\"https://…/audio/voice.mp3\"></audio><p><a href=\"https://…/files/brochure.pdf\" download>پەڕەی ناساندن</a></p>"
  },
  "kmrContent": {
    "title": "Derbarê me",
    "subtitle": "Agahiyên giştî",
    "metaDescription": "…",
    "body": "<h1>Derbarê me</h1><p>…</p><img src=\"https://…/images/team.jpg\"><p><a href=\"https://…/files/brochure.pdf\">Pelê ragihandinê</a></p>"
  },
  "stats": [
    { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,000+" },
    { "labelCkb": "ساڵ",  "labelKmr": "Sal",    "value": "15+"    }
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
    "description": "<p>بەخێرهاتن</p><img src=\"https://…/images/lobby.jpg\"><video controls src=\"https://…/video/office-tour.mp4\"></video><audio controls src=\"https://…/audio/welcome.mp3\"></audio><p><a href=\"https://…/files/directions.pdf\">ڕێگاکان</a></p>"
  },
  "kmrContent": {
    "title": "Têkilî",
    "subtitle": "Em li xizmeta we ne",
    "address": "Hewlêr",
    "workingHours": "09:00–17:00",
    "description": "<p>Bi xêr hatî</p><img src=\"https://…/images/lobby.jpg\"><p><a href=\"https://…/files/directions.pdf\">Rê</a></p>"
  },
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
    {
      "languageCode": "KMR",
      "title": "Karûbarê mîmarî",
      "description": "<p>Destpêk</p><img src=\"https://…/images/blueprint.jpg\"><p><a href=\"https://…/files/portfolio.pdf\">Portfolyo</a></p>"
    }
  ]
}
```

Response mirrors the request, with extra fields: `id`, `active`,
`createdAt`, `updatedAt` (and `contents[].id` per content row).

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
| MIME unknown (`application/octet-stream`)         | Folder falls back to `files/`, extension to the suffix after `/` or `bin`. |

---

## Summary Table

| Module     | Standalone media | Tiptap rewrite hook                         | Endpoints removed in this round |
| ---------- | ---------------- | ------------------------------------------- | ------------------------------- |
| About      | ❌ — body only    | `AboutService.buildAboutContent`            | `/about/upload`, `/about/media` were already gone; now hero/gallery JSON fields gone too. |
| Contact    | ❌ — description only | `ContactService.buildContent`           | `POST /contact/upload`, `DELETE /contact/media`. |
| Service    | ❌ — description only | `ServiceService.buildContent`           | `with-files`, `/collections`, `/files`, `/upload`, `/upload/multiple`, delete upload. |
| News       | ✅ cover + gallery | `NewsService.buildContent`                 | (unchanged)                     |
| Projects   | ✅ cover + gallery | `ProjectService.buildProject` + `applyUpdate` | (unchanged)                 |
| Sound      | structured only   | `SoundTrackService.buildContent`           | (unchanged)                     |
| Video      | structured only   | `VideoService.processTiptapHtml`           | (unchanged)                     |
| Image      | structured only   | `ImageCollectionService.buildContent`      | (unchanged)                     |
| Writing    | structured only   | `WritingService.buildContent` / `mergeContent` | (unchanged)                 |

**New DB columns**: `contact_pages.description_ckb`, `contact_pages.description_kmr`.

**Dropped DB columns**:

- `about_pages.hero_image_url`, `hero_media_type`, `hero_thumbnail_url`, `media_gallery`.
- `contact_pages.hero_image_url`, `hero_media_type`, `hero_thumbnail_url`, `media_gallery`.
- `services.cover_media_url`, `cover_media_type`, `cover_thumbnail_url`.

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
| About | `khi_app/model/about/{About,AboutContent,StatItem}.java`, `khi_app/dto/about/AboutDTOs.java`, `khi_app/service/about/AboutService.java`, `khi_app/api/about/AboutController.java` |
| Contact | `khi_app/model/contact/{Contact,ContactContent}.java`, `khi_app/dto/contact/ContactDTOs.java`, `khi_app/service/contact/ContactService.java`, `khi_app/api/contact/ContactController.java` |
| Service | `khi_app/model/service/{Service,ServiceContent,ServiceAuditLog}.java`, `khi_app/dto/service/ServiceDTOs.java`, `khi_app/service/service/ServiceService.java`, `khi_app/api/service/ServiceController.java` |
| News entity / DTOs | `khi_app/model/news/{News,NewsContent}.java`, `khi_app/dto/news/NewsDto.java` |
| Project entity / DTOs | `khi_app/model/project/{Project,ProjectContentBlock}.java`, `khi_app/dto/project/{ProjectCreateRequest,ProjectResponse}.java` |

---

## Migration Order (for replays / new environments)

1. Apply the About column drops: `hero_image_url`, `hero_media_type`,
   `hero_thumbnail_url`, `media_gallery`.
2. Apply the Contact column drops: same four columns; then add
   `description_ckb TEXT`, `description_kmr TEXT`.
3. Drop the Service media tables and columns:
   `DROP TABLE service_media_files; DROP TABLE service_media_collections;
   ALTER TABLE services DROP COLUMN cover_media_url, DROP COLUMN cover_media_type,
   DROP COLUMN cover_thumbnail_url;`.
4. In dev these are auto-applied by Hibernate on the next boot.
5. Deploy the backend with the new `TiptapHtmlProcessor` (which now also
   rewrites `href="data:..."` for documents) and the three rewritten
   modules.
6. Migrate any historic plain-text descriptions into Tiptap HTML if needed
   (wrap in `<p>…</p>`). Existing inline `<img>` / `<video>` / `<audio>`
   tags already work as-is.
