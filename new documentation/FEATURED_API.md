# Featured Content API

**Purpose:** mark homepage hero items as featured across News, Projects, Writings, Videos, Sound Tracks, and Image Collections.

**Public featured feed:** `/api/v1/featured`

**Admin featured patch endpoints:**

- `/api/v1/news/{id}/featured`
- `/api/v1/projects/{id}/featured`
- `/api/v1/writings/{id}/featured`
- `/api/v1/videos/{id}/featured`
- `/api/v1/sound-tracks/{id}/featured`
- `/api/v1/image-collections/{id}/featured`

---

## Overview

Featured items are manually curated by an admin. Each supported entity now has two fields:

- `featured` — whether the record should appear in the homepage featured list
- `featuredOrder` — optional global sort order; lower values appear first

The service collects featured records from all six content types, merges them into one list, sorts them globally, applies the configured maximum slide count, and returns the final homepage hero payload.

---

## How featured works

### 1. Marking an item as featured

When an admin calls a `PATCH /{id}/featured` endpoint:

- the entity is loaded by ID
- if `featured` is omitted, it is treated as `true`
- if turning featured on for a record that was not already featured, the service checks the **global featured limit**
- the entity is updated:
  - `featured = true/false`
  - `featuredOrder = request.featuredOrder` when featured is on
  - `featuredOrder = null` when featured is turned off

### 2. Global featured limit

The maximum number of featured slides is shared across:

- News
- Projects
- Writings
- Videos
- Sound Tracks
- Image Collections

This limit comes from `SiteSettings.maxFeaturedSlides`.

Code-accurate current behavior:

- if a site settings row exists and `maxFeaturedSlides > 0`, that value is used
- otherwise the service fallback is currently `7`

If the limit is already reached and a new item is being featured, the service throws an error:

```text
Maximum of {N} featured slides allowed across all content. Unfeature one first.
```

### 3. Public featured list sorting

`GET /api/v1/featured` sorts all featured records using this rule:

1. `featuredOrder` ascending
2. if two items have the same order, higher `id` first
3. items with `featuredOrder = null` are placed last

After sorting, the API assigns `displayOrder` sequentially as `1..N`.

---

## Request body for PATCH featured endpoints

Although the controller currently accepts `SiteContentDtos.FeaturedRequest`, the featured logic only uses these two fields:

```json
{
  "featured": true,
  "featuredOrder": 1
}
```

### Fields used by featured logic

| Field | Type | Required | Description |
|--------|------|----------|-------------|
| `featured` | boolean | No | `true` to feature the item, `false` to unfeature it. If omitted or `null`, the service treats it as `true`. |
| `featuredOrder` | integer | No | Global sort order across all featured content types. Lower values appear first. `null` sorts last. |

### Common examples

Feature an item:

```json
{
  "featured": true,
  "featuredOrder": 2
}
```

Feature an item without explicit order:

```json
{
  "featured": true
}
```

Unfeature an item:

```json
{
  "featured": false
}
```

---

## Admin PATCH endpoints

All featured patch endpoints:

- require authentication
- require role: `ADMIN`
- return `204 No Content` on success

### 1. Feature News

`PATCH /api/v1/news/{id}/featured`

Marks a News record as featured or removes it from the featured set.

### 2. Feature Project

`PATCH /api/v1/projects/{id}/featured`

Marks a Project record as featured or removes it from the featured set.

### 3. Feature Writing

`PATCH /api/v1/writings/{id}/featured`

Marks a Writing record as featured or removes it from the featured set.

### 4. Feature Video

`PATCH /api/v1/videos/{id}/featured`

Marks a Video record as featured or removes it from the featured set.

### 5. Feature Sound Track

`PATCH /api/v1/sound-tracks/{id}/featured`

Marks a Sound Track record as featured or removes it from the featured set.

### 6. Feature Image Collection

`PATCH /api/v1/image-collections/{id}/featured`

Marks an Image Collection record as featured or removes it from the featured set.

### Path parameter

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | Yes | ID of the entity record to update |

### Success response

**Status:** `204 No Content`

### Possible error cases

| Status | When |
|--------|------|
| `404 Not Found` | The target entity does not exist |
| `403 Forbidden` | Caller is not authorized as `ADMIN` |
| `400` / `409` style error* | The global featured limit has already been reached |

\* Exact final status depends on your global exception handler, but the service throws `IllegalStateException` with the featured-limit message.

---

## Public GET endpoint

## `GET /api/v1/featured`

Returns the homepage featured items list used by `khi-website`.

**Auth:** none

### Query parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `locale` | string | No | Locale used for localized title, description, image selection, and some slug values. `kmr` and `ku` resolve to `kmr`. Any other value, or omission, resolves to `ckb`. |

### Response

**Status:** `200 OK`

```json
[
  {
    "id": "news-42",
    "source": "news",
    "entityId": 42,
    "type": "article",
    "slug": "42",
    "title": "News KMR",
    "description": "<p>Localized description</p>",
    "image": {
      "url": "https://cdn.example.com/news.jpg",
      "alt": "News KMR"
    },
    "locale": "kmr",
    "featured": true,
    "featuredOrder": 1,
    "displayOrder": 1,
    "active": true
  },
  {
    "id": "image-collection-7",
    "source": "image-collection",
    "entityId": 7,
    "type": "gallery",
    "slug": "kurdish-photo-archive",
    "title": "Images KMR",
    "description": "<p>Localized description</p>",
    "image": {
      "url": "https://cdn.example.com/images-kmr.jpg",
      "alt": "Images KMR"
    },
    "locale": "kmr",
    "featured": true,
    "featuredOrder": 5,
    "displayOrder": 2,
    "active": true
  }
]
```

### Response fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Composite identifier in the form `{source}-{entityId}` |
| `source` | string | Source entity type: `news`, `project`, `writing`, `video`, `sound-track`, `image-collection` |
| `entityId` | long | Original database ID of the source entity |
| `type` | string | Hero content category returned by the service |
| `slug` | string | String used by the frontend for routing or identification |
| `title` | string | Localized title |
| `description` | string | Localized description |
| `image.url` | string | Resolved hero image URL |
| `image.alt` | string | Alt text, currently set to the resolved title |
| `locale` | string | Final resolved locale used by the service |
| `featured` | boolean | Always `true` for returned records |
| `featuredOrder` | integer \| null | Stored global sort priority |
| `displayOrder` | integer | Final sequential order in the returned list starting from `1` |
| `active` | boolean | Currently returned as `true` for all featured items |

---

## Source-to-response mapping

| Source | PATCH endpoint | `source` value | `type` value | `slug` behavior |
|--------|----------------|----------------|--------------|-----------------|
| News | `/api/v1/news/{id}/featured` | `news` | `article` | string version of entity ID |
| Project | `/api/v1/projects/{id}/featured` | `project` | `archive` | string version of entity ID |
| Writing | `/api/v1/writings/{id}/featured` | `writing` | `book` | string version of entity ID |
| Video | `/api/v1/videos/{id}/featured` | `video` | `video` | string version of entity ID |
| Sound Track | `/api/v1/sound-tracks/{id}/featured` | `sound-track` | `audio` | string version of entity ID |
| Image Collection | `/api/v1/image-collections/{id}/featured` | `image-collection` | `gallery` | localized slug if available, otherwise entity ID |

---

## Implementation notes

- News and Projects use their cover media / thumbnail logic to choose the featured image.
- Writings, Videos, Sound Tracks, and Image Collections choose image URLs from localized cover fields, with hover images as fallback in some cases.
- A record is skipped from the featured response if the service cannot resolve an image URL.
- Updating `featuredOrder` for an item that is already featured does **not** consume a new slot in the global featured limit.

---

## Quick examples

### Feature a news item

```http
PATCH /api/v1/news/42/featured
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "featured": true,
  "featuredOrder": 1
}
```

### Unfeature a video

```http
PATCH /api/v1/videos/15/featured
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "featured": false
}
```

### Get homepage featured items in Kurmanji

```http
GET /api/v1/featured?locale=kmr
```
