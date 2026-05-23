# TIPTAP_MIGRATION

# KHI Backend — Tiptap & Multi-Type Media (Complete Reference)

> **Status:** Applied · `./mvnw compile` passes cleanly.
> **Stack:** Spring Boot 3 · Java 21 · PostgreSQL · AWS S3 · JPA/Hibernate

Every public-facing module that previously stored rich content as a tree of
typed `*_media` rows or `*_blocks` now stores it as a single Tiptap HTML
string per language. Inline images / audio / video are uploaded to S3 and
the editor bakes the resulting URLs straight into the HTML.

Beside the in-body media, every public entity now also exposes a
**typed hero/cover field** (image, video, or audio) and an
**out-of-body `mediaGallery`** of mixed `MediaItem`s, so the frontend can
render images, videos, and audios side-by-side without parsing HTML.

This document is the single source of truth for the new shape. It covers:

1. The shared media upload endpoint.
2. The shared `MediaKind` enum and `MediaItem` JSONB POJO.
3. The automatic server-side base64 → S3 rewriter (`TiptapHtmlProcessor`).
4. Per-module table / entity / DTO / endpoint changes — **including the
   new multi-type media fields** on About, Contact, News, Project, Service.
5. The exact end-to-end flow for any “description” or “body” field.

---

## 0 · Shared Media Upload — `POST /api/v1/media/upload`

A single neutral endpoint used by **every** Tiptap editor across the
platform (News, Projects, About, Services, Sound, Video, Image, Writing).
Now also used by every entity that needs an out-of-body hero / cover /
gallery asset (image, video, **or** audio).

### Request — `multipart/form-data`

| Part | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | File | Yes | Any media file |
| `type` | String | No | Folder hint: `image`, `audio`, `video`, `document`, `gallery`. Default `image`. |

> The `type` hint controls only the S3 folder. The backend never trusts the
> client's content-type for the playback `kind` — the frontend sets
> `MediaKind` explicitly on each entity's hero/cover field or
> `MediaItem.kind` in a gallery.

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
“insert multiple images” flows, and for populating a `mediaGallery` in one
shot.

### Cleanup — `DELETE /api/v1/media?fileUrl=…`

Removes a previously uploaded asset from S3. Used by the admin when a known
asset is no longer needed; orphan cleanup is otherwise lazy.

### S3 layout

Driven by `S3Service` and resolved by `MediaService`:

```
<bucket>/<baseFolder=khi-web-folders>/
    images/<uuid>-<sanitized-name>
    video/<uuid>-<sanitized-name>
    audio/<uuid>-<sanitized-name>
    files/<uuid>-<sanitized-name>
```

Implementation:

| Layer | File |
| --- | --- |
| Controller | `khi_app/api/media/MediaController.java` |
| Service | `khi_app/service/media/MediaService.java` |
| DTOs | `khi_app/dto/media/MediaDtos.java` |

---

## 1 · Shared multi-type media types

Two small shared types let every entity carry image / video / audio without
each module reinventing the schema.

### `MediaKind` (enum)

`ak.dev.khi_backend.khi_app.enums.MediaKind`

```
IMAGE   // <img>
VIDEO   // <video>   — thumbnailUrl used as poster
AUDIO   // <audio>   — thumbnailUrl used as cover art
```

Mirrors `ServiceMediaCollection.MediaType` (which Service still uses for
its detailed media-files model) but lives at a neutral package so it can
be shared by the JSONB-backed lightweight galleries.

### `MediaItem` (JSONB element)

`ak.dev.khi_backend.khi_app.model.media.MediaItem` — plain POJO, no JPA
annotations, persisted as one element of a `jsonb` column.

```json
{
  "url":          "https://cdn/.../bar.webm",
  "kind":         "VIDEO",
  "thumbnailUrl": "https://cdn/.../bar-poster.jpg",
  "captionCkb":   "وەسف",
  "captionKmr":   "Şirove",
  "sortOrder":    0
}
```

| Field | Meaning |
| --- | --- |
| `url` | S3 / CDN URL of the asset (required). |
| `kind` | `IMAGE` \| `VIDEO` \| `AUDIO` — drives the player on the frontend. Defaults to `IMAGE` on the server if missing. |
| `thumbnailUrl` | Optional poster (VIDEO) or cover art (AUDIO); ignored for IMAGE. |
| `captionCkb` / `captionKmr` | Bilingual caption shown under the asset. |
| `sortOrder` | Display order inside the gallery (ascending). |

### Per-entity media fields (added)

Each entity that previously had a single image URL now also exposes:

| Field | Purpose |
| --- | --- |
| `<hero/cover>Url` | single primary asset URL (existing column, kept) |
| `<hero/cover>MediaType` | `MediaKind` — `IMAGE` \| `VIDEO` \| `AUDIO`. Defaults to `IMAGE` so legacy rows still render as images. |
| `<hero/cover>ThumbnailUrl` | poster (VIDEO) or cover art (AUDIO); ignored for IMAGE. |
| `mediaGallery` *(JSONB list of `MediaItem`)* | mixed-type gallery rendered beside the hero/cover. |

The legacy column names (`hero_image_url`, `cover_url`, `cover_media_url`)
are **kept**; only their interpretation widens. Set `MediaKind.VIDEO` and
the URL is treated as a video — no rename needed.

---

## 2 · Server-side base64 → S3 rewriter (`TiptapHtmlProcessor`)

The frontend should call `/api/v1/media/upload` and paste the returned URL
into the editor before submit — but it doesn’t always. When a Tiptap user
**drag-drops, pastes, or screenshot-attaches** an image, the editor produces

```html
<img src="data:image/png;base64,iVBORw0KGgoAAAA…">
```

If we stored that as-is we’d blow up the `description_*` TEXT columns, kill
read performance, and make CDN caching impossible.

So the backend has a **safety net** that intercepts every description / body
write, scans the HTML, extracts every base64 data URI, uploads it to S3,
and rewrites the `src` to the resulting public URL **before persisting**.

### File

`src/main/java/ak/dev/khi_backend/khi_app/service/media/TiptapHtmlProcessor.java`

### Behaviour

- Scans for `src="data:<mime>;base64,<payload>"` (single or double quotes) on
  `<img>`, `<video>`, `<audio>`, `<source>` — anywhere `src=` carries a data URI.
- For each match:
    1. Base64-decodes the payload.
    2. Picks an S3 folder from the MIME type (`image/*` → `images/`, `video/*` →
       `video/`, `audio/*` → `audio/`, anything else → `files/`).
    3. Uploads via `S3Service.upload(bytes, filename, mime, ProjectMediaType)`.
    4. Replaces `src="data:…"` with `src="https://<bucket>.s3.<region>.amazonaws.com/…"`.
- **Idempotent**: HTML that already contains only S3 URLs is returned unchanged
  (early-out on `!html.contains("data:")`).
- **Resilient**: a malformed base64 payload or an S3 failure on **one** asset is
  logged and the original `src` is left in place — the save still succeeds.
- **Null-safe / blank-safe**: passes through `null` and empty strings.
- **MIME → extension table**: `jpeg`, `png`, `gif`, `webp`, `svg`, `bmp`, `avif`,
  `mp4`, `webm`, `mov`, `mp3`, `wav`, `ogg`, `weba` are explicitly mapped; anything
  else falls back to the substring after `/`.

| MIME prefix     | S3 folder | `MediaKind` analogue |
|-----------------|-----------|----------------------|
| `image/*`       | `images/` | IMAGE |
| `video/*`       | `video/`  | VIDEO |
| `audio/*`       | `audio/`  | AUDIO |
| anything else   | `files/`  | (DOCUMENT) |

### Recommended TipTap extensions

| Extension                     | What it produces |
|-------------------------------|------------------|
| `@tiptap/extension-image`     | `<img src=…>` (paste, drag-drop) |
| custom `Video` node           | `<video controls src=…>` or `<video><source src=… type=…></video>` |
| custom `Audio` node           | `<audio controls src=…>` |
| `@tiptap/extension-link`      | external links |
| `@tiptap/extension-table`     | inline tables (optional) |

For Video and Audio you'll typically want a small custom extension because
TipTap doesn't ship one. The backend cares only that the resulting HTML has
standard `<video>` / `<audio>` / `<source>` tags with `src` pointing at S3
— `TiptapHtmlProcessor` will hoist any inline base64 to S3 on save.

### Where it runs

`TiptapHtmlProcessor.process(html)` is called inside every service that builds
or merges a content entity from a request DTO. After the call, the entity’s
description (or body) field holds **only** S3 URLs — never `data:` payloads.

GET paths do not re-process — the stored HTML is already clean.

---

## 3 · News Module

### Schema

- **Dropped:** `news_media` table and every column on it. All inline images / audio /
  video now live inside `news.description_ckb` / `news.description_kmr`.
- **Kept as-is:** `news.cover_url`, all category / sub-category / tag / keyword tables.
- The `description_ckb` and `description_kmr` columns are already `TEXT`.
- **Added (multi-type media):**
    - `cover_media_type VARCHAR(16)` — `MediaKind`, defaults to `IMAGE`.
    - `cover_thumbnail_url VARCHAR(1024)` — poster / cover art for non-image covers.
    - `media_gallery JSONB` — list of `MediaItem`s rendered beside the cover.

### Entities

`khi_app/model/news/News.java`

- `coverUrl` (length 1024) — set from a pre-uploaded S3 URL. May now be an
  image, video, or audio URL.
- `coverMediaType: MediaKind` — defaults to `IMAGE`.
- `coverThumbnailUrl` (length 1024) — poster (VIDEO) or cover art (AUDIO).
- `mediaGallery: List<MediaItem>` — JSONB column `media_gallery`.
- `ckbContent` / `kmrContent` — `@Embedded NewsContent` with `title` + Tiptap HTML `description`.
- No `media` collection. No `NewsMedia` / `NewsMediaType` classes (deleted).

### DTOs

`khi_app/dto/news/NewsDto.java`

- `coverUrl: String`
- `coverMediaType: MediaKind` *(new)*
- `coverThumbnailUrl: String` *(new)*
- `mediaGallery: List<MediaItem>` *(new)*
- `ckbContent / kmrContent: LanguageContentDto { title, description }` — `description` is Tiptap HTML.
- `tags / keywords: BilingualSet { ckb, kmr }`
- No `MediaDto`, no `media[]`.

### Endpoints

| Method | Path | Status | Notes |
| --- | --- | --- | --- |
| POST | `/api/v1/news` | Kept | Plain JSON, cover URL pre-uploaded. |
| POST | `/api/v1/news/bulk` | Kept | Plain JSON array. |
| GET | `/api/v1/news` and `/{id}` | Kept | Returns stored HTML verbatim. |
| PUT | `/api/v1/news/{id}` | Kept | Plain JSON. |
| DELETE | `/api/v1/news/{id}` / `/bulk` | Kept | Cascade-deletes content; S3 orphans are lazy. |
| POST | `/api/v1/news/with-files` | Removed | Replaced by JSON + `/api/v1/media/upload`. |
| PUT | `/api/v1/news/{id}/with-files` | Removed | Same reason. |

### Tiptap rewrite hook

`NewsService.buildContent(LanguageContentDto)` runs `tiptapHtmlProcessor.process(...)`
before constructing the `NewsContent` embeddable. Used by both `addNews`,
`addNewsBulk`, and `updateNews` via `applyContentByLanguages(...)`.

`NewsService.buildGallery(...)` normalises each `MediaItem` (trims URL,
defaults `kind` to `IMAGE`, assigns a `sortOrder` when missing) and stable-
sorts the list by `sortOrder` before persisting.

---

## 4 · Projects Module

### Schema

- **Dropped:** `project_media`, `project_contents` (and any CKB/KMR join tables for
  the named content blocks). All inline assets and “named content sections” now
  live inside `projects.description_ckb` / `projects.description_kmr`.
- **Kept as-is:** `projects.cover_url`, project tags / keywords, status, dates.
- **Added (multi-type media):**
    - `cover_media_type VARCHAR(16)` — `MediaKind`, defaults to `IMAGE`.
    - `cover_thumbnail_url VARCHAR(1024)`.
    - `media_gallery JSONB`.

### Entities

`khi_app/model/project/Project.java`

- `coverUrl` (length 1024) — image, video, or audio URL.
- `coverMediaType: MediaKind` — defaults to `IMAGE`.
- `coverThumbnailUrl` (length 1024).
- `mediaGallery: List<MediaItem>` — JSONB column `media_gallery`.
- `ckbContent` / `kmrContent` — `@Embedded ProjectContentBlock` with `title`,
  `description` (Tiptap HTML), `location`.
- No `media`, no `contentsCkb`/`contentsKmr` lists.

### DTOs

`khi_app/dto/project/ProjectCreateRequest.java` and `ProjectResponse.java`

- `coverUrl`, `projectTypeCkb`, `projectTypeKmr`, `status`, `projectDate`.
- `coverMediaType: MediaKind` *(new)*
- `coverThumbnailUrl: String` *(new)*
- `mediaGallery: List<MediaItem>` *(new)*
- `ckbContent / kmrContent: ProjectContentBlockDto { title, description, location }` — `description` is Tiptap HTML.
- `tagsCkb / tagsKmr / keywordsCkb / keywordsKmr`.

### Endpoints

| Method | Path | Status |
| --- | --- | --- |
| POST | `/api/v1/projects/create` | Kept (plain JSON) |
| PUT | `/api/v1/projects/update/{id}` | Kept (plain JSON) |
| GET | listing / by-id / search | Kept |
| DELETE | `/api/v1/projects/{id}` | Kept |
| POST | `/api/v1/projects/with-files` | Removed |
| PUT | `/api/v1/projects/update/{id}/with-files` | Removed |

### Tiptap rewrite hook

`ProjectService.buildProject(...)` (create) and `applyUpdate(...)` (update) both
wrap `dto.getCkbContent().getDescription()` / `getKmrContent().getDescription()`
with `tiptapHtmlProcessor.process(...)` before building the embeddable.
Both paths also call `buildGallery(...)` to normalise the mixed-type gallery.

---

## 5 · About Module

### Schema

- **Dropped:** `about_blocks` and every column it had (`content_text_*`, `title_*`,
  `alt_text_*`, `media_url`, `thumbnail_url`, `metadata`, `sequence`, `content_type`).
- **Added on `about_pages`:**
    - `body_ckb TEXT` — Tiptap HTML body, Sorani.
    - `body_kmr TEXT` — Tiptap HTML body, Kurmanji.
    - `stats JSONB` — structured stats array.
    - **`hero_media_type VARCHAR(16)`** — `MediaKind`, defaults to `IMAGE`.
    - **`hero_thumbnail_url VARCHAR(1000)`** — poster / cover art for non-image heroes.
    - **`media_gallery JSONB`** — list of `MediaItem`s rendered beside the hero.
- **Kept:** `hero_image_url`. May now be an image, video, or audio URL.

### Entities

`khi_app/model/about/About.java`

- `slugCkb` (unique, required) · `slugKmr` (unique, nullable).
- `heroImageUrl` (length 1000) — image, video, or audio URL.
- `heroMediaType: MediaKind` — defaults to `IMAGE`.
- `heroThumbnailUrl` (length 1000).
- `mediaGallery: List<MediaItem>` — JSONB column `media_gallery`.
- `ckbContent` / `kmrContent` — `@Embedded AboutContent { title, subtitle, metaDescription, body }`.
  `body` is Tiptap HTML.
- `stats` — `List<StatItem>` persisted as JSONB via `@JdbcTypeCode(SqlTypes.JSON)`.

`StatItem`: `{ labelCkb, labelKmr, value }`.

`AboutBlock`, `AboutBlockContent`, `ContentType` (TEXT / IMAGE / VIDEO / AUDIO /
GALLERY / QUOTE / STATS) are all deleted. STATS survives as the JSONB column,
not as a block type. The multi-type media use-case (image alongside video and
audio) is now covered by `mediaGallery`.

### DTOs

`khi_app/dto/about/AboutDTOs.java` — `AboutRequest`, `AboutContentRequest`,
`StatItemDto`, `AboutResponse`, `AboutContentResponse`. The request and
response now also carry `heroMediaType`, `heroThumbnailUrl`, and
`mediaGallery`. No `blocks[]` anywhere.

### Endpoints

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/v1/about` (all active) | Kept |
| GET | `/api/v1/about/{slug}` (by slug) | Kept |
| POST | `/api/v1/about` | Kept (body shape changed) |
| PUT | `/api/v1/about/{id}` | Kept (body shape changed) |
| DELETE | `/api/v1/about/{id}` | Kept |

Hero / inline media uploads go through the shared `/api/v1/media/upload`.
The previous `POST /api/v1/about/upload` and `DELETE /api/v1/about/media`
endpoints remain operational for backward compatibility, but new clients
should use the shared endpoint.

### Tiptap rewrite hook + gallery normalisation

`AboutService.buildAboutContent(AboutContentRequest)` runs
`tiptapHtmlProcessor.process(req.getBody())` before constructing the
`AboutContent` embeddable. Hit on both create and update.

`AboutService.buildGallery(...)` is invoked on create and update and:
- drops items with blank URLs,
- defaults `kind` to `IMAGE` when null,
- assigns a `sortOrder` when missing,
- stable-sorts the list by `sortOrder`.

On update and delete, S3 orphans are cleaned: the previous `heroImageUrl`,
`heroThumbnailUrl`, and every `mediaGallery` item's `url` + `thumbnailUrl`
are removed from S3 if they changed or the row is being deleted.

---

## 6 · Contact Module

Contact has no rich-text Tiptap field, but it **does** now have multi-type
hero media + a gallery — same shape as About.

### Schema

- **Added on `contact_pages`:**
    - **`hero_media_type VARCHAR(16)`** — `MediaKind`, defaults to `IMAGE`.
    - **`hero_thumbnail_url VARCHAR(1000)`**.
    - **`media_gallery JSONB`**.
- **Kept:** `hero_image_url`. May now be image, video, or audio.

### Entities

`khi_app/model/contact/Contact.java`

- `heroImageUrl` (length 1000) — image, video, or audio URL.
- `heroMediaType: MediaKind` — defaults to `IMAGE`.
- `heroThumbnailUrl` (length 1000).
- `mediaGallery: List<MediaItem>` — JSONB column `media_gallery`.

### DTOs

`khi_app/dto/contact/ContactDTOs.java` — `ContactRequest` and
`ContactResponse` both carry the new fields. The bilingual `ContactContent`
(title, subtitle, address, workingHours) is unchanged.

### Endpoints

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/v1/contact` (list) | Kept |
| GET | `/api/v1/contact/{slug}` | Kept |
| POST | `/api/v1/contact` | Kept (body widened) |
| PUT | `/api/v1/contact/{id}` | Kept (body widened) |
| DELETE | `/api/v1/contact/{id}` | Kept |

### Gallery normalisation + S3 cleanup

`ContactService.buildGallery(...)` mirrors the About/News/Project version.
Update and delete remove orphan hero, hero thumbnail, and every gallery
item's `url` + `thumbnailUrl` from S3.

---

## 7 · Services Module

Service already had a normalised media model
(`ServiceMediaCollection` → `ServiceMediaFile`) supporting `IMAGE` /
`VIDEO` / `AUDIO`. We added the discriminator and thumbnail fields to the
cover so the **listing card** can also be a video or audio asset, matching
the other modules.

### Schema

- **Added on `services`:**
    - **`cover_media_type VARCHAR(16)`** — `MediaKind`, defaults to `IMAGE`.
    - **`cover_thumbnail_url TEXT`**.
- **Unchanged:** `cover_media_url` (may now be image / video / audio), all
  `service_media_collections` and `service_media_files` rows. Inline content
  inside bilingual descriptions still works the same way.

### Entities

`khi_app/model/service/Service.java`

- `coverMediaUrl` (TEXT) — image, video, or audio URL.
- `coverMediaType: MediaKind` — defaults to `IMAGE`.
- `coverThumbnailUrl` (TEXT) — poster (VIDEO) or cover art (AUDIO).
- `mediaCollections: Set<ServiceMediaCollection>` — **unchanged**, already
  multi-type via its own `MediaType` enum.

If you need a deep gallery with per-file technical metadata
(`durationSeconds`, `codec`, `bitrateKbps`, …) use the existing
`ServiceMediaCollection` model. For lightweight galleries on
About / Contact / News / Project, use `mediaGallery` (JSONB) instead.

### DTOs

`khi_app/dto/service/ServiceDTOs.java`

- `ServiceRequest` adds `coverMediaType` and `coverThumbnailUrl`.
- `ServiceResponse` mirrors them.
- `ServiceMediaCollectionRequest` / `ServiceMediaFileRequest` are unchanged.

### Tiptap rewrite hooks

`ServiceService` wraps both:
- `buildContent(ServiceContentRequest)` — the bilingual service description.
- `buildFileContent(FileContentRequest)` — per-media-file description.

Both hit `tiptapHtmlProcessor.process(...)` on the description before building
the entity.

The multipart `createWithFiles` / `updateWithFiles` paths still exist because
they manage **structured** media collections + per-file metadata (resolution,
duration, codec). They are unrelated to Tiptap inline media.

The new `coverMediaType` / `coverThumbnailUrl` flow through `create`,
`createWithFiles`, `update`, and `updateWithFiles`, and into `toResponse`.
S3 cleanup on `delete` also removes the cover thumbnail.

---

## 8 · Sound Module

### Schema

No table changes. `sound_track_contents.description` (`description_ckb` /
`description_kmr` as overrides) is already `TEXT`.

### Tiptap rewrite hook

`SoundTrackService.buildContent(LanguageContentDto)` calls
`tiptapHtmlProcessor.process(trimOrNull(dto.getDescription()))` before
building `SoundTrackContent`.

The dedicated cover / audio / brochure / attachment file uploads still go
through `SoundTrackService` because they are structured top-level fields,
not inline editor content.

---

## 9 · Video Module

### Schema

No table changes.

- `video.description_ckb` / `description_kmr` (inside `@Embedded VideoContent`) — Tiptap HTML.
- `video_clip_items.description_ckb` / `description_kmr` — Tiptap HTML **per clip**.

### Tiptap rewrite hook

`VideoMapper` is intentionally a static utility class. `VideoService.processTiptapHtml(Video)`
is called after `VideoMapper.toEntity(...)` (create) and after `VideoMapper.updateEntity(...)`
+ `buildAndAttachClipItems(...)` (update). It rewrites:

- `video.ckbContent.description`
- `video.kmrContent.description`
- every `VideoClipItem.descriptionCkb`
- every `VideoClipItem.descriptionKmr`

---

## 10 · Image Collections Module

### Schema

No table changes.

- `image_collections.description_ckb` / `description_kmr` (inside `@Embedded ImageContent`) — Tiptap HTML.
- `image_album_items.description_ckb` / `description_kmr` — Tiptap HTML **per item**.

### Tiptap rewrite hooks

`ImageCollectionService` wraps both:
- `buildContent(LanguageContentDto)` — collection-level description.
- `buildAlbumItems(...)` — each `ImageAlbumItem.setDescriptionCkb / setDescriptionKmr`
  is fed through `tiptapHtmlProcessor.process(...)`.

---

## 11 · Writing Module

### Schema

No table changes. `writing_contents.description_ckb` / `description_kmr` are
already `TEXT`.

### Tiptap rewrite hook

`WritingService` wraps both `buildContent(...)` (create path) and
`mergeContent(...)` (update path) — when `dto.getDescription()` is non-null
it is rewritten through `tiptapHtmlProcessor.process(...)` before being
stored on the `WritingContent` embeddable.

---

## End-to-End Flow (any description / body field)

```
[ Frontend Tiptap editor ]
     │
     │  (recommended) user drops an image / audio / video
     │  → POST /api/v1/media/upload         (type=image|video|audio)
     │  ← { fileUrl: "https://…s3…/khi-web-folders/<folder>/<uuid>-foo.<ext>" }
     │  → editor inserts <img|video|audio src="https://…s3…"> into the doc
     │     (OR the admin pins it as the hero/cover, OR appends to mediaGallery)
     │
     │  (fallback) user pastes / drag-drops a screenshot
     │  → editor produces <img src="data:image/png;base64,…">
     │
     ▼
POST/PUT  /api/v1/<module>  { …, description: "<p>…<img src=…></p>", mediaGallery: [...] }
     │
     ▼
[ Service layer ]
     │
     │  builds the entity for the description-bearing field, e.g.
     │      NewsContent.builder()
     │          .title(...)
     │          .description(tiptapHtmlProcessor.process(dto.getDescription()))
     │          .build();
     │  and normalises the gallery:
     │      news.setMediaGallery(buildGallery(dto.getMediaGallery()));
     │
     ▼
[ TiptapHtmlProcessor.process(html) ]                [ buildGallery(...) ]
     │                                                │
     │  • Fast path: no "data:" → return unchanged.   │  • Drop blank-URL items.
     │  • Match every src="data:<mime>;base64,…"      │  • Default kind=IMAGE.
     │  • For each one: decode → S3Service.upload(...)│  • Assign sortOrder.
     │                  → replace src with public URL │  • Stable-sort by sortOrder.
     │
     ▼
[ Persisted in Postgres ]
   description_ckb / description_kmr / body_ckb / body_kmr
   contains only S3 URLs — no base64 payloads, no inline binaries.
   media_gallery (JSONB) holds the normalised list of MediaItem.

GET /api/v1/<module>/{id}
     ▼
   toDto(...) returns the stored HTML verbatim — the frontend renders S3 URLs.
   media_gallery is returned as an ordered JSON array of MediaItems.
```

---

## How Images / Videos / Audio Are Uploaded — Full Walkthrough

There are **two supported paths** by which a media asset ends up in
storage. Both produce the same final state on disk: HTML whose
`<img>` / `<video>` / `<audio>` tags reference
`https://<bucket>.s3.<region>.amazonaws.com/…` URLs, plus (when populated)
a `mediaGallery` of S3-backed `MediaItem`s.

### Path A — Frontend uploads first (preferred)

This is the path the admin UI should follow. It keeps the request body small
and decouples binary upload from the JSON write.

**Step 1 — upload the binary**

```bash
curl -X POST https://api.khi.iq/api/v1/media/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@/path/to/photo.jpg" \
  -F "type=image"
```

Response:

```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "fileUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/9b3f51a2-ce04-4a2f-ae8a-photo.jpg",
    "fileName": "photo.jpg",
    "fileSize": 204800,
    "contentType": "image/jpeg"
  }
}
```

For a video / audio asset use `type=video` or `type=audio` — the response
shape is identical, only the folder changes.

**Step 2 — frontend bakes the URL into the editor HTML**

The Tiptap document model produces:

```html
<p>This is a paragraph.</p>
<img src="https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/9b3f51a2-ce04-4a2f-ae8a-photo.jpg" alt="photo.jpg">
<p>Another paragraph.</p>
```

…or, for media that lives **outside** the body (hero/cover or gallery), the
frontend simply attaches the URL to the right field on the JSON payload.

**Step 3 — submit the entity as JSON**

```bash
curl -X POST https://api.khi.iq/api/v1/news \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d @news.json
```

`news.json` (now with a video cover and a mixed-type gallery):

```json
{
  "coverUrl":          "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/video/breaking.mp4",
  "coverMediaType":    "VIDEO",
  "coverThumbnailUrl": "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/breaking-poster.jpg",
  "mediaGallery": [
    { "url": "https://…/images/site-1.jpg", "kind": "IMAGE", "sortOrder": 0 },
    { "url": "https://…/audio/quote.mp3",   "kind": "AUDIO",
      "thumbnailUrl": "https://…/images/speaker.jpg",
      "captionCkb": "گوتە", "captionKmr": "Gotin",
      "sortOrder": 1 },
    { "url": "https://…/video/tour.webm",   "kind": "VIDEO",
      "thumbnailUrl": "https://…/images/tour-poster.jpg",
      "sortOrder": 2 }
  ],
  "datePublished": "2026-05-22",
  "contentLanguages": ["CKB", "KMR"],
  "category":    { "ckbName": "هەواڵ",   "kmrName": "Nûçe" },
  "subCategory": { "ckbName": "کلتوور", "kmrName": "Çand" },
  "ckbContent": {
    "title": "ناونیشانی هەواڵ",
    "description": "<p>دەقی هەواڵ</p><img src=\"https://…/images/9b3f51a2-photo.jpg\" alt=\"photo\"><p>کۆتایی</p>"
  },
  "kmrContent": {
    "title": "Sernavê nûçeyê",
    "description": "<p>Deqa nûçeyê</p><img src=\"https://…/images/9b3f51a2-photo.jpg\" alt=\"photo\"><p>Dawî</p>"
  },
  "tags":     { "ckb": ["هۆنراوە"], "kmr": ["Helbest"] },
  "keywords": { "ckb": ["کوردستان"], "kmr": ["Kurdistan"] }
}
```

**What the backend does:**

1. `NewsController.addNews(...)` deserialises into `NewsDto`.
2. `NewsService.addNews(...)` calls `validate(...)`, `buildNewsEntity(...)`,
   `applyContentByLanguages(...)`.
3. `buildNewsEntity` sets `coverMediaType`, `coverThumbnailUrl`, and
   `mediaGallery` via `buildGallery(...)`.
4. `applyContentByLanguages` calls `buildContent(LanguageContentDto)`, which
   feeds the description through `tiptapHtmlProcessor.process(...)`.
5. Because the HTML contains no `data:` URIs, the processor short-circuits
   and returns the string unchanged.
6. Hibernate persists the entity. Done.

### Path B — Frontend pastes / drag-drops an inline image (safety net)

When a user pastes a screenshot or drags an image straight into the editor,
Tiptap emits an `<img src="data:image/png;base64,…">`. The frontend may
forget to convert it — the backend handles it anyway. Same logic applies
to a `<video>` or `<audio>` tag with a `data:` URI.

**Request body (note the `data:` URI inside the description):**

```json
{
  "coverUrl":       "https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/cover.jpg",
  "coverMediaType": "IMAGE",
  "datePublished":  "2026-05-22",
  "contentLanguages": ["CKB"],
  "category":    { "ckbName": "هەواڵ", "kmrName": "Nûçe" },
  "subCategory": { "ckbName": "کلتوور", "kmrName": "Çand" },
  "ckbContent": {
    "title": "ناونیشان",
    "description": "<p>پێشەکی</p><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAA…\" alt=\"\"><p>کۆتایی</p>"
  },
  "tags":     { "ckb": [] },
  "keywords": { "ckb": [] }
}
```

**Inside the service:**

1. `NewsService.addNews(...)` → `buildContent(...)` →
   `tiptapHtmlProcessor.process(html)`.
2. The processor’s regex `src=([\"'])data:([…])base64,([…])\1` matches the
   `data:image/png;base64,…` URI.
3. It base64-decodes the payload, picks `ProjectMediaType.IMAGE` from the
   MIME, builds a filename `tiptap-<nanoTime>.png`, and calls
   `s3Service.upload(bytes, filename, "image/png", IMAGE)`.
4. The returned public URL replaces the `src` in the HTML — quotes preserved.
5. The rewritten HTML is stored in `news.description_ckb`.

**Stored value in Postgres:**

```html
<p>پێشەکی</p><img src="https://khi-bucket.s3.eu-central-1.amazonaws.com/khi-web-folders/images/<uuid>-tiptap-1714000000000000000.png" alt=""><p>کۆتایی</p>
```

Multiple base64 assets in the same description are handled in a single pass —
each one is decoded, uploaded, and replaced in document order.

### Path C — Mixed (some pre-uploaded, some still base64)

When the description contains both S3 URLs and `data:` URIs, the processor
**only** touches the `data:` URIs. Existing `https://…s3…` URLs pass through
untouched (idempotent). This is the normal case for an “edit” operation
where the user added one new screenshot to an existing article.

### What the GET path returns

`GET /api/v1/news/{id}` (and the equivalent on every module) does **no**
rewriting. It returns the HTML that was stored — which, by construction,
contains only S3 URLs — and the persisted `mediaGallery` array verbatim.
The frontend renders the tags directly; the browser fetches each image /
video / audio from S3 via the bucket’s public URL.

```json
{
  "data": {
    "id": 42,
    "coverUrl":          "https://…/video/breaking.mp4",
    "coverMediaType":    "VIDEO",
    "coverThumbnailUrl": "https://…/images/breaking-poster.jpg",
    "mediaGallery": [
      { "url": "https://…/images/site-1.jpg", "kind": "IMAGE", "sortOrder": 0 },
      { "url": "https://…/audio/quote.mp3",   "kind": "AUDIO",
        "thumbnailUrl": "https://…/images/speaker.jpg",
        "captionCkb": "گوتە", "captionKmr": "Gotin", "sortOrder": 1 },
      { "url": "https://…/video/tour.webm",   "kind": "VIDEO",
        "thumbnailUrl": "https://…/images/tour-poster.jpg", "sortOrder": 2 }
    ],
    "ckbContent": {
      "title": "ناونیشان",
      "description": "<p>…</p><img src=\"https://…/images/<uuid>-tiptap-…png\" alt=\"\"><p>…</p>"
    }
  }
}
```

---

## JSON Request / Response Examples Per Module

The shape is consistent across all modules: a top-level cover/hero URL with
a `MediaKind` discriminator (where applicable), an optional gallery, plus
bilingual content embeddables whose `description` (or `body`) field is
Tiptap HTML.

### About — `POST /api/v1/about`

```json
{
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",

  "heroImageUrl":     "https://…/video/intro.mp4",
  "heroMediaType":    "VIDEO",
  "heroThumbnailUrl": "https://…/images/intro-poster.jpg",

  "ckbContent": {
    "title": "دەربارەی ئێمە",
    "subtitle": "زانیاری گشتی",
    "metaDescription": "…",
    "body": "<h1>دەربارە</h1><p>…</p><img src=\"https://…/images/team.jpg\"><p>…</p>"
  },
  "kmrContent": {
    "title": "Derbarê me",
    "subtitle": "Agahiyên giştî",
    "metaDescription": "…",
    "body": "<h1>Derbarê me</h1><p>…</p><img src=\"https://…/images/team.jpg\"><p>…</p>"
  },

  "mediaGallery": [
    { "url": "https://…/images/shot-1.jpg", "kind": "IMAGE", "sortOrder": 0 },
    { "url": "https://…/audio/talk.mp3",    "kind": "AUDIO",
      "thumbnailUrl": "https://…/images/talk-cover.jpg",
      "captionCkb": "گفتوگۆ", "captionKmr": "Hevpeyvîn", "sortOrder": 1 },
    { "url": "https://…/video/tour.webm",   "kind": "VIDEO",
      "thumbnailUrl": "https://…/images/tour-poster.jpg", "sortOrder": 2 }
  ],

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

  "heroImageUrl":     "https://…/images/lobby.jpg",
  "heroMediaType":    "IMAGE",
  "heroThumbnailUrl": null,

  "ckbContent": {
    "title": "پەیوەندیمان پێوە بکە",
    "subtitle": "ئامادە بەخزمەتت",
    "address": "هەولێر",
    "workingHours": "٩:٠٠–١٧:٠٠"
  },
  "kmrContent": {
    "title": "Têkilî",
    "subtitle": "Em li xizmeta we ne",
    "address": "Hewlêr",
    "workingHours": "09:00–17:00"
  },

  "mediaGallery": [
    { "url": "https://…/video/office-tour.mp4", "kind": "VIDEO",
      "thumbnailUrl": "https://…/images/office-tour-poster.jpg",
      "sortOrder": 0 },
    { "url": "https://…/audio/welcome.mp3", "kind": "AUDIO",
      "thumbnailUrl": "https://…/images/welcome-art.jpg",
      "captionCkb": "بەخێرهاتن", "captionKmr": "Bi xêr hatî",
      "sortOrder": 1 }
  ],

  "phone": "+964 770 000 0000",
  "email": "info@khi.iq",
  "mapEmbedUrl": "https://www.google.com/maps/embed?…",
  "latitude":  36.19,
  "longitude": 44.01
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
  "category":    { "ckbName": "هەواڵ",   "kmrName": "Nûçe" },
  "subCategory": { "ckbName": "کلتوور", "kmrName": "Çand" },
  "ckbContent":  { "title": "...", "description": "<p>…</p>" },
  "kmrContent":  { "title": "...", "description": "<p>…</p>" },
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
    { "url": "https://…/images/site-plan.jpg",   "kind": "IMAGE", "sortOrder": 0 },
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
    "description": "<h2>پێشەکی</h2><p>وردەکاری</p><img src=\"https://…/images/site-plan.jpg\"><p>کۆتایی</p>",
    "location": "هەولێر"
  },
  "kmrContent": {
    "title": "Projeya nû",
    "description": "<h2>Pêşkêş</h2><p>Hûrgilî</p><img src=\"https://…/images/site-plan.jpg\"><p>Dawî</p>",
    "location": "Hewlêr"
  },
  "tagsCkb":     ["بیناسازی"],
  "tagsKmr":     ["Mîmarî"],
  "keywordsCkb": ["پرۆژە"],
  "keywordsKmr": ["Proje"]
}
```

Response mirrors the request, with extra fields: `id`, `createdAt`.

### Services — `POST /api/v1/services`

```json
{
  "serviceType": "Architecture",
  "location": "Erbil",

  "coverMediaUrl":     "https://…/video/showreel.mp4",
  "coverMediaType":    "VIDEO",
  "coverThumbnailUrl": "https://…/images/showreel-poster.jpg",

  "publishedAt": "2026-05-22 14:00:00",
  "contents": [
    {
      "languageCode": "CKB",
      "title": "خزمەتگوزاری بیناسازی",
      "description": "<p>دەستپێک</p><img src=\"https://…/images/blueprint.jpg\"><p>کۆتایی</p>"
    },
    {
      "languageCode": "KMR",
      "title": "Karûbarê mîmarî",
      "description": "<p>Destpêk</p><img src=\"https://…/images/blueprint.jpg\"><p>Dawî</p>"
    }
  ],
  "mediaCollections": [
    {
      "collectionName": "Renderings",
      "mediaType": "IMAGE",
      "sortOrder": 0,
      "files": [
        {
          "fileUrl": "https://…/images/render-1.jpg",
          "ckbContent": { "caption": "ڕووکار ١", "title": "ڕووکار", "description": "<p>وردەکاری</p>" },
          "kmrContent": { "caption": "Pêşxistin 1", "title": "Pêşxistin", "description": "<p>Hûrgilî</p>" }
        }
      ]
    },
    {
      "collectionName": "Recap Videos",
      "mediaType": "VIDEO",
      "sortOrder": 1,
      "files": [
        {
          "fileUrl":      "https://…/video/recap-1.mp4",
          "thumbnailUrl": "https://…/images/recap-1-poster.jpg",
          "ckbContent": { "caption": "پێشانگا", "title": "پێشانگا", "description": "<p>پوختە</p>" },
          "kmrContent": { "caption": "Pêşandan", "title": "Pêşandan", "description": "<p>Kurte</p>" }
        }
      ]
    }
  ]
}
```

### Video — `POST /api/v1/videos/with-files` (multipart) or `POST /api/v1/videos` (JSON)

JSON variant:

```json
{
  "videoType": "FILM",
  "ckbCoverUrl": "https://…/images/cover-ckb.jpg",
  "kmrCoverUrl": "https://…/images/cover-kmr.jpg",
  "hoverCoverUrl": "https://…/images/cover-hover.jpg",
  "topicId": 12,
  "contentLanguages": ["CKB", "KMR"],
  "ckbContent": {
    "title": "فیلمی نوێ",
    "description": "<p>پوختە</p><img src=\"https://…/images/still-1.jpg\"><p>کۆتایی</p>",
    "location": "هەولێر",
    "director": "د. ئاکار",
    "producer": "KHI"
  },
  "kmrContent": {
    "title": "Fîlma nû",
    "description": "<p>Kurte</p><img src=\"https://…/images/still-1.jpg\"><p>Dawî</p>",
    "location": "Hewlêr",
    "director": "Dr. Akar",
    "producer": "KHI"
  },
  "sourceUrl": "https://…/video/film.mp4",
  "fileFormat": "mp4",
  "durationSeconds": 1820,
  "resolution": "1920x1080",
  "publishmentDate": "2026-05-22",
  "tagsCkb": ["فیلم"],
  "tagsKmr": ["Fîlm"],
  "keywordsCkb": ["دۆکیومێنتاری"],
  "keywordsKmr": ["Belgefîlm"]
}
```

For a clip-style video, omit `sourceUrl` and pass an array of clip items —
each with its own bilingual `descriptionCkb` / `descriptionKmr` (Tiptap HTML).

### Sound — `POST /api/v1/sound-tracks/with-files` (multipart)

The Sound module uses a multipart request because cover images, audio files,
brochures, and attachments are uploaded at the same time. The non-binary
`request` part is a JSON DTO whose Tiptap fields look exactly like every
other module:

```json
{
  "soundType": "Music",
  "trackState": "PUBLISHED",
  "topicId": 7,
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title": "ئەلبوومی نوێ",
    "description": "<p>پوختە</p><img src=\"https://…/images/album.jpg\"><p>کۆتایی</p>"
  },
  "publishmentDate": "2026-05-22",
  "tags":     { "ckb": ["مۆسیقا"] },
  "keywords": { "ckb": ["گۆرانی"] }
}
```

### Image Collections — `POST /api/v1/image-collections/with-files` (multipart)

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
  "kmrContent": {
    "title": "Galeriya wêneyan",
    "description": "<p>Rêzewêne</p><img src=\"https://…/images/intro.jpg\"><p>Dawî</p>",
    "location": "Hewlêr",
    "collectedBy": "KHI"
  },
  "publishmentDate": "2026-05-22",
  "tags":     { "ckb": ["وێنە"], "kmr": ["Wêne"] },
  "keywords": { "ckb": ["مێژوو"], "kmr": ["Dîrok"] },
  "items": [
    {
      "sortOrder": 0,
      "captionCkb": "وێنە ١",
      "captionKmr": "Wêne 1",
      "descriptionCkb": "<p>وردەکاری وێنە ١</p>",
      "descriptionKmr": "<p>Hûrgilî wêne 1</p>"
    }
  ]
}
```

### Writing — `POST /api/v1/writings/with-files` (multipart)

```json
{
  "topicId": 5,
  "contentLanguages": ["CKB"],
  "ckbContent": {
    "title": "نووسینی نوێ",
    "description": "<h2>پێشەکی</h2><p>…</p><img src=\"https://…/images/fig-1.jpg\"><p>کۆتایی</p>",
    "writer": "ئاکار",
    "genre": "ESSAY"
  },
  "publishmentDate": "2026-05-22"
}
```

---

## End-to-End Image Upload — Sequence Diagram

```
┌──────────┐   ┌──────────────────┐   ┌──────────────────────┐   ┌────────────┐   ┌──────────┐
│ Browser  │   │ /api/v1/media/   │   │ /api/v1/<module>     │   │ Tiptap     │   │ S3       │
│ (Tiptap) │   │ upload           │   │ POST or PUT          │   │ HtmlProc.  │   │ bucket   │
└────┬─────┘   └────────┬─────────┘   └──────────┬───────────┘   └─────┬──────┘   └────┬─────┘
     │                  │                        │                      │               │
     │ multipart file   │                        │                      │               │
     │─────────────────▶│                        │                      │               │
     │                  │ S3Service.upload(...)  │                      │               │
     │                  │────────────────────────┼──────────────────────┼──────────────▶│
     │                  │           public URL                          │               │
     │                  │◀──────────────────────────────────────────────┼───────────────│
     │ { fileUrl: ...} │                        │                      │               │
     │◀─────────────────│                        │                      │               │
     │                                                                                  │
     │ user edits — editor builds HTML with <img src="https://…s3…/…">                  │
     │ admin pins URL as cover (with MediaKind) or appends to mediaGallery              │
     │                                                                                  │
     │ application/json { description: "<p>…<img src=https://…s3…></p>",                │
     │                    coverUrl, coverMediaType, coverThumbnailUrl,                   │
     │                    mediaGallery: [ {url, kind, ...}, ... ] }                     │
     │───────────────────────────────────────▶│                       │               │
     │                                        │ buildContent(dto)     │               │
     │                                        │──────────────────────▶│               │
     │                                        │  no "data:" URIs:     │               │
     │                                        │  HTML returned        │               │
     │                                        │  unchanged            │               │
     │                                        │◀──────────────────────│               │
     │                                        │ buildGallery(dto)     │               │
     │                                        │  normalise + sort     │               │
     │                                        │ JPA save              │               │
     │                                        │                       │               │
     │ { data: { … description, coverUrl, coverMediaType,                              │
     │           coverThumbnailUrl, mediaGallery[] … } }                               │
     │◀──────────────────────────────────────│                       │               │
     │                                                                                  │
     │ Subsequent GET → server returns stored HTML + gallery verbatim; browser fetches │
     │ images / videos / audio directly from S3 via the public URLs.                   │
     │                                                                                  │
```

If the inbound HTML contains `data:image/...;base64,…` URIs (Path B), the
`Tiptap HtmlProc.` step expands like this:

```
     │                                        │ buildContent(dto)     │               │
     │                                        │──────────────────────▶│               │
     │                                        │   match data: URI     │               │
     │                                        │   base64 decode       │               │
     │                                        │   s3.upload(bytes)    │               │
     │                                        │──────────────────────┼──────────────▶│
     │                                        │       public URL                     │
     │                                        │──────────────────────│◀──────────────│
     │                                        │   rewrite src=...     │               │
     │                                        │   return rewritten    │               │
     │                                        │◀──────────────────────│               │
```

---

## Error Modes and Edge Cases

| Scenario | Behaviour |
| --- | --- |
| `description` is `null` or empty | Returned as-is. Hibernate stores `null`. |
| `description` has no `data:` URIs | Returned unchanged (early-out before regex compilation). |
| Single `data:` URI is malformed (bad base64) | Logged at `WARN`, original `src` left in place, save still succeeds. |
| S3 upload fails for one inline asset | Logged at `ERROR`, original `src` left in place, save still succeeds for the rest. |
| Multiple `data:` URIs in one description | Processed in document order, each gets its own S3 object, all replaced in a single pass. |
| Mixed (some `https://…s3…`, some `data:`) | Only the `data:` ones are rewritten. S3 URLs pass through. |
| Same image referenced twice in the same description | Uploaded twice (filename uses `System.nanoTime()`), two distinct S3 objects. |
| MIME unknown (e.g. `application/octet-stream`) | Folder falls back to `files/`, extension to the suffix after `/` or `bin`. |
| `coverMediaType` / `heroMediaType` is `null` on the request | Server defaults to `IMAGE`. Existing rows behave the same as before. |
| `MediaItem.kind` is `null` inside a `mediaGallery` entry | Normalised to `IMAGE`. |
| `MediaItem` has a blank `url` | Dropped by `buildGallery(...)`; never stored. |
| `mediaGallery` is `null` on the request | Persisted as an empty list. |
| Update changes `heroImageUrl` / `coverUrl` / `coverThumbnailUrl` | Old S3 object is deleted (per-module update path). |
| Delete on About / Contact / News / Project | Hero/cover, thumbnail, and **every** gallery item's `url` + `thumbnailUrl` are removed from S3. |
| Same gallery item URL appears across multiple entities | Deletion of one entity removes that S3 object — currently no cross-entity reference count. Treat gallery URLs as entity-owned. |

---

## Summary Table

| Module | Tables dropped | Columns added | Endpoints removed | Multi-type media | Tiptap rewrite hook |
| --- | --- | --- | --- | --- | --- |
| **Shared** | — | — | — | `MediaKind`, `MediaItem` | `MediaController` + `TiptapHtmlProcessor` |
| **News** | `news_media` | `cover_media_type`, `cover_thumbnail_url`, `media_gallery` JSONB | 3 | ✅ cover + gallery | `NewsService.buildContent` |
| **Projects** | `project_media`, `project_contents` (+ join tables) | `cover_media_type`, `cover_thumbnail_url`, `media_gallery` JSONB | 2 | ✅ cover + gallery | `ProjectService.buildProject` + `applyUpdate` |
| **About** | `about_blocks` | `body_ckb`, `body_kmr`, `stats` JSONB, `hero_media_type`, `hero_thumbnail_url`, `media_gallery` JSONB | 0 | ✅ hero + gallery | `AboutService.buildAboutContent` |
| **Contact** | — | `hero_media_type`, `hero_thumbnail_url`, `media_gallery` JSONB | 0 | ✅ hero + gallery | — (no rich-text field) |
| **Services** | — | `cover_media_type`, `cover_thumbnail_url` | 0 | ✅ cover (galleries already via `ServiceMediaCollection`) | `ServiceService.buildContent` + `buildFileContent` |
| **Sound** | — | — | 0 | — | `SoundTrackService.buildContent` |
| **Video** | — | — | 0 | — | `VideoService.processTiptapHtml` (entity + clips) |
| **Image** | — | — | 0 | — | `ImageCollectionService.buildContent` + `buildAlbumItems` |
| **Writing** | — | — | 0 | — | `WritingService.buildContent` + `mergeContent` |

- **Total endpoints removed:** 5 (3 News + 2 Projects)
- **New endpoints:** 3 (shared upload, multi-upload, delete) — all under `/api/v1/media`
- **New DB columns:**
    - `about_pages`: `body_ckb`, `body_kmr`, `stats` JSONB, `hero_media_type`, `hero_thumbnail_url`, `media_gallery` JSONB
    - `contact_pages`: `hero_media_type`, `hero_thumbnail_url`, `media_gallery` JSONB
    - `news`: `cover_media_type`, `cover_thumbnail_url`, `media_gallery` JSONB
    - `projects`: `cover_media_type`, `cover_thumbnail_url`, `media_gallery` JSONB
    - `services`: `cover_media_type`, `cover_thumbnail_url`
- **Tables dropped:** `news_media`, `project_media`, `project_contents` (+ join tables), `about_blocks`

---

## Key Files

| Concern | File |
| --- | --- |
| Shared upload controller | `khi_app/api/media/MediaController.java` |
| Shared upload service | `khi_app/service/media/MediaService.java` |
| Upload DTOs | `khi_app/dto/media/MediaDtos.java` |
| **Base64 → S3 rewriter** | `khi_app/service/media/TiptapHtmlProcessor.java` |
| Low-level S3 | `khi_app/service/S3Service.java` |
| **Shared `MediaKind` enum** | `khi_app/enums/MediaKind.java` |
| **Shared `MediaItem` POJO (JSONB)** | `khi_app/model/media/MediaItem.java` |
| About entity / DTOs | `khi_app/model/about/{About,AboutContent,StatItem}.java`, `khi_app/dto/about/AboutDTOs.java` |
| Contact entity / DTOs | `khi_app/model/contact/{Contact,ContactContent}.java`, `khi_app/dto/contact/ContactDTOs.java` |
| News entity / DTOs | `khi_app/model/news/{News,NewsContent}.java`, `khi_app/dto/news/NewsDto.java` |
| Project entity / DTOs | `khi_app/model/project/{Project,ProjectContentBlock}.java`, `khi_app/dto/project/{ProjectCreateRequest,ProjectResponse}.java` |
| Service entity / DTOs | `khi_app/model/service/{Service,ServiceContent,ServiceMediaCollection,ServiceMediaFile,ServiceMediaFileContent}.java`, `khi_app/dto/service/ServiceDTOs.java` |
| Sound entity / DTOs | `khi_app/model/publishment/sound/{SoundTrack,SoundTrackContent}.java`, `khi_app/dto/publishment/sound/SoundTrackDtos.java` |
| Video entity / mapper | `khi_app/model/publishment/video/{Video,VideoContent,VideoClipItem}.java`, `khi_app/dto/publishment/video/VideoMapper.java` |
| Image entity / DTOs | `khi_app/model/publishment/image/{ImageCollection,ImageContent,ImageAlbumItem}.java`, `khi_app/dto/publishment/image/ImageCollectionDTO.java` |
| Writing entity / DTOs | `khi_app/model/publishment/writing/{Writing,WritingContent}.java`, `khi_app/dto/publishment/writing/WritingDtos.java` |

---

## Migration Order (for replays / new environments)

1. Add the shared `POST /api/v1/media/upload` endpoint (already merged).
2. Apply the About schema changes — drop `about_blocks`, add `body_ckb`,
   `body_kmr`, `stats`.
3. Drop `news_media`, `project_media`, `project_contents` (and any CKB/KMR
   join tables).
4. **Add the multi-type media columns** (one `ALTER TABLE` per entity):
   - `about_pages`: `hero_media_type VARCHAR(16) DEFAULT 'IMAGE'`,
     `hero_thumbnail_url VARCHAR(1000)`, `media_gallery JSONB DEFAULT '[]'::jsonb`.
   - `contact_pages`: `hero_media_type VARCHAR(16) DEFAULT 'IMAGE'`,
     `hero_thumbnail_url VARCHAR(1000)`, `media_gallery JSONB DEFAULT '[]'::jsonb`.
   - `news`: `cover_media_type VARCHAR(16) DEFAULT 'IMAGE'`,
     `cover_thumbnail_url VARCHAR(1024)`, `media_gallery JSONB DEFAULT '[]'::jsonb`.
   - `projects`: `cover_media_type VARCHAR(16) DEFAULT 'IMAGE'`,
     `cover_thumbnail_url VARCHAR(1024)`, `media_gallery JSONB DEFAULT '[]'::jsonb`.
   - `services`: `cover_media_type VARCHAR(16) DEFAULT 'IMAGE'`,
     `cover_thumbnail_url TEXT`.
   In dev these are auto-created by Hibernate on the next boot.
5. Deploy the backend with `TiptapHtmlProcessor` wired into every module and
   `MediaKind` / `MediaItem` mapping wired into About, Contact, News, Project,
   and Service (this branch).
6. Migrate any historic plain-text descriptions into Tiptap HTML if needed
   (wrap in `<p>…</p>`). No existing column type changes are required.
