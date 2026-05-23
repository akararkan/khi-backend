# TipTap & Multi-Type Media — Backend Integration Guide

The platform's TipTap editor stores HTML in a single `description` / `body`
column per language. Inline images, videos, and audio live **inside** that
HTML; the editor never sends a separate `media[]` array.

Beside the in-body media, every public-facing entity also exposes a
**typed hero/cover field** and an **out-of-body gallery** so the frontend can
render images, videos, and audio side-by-side without parsing HTML.

This document covers:
1. The shared upload endpoint
2. The shared `MediaKind` and `MediaItem` types
3. Per-entity hero / cover / gallery shape
4. Inline TipTap HTML — supported tags and base64 handling

---

## 1. Shared upload endpoint

All editors and admin forms upload through the same controller:

`POST /api/v1/media/upload`  *(multipart/form-data)*

| Part | Required | Notes |
|------|----------|-------|
| `file` | yes | the binary asset |
| `type` | no  | folder hint: `image` \| `video` \| `audio` \| `document` \| `gallery`. Defaults to `image`. |

Response (wrapped in the standard `ApiResponse` envelope):

```json
{
  "fileUrl": "https://cdn.example.com/testweb/video/foo.mp4",
  "fileName": "foo.mp4",
  "fileSize": 12345678,
  "contentType": "video/mp4"
}
```

There is also `POST /api/v1/media/upload/multiple` (same shape, but `files`
is a list) and `DELETE /api/v1/media?fileUrl=…` for orphan cleanup.

The returned `fileUrl` is then either:
- pasted directly into the TipTap HTML by the editor, **or**
- attached to one of the typed media fields below.

> The `type` hint controls only the S3 folder. The backend never trusts the
> client's content-type for the playback `kind` — the frontend sets
> `MediaKind` explicitly on each `MediaItem`.

---

## 2. Shared types

### `MediaKind` (enum)

`ak.dev.khi_backend.khi_app.enums.MediaKind`

```
IMAGE   // <img>
VIDEO   // <video>
AUDIO   // <audio>
```

### `MediaItem` (JSONB element)

`ak.dev.khi_backend.khi_app.model.media.MediaItem`

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

`thumbnailUrl` is **optional** — used as the `<video poster=…>` for VIDEO
and as cover art for AUDIO. It is ignored for IMAGE.

---

## 3. Per-entity shape

Every public entity now exposes **three** things:

| # | Field | Purpose |
|---|-------|---------|
| 1 | `<hero/cover>Url` | single primary asset URL |
| 2 | `<hero/cover>MediaType` | `IMAGE` \| `VIDEO` \| `AUDIO` — tells the frontend which `<img>`/`<video>`/`<audio>` tag to render |
| 3 | `<hero/cover>ThumbnailUrl` | poster (VIDEO) or cover art (AUDIO); ignored for IMAGE |
| 4 | `mediaGallery` *(JSONB list of `MediaItem`)* | mixed-type gallery rendered beside the hero/cover |

Defaults: if `MediaType` is null the backend treats it as `IMAGE`, so existing
rows that only know about images continue to work unchanged.

### About — `About.java`

```
heroImageUrl       String
heroMediaType      MediaKind   default IMAGE
heroThumbnailUrl   String
mediaGallery       List<MediaItem>  (JSONB)
```

The existing `hero_image_url` column is kept under its original name; only
the **interpretation** widens (it may now be a video or audio URL).

### Contact — `Contact.java`

```
heroImageUrl       String
heroMediaType      MediaKind   default IMAGE
heroThumbnailUrl   String
mediaGallery       List<MediaItem>  (JSONB)
```

### News — `News.java`

```
coverUrl            String
coverMediaType      MediaKind   default IMAGE
coverThumbnailUrl   String
mediaGallery        List<MediaItem>  (JSONB)
```

`coverUrl` is still required by `NewsService.validate(…)` — it just no
longer has to be an image.

### Project — `Project.java`

```
coverUrl            String
coverMediaType      MediaKind   default IMAGE
coverThumbnailUrl   String
mediaGallery        List<MediaItem>  (JSONB)
```

### Service — `Service.java`

Service already had a normalised media model (`ServiceMediaCollection` →
`ServiceMediaFile`) supporting `IMAGE` / `VIDEO` / `AUDIO`. We added the
discriminator and thumbnail fields to the cover for consistency:

```
coverMediaUrl       String
coverMediaType      MediaKind   default IMAGE
coverThumbnailUrl   String
mediaCollections    Set<ServiceMediaCollection>   // unchanged, already multi-type
```

If you need a deep gallery with per-file technical metadata
(`durationSeconds`, `codec`, `bitrateKbps`, …), use the existing
`ServiceMediaCollection` model. For lightweight galleries on
About/Contact/News/Project, use `mediaGallery`.

---

## 4. Inline TipTap HTML

The editor produces HTML directly into the language-scoped `body` /
`description` columns. The backend does two things with that HTML:

### a. Sanitisation
None — it is stored verbatim. Make sure the editor's allow-list keeps
hostile attributes out before sending.

### b. Inline-base64 hoisting
`TiptapHtmlProcessor` (`service/media/TiptapHtmlProcessor.java`) scans the
HTML for `src="data:<mime>;base64,…"` on `<img>`, `<video>`, `<audio>`, and
`<source>` tags, uploads each one to S3, and rewrites the `src` to point at
the public URL. The folder is chosen from the MIME prefix:

| MIME prefix     | S3 folder | `MediaKind` analogue |
|-----------------|-----------|----------------------|
| `image/*`       | `images/` | IMAGE |
| `video/*`       | `video/`  | VIDEO |
| `audio/*`       | `audio/`  | AUDIO |
| anything else   | `files/`  | (DOCUMENT) |

The processor is idempotent: HTML that already contains S3 URLs is left
alone, so re-saves are safe.

### c. Recommended TipTap extensions

The editor on the frontend should enable, at minimum:

| Extension                     | What it produces |
|-------------------------------|------------------|
| `@tiptap/extension-image`     | `<img src=…>` (paste, drag-drop) |
| custom `Video` node           | `<video controls src=…>` or `<video><source src=… type=…></video>` |
| custom `Audio` node           | `<audio controls src=…>` |
| `@tiptap/extension-link`      | external links |
| `@tiptap/extension-table`     | inline tables (optional) |

For Video and Audio you'll typically want a small custom extension because
TipTap doesn't ship one — see the Notion-style node templates in the
frontend repo. The backend cares only that the resulting HTML has standard
`<video>` / `<audio>` / `<source>` tags with `src` pointing at S3.

### d. Upload flow for inline assets

The editor uses one of two flows; both work transparently:

1. **Upload-then-paste**: editor calls `POST /api/v1/media/upload`, then
   inserts `<img src="<returned url>">`. The HTML arrives at the backend
   already clean — `TiptapHtmlProcessor` is a no-op.

2. **Paste-base64**: editor drops the data URI directly into the HTML
   (`<img src="data:image/png;base64,…">`). The backend hoists it to S3
   on save via `TiptapHtmlProcessor`.

Flow (1) is preferred for large videos so the browser doesn't have to hold
the file in a data URI; flow (2) is fine for small images.

---

## 5. Worked examples

### About — minimal request with mixed gallery

```json
POST /api/v1/about
{
  "slugCkb": "derbare",
  "heroImageUrl":     "https://cdn/.../intro.mp4",
  "heroMediaType":    "VIDEO",
  "heroThumbnailUrl": "https://cdn/.../intro-poster.jpg",
  "ckbContent": {
    "title": "دەربارەمان",
    "body":  "<p>تێکست…</p><img src=\"https://cdn/.../inline.jpg\">"
  },
  "mediaGallery": [
    { "url": "https://cdn/.../shot-1.jpg", "kind": "IMAGE", "sortOrder": 0 },
    { "url": "https://cdn/.../talk.mp3",   "kind": "AUDIO",
      "thumbnailUrl": "https://cdn/.../talk-cover.jpg", "sortOrder": 1 },
    { "url": "https://cdn/.../tour.webm",  "kind": "VIDEO",
      "thumbnailUrl": "https://cdn/.../tour-poster.jpg", "sortOrder": 2 }
  ]
}
```

### News — video cover, no gallery

```json
{
  "coverUrl":         "https://cdn/.../breaking.mp4",
  "coverMediaType":   "VIDEO",
  "coverThumbnailUrl":"https://cdn/.../breaking-poster.jpg",
  "datePublished":    "2026-05-23",
  "contentLanguages": ["CKB"],
  "category":    { "ckbName": "هەواڵ", "kmrName": "Nûçe" },
  "subCategory": { "ckbName": "سیاسی", "kmrName": "Siyasî" },
  "ckbContent": { "title": "...", "description": "<p>...</p>" }
}
```

### Project — audio cover

```json
{
  "coverUrl":       "https://cdn/.../theme.mp3",
  "coverMediaType": "AUDIO",
  "coverThumbnailUrl": "https://cdn/.../theme-art.jpg",
  ...
}
```

---

## 6. Backwards compatibility

| Old behaviour | New behaviour |
|---------------|---------------|
| `heroImageUrl` / `coverUrl` is always an image | Still works — `*MediaType` defaults to `IMAGE` when null. |
| No gallery field | `mediaGallery` defaults to `[]`. Existing rows return an empty list. |
| Service only had multi-type via `mediaCollections` | Still works — collections are unchanged. The new `coverMediaType` is additive. |

No migrations are required for existing data; the new columns (`hero_media_type`,
`hero_thumbnail_url`, `cover_media_type`, `cover_thumbnail_url`, and
`media_gallery` JSONB) are added by Hibernate on the next boot in dev, and
should be backfilled with a single `ALTER TABLE` per entity in production.

---

## 7. Quick reference — where things live

| Layer          | File |
|----------------|------|
| Shared enum    | `khi_app/enums/MediaKind.java` |
| Shared POJO    | `khi_app/model/media/MediaItem.java` |
| Upload API     | `khi_app/api/media/MediaController.java` |
| Upload service | `khi_app/service/media/MediaService.java` |
| Inline hoister | `khi_app/service/media/TiptapHtmlProcessor.java` |
| About fields   | `khi_app/model/about/About.java` |
| Contact fields | `khi_app/model/contact/Contact.java` |
| News fields    | `khi_app/model/news/News.java` |
| Project fields | `khi_app/model/project/Project.java` |
| Service fields | `khi_app/model/service/Service.java` (+ existing `ServiceMediaCollection` / `ServiceMediaFile`) |
