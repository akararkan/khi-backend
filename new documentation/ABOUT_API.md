# KHI Backend — About API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 5 Endpoints · JSON Only · Tiptap HTML

Complete documentation for all About page management endpoints — create, update, delete, list, and slug lookup — including bilingual content, Tiptap HTML bodies (where inline images / video / audio / files live), structured stats, enums, DTOs, and full request/response examples.

> 🚨 **MAJOR REWRITE.** The About module no longer has separate "blocks", a `ContentType` enum, a hero image, or any media-upload endpoints. All visual media now lives **inline inside a Tiptap HTML body** per language. Stats survive as a structured JSONB array. See [§12 · Change Log](#12--change-log--old-vs-new) for full details.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Authentication & Notes](#03--authentication--notes)
- [04 · Read](#04--read)
  - `GET /` (getAll — active only)
  - `GET /{slug}` (by slug)
- [05 · Create](#05--create) — `POST /`
- [06 · Update](#06--update) — `PUT /{id}`
- [07 · Delete](#07--delete) — `DELETE /{id}` (returns `204 No Content`)
- [08 · Media Upload Pipeline (Tiptap)](#08--media-upload-pipeline-tiptap)
- [09 · DTO Reference](#09--dto-reference)
- [10 · Response Envelope](#10--response-envelope)
- [11 · Error Responses](#11--error-responses)
- [12 · Change Log — Old vs. New](#12--change-log--old-vs-new)

---

## 01 · Overview

The About module manages bilingual About pages for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each About page carries:

- **Bilingual slugs** — `slugCkb` (Sorani, **required**, unique) and `slugKmr` (Kurmanji, optional, unique). Either slug resolves to the same page via `GET /{slug}`.
- **Per-language page-level content** (`ckbContent` / `kmrContent`) — each holding: `title`, `subtitle`, `metaDescription`, and a **Tiptap HTML `body`**.
- **Tiptap HTML body** — all visual media (images, videos, voice / audio, downloadable files) lives **inline inside the `body` HTML** as `<img>`, `<video>`, `<audio>`, or `<a href>` tags whose URLs already point to S3. There is no separate hero / gallery / blocks list anymore.
- **Structured stats** — a JSONB `List<StatItem>` column carrying `{ labelCkb, labelKmr, value }` entries. Stats are not embeddable in HTML, so they survive as structured JSON for the frontend to render.
- **Active flag** — `active` controls public visibility (no admin-only `getAll` endpoint exists right now; the single `GET /` returns active pages).
- **Display order** — `displayOrder` integer for sorting pages.
- **Auto timestamps** via `@CreationTimestamp` / `@UpdateTimestamp`.

### Base URL

```
/api/v1/about
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | All **active** about pages |
| `GET` | `/{slug}` | Fetch a page by CKB or KMR slug |
| `POST` | `/` | Create a new about page — `application/json` |
| `PUT` | `/{id}` | Update an about page — `application/json` |
| `DELETE` | `/{id}` | Delete an about page (returns `204 No Content`) |

> ⚠️ **Endpoint count: 5** (down from 10 in the previous doc). All multipart and media-upload endpoints have been removed in favour of the shared `POST /api/v1/media/upload` pipeline — see [§08](#08--media-upload-pipeline-tiptap).

---

## 02 · Data Models

One JPA entity + one embeddable + one plain POJO make up the About module. `About` is the aggregate root.

### About — `about_pages`

**DB indexes:** `idx_about_slug_ckb`, `idx_about_slug_kmr`, `idx_about_active`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `slugCkb` | `slug_ckb` | VARCHAR(200) | **UNIQUE, NOT NULL** (`@NotBlank`) | Sorani route identifier |
| `slugKmr` | `slug_kmr` | VARCHAR(200) | UNIQUE, NULLABLE | Kurmanji route identifier — null when no Kurmanji version exists |
| `active` | `active` | BOOLEAN | NOT NULL | `true` = visible on public frontend. Default `true` |
| `displayOrder` | `display_order` | INT | NULLABLE | Sort order. Default `0` |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani page-level content: `title`, `subtitle`, `metaDescription`, `body` (Tiptap HTML) |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji page-level content: same shape |
| `stats` | `stats` | **JSONB** | NULLABLE | `List<StatItem>` — array of `{labelCkb, labelKmr, value}` entries |
| `createdAt` | `created_at` | TIMESTAMP | (auto, NOT NULL on insert) | Set by `@CreationTimestamp`. `LocalDateTime` |
| `updatedAt` | `updated_at` | TIMESTAMP | (auto) | Set by `@UpdateTimestamp` |

> ⛔ **Removed from this entity (vs. old doc):**
> - `heroImageUrl` — gone. Hero image is now an `<img>` at the top of the Tiptap body.
> - `blocks` (`List<AboutBlock>`) — gone. All blocks have collapsed into a single bilingual Tiptap HTML body per language.
> - The `AboutBlock` and `AboutBlockContent` JPA entities — both **dropped**.

### AboutContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `About`.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(300) | Page title in this language |
| `subtitle` | `subtitle_ckb` | `subtitle_kmr` | VARCHAR(500) | Short subtitle or tagline |
| `metaDescription` | `meta_description_ckb` | `meta_description_kmr` | TEXT | SEO meta description in this language |
| `body` | `body_ckb` | `body_kmr` | TEXT | **Tiptap HTML body** — full editor output. Inline `<img>` / `<video>` / `<audio>` / `<a>` tags already point at S3 URLs |

### StatItem — JSONB element (not a JPA entity)

Stored as one element of the `stats` JSONB column on `About`. Plain POJO with no JPA annotations — order and shape are preserved by JSONB.

| Field | Type | Description |
| --- | --- | --- |
| `labelCkb` | String | Sorani label, e.g. `"کتێب"` |
| `labelKmr` | String | Kurmanji label, e.g. `"Pirtûk"` |
| `value` | String | Display value, e.g. `"5,000+"` |

---

## 03 · Authentication & Notes

> ℹ️ **Authentication** is enforced by the project's global security configuration (JWT via `Authorization: Bearer …` header or `auth_token` HttpOnly cookie) rather than per-endpoint annotations on the `AboutController`. All endpoints are reachable by any authenticated principal in the current build — there's no admin-only vs. public split inside this controller.

> 📝 **Tiptap HTML pipeline:** the `ckbContent.body` and `kmrContent.body` columns store full Tiptap HTML. On save, the `TiptapHtmlProcessor` rewrites any inline base64 payloads into S3 URLs as a safety net — so the column never holds raw binary data even if the frontend forgets to upload first.

> ℹ️ **No more "blocks":** the old `AboutBlock` collection is gone. Pages are a single bilingual HTML body each. The `ContentType` enum (TEXT / IMAGE / VIDEO / AUDIO / GALLERY / QUOTE / STATS) has also been removed.

> ℹ️ **Stats survive separately:** the STATS data **cannot** be embedded cleanly in HTML and the frontend renders it from structured JSON. It lives as the JSONB `stats` array on the About row.

> ℹ️ **No upload endpoints in this module:** the previous `POST /upload`, `POST /upload/multiple`, and `DELETE /media` endpoints have been removed. Use the shared `POST /api/v1/media/upload` instead, bake the returned URL into the Tiptap HTML, then submit the JSON body. See [§08](#08--media-upload-pipeline-tiptap).

> ℹ️ **Slug uniqueness:** Both `slugCkb` and `slugKmr` are globally unique across the `about_pages` table. Attempting to create or update with an already-used slug surfaces a DB unique-constraint violation.

> ℹ️ **No `ApiResponse<T>` wrapper:** unlike News / Image / SoundTrack, this controller returns DTOs directly (`AboutResponse`, `List<AboutResponse>`, or empty `204 No Content`). See [§10](#10--response-envelope).

---

## 04 · Read

### `GET /api/v1/about` — getAll

🔒 Auth handled by global security config

Returns all **active** about pages as a flat `List<AboutResponse>`. The service implementation calls `getAllActive()` — inactive pages are filtered out. No pagination at the controller level.

### Request

```
GET /api/v1/about
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
[
  {
    "id":      1,
    "slugCkb": "دەربارەی-ئێمە",
    "slugKmr": "derbare-me",
    "ckbContent": {
      "title":           "دەربارەی ئێمە",
      "subtitle":        "ناوەندی کوردستان بۆ مێژووی هاوچەرخ",
      "metaDescription": "فێربوون دەربارەی مێژوو و ئامانجەکانی ناوەندی کهی",
      "body":            "<h2>پێشەکی</h2><p>ناوەندی کهی…</p><img src=\"https://cdn.khi.iq/about/1/building.jpg\" alt=\"ناوەندەکە\" /><p>…</p>"
    },
    "kmrContent": {
      "title":           "Derbarê Me",
      "subtitle":        "Navenda Kurdistanê ji bo Dîroka Hemdem",
      "metaDescription": "Li ser dîrok û armancên Navenda KHI zêdetir fêr bibin",
      "body":            "<h2>Pêşgotin</h2><p>Navenda KHI…</p><img src=\"https://cdn.khi.iq/about/1/building.jpg\" alt=\"Navend\" />"
    },
    "active": true,
    "stats": [
      { "labelCkb": "کتێب",        "labelKmr": "Pirtûk", "value": "5,000+" },
      { "labelCkb": "ساڵ",          "labelKmr": "Sal",    "value": "15+" },
      { "labelCkb": "توێژینەوە", "labelKmr": "Lêkolîn", "value": "300+" }
    ],
    "createdAt": "2026-04-10T09:00:00",
    "updatedAt": "2026-04-11T14:30:00"
  }
]
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT (if global security requires it) |

---

### `GET /api/v1/about/{slug}` — by slug

🔒 Auth handled by global security config

Fetch an about page by slug. Accepts either the CKB slug or the KMR slug — the service checks both columns and returns whichever matches.

> ⚠️ **Path is `/{slug}` directly** — NOT `/slug/{slug}` as the old doc described. There is no `/active` or `/{id}` GET endpoint in the current controller.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `slug` | String | **Yes** | Either the `slugCkb` or `slugKmr` value |

### Request · CKB Slug (URL-encoded)

```
GET /api/v1/about/%D8%AF%DB%95%D8%B1%D8%A8%D8%A7%D8%B1%DB%95%DB%8C-%D8%A6%DB%8E%D9%85%DB%95
```

### Request · KMR Slug (Latin)

```
GET /api/v1/about/derbare-me
```

### Response · 200 OK

Single `AboutResponse` — same shape as one element of `GET /`.

### Error Responses

| Status | Description |
| --- | --- |
| `404` | No about page found for the given slug |
| `401` | Missing or expired JWT |

---

## 05 · Create

### `POST /api/v1/about`

🔒 Auth handled by global security config · `Content-Type: application/json`

Create a new about page. **JSON-only** — no multipart, no file uploads on this endpoint. Inline images / videos / audio / files must already be uploaded via `POST /api/v1/media/upload`, and their URLs baked into the Tiptap HTML.

### Request Body — Both Languages, Tiptap Body, Stats

```json
{
  "slugCkb": "دەربارەی-ئێمە",
  "slugKmr": "derbare-me",
  "ckbContent": {
    "title":           "دەربارەی ئێمە",
    "subtitle":        "ناوەندی کوردستان بۆ مێژووی هاوچەرخ",
    "metaDescription": "فێربوون دەربارەی مێژوو و ئامانجەکانی ناوەندی کهی",
    "body": "<h2>پێشەکی</h2><p>ناوەندی کهی دامەزراوەیەکی لێکۆڵینەوەییە…</p><img src=\"https://cdn.khi.iq/about/building.jpg\" alt=\"ناوەندەکە\" /><h3>ئامانجەکانمان</h3><p>…</p><blockquote>مێژوو نامەیەکە کە هەموومان دەیدەنووسین — د. ئەحمەد کەریم</blockquote>"
  },
  "kmrContent": {
    "title":           "Derbarê Me",
    "subtitle":        "Navenda Kurdistanê ji bo Dîroka Hemdem",
    "metaDescription": "Li ser dîrok û armancên Navenda KHI zêdetir fêr bibin",
    "body": "<h2>Pêşgotin</h2><p>Navenda KHI damezraweyeke lêkolînê û çandiyê ye…</p><img src=\"https://cdn.khi.iq/about/building.jpg\" alt=\"Navend\" /><h3>Armancên Me</h3><p>…</p><blockquote>Dîrok nameyek e ku em hemû dinivîsin — Dr. Ehmed Kerîm</blockquote>"
  },
  "stats": [
    { "labelCkb": "کتێب",        "labelKmr": "Pirtûk", "value": "5,000+" },
    { "labelCkb": "ساڵ",          "labelKmr": "Sal",    "value": "15+" },
    { "labelCkb": "توێژینەوە", "labelKmr": "Lêkolîn", "value": "300+" }
  ]
}
```

### Request Body — CKB Only, No Stats

```json
{
  "slugCkb": "درباره-ckb",
  "slugKmr": null,
  "ckbContent": {
    "title":           "دەربارەی ئێمە",
    "subtitle":        "ناوەندی کهی",
    "metaDescription": "زانیاری دەربارەی ئێمە",
    "body":            "<p>کورتە دەربارەی ناوەندی کهی.</p>"
  },
  "kmrContent": null,
  "stats":      []
}
```

### Request · curl Example

```bash
curl -X POST https://api.khi.iq/api/v1/about \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "slugCkb": "دەربارەی-ئێمە",
    "slugKmr": "derbare-me",
    "ckbContent": {
      "title": "دەربارەی ئێمە",
      "subtitle": "ناوەندی کوردستان بۆ مێژووی هاوچەرخ",
      "metaDescription": "فێربوون دەربارەی مێژوو و ئامانجەکانی ناوەندی کهی",
      "body": "<h2>پێشەکی</h2><p>ناوەندی کهی…</p>"
    },
    "kmrContent": {
      "title": "Derbarê Me",
      "subtitle": "Navenda Kurdistanê ji bo Dîroka Hemdem",
      "metaDescription": "Li ser dîrok û armancên Navenda KHI fêr bibin",
      "body": "<h2>Pêşgotin</h2><p>Navenda KHI…</p>"
    },
    "stats": []
  }'
```

### Response · 201 Created

```json
{
  "id":      1,
  "slugCkb": "دەربارەی-ئێمە",
  "slugKmr": "derbare-me",
  "ckbContent": {
    "title":           "دەربارەی ئێمە",
    "subtitle":        "ناوەندی کوردستان بۆ مێژووی هاوچەرخ",
    "metaDescription": "فێربوون دەربارەی مێژوو و ئامانجەکانی ناوەندی کهی",
    "body":            "<h2>پێشەکی</h2><p>ناوەندی کهی…</p>"
  },
  "kmrContent": {
    "title":           "Derbarê Me",
    "subtitle":        "Navenda Kurdistanê ji bo Dîroka Hemdem",
    "metaDescription": "Li ser dîrok û armancên Navenda KHI fêr bibin",
    "body":            "<h2>Pêşgotin</h2><p>Navenda KHI…</p>"
  },
  "active": true,
  "stats":  [],
  "createdAt": "2026-04-12T10:00:00",
  "updatedAt": "2026-04-12T10:00:00"
}
```

> ℹ️ Response is **not wrapped** in `ApiResponse<T>` — the controller returns `AboutResponse` directly.

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `slugCkb` is blank or missing (DB-level `@NotBlank` envelope) |
| `409` / `500` | `slugCkb` or `slugKmr` already exists — surfaces as a DB unique-constraint violation (the controller does not pre-check for this) |
| `401` | Missing or expired JWT |

---

## 06 · Update

### `PUT /api/v1/about/{id}`

🔒 Auth handled by global security config · `Content-Type: application/json`

Update an existing about page. The controller receives the same `AboutRequest` shape as create; the service decides which fields to apply. JSON-only.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the about page to update |

### Request Body — Update Page-Level Content + Tiptap Body

```json
{
  "slugCkb": "دەربارەی-ئێمە",
  "slugKmr": "derbare-me",
  "ckbContent": {
    "title":           "دەربارەی ئێمە — نوێکراوەوە",
    "subtitle":        "ناوەندی کوردستان بۆ مێژووی هاوچەرخ",
    "metaDescription": "زانیاری نوێکراوە دەربارەی ئامانجەکانمان",
    "body":            "<h2>پێشەکی نوێکراوە</h2><p>وردبینی نوێی…</p><img src=\"https://cdn.khi.iq/about/hero-new.jpg\" alt=\"دیمەنی نوێ\" />"
  },
  "kmrContent": {
    "title":           "Derbarê Me — Nûvekirî",
    "subtitle":        "Navenda Kurdistanê ji bo Dîroka Hemdem",
    "metaDescription": "Agahiyên nûvekirî li ser armancên me",
    "body":            "<h2>Pêşgotina Nûkirî</h2><p>Naveroka nû…</p>"
  },
  "stats": [
    { "labelCkb": "کتێب",          "labelKmr": "Pirtûk", "value": "6,000+" },
    { "labelCkb": "توێژینەوە", "labelKmr": "Lêkolîn", "value": "350+" }
  ]
}
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/about/1 \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "slugCkb": "دەربارەی-ئێمە",
    "ckbContent": {
      "title": "دەربارەی ئێمە — نوێکراوەوە",
      "body":  "<p>وردبینی نوێ…</p>"
    }
  }'
```

### Response · 200 OK

Returns the updated `AboutResponse`.

### Error Responses

| Status | Description |
| --- | --- |
| `404` | About page with given `id` does not exist |
| `409` / `500` | Updated slug conflicts with an existing record (DB unique-constraint violation) |
| `401` | Missing or expired JWT |

---

## 07 · Delete

### `DELETE /api/v1/about/{id}`

🔒 Auth handled by global security config

Permanently deletes an about page.

> ⚠️ **Returns `204 No Content` with an empty body** — unlike News / Image / SoundTrack / Writing which return `200 OK` with an `ApiResponse` envelope.

> ℹ️ Media files referenced inside the Tiptap body are **not** automatically deleted from S3. Clean-up of orphaned media is a separate concern handled by the shared media-management pipeline.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the about page to delete |

### Response · 204 No Content

```
HTTP/1.1 204 No Content
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | About page with given `id` does not exist |
| `401` | Missing or expired JWT |

---

## 08 · Media Upload Pipeline (Tiptap)

The About module **no longer has its own upload endpoints**. The previous `POST /upload`, `POST /upload/multiple`, and `DELETE /media` endpoints have been removed.

### How it works

1. **Frontend uploads** each image / video / audio / document first via the shared:
   ```
   POST /api/v1/media/upload
   ```
2. The upload endpoint returns a stored URL (S3 or CDN).
3. The frontend bakes that URL into the Tiptap editor as an `<img src="…">`, `<video src="…">`, `<audio src="…">`, or `<a href="…">` element.
4. When the user saves, the entire `ckbContent.body` / `kmrContent.body` HTML is sent in the JSON request to `POST /api/v1/about` or `PUT /api/v1/about/{id}`.
5. The backend `TiptapHtmlProcessor` sanitizes the HTML and acts as a safety net — if any inline base64 payload slipped through (e.g. paste-from-clipboard), it hoists that payload up to S3 and rewrites the `src` to the resulting URL before persisting.

### What was removed

- ❌ `POST /api/v1/about/upload` — gone
- ❌ `POST /api/v1/about/upload/multiple` — gone
- ❌ `DELETE /api/v1/about/media` — gone
- ❌ `UploadResponse` DTO — no longer needed by this module

---

## 09 · DTO Reference

### AboutRequest

Sent as `application/json` body to `POST /` and `PUT /{id}`. There are **no Bean-Validation annotations** on this DTO — slug/length validation is enforced by the JPA entity (`@NotBlank` on `slugCkb`, column lengths).

| Field | Type | Required (Create) | Description |
| --- | --- | --- | --- |
| `slugCkb` | String | **Yes** | Sorani URL slug — unique. Max 200 chars at entity level |
| `slugKmr` | String | No | Kurmanji URL slug — unique, nullable. Max 200 chars |
| `ckbContent` | `AboutContentRequest` | No | Sorani page-level content block |
| `kmrContent` | `AboutContentRequest` | No | Kurmanji page-level content block |
| `stats` | `List<StatItemDto>` | No | Structured stats array |

> ⛔ **Removed from this DTO (vs. old doc):** `heroImageUrl`, `blocks`. These no longer exist on the entity.

### AboutContentRequest

Used inside `ckbContent` / `kmrContent` of `AboutRequest`.

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Page title in this language. Max 300 chars at entity level |
| `subtitle` | String | Short subtitle or tagline. Max 500 chars |
| `metaDescription` | String | SEO meta description. TEXT column |
| `body` | String | **Tiptap HTML body** — all media embedded inline and rewritten to S3 URLs on save |

### StatItemDto

Used in `AboutRequest.stats` and `AboutResponse.stats`.

| Field | Type | Description |
| --- | --- | --- |
| `labelCkb` | String | Sorani label |
| `labelKmr` | String | Kurmanji label |
| `value` | String | Display value (kept as String to allow `"5,000+"`, `"15+"`, etc.) |

### AboutResponse

Returned directly by all read and write endpoints.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `slugCkb` | String | Sorani URL slug |
| `slugKmr` | String | Kurmanji URL slug — may be `null` |
| `ckbContent` | `AboutContentResponse` | Sorani content |
| `kmrContent` | `AboutContentResponse` | Kurmanji content |
| `active` | boolean | Public visibility flag |
| `stats` | `List<StatItemDto>` | Structured stats array |
| `createdAt` | String | ISO-8601 local datetime |
| `updatedAt` | String | ISO-8601 local datetime |

### AboutContentResponse

Used inside `ckbContent` / `kmrContent` of `AboutResponse`.

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Page title |
| `subtitle` | String | Subtitle |
| `metaDescription` | String | SEO meta description |
| `body` | String | **Tiptap HTML body** |

---

## 10 · Response Envelope

The About module is the **only** publishment-style module in the project that does **not** wrap responses in `ApiResponse<T>`. The controller returns:

| Endpoint | Returned shape |
| --- | --- |
| `GET /` | `List<AboutResponse>` directly |
| `GET /{slug}` | `AboutResponse` directly |
| `POST /` | `AboutResponse` directly (with `201 Created`) |
| `PUT /{id}` | `AboutResponse` directly |
| `DELETE /{id}` | `204 No Content` (empty body) |

> ⚠️ When integrating, do **not** assume an `{ success, message, data }` envelope on About responses. The shape is the bare DTO (or empty for delete).

---

## 11 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New about page saved successfully |
| `200 OK` | Read or update succeeded |
| `204 No Content` | Delete succeeded |
| `400 Bad Request` | Validation error (e.g. blank `slugCkb`) |
| `401 Unauthorized` | JWT is missing or expired |
| `404 Not Found` | About page not found |
| `409 Conflict` / `500` | Slug uniqueness violated at the DB level |

> ⚠️ **Validation:** The DTOs themselves have no `@NotBlank`/`@Size` annotations — validation is enforced by JPA on the entity. `@NotBlank` on `slugCkb` triggers a `ConstraintViolationException` (typically surfaced as `400`). Unique slug violations come from the DB.

### Not Found Error Body — `404`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    404,
  "error":     "Not Found",
  "message":   "About page not found with id: 99"
}
```

### Auth Error Body — `401`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Authentication token is missing or expired"
}
```

> ℹ️ Timestamps are ISO-8601 local datetime. `createdAt` / `updatedAt` on `AboutResponse` are **serialized as `String`** (not `LocalDateTime`).

---

## 12 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `GET /` (Admin all) | "Admin all pages incl. inactive" | Now returns **active only** | 🟡 Behaviour changed — there is **no admin-only `getAll`** anymore |
| `GET /active` | "Public active pages" | — | 🔴 **Removed** (merged into `GET /`) |
| `GET /{id}` | "Admin get by id" | — | 🔴 **Removed** |
| `GET /slug/{slug}` | "Get by slug" | Now at `GET /{slug}` (no `/slug/` prefix) | 🟡 **Path changed** |
| `POST /` | Create | Create — **JSON only** | ⚪ Unchanged shape |
| `PUT /{id}` | Update | Update — **JSON only** | ⚪ Unchanged shape |
| `DELETE /{id}` | Delete | Delete — returns **`204 No Content`** (was `ApiResponse<Void>` in old doc) | 🟡 Response envelope changed |
| `POST /upload` | Single file upload | — | 🔴 **Removed** — use shared `POST /api/v1/media/upload` |
| `POST /upload/multiple` | Multi-file upload | — | 🔴 **Removed** |
| `DELETE /media` | Delete S3 file | — | 🔴 **Removed** |

**Endpoint count:** **10 → 5** (5 removed: `/active`, `/{id}`, `/upload`, `/upload/multiple`, `/media`; 1 path renamed: `/slug/{slug}` → `/{slug}`).

> 🚨 **Action required for clients:**
> - `GET /api/v1/about/{id}` no longer exists — fetch by slug instead (or via the `id` returned from `GET /`).
> - `GET /api/v1/about/slug/derbare-me` becomes `GET /api/v1/about/derbare-me`.
> - `GET /api/v1/about/active` becomes `GET /api/v1/about` (still returns active only).
> - All `/upload` and `/media` paths return `404` — switch to the shared `/api/v1/media/upload` pipeline.
> - `DELETE /api/v1/about/{id}` now returns an empty `204`, not an `ApiResponse<Void>` JSON body.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `AboutBlock` JPA entity | Existed | 🔴 **Dropped** — pages no longer have a list of typed blocks |
| `AboutBlockContent` `@Embeddable` | Existed | 🔴 **Dropped** |
| `ContentType` enum (`TEXT`, `IMAGE`, `VIDEO`, `AUDIO`, `GALLERY`, `QUOTE`, `STATS`) | Existed | 🔴 **Dropped** |
| `About.heroImageUrl` | Existed (VARCHAR 1000) | 🔴 **Dropped** — hero is now an `<img>` at the top of the Tiptap body |
| `About.blocks` (`@OneToMany`) | Existed | 🔴 **Dropped** |
| `AboutContent.body` (Tiptap HTML) | — | 🟢 **Added** — TEXT column per language holding full editor output |
| `AboutContent.title`/`subtitle`/`metaDescription` | Existed | ⚪ Unchanged |
| `About.stats` (JSONB `List<StatItem>`) | Was a `STATS` block | 🟢 **Restructured** — now a dedicated JSONB column on `About`, not a block |
| `StatItem` POJO | Was inside block `metadata.statItems` | 🟢 **Promoted** to a top-level structured field |
| `About.active` | Existed | ⚪ Unchanged |
| `About.displayOrder` | Existed | ⚪ Unchanged |
| Slug uniqueness on both `slugCkb` and `slugKmr` | Existed | ⚪ Unchanged |
| DB indexes | Not documented | 🟢 Documented: `idx_about_slug_ckb`, `idx_about_slug_kmr`, `idx_about_active` |
| `@CreationTimestamp` / `@UpdateTimestamp` | Existed | ⚪ Unchanged |

### C) DTO comparison

| DTO | Old version | New version |
| --- | --- | --- |
| `AboutRequest` | `slugCkb`, `slugKmr`, `heroImageUrl`, `ckbContent`, `kmrContent`, `blocks` | `slugCkb`, `slugKmr`, `ckbContent`, `kmrContent`, `stats`. **`heroImageUrl` and `blocks` removed.** **`stats` added.** |
| `AboutContentRequest` | `title`, `subtitle`, `metaDescription` | + `body` (Tiptap HTML). 🟢 **Added** |
| `AboutBlockRequest` | Existed (`contentType`, `sequence`, `ckbContent`, `kmrContent`, `mediaUrl`, `thumbnailUrl`, `metadata`) | 🔴 **Removed** |
| `AboutBlockContentRequest` | Existed (`contentText`, `title`, `altText`) | 🔴 **Removed** |
| `AboutResponse` | `id`, `slugCkb`, `slugKmr`, `heroImageUrl`, `ckbContent`, `kmrContent`, `active`, `blocks`, `createdAt`, `updatedAt` | `id`, `slugCkb`, `slugKmr`, `ckbContent`, `kmrContent`, `active`, `stats`, `createdAt`, `updatedAt`. **`heroImageUrl` and `blocks` removed; `stats` added.** Timestamps are `String`, not `LocalDateTime` |
| `AboutContentResponse` | `title`, `subtitle`, `metaDescription` | + `body` (Tiptap HTML) |
| `AboutBlockResponse` / `AboutBlockContentResponse` | Existed | 🔴 **Removed** |
| `StatItemDto` (`labelCkb`, `labelKmr`, `value`) | Was inside block `metadata` | 🟢 **Top-level DTO** now, used by `stats` |
| `UploadResponse` | Existed | 🔴 **Removed** — module no longer has its own upload endpoints |
| Bean Validation on DTOs | Implied | 🟡 **Confirmed absent** — DTOs carry no `@NotBlank` / `@Size`. Enforcement happens on the JPA entity (`@NotBlank` on `slugCkb`, column lengths) |

### D) Validation / error-key comparison

| Old error key | New status |
| --- | --- |
| `about.not.found` | 🟡 No dedicated key in code — surfaced as generic `404` |
| `about.slug.not.found` | 🟡 Same — generic `404` from `GET /{slug}` |
| `about.slug.ckb.required` | 🟡 Surfaced as a `ConstraintViolationException` on the entity's `@NotBlank` |
| `about.slug.ckb.exists` / `about.slug.kmr.exists` | 🟡 Surfaced as a DB unique-constraint violation (no pre-check in controller / service) |
| `about.block.invalid.type` | 🔴 **Obsolete** — no `ContentType` enum, no blocks |
| `about.media.url.required` | 🔴 **Obsolete** — no `DELETE /media` endpoint anymore |

### E) Auth model

| Item | Old | New |
| --- | --- | --- |
| Per-endpoint admin/public split | Documented (admin vs. public mix) | 🟡 **Controller has no per-method auth annotations** — auth is handled by the project's global security configuration. No explicit `@PreAuthorize` split between admin and public inside this module |
| `GET /active` as a separate public endpoint | Existed | 🔴 Removed — `GET /` returns active only now |

### F) Response envelope

| Endpoint group | Old | New |
| --- | --- | --- |
| `GET /` / `GET /active` | `ApiResponse<List<AboutResponse>>` (claimed) | 🟡 **`List<AboutResponse>` directly** — no envelope |
| `GET /{id}` / `GET /slug/{slug}` | `ApiResponse<AboutResponse>` (claimed) | 🟡 **`AboutResponse` directly** |
| `POST /` / `PUT /{id}` | `ApiResponse<AboutResponse>` (claimed) | 🟡 **`AboutResponse` directly** |
| `DELETE /{id}` | `ApiResponse<Void>` (claimed) | 🟡 **`204 No Content`** with empty body |
| Upload endpoints | `ApiResponse<UploadResponse>` | 🔴 Removed entirely |

### G) Summary

- 🔴 **Removed:** the entire block-based architecture (`AboutBlock`, `AboutBlockContent`, `ContentType` enum, `blocks` collection, `AboutBlockRequest` / `AboutBlockResponse` / `AboutBlockContentRequest` / `AboutBlockContentResponse`); the `heroImageUrl` field; all media-upload endpoints (`POST /upload`, `POST /upload/multiple`, `DELETE /media`) and the `UploadResponse` DTO; the separate `GET /active` and `GET /{id}` endpoints; the `/slug/` URL prefix; the `ApiResponse<T>` wrapper on every endpoint.
- 🟢 **Added:** bilingual **Tiptap HTML `body`** column on `AboutContent` (the new home for all inline images / videos / audio / files); top-level JSONB `stats` column with `StatItem` POJO (`labelCkb`, `labelKmr`, `value`); explicit DB indexes (`idx_about_slug_ckb`, `idx_about_slug_kmr`, `idx_about_active`); `TiptapHtmlProcessor` safety net that hoists inline base64 payloads to S3 on save.
- 🟡 **Changed (still same URL):** `GET /` now returns **active only** (no admin-vs-public split); `DELETE /{id}` now returns **`204 No Content`** instead of `ApiResponse<Void>`; every endpoint returns DTOs **directly** (no `ApiResponse<T>` wrapper); `GET /slug/{slug}` is now `GET /{slug}`.
- 🟡 **Caveats:** the DTOs themselves have **no Bean-Validation annotations** — validation is enforced by the JPA entity (`@NotBlank` on `slugCkb`, column length caps). The controller has **no `@PreAuthorize`** annotations — auth is enforced globally. Slug uniqueness conflicts surface as raw DB constraint violations (no pre-check).
- ⚪ **Unchanged:** the `About` table name (`about_pages`), bilingual slug uniqueness, `active` flag, `displayOrder` column, the `AboutContent` embeddable's `title` / `subtitle` / `metaDescription` fields, `@CreationTimestamp` / `@UpdateTimestamp` behaviour.
