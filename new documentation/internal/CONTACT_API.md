# Contact API — Internal (Admin)

**Base URL:** `/api/v1/contact`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Tiptap HTML
**Note:** All media is embedded inside the Tiptap `description` field per language. Upload files via `POST /api/v1/media/upload` first.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/contact` | Yes | `ADMIN` / `SUPER_ADMIN` | Get all Contact pages including inactive |
| `GET` | `/api/v1/contact/active` | No | Public | Get only active Contact pages |
| `GET` | `/api/v1/contact/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Get one Contact page by ID |
| `GET` | `/api/v1/contact/slug/{slug}` | No | Public | Get one Contact page by CKB or KMR slug |
| `POST` | `/api/v1/contact` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new Contact page |
| `PUT` | `/api/v1/contact/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update an existing Contact page |
| `DELETE` | `/api/v1/contact/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a Contact page |

---

## `GET /api/v1/contact` — Get All Contact Pages (including inactive)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Contact pages fetched",
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

---

## `GET /api/v1/contact/active` — Get All Active Contact Pages

**Auth:** None — public

**Response `200 OK`:** Same shape as `GET /contact` but only returns pages where `active = true`.

---

## `GET /api/v1/contact/{id}` — Get Contact Page by ID

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Contact page ID |

**Response `200 OK`:** Single Contact page object (same shape as array item above).

---

## `GET /api/v1/contact/slug/{slug}` — Get Contact Page by Slug

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | **Yes** | Accepts either `slugCkb` or `slugKmr` — both resolve to the same page |

**Response `200 OK`:** Single Contact page object.

---

## `POST /api/v1/contact` — Create Contact Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `slugCkb` | string | **Yes** | Sorani URL slug — must be unique |
| `slugKmr` | string | No | Kurmanji URL slug — must be unique if provided |
| `ckbContent` | object | No | Sorani language content block |
| `ckbContent.title` | string | No | Page title in Sorani Kurdish |
| `ckbContent.subtitle` | string | No | Secondary heading below the title |
| `ckbContent.address` | string | No | Physical address in Sorani |
| `ckbContent.workingHours` | string | No | Working hours text in Sorani |
| `ckbContent.description` | string | No | Tiptap HTML body — all inline media embedded here |
| `kmrContent` | object | No | Kurmanji language content block — same fields as `ckbContent` |
| `kmrContent.title` | string | No | Page title in Kurmanji |
| `kmrContent.subtitle` | string | No | Secondary heading (KMR) |
| `kmrContent.address` | string | No | Address in Kurmanji |
| `kmrContent.workingHours` | string | No | Working hours (KMR) |
| `kmrContent.description` | string | No | Tiptap HTML body (KMR) |
| `phone` | string | No | Primary phone number |
| `secondaryPhone` | string | No | Secondary / alternative phone number |
| `email` | string | No | Contact email address |
| `mapEmbedUrl` | string | No | Google Maps embed URL (iframe `src`) |
| `latitude` | double | No | Geographic latitude for the map pin |
| `longitude` | double | No | Geographic longitude for the map pin |

**Request JSON:**
```json
{
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
  "longitude": 44.0092
}
```

**Response `201 Created`:** Full Contact page object (same shape as the GET response item above).

---

## `PUT /api/v1/contact/{id}` — Update Contact Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the Contact page to update |

**Request Body:** Same fields as `POST`. All fields are optional — only provided fields are updated (partial update).

**Response `200 OK`:** Full updated Contact page object.

---

## `DELETE /api/v1/contact/{id}` — Delete Contact Page

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the Contact page to delete |

**Response `200 OK`:**
```json
{ "success": true, "message": "Contact page deleted successfully", "data": null }
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Invalid request body or validation failure |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have the required role |
| `404 Not Found` | No Contact page found with the given `id` or `slug` |
| `409 Conflict` | `slugCkb` or `slugKmr` already exists on another page |
| `500 Internal Server Error` | Unexpected server-side failure |
