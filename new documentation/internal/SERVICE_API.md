# Service API — Internal (Admin)

**Base URL:** `/api/v1/services`
**Platform:** Spring Boot 3 · PostgreSQL · JWT · Bilingual (CKB / KMR) · Paginated · Redis-cached
**Updated:** 2026-07-22 — aligned with **Services Backend API (new requirement)**

> **What this module now models.** The Services page is **dynamic**: the public site
> fetches active records from `/api/v1/services/all` and renders **one scroll section per
> record**, in server order. There is no fixed section list. Section **titles/bodies** come
> from `contents[]`; page chrome stays in the site's i18n. This doc covers the full admin
> surface (CRUD + the fields that drive that page).

> **Media.** Two independent media paths:
> 1. **`galleryMedia[]`** — the recommended, ordered per-section gallery (each slot is an
>    `IMAGE` or `VIDEO`). URLs come from `POST /api/v1/media/upload`.
> 2. **Inline media inside `contents[].description`** — the Tiptap/HTML (or GFM Markdown)
>    body may embed `<img>`/`<video>`/`<audio>`/`<a>`. Inline base64 is hoisted to S3 on
>    save by `TiptapHtmlProcessor`. `heroVideoUrl`/`heroPosterUrl`/`featureImageUrls`/
>    `thumbnailUrls` remain as legacy fallbacks.

---

## 🔄 Changes in this revision (vs. the previous doc & the new requirement)

| Area | Before | Now | Matches new requirement? |
|------|--------|-----|--------------------------|
| **`galleryMedia[]`** | not present | **Added** — ordered slots, each `IMAGE`/`VIDEO`, with `url` + optional `posterUrl`/`alt`. Empty/duplicate URLs skipped; missing `type` auto-detected from extension | ✅ New field implemented |
| **`sortOrder`** | not present; list ordered by `publishedAt DESC` | **Added** — controls nav/scroll order. Public & admin lists now order by `sortOrder ASC` (nulls last), then `publishedAt DESC`, then `createdAt DESC` | ✅ (`sortOrder`; `displayOrder` is treated as the same concept — only `sortOrder` is accepted) |
| **`GET /services/all`** | doc said *admin-only, incl. inactive* | Correctly documented as the **public bulk list, active-only** (this is what the code already does). Admin “all incl. inactive” lives at **`/admin/all`** | ✅ Public bulk fetch |
| **`layoutType`** | undocumented free string | Documented + **validated** against `MEDIA_HERO` \| `FEATURE_GRID` \| `DEFAULT` (stored uppercased) | ✅ Enum enforced |
| **`navAnchorId`** | undocumented | Documented + **validated**: slug-like and **globally unique** (case-insensitive) | ✅ Unique-when-set |
| **`heroVideoUrl`, `heroPosterUrl`, `featureImageUrls[]`, `thumbnailUrls[]`, `partnerIds[]`** | undocumented (existed in code) | Now fully documented | ✅ Present |
| **`contents[]` order** | non-deterministic (`Set`) | Response now sorted by `languageCode` (CKB before KMR) for stable output | ✅ |
| **`serviceType`** | required | **Still required** (`@NotBlank`, DB `NOT NULL`). The requirement lists it as optional; relaxing needs a manual `ALTER TABLE … DROP NOT NULL` (excluded here to avoid a runtime-breaking half-change) | ⚠️ **Divergence — keep sending a label** |
| **Response envelope** | — | All responses are wrapped in `{ success, message, data }`. The page must read `data.content` (the requirement's snippet shows the raw page) | ⚠️ Integration note |

---

## Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/services` | Public | Active services, optional `type` filter (paginated) |
| `GET` | `/api/v1/services/all` | Public | **Bulk list for the public site** — active only (paginated) |
| `GET` | `/api/v1/services/admin/all` | Public GET¹ | All services **including inactive** (paginated) |
| `GET` | `/api/v1/services/featured` | Public | Always an empty page (services don't use the featured model) |
| `GET` | `/api/v1/services/{id}` | Public | One service by ID (full detail) |
| `GET` | `/api/v1/services/types` | Public | Distinct service-type labels |
| `GET` | `/api/v1/services/search` | Public | Search **active** services |
| `GET` | `/api/v1/services/search/admin` | Public GET¹ | Search **all** services (incl. inactive) |
| `POST` | `/api/v1/services` | `ADMIN` / `SUPER_ADMIN` | Create |
| `PUT` | `/api/v1/services/{id}` | `ADMIN` / `SUPER_ADMIN` | Update (full replace) |
| `PATCH` | `/api/v1/services/{id}/active` | `ADMIN` / `SUPER_ADMIN` | Toggle active/inactive |
| `DELETE` | `/api/v1/services/{id}` | `ADMIN` / `SUPER_ADMIN` | Delete one |
| `DELETE` | `/api/v1/services/bulk` | `ADMIN` / `SUPER_ADMIN` | Delete many |

> **¹ Auth note.** `SecurityConfig` opens **every `GET /api/v1/services/**`** with `permitAll`;
> only non-GET verbs require `ADMIN`/`SUPER_ADMIN`. So `admin/all` and `search/admin` — which
> return inactive records — are technically reachable without a token. This is pre-existing
> behavior; if inactive records must be hidden from anonymous callers, tighten these two GET
> matchers (out of scope for this revision — **flagged for review**).

---

## Field Reference (Service record)

| Field | Type | Req. | Notes |
|-------|------|:----:|-------|
| `id` | number | — | Server-assigned |
| `serviceType` | string | ✅ | Free-text label (e.g. `Training`, `Event`). **Required** (see divergence above) |
| `location` | string | — | Physical/virtual location |
| `active` | boolean | — | Defaults `true`; `false` hides from public lists |
| `publishedAt` | string | — | `yyyy-MM-dd HH:mm:ss` (ISO-8601 also accepted on input). Null = draft |
| `sortOrder` | number | — | Nav/scroll order — lower first; null sorts last |
| `layoutType` | enum | — | `MEDIA_HERO` \| `FEATURE_GRID` \| `DEFAULT` (case-insensitive on input, stored uppercase) |
| `navAnchorId` | string | — | Slug for `#anchor`; **unique when set**. Site falls back to `id` |
| `galleryMedia[]` | array | — | **Recommended** ordered gallery — see below |
| `heroVideoUrl` | string | — | Legacy hero/gallery fallback |
| `heroPosterUrl` | string | — | Legacy poster / page-hero background |
| `featureImageUrls[]` | string[] | — | Legacy gallery fallback (used only when `galleryMedia` empty) |
| `thumbnailUrls[]` | string[] | — | Legacy gallery fallback (used only when `galleryMedia` empty) |
| `partnerIds[]` | number[] | — | About-partner IDs for bottom cards |
| `contents[]` | array | ✅¹ | Bilingual rows — see below |
| `createdAt` / `updatedAt` | string | — | `yyyy-MM-dd HH:mm:ss` |

> **¹** At least one content entry is expected in practice. If `contents` is omitted/empty the
> record persists with no title; the public site skips titleless sections for the active locale.

### `contents[]` — bilingual rows

| Field | Req. | Notes |
|-------|:----:|-------|
| `id` | — | Content row ID (response only) |
| `languageCode` | ✅ | `CKB` (Sorani) or `KMR` (Kurmanji). Duplicates in one request → `400` |
| `title` | ✅ | Nav label + section heading |
| `description` | — | Rich body — Tiptap HTML **or** GFM Markdown (stored as-is; inline base64 hoisted to S3) |

### `galleryMedia[]` — ordered gallery slots

```json
"galleryMedia": [
  { "type": "IMAGE", "url": "https://cdn/…/1.jpg", "alt": "…" },
  { "type": "VIDEO", "url": "https://cdn/…/clip.mp4", "posterUrl": "https://cdn/…/poster.jpg" },
  { "type": "IMAGE", "url": "https://cdn/…/2.jpg" }
]
```

| Field | Req. | Notes |
|-------|:----:|-------|
| `type` | — | `IMAGE` \| `VIDEO`. **Optional on input** — auto-detected from the URL extension (`.mp4`, `.webm`, `.mov`, `.m4v`, `.ogv`, `.ogg` → `VIDEO`) when omitted. Any other value → `400` |
| `url` | ✅ | Image or video file URL. Slots with a blank URL are dropped |
| `posterUrl` | — | Recommended for `VIDEO` |
| `alt` | — | Accessibility text |

**Server normalization:** order is preserved; blank-URL slots removed; **duplicate URLs de-duplicated** (first occurrence wins).

---

## `GET /api/v1/services/all` — Public Bulk List (active only)

**Auth:** None. **Query:** `page` (0), `size` (20). Ordered by `sortOrder ASC` (nulls last) → `publishedAt DESC` → `createdAt DESC`.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Services fetched successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "serviceType": "Studio",
        "location": "هەولێر",
        "active": true,
        "publishedAt": "2026-06-15 09:00:00",
        "sortOrder": 2,
        "layoutType": "MEDIA_HERO",
        "navAnchorId": "recording-studio",
        "galleryMedia": [
          { "type": "IMAGE", "url": "https://cdn.example.com/a.jpg", "posterUrl": null, "alt": null },
          { "type": "VIDEO", "url": "https://cdn.example.com/tour.mp4", "posterUrl": "https://cdn.example.com/tour.jpg", "alt": null }
        ],
        "heroVideoUrl": null,
        "heroPosterUrl": null,
        "featureImageUrls": [],
        "thumbnailUrls": [],
        "partnerIds": [1],
        "contents": [
          { "id": 1, "languageCode": "CKB", "title": "ستۆدیۆ", "description": "<p>…</p>" },
          { "id": 2, "languageCode": "KMR", "title": "Stûdyo", "description": "<p>…</p>" }
        ],
        "createdAt": "2026-06-12 10:00:00",
        "updatedAt": "2026-06-12 10:00:00"
      }
    ],
    "totalElements": 12,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

> `GET /api/v1/services` (with optional `?type=`), `GET /api/v1/services/admin/all`,
> `GET /api/v1/services/{id}`, `GET /api/v1/services/search`, and
> `GET /api/v1/services/search/admin` all return the **same object shape** (single object for
> `/{id}`, page for the rest). Only visibility/ordering differs. `admin/all` and `search/admin`
> additionally include `active:false` records.

---

## `GET /api/v1/services/types`

```json
{ "success": true, "message": "Service types fetched", "data": ["Training", "Event", "Workshop"] }
```

---

## `POST /api/v1/services` — Create

**Auth:** `ADMIN` / `SUPER_ADMIN` · **Content-Type:** `application/json`

**Request:**
```json
{
  "serviceType": "Studio",
  "location": "هەولێر",
  "publishedAt": "2026-06-15 09:00:00",
  "sortOrder": 2,
  "layoutType": "MEDIA_HERO",
  "navAnchorId": "recording-studio",
  "galleryMedia": [
    { "type": "IMAGE", "url": "https://cdn.example.com/a.jpg" },
    { "type": "VIDEO", "url": "https://cdn.example.com/tour.mp4", "posterUrl": "https://cdn.example.com/tour.jpg" }
  ],
  "partnerIds": [1],
  "featureImageUrls": [],
  "thumbnailUrls": [],
  "contents": [
    { "languageCode": "CKB", "title": "ستۆدیۆ", "description": "<p>…</p>" },
    { "languageCode": "KMR", "title": "Stûdyo", "description": "<p>…</p>" }
  ]
}
```

**Response `201 Created`:** full service object (shape above). `active` defaults to `true`.

---

## `PUT /api/v1/services/{id}` — Update

**Auth:** `ADMIN` / `SUPER_ADMIN`. Same body as `POST`. **Full replace** — supplying `contents`,
`galleryMedia`, `featureImageUrls`, `thumbnailUrls`, or `partnerIds` replaces that whole
collection. **Response `200 OK`:** updated object.

---

## `PATCH /api/v1/services/{id}/active?value={bool}` — Toggle

**Auth:** `ADMIN` / `SUPER_ADMIN`.
```json
{ "success": true, "message": "Service activated", "data": { "id": 1, "active": true, "...": "..." } }
```

---

## `DELETE /api/v1/services/{id}` · `DELETE /api/v1/services/bulk`

**Auth:** `ADMIN` / `SUPER_ADMIN`. Bulk body: `[1, 2, 3]`.
```json
{ "success": true, "message": "Service deleted successfully", "data": null }
```

---

## Validation (backend)

- **`contents`** — each entry needs `languageCode` ∈ {`CKB`,`KMR`} and a non-blank `title`; duplicate language codes in one request → `400`.
- **`serviceType`** — required, non-blank.
- **`layoutType`** — when set, must be `MEDIA_HERO`/`FEATURE_GRID`/`DEFAULT` → else `400`.
- **`navAnchorId`** — when set, must be slug-like (`^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$`) and globally unique (case-insensitive) → else `400`.
- **`galleryMedia[].url`** required per slot; `type` must be `IMAGE`/`VIDEO` when explicitly provided.
- **`active:false`** excludes the record from public lists.

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing/duplicate `contents` language, missing `title`/`serviceType`, bad `layoutType`, invalid/duplicate `navAnchorId`, bad `galleryMedia.type`, invalid `publishedAt` |
| `401 Unauthorized` | JWT missing/invalid (write endpoints) |
| `403 Forbidden` | Authenticated user lacks `ADMIN`/`SUPER_ADMIN` |
| `404 Not Found` | No service for the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |

> Error bodies carry `{ code, message, messageEn, messageKu, details, traceId }`; the `details`
> map echoes the offending field/value even when a locale message key isn't registered.
