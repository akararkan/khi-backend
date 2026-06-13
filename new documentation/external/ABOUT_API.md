# About API — External (Public)

**Base URL:** `/api/v1/about`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Tiptap HTML
**Note:** Read-only public endpoints. All media URLs inside the Tiptap `body` field point to S3/CDN.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/about` | No | Get all active About pages |
| `GET` | `/api/v1/about/{slug}` | No | Get one About page by CKB or KMR slug |

---

## `GET /api/v1/about` — Get All Active About Pages

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "slugCkb": "der-bareman",
    "slugKmr": "der-bare-me",
    "ckbContent": {
      "title": "دەربارەی ئێمە",
      "subtitle": "دامەزراندن و ئامانجەکان",
      "metaDescription": "ڕێکخراوی خوێندنی کوردی KHI",
      "body": "<p>ناوەرۆکی دەربارەی ئێمە لێرەدایە...</p>"
    },
    "kmrContent": {
      "title": "Der Barê Me",
      "subtitle": "Damezrandin û Armancên",
      "metaDescription": "Rêxistina Xwendina Kurdî KHI",
      "body": "<p>Naveroka der barê me li vir e...</p>"
    },
    "active": true,
    "stats": [
      { "labelCkb": "ساڵی دامەزراندن", "labelKmr": "Sala Damezrandinê", "value": "2005" },
      { "labelCkb": "تاقمی کار", "labelKmr": "Tîma Karî", "value": "120+" },
      { "labelCkb": "پڕۆژەی تەواوبوو", "labelKmr": "Projeyên Temambûyî", "value": "350+" }
    ],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
]
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique About page identifier |
| `slugCkb` | string | Sorani URL slug used for page routing |
| `slugKmr` | string | Kurmanji URL slug (may be null if not set) |
| `ckbContent.title` | string | Page title in Sorani Kurdish |
| `ckbContent.subtitle` | string | Secondary heading |
| `ckbContent.metaDescription` | string | SEO meta description |
| `ckbContent.body` | string | Tiptap HTML body with all inline media |
| `kmrContent` | object | Same structure as `ckbContent` for Kurmanji |
| `active` | boolean | Always `true` for public responses (inactive pages are filtered out) |
| `stats` | array | Structured statistics list |
| `stats[].labelCkb` | string | Stat label in Sorani |
| `stats[].labelKmr` | string | Stat label in Kurmanji |
| `stats[].value` | string | Stat value string |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/about/{slug}` — Get About Page by Slug

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | **Yes** | Accepts either the `slugCkb` or `slugKmr` value — both resolve to the same page |

**Response `200 OK`:** Single About page object (same shape and fields as the array item above).

---

## Error Responses

| Status | Reason |
|--------|--------|
| `404 Not Found` | No active About page found with the given slug |
| `500 Internal Server Error` | Unexpected server-side failure |
