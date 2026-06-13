# Image Collection API — External (Public)

**Base URL:** `/api/v1/image-collections`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR) · Paginated
**Note:** Read-only public endpoints. All image URLs point to S3/CDN.

---

## Endpoint Summary

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| `GET` | `/api/v1/image-collections` | No | Get all collections (paginated) |
| `GET` | `/api/v1/image-collections/{id}` | No | Get one collection by ID |
| `GET` | `/api/v1/image-collections/topics` | No | Get IMAGE topics for filtering/autocomplete |

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
        "keywords": { "ckb": ["جەژن"], "kmr": ["cejn"] },
        "imageAlbum": [
          {
            "id": 1,
            "imageUrl": "https://cdn.khi.org/img1.jpg",
            "captionCkb": "وێنەی یەکەم",
            "captionKmr": "Wêneya yekem",
            "sortOrder": 0,
            "fileSizeBytes": 204800,
            "widthPx": 1920,
            "heightPx": 1080,
            "mimeType": "image/jpeg",
            "aspectRatio": 1.78,
            "humanReadableSize": "200 KB"
          }
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

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | long | Unique collection identifier |
| `collectionType` | enum | Collection category type |
| `ckbCoverUrl` | string | Sorani cover image URL |
| `kmrCoverUrl` | string | Kurmanji cover image URL (may be null) |
| `hoverCoverUrl` | string | Hover-state cover image URL (may be null) |
| `topicId` | long | Assigned topic ID (may be null) |
| `topicNameCkb` | string | Topic name in Sorani (may be null) |
| `topicNameKmr` | string | Topic name in Kurmanji (may be null) |
| `publishmentDate` | string | Publication date (`yyyy-MM-dd`) |
| `contentLanguages` | array | Languages present in this collection |
| `ckbContent.title` | string | Collection title (Sorani) |
| `ckbContent.description` | string | Description (Sorani) |
| `ckbContent.location` | string | Location name (Sorani) |
| `ckbContent.collectedBy` | string | Collector/photographer name (Sorani) |
| `kmrContent` | object | Same fields as `ckbContent` for Kurmanji |
| `tags.ckb` / `tags.kmr` | array | Tag strings per language |
| `keywords.ckb` / `keywords.kmr` | array | Keyword strings per language |
| `imageAlbum[].id` | long | Album item ID |
| `imageAlbum[].imageUrl` | string | Image URL |
| `imageAlbum[].captionCkb` | string | Caption (Sorani) |
| `imageAlbum[].captionKmr` | string | Caption (Kurmanji) |
| `imageAlbum[].sortOrder` | int | Display order (0-based) |
| `imageAlbum[].fileSizeBytes` | long | File size in bytes (auto-detected on upload) |
| `imageAlbum[].widthPx` | int | Image width in pixels |
| `imageAlbum[].heightPx` | int | Image height in pixels |
| `imageAlbum[].mimeType` | string | MIME type (e.g. `image/jpeg`) |
| `imageAlbum[].aspectRatio` | double | Width ÷ height ratio |
| `imageAlbum[].humanReadableSize` | string | Human-friendly file size (e.g. `200 KB`) |
| `createdAt` | string | ISO-8601 creation timestamp |
| `updatedAt` | string | ISO-8601 last update timestamp |

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
    { "id": 2, "nameCkb": "سەرجەم", "nameKmr": "Giştî" },
    { "id": 3, "nameCkb": "ئارکیڤ", "nameKmr": "Arşîv" }
  ]
}
```

---

## Error Responses

| Status | Reason |
|--------|--------|
| `404 Not Found` | No collection found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
