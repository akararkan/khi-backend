# Contact API — External (Public)

**Base URL:** `/api/v1/contact`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Tiptap HTML
**Note:** Read-only public endpoints. Returns only active Contact pages.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/contact/active` | No | Get all active Contact pages |
| `GET` | `/api/v1/contact/slug/{slug}` | No | Get one Contact page by CKB or KMR slug |

---

## `GET /api/v1/contact/active` — Get All Active Contact Pages

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Active contact pages fetched",
  "data": [
    {
      "id": 1,
      "slugCkb": "peywendiman",
      "slugKmr": "peywendiya-min",
      "ckbContent": {
        "title": "پەیوەندیمان",
        "subtitle": "پەیوەندی بکەن لەگەڵمان",
        "address": "هەولێر، گەڕەکی ئازادی، کوردستان",
        "workingHours": "شەممە تا چوارشەممە: ٩ بەیانی — ٥ ئێوارە",
        "description": "<p>ناوەرۆکی تێکەڵ بۆ پەیوەندیکردن...</p>"
      },
      "kmrContent": {
        "title": "Peywendiya Me",
        "subtitle": "Bi me re peywendî bikin",
        "address": "Hewlêr, Navçeya Azadî, Kurdistanê",
        "workingHours": "Şemî heta Çarşemê: 9 — 17",
        "description": "<p>Naveroka têkel ji bo peywendîkirinê...</p>"
      },
      "phone": "+964-750-000-0000",
      "secondaryPhone": "+964-770-000-0000",
      "email": "info@khi.org",
      "mapEmbedUrl": "https://maps.google.com/embed?pb=...",
      "latitude": 36.1911,
      "longitude": 44.0092,
      "active": true,
      "createdAt": "2026-06-12T10:00:00",
      "updatedAt": "2026-06-12T10:00:00"
    }
  ]
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique Contact page identifier |
| `slugCkb` | string | Sorani URL slug |
| `slugKmr` | string | Kurmanji URL slug (may be null) |
| `ckbContent.title` | string | Page title in Sorani Kurdish |
| `ckbContent.subtitle` | string | Secondary heading |
| `ckbContent.address` | string | Physical address text (Sorani) |
| `ckbContent.workingHours` | string | Working hours text (Sorani) |
| `ckbContent.description` | string | Tiptap HTML body with inline media |
| `kmrContent` | object | Same structure as `ckbContent` for Kurmanji |
| `phone` | string | Primary contact phone number |
| `secondaryPhone` | string | Alternative phone number |
| `email` | string | Contact email address |
| `mapEmbedUrl` | string | Google Maps embed URL |
| `latitude` | double | Map pin latitude |
| `longitude` | double | Map pin longitude |
| `active` | boolean | Always `true` for public responses |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

---

## `GET /api/v1/contact/slug/{slug}` — Get Contact Page by Slug

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | **Yes** | Accepts either the `slugCkb` or `slugKmr` value — both resolve to the same page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Contact page fetched",
  "data": {
    "id": 1,
    "slugCkb": "peywendiman",
    "slugKmr": "peywendiya-min",
    "ckbContent": { "title": "پەیوەندیمان", "address": "هەولێر", "description": "<p>...</p>" },
    "kmrContent": { "title": "Peywendiya Me", "address": "Hewlêr", "description": "<p>...</p>" },
    "phone": "+964-750-000-0000",
    "email": "info@khi.org",
    "latitude": 36.1911,
    "longitude": 44.0092,
    "active": true
  }
}
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `404 Not Found` | No active Contact page found with the given slug |
| `500 Internal Server Error` | Unexpected server-side failure |
