# Service API — External (Public)

**Base URL:** `/api/v1/services`
**Platform:** Spring Boot 3 · No Auth · Bilingual (CKB / KMR) · Paginated
**Updated:** 2026-07-22 — aligned with **Services Backend API (new requirement)**

> **How the site consumes this.** The `/[locale]/services` page is **dynamic**: it fetches all
> active records from **`GET /api/v1/services/all`** and renders **one nav item + one scroll
> section per record**, in the order returned. There is no fixed section list — the API may
> return 2 sections or 30. Section **titles/bodies** come from `contents[]`; page chrome stays
> in the site's i18n.

> **Read the page from `data.content`.** Every response is wrapped in
> `{ "success", "message", "data": { … } }`. List endpoints put the Spring page under `data`
> (`data.content`, `data.totalElements`, `data.totalPages`).

---

## 🔄 Changes in this revision (vs. previous public doc)

| Change | Detail |
|--------|--------|
| **New `galleryMedia[]`** | Ordered gallery per section — each slot is independently `IMAGE` or `VIDEO`, with `url` + optional `posterUrl`/`alt`. Any position may be a video; multiple videos allowed. |
| **New `sortOrder`** | Explicit display order. `/all` and `/services` are now ordered by `sortOrder` (ascending, nulls last), then newest published, then newest created. |
| **`/all` = the public bulk endpoint** | Returns **active-only** records for the page. Use this as the primary fetch. |
| **Now-documented fields** | `layoutType`, `navAnchorId`, `heroVideoUrl`, `heroPosterUrl`, `featureImageUrls[]`, `thumbnailUrls[]`, `partnerIds[]` (all already served, previously undocumented). |
| **Stable `contents[]` order** | Content rows are returned sorted by `languageCode` (CKB then KMR). |

---

## Locales

| Site URL | Preferred `contents[].languageCode` |
|----------|-------------------------------------|
| `/ckb/services` | `CKB` |
| `/ku/services` | `KMR` |

**Fallback:** if the preferred language row is absent, use the first available `contents` entry.
A record with **no localized title** for the active locale (after fallback) is skipped by the site.

---

## Endpoint Summary

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/services/all` | **Bulk list for the Services page** — active only (paginated) |
| `GET` | `/api/v1/services` | Active services, optional `type` filter (paginated) |
| `GET` | `/api/v1/services/{id}` | One service by ID |
| `GET` | `/api/v1/services/types` | Distinct service-type labels |
| `GET` | `/api/v1/services/search` | Search active services |

---

## `GET /api/v1/services/all` — Bulk List (active only)

**Auth:** None.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `20` | Items per page — pass your bulk size |

**Order:** `sortOrder` ascending (nulls last) → `publishedAt` desc → `createdAt` desc. This is the render order for nav items and scroll sections.

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
          { "type": "IMAGE", "url": "https://cdn.example.com/a.jpg", "posterUrl": null, "alt": "Studio floor" },
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

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Service identifier. Hash target when `navAnchorId` is absent |
| `serviceType` | string | Free-text label/category |
| `location` | string | Physical/virtual location (nullable) |
| `active` | boolean | Always `true` on public list endpoints |
| `publishedAt` | string | `yyyy-MM-dd HH:mm:ss` (nullable) |
| `sortOrder` | number | Display order — lower first; null sorts last |
| `layoutType` | string | `MEDIA_HERO` \| `FEATURE_GRID` \| `DEFAULT` (nullable) |
| `navAnchorId` | string | Slug for `#anchor` links; unique when set (nullable → fall back to `id`) |
| `galleryMedia[]` | array | Ordered gallery slots — see below |
| `heroVideoUrl` | string | Legacy hero/gallery fallback (nullable) |
| `heroPosterUrl` | string | Legacy poster / page-hero background (nullable) |
| `featureImageUrls[]` | string[] | Legacy gallery fallback (used only when `galleryMedia` empty) |
| `thumbnailUrls[]` | string[] | Legacy gallery fallback |
| `partnerIds[]` | number[] | About-partner IDs for bottom cards |
| `contents[]` | array | Bilingual rows (CKB then KMR) |
| `contents[].id` | long | Content row ID |
| `contents[].languageCode` | string | `CKB` or `KMR` |
| `contents[].title` | string | Nav label + section heading |
| `contents[].description` | string | Rich body (Tiptap HTML or GFM Markdown) with inline media |
| `createdAt` / `updatedAt` | string | `yyyy-MM-dd HH:mm:ss` |

### `galleryMedia[]` — ordered slots

| Field | Type | Notes |
|-------|------|-------|
| `type` | string | `IMAGE` or `VIDEO` |
| `url` | string | Image or video file URL |
| `posterUrl` | string | Poster frame — present for most `VIDEO` slots (nullable) |
| `alt` | string | Accessibility text (nullable) |

**Rendering:** slots are already de-duplicated and in display order; render the filmstrip in
array order. When `galleryMedia` is empty, fall back to `featureImageUrls[0]` then
`thumbnailUrls[]`; treat video file extensions (`.mp4`, `.webm`, …) as video.

---

## `GET /api/v1/services` — Active Services (optional type filter)

**Auth:** None. Same object/page shape as `/all`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | string | No | Filter by `serviceType` (e.g. `Training`) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

---

## `GET /api/v1/services/{id}` — Get by ID

**Auth:** None. Returns a single service object (shape above) under `data`.
`404` if no service has that `id`.

---

## `GET /api/v1/services/types` — Type Labels

```json
{ "success": true, "message": "Service types fetched", "data": ["Training", "Event", "Workshop"] }
```

---

## `GET /api/v1/services/search` — Search Active Services

**Auth:** None.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | **Yes** | Matched against type, location, and bilingual title/description |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

Same page/object shape as `/all` (results ordered by recency).

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required query parameter `q` for search |
| `404 Not Found` | No service found for the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
