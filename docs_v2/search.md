# Global Search Module

> One endpoint that searches Projects, News, Videos, Writings, Sound Tracks, and Image Collections simultaneously, returning bilingual (CKB / KMR) hits grouped by content type with per-section pagination.

## Table of Contents
- 01 · Module Overview
- 02 · Authentication & Roles
- 03 · Public API
- 04 · Type Filter Values
- 05 · Response Shape
- 06 · DTO Reference
- 07 · Internal API
- 08 · Error Responses
- 09 · Notes

---

## 01 · Module Overview

- Base path: `/api/v1/search`
- One endpoint: `GET /api/v1/search`
- Searches six content types in a single round-trip: **Projects, News, Videos, Writings, Sound Tracks, Image Collections**.
- Pagination is **per-section**: the `size` query parameter applies to each content type independently. With `type=ALL` and `size=10`, the response can contain up to `6 × 10 = 60` items in total.
- Each section follows the same shape as a Spring `Page`, so a single Vue pagination component can render every section.
- Bilingual by design: each `SearchItem` carries both Sorani (CKB) and Kurmanji (KMR) titles and descriptions.
- The service is `@Transactional(readOnly = true)` and uses a two-phase strategy (ID query → batch hydration) for predictable, index-friendly performance.

---

## 02 · Authentication & Roles

| Aspect | Value |
|---|---|
| Authentication | **Public** — no token, cookie, or header required |
| Authorization | None |
| CORS / rate-limit | Inherits global defaults from `SecurityConfig` |
| Covered by | Global `GET` permit-all rule in `SecurityConfig` |

There are no write endpoints on this controller, so no role gates exist.

---

## 03 · Public API

### GET /api/v1/search

Search every supported content type (or one specific type) simultaneously and return the matching items grouped by section.

- **Description**: Runs a global search and returns paginated hits per content type.
- **Authentication**: Public.
- **Produces**: `application/json`
- **Controller method**: `GlobalSearchController#search`

#### Query parameters

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `q` | string | yes | — | Search term. Examples: `"کوردستان"`, `"هاوار"`, `"music"`. Trimmed server-side; an empty/`null` value is treated as `""`. |
| `type` | string | no | `ALL` | Restricts the search to a single content type. One of `ALL`, `PROJECT`, `NEWS`, `VIDEO`, `WRITING`, `SOUNDTRACK`, `IMAGE`. Case-insensitive (uppercased server-side). |
| `page` | int | no | `0` | 0-based page index. |
| `size` | int | no | `10` | Items per page **per section**. With `type=ALL` and `size=10` the response can include up to 60 items total. |

#### Behavior

- `type=ALL` populates all six sections; only sections whose type was requested appear, the rest stay `null`.
- The same `page` / `size` are applied to every section independently.
- `q` is matched against the underlying repository's `findIdsByGlobalSearch` (titles, descriptions, tags/keywords), in both CKB and KMR where the model exposes them.
- Description snippets are stripped of HTML (Tiptap output) and truncated to **200 characters** (with an ellipsis suffix when truncated).

#### Response

- `200 OK` + `ApiResponse<GlobalSearchResponse>`.

#### Example — search everything

```bash
curl 'https://api.khi.local/api/v1/search?q=%DA%A9%D9%88%D8%B1%D8%AF%D8%B3%D8%AA%D8%A7%D9%86&page=0&size=10'
```

Decoded query: `q=کوردستان&page=0&size=10`.

```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "query": "کوردستان",
    "page": 0,
    "size": 10,
    "type": "ALL",
    "projects": {
      "items": [
        {
          "id": 12,
          "type": "PROJECT",
          "titleCkb": "پڕۆژەی کوردستانی نوێ",
          "titleKmr": "Projeya Kurdistana Nû",
          "descriptionCkb": "پڕۆژەیەکی فەرهەنگی بۆ پاراستنی میراتی کوردستان...",
          "descriptionKmr": "Projeyek çandî ji bo parastina mîrateya Kurdistanê...",
          "coverUrl": "https://cdn.khi.local/projects/12/cover.jpg",
          "createdAt": "2026-03-14T10:22:11"
        }
      ],
      "totalElements": 12,
      "totalPages": 2,
      "currentPage": 0,
      "size": 10
    },
    "news": {
      "items": [
        {
          "id": 88,
          "type": "NEWS",
          "titleCkb": "هەواڵی نوێ لە کوردستان",
          "titleKmr": "Nûçeyên Nû ji Kurdistanê",
          "descriptionCkb": "ڕاپۆرتێکی تەواو دەربارەی ڕووداوەکانی ئەم هەفتە...",
          "descriptionKmr": "Raporeke berfireh derbarê bûyerên vê heftiyê...",
          "coverUrl": "https://cdn.khi.local/news/88/cover.jpg",
          "createdAt": "2026-05-21T08:05:00"
        }
      ],
      "totalElements": 45,
      "totalPages": 5,
      "currentPage": 0,
      "size": 10
    },
    "videos": {
      "items": [
        {
          "id": 4,
          "type": "VIDEO",
          "titleCkb": "ڤیدیۆی کوردستان",
          "titleKmr": "Vîdyoya Kurdistanê",
          "descriptionCkb": "ڤیدیۆی دۆکیۆمێنتاری دەربارەی کۆبانی...",
          "descriptionKmr": "Vîdyoya dokumenter derbarê Kobanê...",
          "coverUrl": "https://cdn.khi.local/videos/4/ckb-cover.jpg",
          "createdAt": "2026-02-09T19:30:00"
        }
      ],
      "totalElements": 5,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10
    },
    "writings": {
      "items": [
        {
          "id": 21,
          "type": "WRITING",
          "titleCkb": "نووسینێک دەربارەی کوردستان",
          "titleKmr": "Nivîsek derbarê Kurdistanê",
          "descriptionCkb": "وتارێک سەبارەت بە مێژووی نوێی کوردستان...",
          "descriptionKmr": "Gotarek li ser dîroka nû ya Kurdistanê...",
          "coverUrl": "https://cdn.khi.local/writings/21/ckb-cover.jpg",
          "createdAt": "2026-04-02T12:00:00"
        }
      ],
      "totalElements": 8,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10
    },
    "soundTracks": {
      "items": [
        {
          "id": 7,
          "type": "SOUNDTRACK",
          "titleCkb": "گۆرانی کوردستان",
          "titleKmr": "Stranên Kurdistanê",
          "descriptionCkb": "ئەلبومێکی مۆسیقای فۆلکلۆری کوردی...",
          "descriptionKmr": "Albûmek muzîka folklorî ya kurdî...",
          "coverUrl": "https://cdn.khi.local/soundtracks/7/ckb-cover.jpg",
          "createdAt": "2026-01-18T16:45:00"
        }
      ],
      "totalElements": 3,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10
    },
    "imageCollections": {
      "items": [
        {
          "id": 33,
          "type": "IMAGE",
          "titleCkb": "وێنەکانی کوردستان",
          "titleKmr": "Wêneyên Kurdistanê",
          "descriptionCkb": "کۆمەڵە وێنە لە شارە جوانەکانی کوردستان...",
          "descriptionKmr": "Komek wêne ji bajarên xweş ên Kurdistanê...",
          "coverUrl": "https://cdn.khi.local/images/33/ckb-cover.jpg",
          "createdAt": "2026-05-01T09:10:00"
        }
      ],
      "totalElements": 7,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10
    }
  }
}
```

#### Example — single-type search (`type=NEWS`)

```bash
curl 'https://api.khi.local/api/v1/search?q=%D9%87%D8%A7%D9%88%D8%A7%D8%B1&type=NEWS&page=0&size=10'
```

Decoded query: `q=هاوار&type=NEWS&page=0&size=10`.

```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "query": "هاوار",
    "page": 0,
    "size": 10,
    "type": "NEWS",
    "projects": null,
    "news": {
      "items": [
        {
          "id": 101,
          "type": "NEWS",
          "titleCkb": "هاواری گەلی کورد",
          "titleKmr": "Hawara Gelê Kurd",
          "descriptionCkb": "بەیاننامەیەکی نوێ دەربارەی بارودۆخی کۆچبەران...",
          "descriptionKmr": "Daxuyaniyek nû derbarê rewşa koçberan...",
          "coverUrl": "https://cdn.khi.local/news/101/cover.jpg",
          "createdAt": "2026-05-29T14:00:00"
        }
      ],
      "totalElements": 2,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10
    },
    "videos": null,
    "writings": null,
    "soundTracks": null,
    "imageCollections": null
  }
}
```

---

## 04 · Type Filter Values

The `type` query parameter restricts the search scope. Values are case-insensitive (the service trims and uppercases the input).

| Value | Effect | Sections populated |
|---|---|---|
| `ALL` | Default. Search every supported content type. | `projects`, `news`, `videos`, `writings`, `soundTracks`, `imageCollections` |
| `PROJECT` | Restrict to Projects only. | `projects` |
| `NEWS` | Restrict to News articles only. | `news` |
| `VIDEO` | Restrict to Videos only. | `videos` |
| `WRITING` | Restrict to Writings only. | `writings` |
| `SOUNDTRACK` | Restrict to Sound Tracks only. | `soundTracks` |
| `IMAGE` | Restrict to Image Collections only. | `imageCollections` |

Sections that are not in scope are returned as JSON `null` (not omitted from the envelope) so the client can rely on a stable shape.

---

## 05 · Response Shape

The endpoint always returns the standard envelope wrapping a `GlobalSearchResponse`:

```jsonc
{
  "success": true,
  "message": "Search completed",
  "data": {
    "query": "کوردستان",
    "page": 0,
    "size": 10,
    "type": "ALL",
    "projects":         { "items": [SearchItem...], "totalElements": 12, "totalPages": 2, "currentPage": 0, "size": 10 } | null,
    "news":             { "items": [SearchItem...], "totalElements": 45, "totalPages": 5, "currentPage": 0, "size": 10 } | null,
    "videos":           { "items": [SearchItem...], "totalElements": 5,  "totalPages": 1, "currentPage": 0, "size": 10 } | null,
    "writings":         { "items": [SearchItem...], "totalElements": 8,  "totalPages": 1, "currentPage": 0, "size": 10 } | null,
    "soundTracks":      { "items": [SearchItem...], "totalElements": 3,  "totalPages": 1, "currentPage": 0, "size": 10 } | null,
    "imageCollections": { "items": [SearchItem...], "totalElements": 7,  "totalPages": 1, "currentPage": 0, "size": 10 } | null
  }
}
```

Field names match the Java DTO exactly (Lombok `@Data` getters → JSON camelCase).

---

## 06 · DTO Reference

### `GlobalSearchResponse`

Top-level payload carried inside `ApiResponse.data`.

| Field | Type | Description |
|---|---|---|
| `query` | `String` | The raw search term sent by the client (trimmed; `null` becomes `""`). |
| `page` | `int` | 0-based page index requested. |
| `size` | `int` | Items per page requested (per section). |
| `type` | `String` | Content scope of this response. Values: `ALL`, `PROJECT`, `NEWS`, `VIDEO`, `WRITING`, `SOUNDTRACK`, `IMAGE`. |
| `projects` | `SearchSection` \| `null` | Project hits — `null` when not in scope. |
| `news` | `SearchSection` \| `null` | News hits — `null` when not in scope. |
| `videos` | `SearchSection` \| `null` | Video hits — `null` when not in scope. |
| `writings` | `SearchSection` \| `null` | Writing hits — `null` when not in scope. |
| `soundTracks` | `SearchSection` \| `null` | Sound-track hits — `null` when not in scope. |
| `imageCollections` | `SearchSection` \| `null` | Image-collection hits — `null` when not in scope. |

### `GlobalSearchResponse.SearchSection`

A single content-type result page. Mirrors Spring's `Page` shape so the same Vue pagination component can render every section.

| Field | Type | Description |
|---|---|---|
| `items` | `List<SearchItem>` | The hits on the current page. Empty list when no matches. |
| `totalElements` | `long` | Total matching records in the database for this type + query. |
| `totalPages` | `int` | Total pages available (`ceil(totalElements / size)`). |
| `currentPage` | `int` | 0-based index of this page (echoes the `page` query parameter). |
| `size` | `int` | Number of items per page (echoes the `size` query parameter). |

**Empty-section factory.** When a type yields zero hits, the service returns `SearchSection.empty(page, size)` which produces:

```json
{ "items": [], "totalElements": 0, "totalPages": 0, "currentPage": <page>, "size": <size> }
```

### `SearchItem`

A lightweight, type-agnostic hit. Designed to render a Vue search-result card without further API calls.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Database primary key of the original entity. |
| `type` | `String` | Content type discriminator. One of `PROJECT`, `NEWS`, `VIDEO`, `WRITING`, `SOUNDTRACK`, `IMAGE`. Tells the client which detail route to use. |
| `titleCkb` | `String` | Sorani (CKB) title. Empty string when missing. |
| `titleKmr` | `String` | Kurmanji (KMR) title. Empty string when missing. |
| `descriptionCkb` | `String` | Sorani snippet — HTML stripped, whitespace collapsed, capped at 200 characters with `…` suffix when truncated. Empty string when missing. |
| `descriptionKmr` | `String` | Kurmanji snippet — same rules as `descriptionCkb`. |
| `coverUrl` | `String` | First available cover image URL. For entities with split CKB / KMR covers, CKB is preferred and falls back to KMR; may be `null` if neither exists. |
| `createdAt` | `LocalDateTime` | ISO-8601 creation timestamp of the entity — useful for "published X days ago" labels. |

**Field-population notes (from `GlobalSearchService`):**

- For **Projects** and **News**, `coverUrl` comes from the entity's single `coverUrl` field.
- For **Videos, Writings, Sound Tracks, Image Collections**, `coverUrl` is `firstNonNull(ckbCoverUrl, kmrCoverUrl)`.
- Titles are normalized via the internal `title(raw)` helper (`null` → `""`).
- Descriptions are normalized via the internal `snippet(raw)` helper (HTML strip + whitespace collapse + 200-char cap).

---

## 07 · Internal API

This module exposes no admin / authenticated endpoints — **all queries are public**. There is no `/admin/search`, no role-gated variant, and no write operation on this controller.

---

## 08 · Error Responses

Errors follow the standard bilingual `ApiErrorResponse` envelope used elsewhere in the application.

| HTTP | Code | Trigger |
|---|---|---|
| `400` | `VALIDATION_ERROR` | The required `q` query parameter is missing. Spring's `@RequestParam String q` (no default) fails binding before the handler runs. |
| `400` | `BAD_REQUEST` | `page` or `size` is non-numeric (binding failure on `int`). |
| `500` | `INTERNAL_ERROR` | Unexpected server-side exception (repository / database failure). |

> Note: an unknown `type` value (for example `type=FOO`) is **not** rejected — `searchesType("FOO", ...)` returns `false` for every section, so the response contains the standard envelope with every section set to `null`. Only `ALL` plus the six valid types populate any section.

#### Example — missing `q`

```json
{
  "success": false,
  "message": "Required request parameter 'q' is missing.",
  "messageCkb": "پێویستە پارامەتری گەڕان 'q' ببەخشرێت.",
  "messageKmr": "Pîvana lêgerînê 'q' divê were dayîn.",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-05-31T10:15:42.123Z",
  "path": "/api/v1/search"
}
```

---

## 09 · Notes

- **Pagination is per-section, NOT global.** Each section's `totalElements` reflects only the matches inside that single content type. There is no aggregate "total hits across all types" value in the response.
- **Empty sections in `type=ALL`.** When a section yields zero hits, `SearchSection.empty(page, size)` is returned with `items: []`, `totalElements: 0`, `totalPages: 0`, and the echoed `currentPage` / `size`. The section is never silently dropped.
- **Bilingual matching.** Each entity's `findIdsByGlobalSearch` query is responsible for matching CKB and KMR content; the service then exposes both languages on every `SearchItem` regardless of which language the term matched.
- **HTML-safe snippets.** Descriptions are stripped of Tiptap HTML (`<[^>]+>` → space), whitespace is collapsed, and the result is truncated to 200 characters (with `…` suffix on truncation). Empty / blank inputs become `""`.
- **Cover-URL fallback.** For Videos, Writings, Sound Tracks, and Image Collections, the CKB cover is preferred and the KMR cover is used as a fallback (`firstNonNull(ckbCoverUrl, kmrCoverUrl)`). Projects and News use their single `coverUrl` field.
- **Two-phase query strategy.** Per content type the service issues exactly two queries: (1) `findIdsByGlobalSearch` returning a `Page<Long>` and (2) `findAllByIds` for the matched IDs. With `type=ALL` this is 12 queries total (2 × 6 sections), independent of the number of tags / keywords on each entity.
- **Case-insensitive `type`.** The service trims and uppercases the value before matching, so `type=news`, `type=News`, and `type=NEWS` are all equivalent.
- **Empty `q`.** A `null` or whitespace-only `q` is normalized to `""`; the underlying repositories decide how to interpret an empty term (typically "match everything"). The required-parameter check still applies at the HTTP layer — `q` must be present.
- **Read-only transaction.** The service runs under `@Transactional(readOnly = true)`, enabling Hibernate read-only optimizations and preventing accidental writes.
