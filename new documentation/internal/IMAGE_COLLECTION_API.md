# Image Collection API — Internal (Admin)

**Base URL:** `/api/v1/image-collections`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Multipart or JSON · Paginated
**Note:** Two create/update flavors — multipart (with file uploads) or JSON (URL-only). File parts and JSON data are index-matched.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/image-collections` | No | Public | Get all collections (paginated) |
| `GET` | `/api/v1/image-collections/{id}` | No | Public | Get one collection by ID |
| `GET` | `/api/v1/image-collections/topics` | No | Public | Get IMAGE topics for autocomplete |
| `POST` | `/api/v1/image-collections` | Yes | `ADMIN` / `SUPER_ADMIN` | Create collection with file uploads (multipart) |
| `POST` | `/api/v1/image-collections/json` | Yes | `ADMIN` / `SUPER_ADMIN` | Create collection with URL-only sources (JSON) |
| `PUT` | `/api/v1/image-collections/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update collection (multipart) |
| `DELETE` | `/api/v1/image-collections/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete collection |

---

## `GET /api/v1/image-collections` — Get All Collections (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `20` | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Image collections fetched successfully",
  "data": {
    "content": [
      {
        "id": 5,
        "collectionType": "PHOTO_GALLERY",
        "ckbCoverUrl": "https://cdn.khi.org/covers/ckb.jpg",
        "kmrCoverUrl": "https://cdn.khi.org/covers/kmr.jpg",
        "hoverCoverUrl": null,
        "topicId": 3,
        "topicNameCkb": "نەورۆز",
        "topicNameKmr": "Newroz",
        "publishmentDate": "2026-06-12",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": { "title": "گەڵەری وێنەکانی نەورۆز", "description": "وێنەی جەژنی نەورۆز", "location": "هەولێر", "collectedBy": "تیمی KHI" },
        "kmrContent": { "title": "Galeriya Wêneyên Newrozê", "description": "Wêneyên cejna Newrozê", "location": "Hewlêr", "collectedBy": "Tîma KHI" },
        "tags": { "ckb": ["نەورۆز", "کوردستان"], "kmr": ["Newroz", "Kurdistan"] },
        "keywords": { "ckb": ["جەژن"], "kmr": ["cejn"] },
        "imageAlbum": [
          { "id": 1, "imageUrl": "https://cdn.khi.org/img1.jpg", "captionCkb": "وێنەی یەکەم", "captionKmr": "Wêneya yekem", "sortOrder": 0, "widthPx": 1920, "heightPx": 1080, "aspectRatio": 1.78, "humanReadableSize": "200 KB" }
        ],
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00"
      }
    ],
    "totalElements": 25,
    "totalPages": 2,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/image-collections/{id}` — Get Collection by ID

**Auth:** None — public

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | Collection ID |

**Response `200 OK`:** Single collection object (same shape as array item above).

---

## `GET /api/v1/image-collections/topics` — Get IMAGE Topics

**Auth:** None — public
**Query Params:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "IMAGE topics fetched successfully",
  "data": [
    { "id": 1, "nameCkb": "نەورۆز", "nameKmr": "Newroz" },
    { "id": 2, "nameCkb": "سەرجەم", "nameKmr": "Giştî" }
  ]
}
```

---

## `POST /api/v1/image-collections` — Create Collection (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Form Parts:**

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `data` | string (JSON) | **Yes** | Serialized `CreateRequest` JSON (see fields below) |
| `ckbCoverImage` | file | No | Sorani cover image file — overrides `ckbCoverUrl` in data |
| `kmrCoverImage` | file | No | Kurmanji cover image file — overrides `kmrCoverUrl` in data |
| `hoverCoverImage` | file | No | Hover-state cover image file — overrides `hoverCoverUrl` in data |
| `images` | file[] | No | Album image files — index-matched to `imageAlbum[]` in data |

**`data` JSON Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `collectionType` | enum | **Yes** | Collection category: `PHOTO_GALLERY` \| `ARCHIVE` \| `EVENT` etc. |
| `contentLanguages` | array | **Yes** | Languages present — `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `publishmentDate` | string | No | Publication date in `yyyy-MM-dd` format |
| `topicId` | long | No | ID of an existing topic to assign |
| `newTopic.nameCkb` | string | No | Inline new topic — creates and assigns a new topic (Sorani name) |
| `newTopic.nameKmr` | string | No | Inline new topic — Kurmanji name |
| `ckbCoverUrl` | string | No | Fallback CKB cover URL if no file is uploaded |
| `kmrCoverUrl` | string | No | Fallback KMR cover URL |
| `hoverCoverUrl` | string | No | Fallback hover cover URL |
| `ckbContent.title` | string | No | Collection title in Sorani |
| `ckbContent.description` | string | No | Collection description in Sorani |
| `ckbContent.location` | string | No | Location name in Sorani |
| `ckbContent.collectedBy` | string | No | Collector or photographer name (Sorani) |
| `kmrContent` | object | No | Same fields as `ckbContent` for Kurmanji |
| `tags.ckb` | array | No | Tag strings in Sorani |
| `tags.kmr` | array | No | Tag strings in Kurmanji |
| `keywords.ckb` | array | No | Keyword strings in Sorani |
| `keywords.kmr` | array | No | Keyword strings in Kurmanji |
| `imageAlbum` | array | No | Album items using URL-only sources (no uploaded file) |
| `imageAlbum[].imageUrl` | string | No | Direct S3/CDN image URL |
| `imageAlbum[].externalUrl` | string | No | External page link (Flickr, Unsplash, etc.) |
| `imageAlbum[].embedUrl` | string | No | Embed/iframe URL |
| `imageAlbum[].captionCkb` | string | No | Image caption in Sorani |
| `imageAlbum[].captionKmr` | string | No | Image caption in Kurmanji |
| `imageAlbum[].descriptionCkb` | string | No | Extended description (Sorani) |
| `imageAlbum[].descriptionKmr` | string | No | Extended description (Kurmanji) |
| `imageAlbum[].sortOrder` | int | No | Display order inside the album (0-based) |

**`data` JSON Example:**
```json
{
  "collectionType": "PHOTO_GALLERY",
  "contentLanguages": ["CKB", "KMR"],
  "publishmentDate": "2026-06-12",
  "topicId": 3,
  "ckbContent": {
    "title": "گەڵەری وێنەکانی نەورۆز",
    "description": "وێنەی جەژنی نەورۆز لە هەولێر",
    "location": "هەولێر",
    "collectedBy": "تیمی KHI"
  },
  "kmrContent": {
    "title": "Galeriya Wêneyên Newrozê",
    "description": "Wêneyên cejna Newrozê li Hewlêr",
    "location": "Hewlêr",
    "collectedBy": "Tîma KHI"
  },
  "tags": { "ckb": ["نەورۆز", "کوردستان"], "kmr": ["Newroz", "Kurdistan"] },
  "keywords": { "ckb": ["جەژن", "وێنە"], "kmr": ["cejn", "wêne"] },
  "imageAlbum": [
    { "imageUrl": "https://cdn.khi.org/img1.jpg", "captionCkb": "وێنەی یەکەم", "captionKmr": "Wêneya yekem", "sortOrder": 0 },
    { "imageUrl": "https://cdn.khi.org/img2.jpg", "captionCkb": "وێنەی دووەم", "captionKmr": "Wêneya duyemîn", "sortOrder": 1 }
  ]
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Image collection created successfully",
  "data": {
    "id": 5,
    "collectionType": "PHOTO_GALLERY",
    "ckbCoverUrl": "https://cdn.khi.org/covers/ckb.jpg",
    "kmrCoverUrl": "https://cdn.khi.org/covers/kmr.jpg",
    "hoverCoverUrl": null,
    "topicId": 3,
    "topicNameCkb": "نەورۆز",
    "topicNameKmr": "Newroz",
    "publishmentDate": "2026-06-12",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "گەڵەری وێنەکانی نەورۆز", "description": "...", "location": "هەولێر", "collectedBy": "تیمی KHI" },
    "kmrContent": { "title": "Galeriya Wêneyên Newrozê", "description": "...", "location": "Hewlêr", "collectedBy": "Tîma KHI" },
    "tags": { "ckb": ["نەورۆز"], "kmr": ["Newroz"] },
    "keywords": { "ckb": ["جەژن"], "kmr": ["cejn"] },
    "imageAlbum": [
      { "id": 1, "imageUrl": "https://cdn.khi.org/img1.jpg", "captionCkb": "وێنەی یەکەم", "sortOrder": 0, "fileSizeBytes": 204800, "widthPx": 1920, "heightPx": 1080, "mimeType": "image/jpeg", "aspectRatio": 1.78, "humanReadableSize": "200 KB" }
    ],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00"
  }
}
```

---

## `POST /api/v1/image-collections/json` — Create Collection (JSON only)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body:** Same `data` JSON fields as the multipart `POST`. No file uploads — all images must be provided via URLs in `imageAlbum[].imageUrl`.

**Response `201 Created`:** Same shape as multipart create response.

---

## `PUT /api/v1/image-collections/{id}` — Update Collection (Multipart)

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `multipart/form-data`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the collection to update |

**Form Parts:** Same as `POST /` multipart. Uses `UpdateRequest` in `data`.

**Extra field in `data`:**

| Field | Type | Description |
|-------|------|-------------|
| `clearTopic` | boolean | Set `true` to remove the existing topic association |

**Response `200 OK`:** Updated collection object (same shape as create response).

---

## `DELETE /api/v1/image-collections/{id}` — Delete Collection

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the collection to delete |

**Response `200 OK`:**
```json
{ "success": true, "message": "Image collection deleted successfully", "data": null }
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `collectionType`, missing `contentLanguages`, or invalid JSON in `data` part |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | Collection or topic not found with the given ID |
| `500 Internal Server Error` | Unexpected server-side failure or file upload error |
