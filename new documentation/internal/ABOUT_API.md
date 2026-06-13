# About API — Internal (Admin)

**Base URL:** `/api/v1/about`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Tiptap HTML
**Note:** All media (images, video, audio, files) is embedded inside the Tiptap `body` field. Upload files first via `POST /api/v1/media/upload`, then embed the returned URL before submitting.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/about` | No | Public | Get all active About pages |
| `GET` | `/api/v1/about/{slug}` | No | Public | Get one About page by CKB or KMR slug |
| `POST` | `/api/v1/about` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new About page |
| `PUT` | `/api/v1/about/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update an existing About page |
| `DELETE` | `/api/v1/about/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete an About page |

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
      "metaDescription": "ڕێکخراوی خوێندنی کوردی",
      "body": "<p>ناوەرۆکی ئامادەکراو بۆ دەربارەی ئێمە...</p>"
    },
    "kmrContent": {
      "title": "Der Barê Me",
      "subtitle": "Damezrandin û Armancên",
      "metaDescription": "Rêxistina Xwendina Kurdî",
      "body": "<p>Naveroka amadekirî ji der barê me...</p>"
    },
    "active": true,
    "stats": [
      { "labelCkb": "ساڵی دامەزراندن", "labelKmr": "Sala Damezrandinê", "value": "2005" },
      { "labelCkb": "تاقمی کار", "labelKmr": "Tîma Karî", "value": "120+" }
    ],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
]
```

---

## `GET /api/v1/about/{slug}` — Get About Page by Slug

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | Yes | Accepts either the CKB slug or the KMR slug — both resolve to the same page |

**Response `200 OK`:** Single About page object (same shape as the array item above).

**Error `404`:** No active About page found for the given slug.

---

## `POST /api/v1/about` — Create About Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `slugCkb` | string | **Yes** | Sorani URL slug — must be unique across all About pages |
| `slugKmr` | string | No | Kurmanji URL slug — must be unique if provided |
| `ckbContent` | object | No | Sorani (CKB) language content block |
| `ckbContent.title` | string | No | Page title in Sorani Kurdish |
| `ckbContent.subtitle` | string | No | Secondary heading below the title |
| `ckbContent.metaDescription` | string | No | SEO meta description (shown in search results) |
| `ckbContent.body` | string | No | Tiptap HTML body — all media is embedded inline here |
| `kmrContent` | object | No | Kurmanji (KMR) language content block — same fields as `ckbContent` |
| `kmrContent.title` | string | No | Page title in Kurmanji Kurdish |
| `kmrContent.subtitle` | string | No | Secondary heading (KMR) |
| `kmrContent.metaDescription` | string | No | SEO meta description (KMR) |
| `kmrContent.body` | string | No | Tiptap HTML body (KMR) |
| `stats` | array | No | Structured statistics displayed on the page |
| `stats[].labelCkb` | string | No | Stat label in Sorani |
| `stats[].labelKmr` | string | No | Stat label in Kurmanji |
| `stats[].value` | string | No | Stat value (e.g. `"2005"`, `"120+"`) |

**Request JSON:**
```json
{
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
  "stats": [
    { "labelCkb": "ساڵی دامەزراندن", "labelKmr": "Sala Damezrandinê", "value": "2005" },
    { "labelCkb": "تاقمی کار", "labelKmr": "Tîma Karî", "value": "120+" },
    { "labelCkb": "پڕۆژەی تەواوبوو", "labelKmr": "Projeyên Temambûyî", "value": "350+" }
  ]
}
```

**Response `201 Created`:**
```json
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
    { "labelCkb": "تاقمی کار", "labelKmr": "Tîma Karî", "value": "120+" }
  ],
  "createdAt": "2026-06-12T10:00:00",
  "updatedAt": "2026-06-12T10:00:00"
}
```

---

## `PUT /api/v1/about/{id}` — Update About Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the About page to update |

**Request Body Fields:** Same as `POST` — all fields are optional. Only provided fields are updated (partial update).

**Request JSON (example — updating only the CKB title and stats):**
```json
{
  "ckbContent": {
    "title": "دەربارەی ڕێکخراوی KHI"
  },
  "stats": [
    { "labelCkb": "ساڵی دامەزراندن", "labelKmr": "Sala Damezrandinê", "value": "2005" },
    { "labelCkb": "تاقمی کار", "labelKmr": "Tîma Karî", "value": "150+" }
  ]
}
```

**Response `200 OK`:** Full updated About page object (same shape as create response).

---

## `DELETE /api/v1/about/{id}` — Delete About Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the About page to delete |

**Response `204 No Content`:** Empty body — deletion successful.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Invalid request body or constraint violation |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have the required role |
| `404 Not Found` | No About page found with the given `id` or `slug` |
| `409 Conflict` | `slugCkb` or `slugKmr` already exists on another page |
| `500 Internal Server Error` | Unexpected server-side failure |
