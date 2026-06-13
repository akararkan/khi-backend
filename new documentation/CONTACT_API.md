# KHI Backend — Contact API Reference

> **ak.dev · KHI Platform** · Spring Boot 3 · JWT Auth Required · Bilingual (CKB / KMR) · 7 Endpoints · JSON Only · Tiptap HTML · Map Embed

Complete documentation for all Contact page management endpoints — create, update, delete, list, and slug lookup — including bilingual content, Tiptap HTML descriptions (where inline images / video / audio / files live), contact details, map embedding, DTOs, and full request/response examples.

> 🚨 **MAJOR REWRITE.** The Contact module no longer has a `heroImageUrl` field or any media-upload endpoints. All visual media now lives **inline inside a Tiptap HTML `description`** per language. See [§11 · Change Log](#11--change-log--old-vs-new) for full details.

---

## Table of Contents

- [01 · Overview](#01--overview)
- [02 · Data Models](#02--data-models)
- [03 · Authentication & Notes](#03--authentication--notes)
- [04 · Read](#04--read)
  - `GET /` (getAll — admin, includes inactive)
  - `GET /active` (getAllActive — public, active only)
  - `GET /{id}`
  - `GET /slug/{slug}`
- [05 · Create](#05--create) — `POST /`
- [06 · Update](#06--update) — `PUT /{id}`
- [07 · Delete](#07--delete) — `DELETE /{id}`
- [08 · Media Upload Pipeline (Tiptap)](#08--media-upload-pipeline-tiptap)
- [09 · DTO Reference](#09--dto-reference)
- [10 · Error Responses](#10--error-responses)
- [11 · Change Log — Old vs. New](#11--change-log--old-vs-new)

---

## 01 · Overview

The Contact module manages bilingual Contact pages for the KHI platform with full support for CKB (Sorani) and KMR (Kurmanji) Kurdish. Each Contact page carries:

- **Bilingual slugs** — `slugCkb` (Sorani, **required**, unique) and `slugKmr` (Kurmanji, optional, unique). Either slug resolves to the same page via `GET /slug/{slug}`.
- **Per-language page-level content** (`ckbContent` / `kmrContent`) — each holding: `title`, `subtitle`, `address`, `workingHours`, and a **Tiptap HTML `description`**.
- **Tiptap HTML `description`** — all visual media (images, videos, voice/audio, downloadable files) lives **inline inside the `description` HTML** as `<img>`, `<video>`, `<audio>`, or `<a href>` tags whose URLs already point to S3. There is no separate hero or gallery field anymore.
- **Language-agnostic contact details** — `phone`, `secondaryPhone`, `email`, `mapEmbedUrl`, `latitude`, `longitude`. Same regardless of locale.
- **Active flag** — `active` controls public visibility. Both admin (`GET /`) and public (`GET /active`) listings exist.
- **Display order** — `displayOrder` integer for sorting pages.
- **Auto timestamps** via `@CreationTimestamp` / `@UpdateTimestamp`.

### Base URL

```
/api/v1/contact
```

### Endpoint Summary

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | All contact pages (includes inactive — intended for admin) |
| `GET` | `/active` | All active contact pages (public-facing) |
| `GET` | `/{id}` | Fetch a single page by ID |
| `GET` | `/slug/{slug}` | Fetch a page by CKB or KMR slug |
| `POST` | `/` | Create a new contact page — `application/json` |
| `PUT` | `/{id}` | Update a contact page — `application/json` |
| `DELETE` | `/{id}` | Delete a contact page |

> ⚠️ **Endpoint count: 7** (down from the old doc's claim of 9). The previous `POST /upload` and `DELETE /media` endpoints have been removed — see [§08](#08--media-upload-pipeline-tiptap) for the new upload flow.

---

## 02 · Data Models

One JPA entity + one embeddable make up the Contact module. `Contact` is the aggregate root.

### Contact — `contact_pages`

**DB indexes:** `idx_contact_slug_ckb`, `idx_contact_slug_kmr`, `idx_contact_active`.

| Field | DB Column | DB Type | Constraint | Description |
| --- | --- | --- | --- | --- |
| `id` | `id` | BIGINT | PK / AUTO | Auto-increment primary key |
| `slugCkb` | `slug_ckb` | VARCHAR(200) | **UNIQUE, NOT NULL** (`@NotBlank`) | Sorani route identifier |
| `slugKmr` | `slug_kmr` | VARCHAR(200) | UNIQUE, NULLABLE | Kurmanji route identifier |
| `active` | `active` | BOOLEAN | NOT NULL | `true` = visible on public frontend. Default `true` |
| `displayOrder` | `display_order` | INT | NULLABLE | Sort order. Default `0` |
| `ckbContent` | (embedded) | — | NULLABLE | Sorani page-level content: `title`, `subtitle`, `address`, `workingHours`, `description` (Tiptap HTML) |
| `kmrContent` | (embedded) | — | NULLABLE | Kurmanji page-level content: same shape |
| `phone` | `phone` | VARCHAR(60) | NULLABLE | Primary phone number — e.g. `"+964 770 123 4567"` |
| `secondaryPhone` | `secondary_phone` | VARCHAR(60) | NULLABLE | Secondary / additional phone number |
| `email` | `email` | VARCHAR(200) | NULLABLE | Primary contact email |
| `mapEmbedUrl` | `map_embed_url` | TEXT | NULLABLE | Google Maps embed URL or any `<iframe>`-compatible map URL |
| `latitude` | `latitude` | DOUBLE | NULLABLE | Latitude for map marker or Open Maps link |
| `longitude` | `longitude` | DOUBLE | NULLABLE | Longitude for map marker or Open Maps link |
| `createdAt` | `created_at` | TIMESTAMP | (auto, NOT NULL on insert) | Set by `@CreationTimestamp`. `LocalDateTime` |
| `updatedAt` | `updated_at` | TIMESTAMP | (auto) | Set by `@UpdateTimestamp` |

> ⛔ **Removed from this entity (vs. old doc):** `heroImageUrl` — gone. Hero / banner image is now an `<img>` at the top of the Tiptap description per language.

### ContactContent — `@Embeddable`

Shared embeddable used for both `ckbContent` and `kmrContent` inside `Contact`.

| Field | DB Column (CKB) | DB Column (KMR) | DB Type | Description |
| --- | --- | --- | --- | --- |
| `title` | `title_ckb` | `title_kmr` | VARCHAR(300) | Page title in this language |
| `subtitle` | `subtitle_ckb` | `subtitle_kmr` | VARCHAR(500) | Short subtitle / call-to-action text |
| `address` | `address_ckb` | `address_kmr` | VARCHAR(500) | Physical address in this language |
| `workingHours` | `working_hours_ckb` | `working_hours_kmr` | VARCHAR(300) | Office / working hours in this language |
| `description` | `description_ckb` | `description_kmr` | TEXT | **Tiptap HTML description** — full editor output, with inline `<img>` / `<video>` / `<audio>` / `<a>` tags already pointing at S3 URLs |

---

## 03 · Authentication & Notes

> ℹ️ **Authentication** is enforced by the project's global security configuration (JWT via `Authorization: Bearer …` header or `auth_token` HttpOnly cookie). The `ContactController` does NOT carry per-endpoint `@PreAuthorize` annotations — there is no explicit admin-vs-public split inside this module, even though `GET /` and `GET /active` are intended to play those roles.

> 📝 **Tiptap HTML pipeline:** the `ckbContent.description` and `kmrContent.description` columns store full Tiptap HTML. On save, the `TiptapHtmlProcessor` rewrites any inline base64 payloads into S3 URLs as a safety net — so the column never holds raw binary data even if the frontend forgets to upload first.

> ℹ️ **No upload endpoints in this module:** the previous `POST /upload` and `DELETE /media` endpoints have been removed. Use the shared `POST /api/v1/media/upload` instead, bake the returned URL into the Tiptap HTML, then submit the JSON body. See [§08](#08--media-upload-pipeline-tiptap).

> ℹ️ **Slug uniqueness:** Both `slugCkb` and `slugKmr` are globally unique across the `contact_pages` table. Uniqueness conflicts surface as raw DB constraint violations (no pre-check in controller / service).

> ℹ️ **Phone formatting:** No server-side format validation is applied to `phone` or `secondaryPhone`. Store in E.164 or any human-readable format — e.g. `"+964 770 123 4567"`.

> ℹ️ **Update semantics:** On `PUT /{id}`, a `null` field in the JSON is left to the service implementation — the controller passes through the full DTO. Send only the fields you want to change.

---

## 04 · Read

### `GET /api/v1/contact` — getAll

🔒 Auth handled by global security config

Returns **all contact pages** (including inactive). Service implementation calls `getAll()`. Intended for admin use. Returns a flat `List<ContactResponse>` wrapped in `ApiResponse`.

### Request

```
GET /api/v1/contact
Authorization: Bearer eyJhbGci...
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Contact pages fetched",
  "data": [
    {
      "id":      1,
      "slugCkb": "پەیوەندی",
      "slugKmr": "peywendî",
      "ckbContent": {
        "title":        "پەیوەندیمان پێوە بکە",
        "subtitle":     "خۆشحاڵ دەبین بیستینە",
        "address":      "هەولێر، کوردستان، عێراق",
        "workingHours": "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠",
        "description":  "<p>پەیوەندیمان پێوە بکە بۆ هەر پرسیارێک.</p><img src=\"https://cdn.khi.iq/contact/team.jpg\" alt=\"تیمەکەی کهی\" />"
      },
      "kmrContent": {
        "title":        "Bi Me re Têkilî Daynin",
        "subtitle":     "Kêfxweş dibin ku ji we bihizin",
        "address":      "Hewlêr, Kurdistanê, Iraq",
        "workingHours": "Yekşem - Pêncşem, 9:00 - 16:00",
        "description":  "<p>Ji bo her pirsekê bi me re têkilî daynin.</p><img src=\"https://cdn.khi.iq/contact/team.jpg\" alt=\"Tîma KHI\" />"
      },
      "phone":          "+964 750 123 4567",
      "secondaryPhone": "+964 770 987 6543",
      "email":          "info@khi.iq",
      "mapEmbedUrl":    "https://www.google.com/maps/embed?pb=…",
      "latitude":       36.1901,
      "longitude":      44.0091,
      "active":         true,
      "createdAt":      "2026-04-10T09:00:00",
      "updatedAt":      "2026-04-11T14:00:00"
    }
  ]
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `401` | Missing or expired JWT (if global security requires it) |

---

### `GET /api/v1/contact/active` — getAllActive

🌐 Auth handled by global security config (typically public)

Returns only **active** contact pages (`active = true`). Intended for the public frontend.

### Request

```
GET /api/v1/contact/active
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Active contact pages fetched",
  "data": [ /* same shape as GET / — only active=true records */ ]
}
```

---

### `GET /api/v1/contact/{id}`

🔒 Auth handled by global security config

Fetch a single contact page by primary key.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the contact page |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Contact page fetched",
  "data": { /* full ContactResponse — same shape as single item in getAll */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Contact page with given `id` does not exist |
| `401` | Missing or expired JWT |

---

### `GET /api/v1/contact/slug/{slug}`

🌐 Auth handled by global security config (typically public)

Fetch a contact page by slug. Accepts either the CKB slug or the KMR slug — the service resolves whichever matches. Used by the public frontend for route-based navigation.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `slug` | String | **Yes** | Either the `slugCkb` or `slugKmr` value |

### Request · CKB Slug (URL-encoded)

```
GET /api/v1/contact/slug/%D9%BE%DB%95%DB%8C%D9%88%DB%95%D9%86%D8%AF%DB%8C
```

### Request · KMR Slug (Latin)

```
GET /api/v1/contact/slug/peywendî
```

### Response · 200 OK

```json
{
  "success": true,
  "message": "Contact page fetched",
  "data": { /* full ContactResponse */ }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | No contact page found for the given slug |

---

## 05 · Create

### `POST /api/v1/contact`

🔒 Auth handled by global security config · `Content-Type: application/json`

Create a new contact page with bilingual content and contact details. **JSON-only** — no multipart, no file uploads. Inline images / videos / audio / files must already be uploaded via `POST /api/v1/media/upload` and their URLs baked into the Tiptap HTML.

### Request Body — Full Example (Both Languages, Tiptap Description, Map Embed)

```json
{
  "slugCkb": "پەیوەندی",
  "slugKmr": "peywendî",
  "ckbContent": {
    "title":        "پەیوەندیمان پێوە بکە",
    "subtitle":     "خۆشحاڵ دەبین بیستینە",
    "address":      "هەولێر، کوردستان، عێراق",
    "workingHours": "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠",
    "description":  "<p>پەیوەندیمان پێوە بکە بۆ هەر پرسیارێک.</p><img src=\"https://cdn.khi.iq/contact/team.jpg\" alt=\"تیمەکەی کهی\" />"
  },
  "kmrContent": {
    "title":        "Bi Me re Têkilî Daynin",
    "subtitle":     "Kêfxweş dibin ku ji we bihizin",
    "address":      "Hewlêr, Kurdistanê, Iraq",
    "workingHours": "Yekşem - Pêncşem, 9:00 - 16:00",
    "description":  "<p>Ji bo her pirsekê bi me re têkilî daynin.</p><img src=\"https://cdn.khi.iq/contact/team.jpg\" alt=\"Tîma KHI\" />"
  },
  "phone":          "+964 750 123 4567",
  "secondaryPhone": "+964 770 987 6543",
  "email":          "info@khi.iq",
  "mapEmbedUrl":    "https://www.google.com/maps/embed?pb=!1m18!1m12!...",
  "latitude":       36.1901,
  "longitude":      44.0091
}
```

### Request Body — CKB Only, No Map Embed

```json
{
  "slugCkb": "پەیوەندی-ckb",
  "slugKmr": null,
  "ckbContent": {
    "title":        "پەیوەندیمان پێوە بکە",
    "address":      "سلێمانی، کوردستان، عێراق",
    "workingHours": "شەممە - چوارشەممە، ١٠:٠٠ - ١٥:٠٠",
    "description":  "<p>کۆتایی ڕۆژی شەممە و یەکشەممە داخراوین.</p>"
  },
  "kmrContent":     null,
  "phone":          "+964 770 111 2222",
  "email":          "contact@khi.iq",
  "latitude":       35.5650,
  "longitude":      45.4350
}
```

### Request · curl Example

```bash
curl -X POST https://api.khi.iq/api/v1/contact \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "slugCkb": "پەیوەندی",
    "slugKmr": "peywendî",
    "ckbContent": {
      "title": "پەیوەندیمان پێوە بکە",
      "address": "هەولێر، کوردستان، عێراق",
      "workingHours": "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠",
      "description": "<p>پەیوەندی پێوەمان بگرە.</p>"
    },
    "kmrContent": {
      "title": "Bi Me re Têkilî Daynin",
      "address": "Hewlêr, Kurdistanê, Iraq",
      "workingHours": "Yekşem - Pêncşem, 9:00 - 16:00",
      "description": "<p>Bi me re têkilî daynin.</p>"
    },
    "phone": "+964 750 123 4567",
    "email": "info@khi.iq",
    "latitude": 36.1901,
    "longitude": 44.0091
  }'
```

### Response · 201 Created

```json
{
  "success": true,
  "message": "Contact page created successfully",
  "data": {
    "id":      1,
    "slugCkb": "پەیوەندی",
    "slugKmr": "peywendî",
    "ckbContent": {
      "title":        "پەیوەندیمان پێوە بکە",
      "subtitle":     "خۆشحاڵ دەبین بیستینە",
      "address":      "هەولێر، کوردستان، عێراق",
      "workingHours": "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠",
      "description":  "<p>پەیوەندیمان پێوە بکە بۆ هەر پرسیارێک.</p>"
    },
    "kmrContent": {
      "title":        "Bi Me re Têkilî Daynin",
      "subtitle":     "Kêfxweş dibin ku ji we bihizin",
      "address":      "Hewlêr, Kurdistanê, Iraq",
      "workingHours": "Yekşem - Pêncşem, 9:00 - 16:00",
      "description":  "<p>Ji bo her pirsekê bi me re têkilî daynin.</p>"
    },
    "phone":          "+964 750 123 4567",
    "secondaryPhone": null,
    "email":          "info@khi.iq",
    "mapEmbedUrl":    null,
    "latitude":       36.1901,
    "longitude":      44.0091,
    "active":         true,
    "createdAt":      "2026-04-12T10:00:00",
    "updatedAt":      "2026-04-12T10:00:00"
  }
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `400` | `slugCkb` is blank or missing (entity-level `@NotBlank`) |
| `409` / `500` | `slugCkb` or `slugKmr` already exists — surfaces as a DB unique-constraint violation |
| `401` | Missing or expired JWT |

---

## 06 · Update

### `PUT /api/v1/contact/{id}`

🔒 Auth handled by global security config · `Content-Type: application/json`

Update an existing contact page. The controller receives the same `ContactRequest` shape as create; the service decides which fields to apply. JSON-only.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the contact page to update |

### Request Body — Update Phone and Email

```json
{
  "phone":          "+964 750 999 8888",
  "secondaryPhone": "+964 770 111 0000",
  "email":          "newcontact@khi.iq"
}
```

### Request Body — Update Bilingual Text

```json
{
  "ckbContent": {
    "title":        "پەیوەندیمان پێوە بکە — نوێکراوەوە",
    "address":      "هەولێر، شاری باکووری کوردستان، عێراق",
    "workingHours": "یەکشەممە - شەممە، ٨:٠٠ - ١٧:٠٠",
    "description":  "<p>کاتژمێرە نوێکراوەکانمان.</p>"
  },
  "kmrContent": {
    "title":        "Bi Me re Têkilî Daynin — Nûvekirî",
    "address":      "Hewlêr, Bakurê Kurdistanê, Iraq",
    "workingHours": "Yekşem - Şemî, 8:00 - 17:00",
    "description":  "<p>Demên xebatê yên nûvekirî.</p>"
  }
}
```

### Request Body — Add Map Embed and Coordinates

```json
{
  "mapEmbedUrl": "https://www.google.com/maps/embed?pb=!1m18!1m12!...",
  "latitude":    36.1912,
  "longitude":   44.0105
}
```

### Request · curl Example

```bash
curl -X PUT https://api.khi.iq/api/v1/contact/1 \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+964 750 999 8888",
    "email": "newcontact@khi.iq",
    "mapEmbedUrl": "https://www.google.com/maps/embed?pb=!1m18!1m12!..."
  }'
```

### Response · 200 OK

Returns the updated `ContactResponse` wrapped in `ApiResponse`.

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Contact page with given `id` does not exist |
| `409` / `500` | Updated slug conflicts with an existing record (DB unique-constraint violation) |
| `401` | Missing or expired JWT |

---

## 07 · Delete

### `DELETE /api/v1/contact/{id}`

🔒 Auth handled by global security config

Permanently deletes a contact page. Returns `200 OK` with an `ApiResponse<Void>` (no `204 No Content` here — that's an About-only convention).

> ℹ️ Media files referenced inside the Tiptap description are **not** automatically deleted from S3. Clean-up of orphaned media is a separate concern handled by the shared media-management pipeline.

### Path Parameter

| Param | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | Long | **Yes** | Primary key of the contact page to delete |

### Response · 200 OK

```json
{
  "success": true,
  "message": "Contact page deleted successfully",
  "data":    null
}
```

### Error Responses

| Status | Description |
| --- | --- |
| `404` | Contact page with given `id` does not exist |
| `401` | Missing or expired JWT |

---

## 08 · Media Upload Pipeline (Tiptap)

The Contact module **no longer has its own upload endpoints**. The previous `POST /upload` and `DELETE /media` endpoints have been removed.

### How it works

1. **Frontend uploads** each image / video / audio / document first via the shared:
   ```
   POST /api/v1/media/upload
   ```
2. The upload endpoint returns a stored URL (S3 or CDN).
3. The frontend bakes that URL into the Tiptap editor as an `<img src="…">`, `<video src="…">`, `<audio src="…">`, or `<a href="…">` element.
4. When the user saves, the entire `ckbContent.description` / `kmrContent.description` HTML is sent in the JSON request to `POST /api/v1/contact` or `PUT /api/v1/contact/{id}`.
5. The backend `TiptapHtmlProcessor` sanitizes the HTML and acts as a safety net — if any inline base64 payload slipped through (e.g. paste-from-clipboard), it hoists that payload up to S3 and rewrites the `src` to the resulting URL before persisting.

### What was removed

- ❌ `POST /api/v1/contact/upload` — gone
- ❌ `DELETE /api/v1/contact/media` — gone
- ❌ `UploadResponse` DTO — no longer needed by this module

---

## 09 · DTO Reference

### ContactRequest

Sent as `application/json` body to `POST /` and `PUT /{id}`. **No Bean-Validation annotations** on this DTO — slug/length validation is enforced by the JPA entity (`@NotBlank` on `slugCkb`, column lengths).

| Field | Type | Required (Create) | Description |
| --- | --- | --- | --- |
| `slugCkb` | String | **Yes** | Sorani URL slug — unique. Max 200 chars at entity level |
| `slugKmr` | String | No | Kurmanji URL slug — unique, nullable. Max 200 chars |
| `ckbContent` | `ContactContentRequest` | No | Sorani page-level content block |
| `kmrContent` | `ContactContentRequest` | No | Kurmanji page-level content block |
| `phone` | String | No | Primary phone number. Max 60 chars |
| `secondaryPhone` | String | No | Secondary phone number. Max 60 chars |
| `email` | String | No | Primary contact email. Max 200 chars |
| `mapEmbedUrl` | String | No | Google Maps embed URL or any `<iframe>`-compatible map URL. TEXT column |
| `latitude` | Double | No | Latitude for map marker |
| `longitude` | Double | No | Longitude for map marker |

> ⛔ **Removed from this DTO (vs. old doc):** `heroImageUrl`. The field no longer exists on the entity.

### ContactContentRequest

Used inside `ckbContent` / `kmrContent` of `ContactRequest`.

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Page title in this language. Max 300 chars |
| `subtitle` | String | Short subtitle / call-to-action. Max 500 chars |
| `address` | String | Physical address in this language. Max 500 chars |
| `workingHours` | String | Office / working hours. Max 300 chars |
| `description` | String | **Tiptap HTML description** — all media embedded inline and rewritten to S3 URLs on save |

### ContactResponse

Returned by all read and write endpoints, wrapped in `ApiResponse<T>`.

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | DB primary key |
| `slugCkb` | String | Sorani URL slug |
| `slugKmr` | String | Kurmanji URL slug — may be `null` |
| `ckbContent` | `ContactContentResponse` | Sorani content |
| `kmrContent` | `ContactContentResponse` | Kurmanji content |
| `phone` | String | Primary phone number — may be `null` |
| `secondaryPhone` | String | Secondary phone number — may be `null` |
| `email` | String | Primary contact email — may be `null` |
| `mapEmbedUrl` | String | Map embed URL — may be `null` |
| `latitude` | Double | Latitude — may be `null` |
| `longitude` | Double | Longitude — may be `null` |
| `active` | boolean | Public visibility flag |
| `createdAt` | String | ISO-8601 local datetime (**String, not LocalDateTime**) |
| `updatedAt` | String | ISO-8601 local datetime (**String, not LocalDateTime**) |

### ContactContentResponse

Used inside `ckbContent` / `kmrContent` of `ContactResponse`.

| Field | Type | Description |
| --- | --- | --- |
| `title` | String | Page title |
| `subtitle` | String | Subtitle / call-to-action |
| `address` | String | Physical address |
| `workingHours` | String | Office / working hours |
| `description` | String | **Tiptap HTML description** |

### ApiResponse&lt;T&gt;

All endpoints return this wrapper.

| Field | Type | Description |
| --- | --- | --- |
| `success` | boolean | `true` on success, `false` on failure |
| `message` | String | Human-readable result message |
| `data` | T | Response payload. Absent on failure |

---

## 10 · Error Responses

### HTTP Status Code Reference

| Status | Description |
| --- | --- |
| `201 Created` | New contact page saved successfully |
| `200 OK` | Read, update, or delete succeeded |
| `400 Bad Request` | Validation error (e.g. blank `slugCkb`) |
| `401 Unauthorized` | JWT is missing or expired |
| `404 Not Found` | Contact page not found |
| `409 Conflict` / `500` | Slug uniqueness violated at the DB level |
| `500 Internal Error` | Unexpected server failure — check logs |

> ⚠️ **Validation:** The DTOs themselves have no `@NotBlank`/`@Size` annotations — validation is enforced by JPA on the entity. `@NotBlank` on `slugCkb` triggers a `ConstraintViolationException` (typically surfaced as `400`). Unique slug violations come from the DB.

### Not Found Error Body — `404`

```json
{
  "timestamp": "2026-04-12T10:00:00",
  "status":    404,
  "error":     "Not Found",
  "message":   "Contact page not found with id: 99"
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

> ℹ️ Timestamps are ISO-8601 local datetime. `createdAt` / `updatedAt` on `ContactResponse` are **serialized as `String`** (not `LocalDateTime`).

---

## 11 · Change Log — Old vs. New

### A) Endpoint comparison

| Endpoint (path) | Old version | New version | Change |
| --- | --- | --- | --- |
| `GET /` | "Admin all incl. inactive" | "All pages (incl. inactive)" — wrapped in `ApiResponse` | ⚪ Unchanged shape |
| `GET /active` | "Public active pages" | "All active pages" | ⚪ Unchanged |
| `GET /{id}` | Admin get by id | Get by id | ⚪ Unchanged |
| `GET /slug/{slug}` | Public get by slug | Get by slug | ⚪ Unchanged |
| `POST /` | Create — JSON | Create — JSON | ⚪ Unchanged shape (body trimmed of `heroImageUrl`) |
| `PUT /{id}` | Update — JSON | Update — JSON | ⚪ Unchanged shape (body trimmed of `heroImageUrl`) |
| `DELETE /{id}` | Delete | Delete — returns `ApiResponse<Void>` `200 OK` | ⚪ Unchanged shape |
| `POST /upload` | Single file upload | — | 🔴 **Removed** — use shared `POST /api/v1/media/upload` |
| `DELETE /media` | Delete S3 file | — | 🔴 **Removed** |

**Endpoint count:** **9 → 7** (2 removed: `/upload`, `/media`).

> 🚨 **Action required for clients:**
> - `POST /api/v1/contact/upload` and `DELETE /api/v1/contact/media` now return `404` — switch to the shared `/api/v1/media/upload` pipeline.
> - Stop sending `heroImageUrl` in create/update payloads — the field is gone. Bake your hero image as an `<img>` at the top of the Tiptap description.

### B) Data model comparison

| Item | Old version | New version |
| --- | --- | --- |
| `Contact.heroImageUrl` | Existed (VARCHAR 1000) | 🔴 **Dropped** — hero is now an `<img>` at the top of the Tiptap description |
| `ContactContent.description` (Tiptap HTML) | — | 🟢 **Added** — TEXT column per language holding full editor output |
| `ContactContent.title` / `subtitle` / `address` / `workingHours` | Existed | ⚪ Unchanged |
| Language-agnostic contact details (`phone`, `secondaryPhone`, `email`, `mapEmbedUrl`, `latitude`, `longitude`) | Existed | ⚪ Unchanged |
| `Contact.active` / `displayOrder` | Existed | ⚪ Unchanged |
| Slug uniqueness on both `slugCkb` and `slugKmr` | Existed | ⚪ Unchanged |
| DB indexes | Not documented | 🟢 Documented: `idx_contact_slug_ckb`, `idx_contact_slug_kmr`, `idx_contact_active` |
| `@CreationTimestamp` / `@UpdateTimestamp` | Existed | ⚪ Unchanged |

### C) DTO comparison

| DTO | Old version | New version |
| --- | --- | --- |
| `ContactRequest` | `slugCkb`, `slugKmr`, `heroImageUrl`, `ckbContent`, `kmrContent`, `phone`, `secondaryPhone`, `email`, `mapEmbedUrl`, `latitude`, `longitude` | **`heroImageUrl` removed.** Everything else unchanged |
| `ContactContentRequest` | `title`, `subtitle`, `address`, `workingHours` | + `description` (Tiptap HTML). 🟢 **Added** |
| `ContactResponse` | Old shape included `heroImageUrl`; timestamps were implied `LocalDateTime` | **`heroImageUrl` removed.** Timestamps are now **`String`**, not `LocalDateTime` |
| `ContactContentResponse` | `title`, `subtitle`, `address`, `workingHours` | + `description` (Tiptap HTML) |
| `UploadResponse` | Existed | 🔴 **Removed** — module no longer has its own upload endpoint |
| Bean Validation on DTOs | Implied (`slugCkb` required, max lengths) | 🟡 **Confirmed absent** on the DTOs — enforcement happens on the JPA entity (`@NotBlank` on `slugCkb`, column lengths) |

### D) Validation / error-key comparison

| Old error key | New status |
| --- | --- |
| `contact.not.found` | 🟡 No dedicated key in code — surfaced as a generic `404` |
| `contact.slug.not.found` | 🟡 Same — generic `404` from `GET /slug/{slug}` |
| `contact.slug.ckb.required` | 🟡 Surfaced as a `ConstraintViolationException` on the entity's `@NotBlank` |
| `contact.slug.ckb.exists` / `contact.slug.kmr.exists` | 🟡 Surfaced as a DB unique-constraint violation (no pre-check in controller / service) |
| `contact.media.url.required` | 🔴 **Obsolete** — no `DELETE /media` endpoint anymore |

### E) Auth model

| Item | Old | New |
| --- | --- | --- |
| Per-endpoint admin/public split | Documented (admin vs. public mix) | 🟡 **Controller has no per-method auth annotations** — auth is handled by the project's global security configuration. No explicit `@PreAuthorize` split between admin and public inside this module |

### F) Response envelope

| Endpoint group | Old | New |
| --- | --- | --- |
| All endpoints | `ApiResponse<T>` wrapper claimed | ⚪ **Unchanged** — still `ApiResponse<T>` on every endpoint (including `DELETE /{id}` → `ApiResponse<Void>`). This is **different from the About module**, which strips the wrapper. |
| Upload endpoints | `ApiResponse<UploadResponse>` | 🔴 Removed entirely |

### G) Summary

- 🔴 **Removed:** `POST /api/v1/contact/upload` and `DELETE /api/v1/contact/media` endpoints; the `heroImageUrl` column on the entity and in DTOs; the `UploadResponse` DTO.
- 🟢 **Added:** bilingual **Tiptap HTML `description`** field on `ContactContent` (the new home for all inline images / videos / audio / files); explicit DB indexes (`idx_contact_slug_ckb`, `idx_contact_slug_kmr`, `idx_contact_active`); `TiptapHtmlProcessor` safety net that hoists inline base64 payloads to S3 on save.
- 🟡 **Caveats:** the DTOs themselves have **no Bean-Validation annotations** — validation is enforced by the JPA entity (`@NotBlank` on `slugCkb`, column-length caps). The controller has **no `@PreAuthorize`** annotations — auth is enforced globally. Slug uniqueness conflicts surface as raw DB constraint violations (no pre-check). `createdAt` / `updatedAt` on `ContactResponse` are serialized as `String`, not `LocalDateTime`.
- ⚪ **Unchanged:** all 7 surviving endpoints (paths, params, response envelope), the `ApiResponse<T>` wrapper on every endpoint (unlike About which strips it), the `Contact` table name (`contact_pages`), bilingual slug uniqueness, all language-agnostic contact details (`phone`, `secondaryPhone`, `email`, `mapEmbedUrl`, `latitude`, `longitude`), the `active` flag, the `displayOrder` column, the original `ContactContent` fields (`title`, `subtitle`, `address`, `workingHours`), and `@CreationTimestamp` / `@UpdateTimestamp` behaviour.
