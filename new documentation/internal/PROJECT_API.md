# Project API — Internal (Admin)

**Base URL:** `/api/v1/projects`
**Platform:** Spring Boot 3 · JWT · Bilingual (CKB / KMR) · Tiptap HTML · Paginated
**Note:** Upload cover and inline media via `POST /api/v1/media/upload` first, then send the returned URLs in the JSON body.

---

## Endpoint Summary

| Method | Path | Auth Required | Role | Description |
|--------|------|--------------|------|-------------|
| `GET` | `/api/v1/projects/getAll` | No | Public | Get all projects (paginated) |
| `GET` | `/api/v1/projects/search/tag` | No | Public | Search projects by tag |
| `GET` | `/api/v1/projects/search/keyword` | No | Public | Search projects by keyword |
| `POST` | `/api/v1/projects/create` | Yes | `ADMIN` / `SUPER_ADMIN` | Create a new project |
| `PUT` | `/api/v1/projects/update/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Update an existing project |
| `PATCH` | `/api/v1/projects/{id}/featured` | Yes | `ADMIN` | Set featured status and order |
| `DELETE` | `/api/v1/projects/delete/{id}` | Yes | `ADMIN` / `SUPER_ADMIN` | Delete a project |

---

## `GET /api/v1/projects/getAll` — Get All Projects (Paginated)

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page index (0-based) |
| `size` | int | `20` | Number of items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Projects fetched successfully",
  "data": {
    "content": [
      {
        "id": 10,
        "featured": true,
        "featuredOrder": 2,
        "coverUrl": "https://cdn.khi.org/projects/cover.jpg",
        "coverMediaType": "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery": [],
        "projectTypeCkb": "پەروەردەیی",
        "projectTypeKmr": "Perwerdehî",
        "status": "ACTIVE",
        "projectDate": "2026-01-15",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "پڕۆژەی پەرەپێدانی زمانی کوردی",
          "description": "<p>ناوەرۆکی پڕۆژەکە...</p>",
          "location": "هەولێر"
        },
        "kmrContent": {
          "title": "Projeya Pêşveçûna Zimanê Kurdî",
          "description": "<p>Naveroka projeyê...</p>",
          "location": "Hewlêr"
        },
        "tagsCkb": ["زمان", "کوردستان"],
        "tagsKmr": ["Ziman", "Kurdistan"],
        "keywordsCkb": ["زمانناسی"],
        "keywordsKmr": ["zimannasî"],
        "createdAt": "2026-06-12T10:00:00",
        "updatedAt": "2026-06-12T10:00:00",
        "createdBy": "admin",
        "updatedBy": "admin"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/projects/search/tag` — Search by Tag

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | **Yes** | Tag value to search (CKB or KMR) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by tag completed",
  "data": {
    "content": [
      { "id": 10, "coverUrl": "https://cdn.khi.org/projects/cover.jpg", "coverMediaType": "IMAGE", "projectTypeCkb": "پەروەردەیی", "projectTypeKmr": "Perwerdehî", "status": "ACTIVE", "contentLanguages": ["CKB", "KMR"], "ckbContent": { "title": "پڕۆژەی پەرەپێدانی زمانی کوردی", "description": "<p>...</p>", "location": "هەولێر" }, "tagsCkb": ["زمان", "کوردستان"], "tagsKmr": ["Ziman", "Kurdistan"], "createdAt": "2026-06-12T10:00:00" }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `GET /api/v1/projects/search/keyword` — Search by Keyword

**Auth:** None — public

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Keyword to search (CKB or KMR) |
| `page` | int | No (0) | Page index |
| `size` | int | No (20) | Items per page |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Search by keyword completed",
  "data": {
    "content": [
      { "id": 8, "coverUrl": "https://cdn.khi.org/projects/cover2.jpg", "coverMediaType": "IMAGE", "projectTypeCkb": "کەلتووری", "status": "COMPLETED", "contentLanguages": ["CKB"], "ckbContent": { "title": "پڕۆژەی زمانناسی", "description": "<p>...</p>", "location": "سلێمانی" }, "keywordsCkb": ["زمانناسی"], "createdAt": "2026-04-01T08:00:00" }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

## `POST /api/v1/projects/create` — Create Project

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Request Body Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentLanguages` | array | **Yes** | Languages present — `["CKB"]`, `["KMR"]`, or `["CKB","KMR"]` |
| `coverUrl` | string | No | Cover asset URL (max 1024 chars) — uploaded via `/api/v1/media/upload` |
| `coverMediaType` | enum | No | Type of cover: `IMAGE` \| `VIDEO` \| `AUDIO` — defaults to `IMAGE` |
| `coverThumbnailUrl` | string | No | Poster/thumbnail URL for video or audio cover (max 1024 chars) |
| `mediaGallery` | array | No | Additional media items displayed in a gallery beside the cover |
| `projectTypeCkb` | string | No | Project type/category label in Sorani (max 128 chars) |
| `projectTypeKmr` | string | No | Project type/category label in Kurmanji (max 128 chars) |
| `status` | enum | No | Project status: `ACTIVE` \| `COMPLETED` \| `ARCHIVED` |
| `projectDate` | string | No | Project date in `yyyy-MM-dd` format |
| `ckbContent` | object | No | Sorani language content block |
| `ckbContent.title` | string | No | Project title in Sorani |
| `ckbContent.description` | string | No | Tiptap HTML body for Sorani — all inline media embedded here |
| `ckbContent.location` | string | No | Project location in Sorani |
| `kmrContent` | object | No | Kurmanji language content block — same fields as `ckbContent` |
| `kmrContent.title` | string | No | Project title in Kurmanji |
| `kmrContent.description` | string | No | Tiptap HTML body (Kurmanji) |
| `kmrContent.location` | string | No | Project location (Kurmanji) |
| `tagsCkb` | array | No | Tag strings in Sorani Kurdish |
| `tagsKmr` | array | No | Tag strings in Kurmanji Kurdish |
| `keywordsCkb` | array | No | Keyword strings in Sorani |
| `keywordsKmr` | array | No | Keyword strings in Kurmanji |

**Request JSON:**
```json
{
  "contentLanguages": ["CKB", "KMR"],
  "coverUrl": "https://cdn.khi.org/projects/cover.jpg",
  "coverMediaType": "IMAGE",
  "projectTypeCkb": "پەروەردەیی",
  "projectTypeKmr": "Perwerdehî",
  "status": "ACTIVE",
  "projectDate": "2026-01-15",
  "ckbContent": {
    "title": "پڕۆژەی پەرەپێدانی زمانی کوردی",
    "description": "<p>ناوەرۆکی پڕۆژەکە لێرەدایە...</p>",
    "location": "هەولێر"
  },
  "kmrContent": {
    "title": "Projeya Pêşveçûna Zimanê Kurdî",
    "description": "<p>Naveroka projeyê li vir e...</p>",
    "location": "Hewlêr"
  },
  "tagsCkb": ["زمان", "کوردستان", "پەروەردە"],
  "tagsKmr": ["Ziman", "Kurdistan", "Perwerdehî"],
  "keywordsCkb": ["زمانناسی", "کوردی"],
  "keywordsKmr": ["zimannasî", "Kurdî"]
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Project created successfully",
  "data": {
    "id": 10,
    "coverUrl": "https://cdn.khi.org/projects/cover.jpg",
    "coverMediaType": "IMAGE",
    "coverThumbnailUrl": null,
    "mediaGallery": [],
    "projectTypeCkb": "پەروەردەیی",
    "projectTypeKmr": "Perwerdehî",
    "status": "ACTIVE",
    "projectDate": "2026-01-15",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": { "title": "پڕۆژەی پەرەپێدانی زمانی کوردی", "description": "<p>...</p>", "location": "هەولێر" },
    "kmrContent": { "title": "Projeya Pêşveçûna Zimanê Kurdî", "description": "<p>...</p>", "location": "Hewlêr" },
    "tagsCkb": ["زمان", "کوردستان"],
    "tagsKmr": ["Ziman", "Kurdistan"],
    "keywordsCkb": ["زمانناسی"],
    "keywordsKmr": ["zimannasî"],
    "createdAt": "2026-06-12T10:00:00",
    "updatedAt": "2026-06-12T10:00:00",
    "createdBy": "admin",
    "updatedBy": "admin"
  }
}
```

---

## `PUT /api/v1/projects/update/{id}` — Update Project

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`
**Content-Type:** `application/json`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the project to update |

**Request Body:** Same fields as `POST /create`. All fields are optional (partial update).

**Response `200 OK`:** Full updated project object (same shape as create response).

---

## `DELETE /api/v1/projects/delete/{id}` — Delete Project

**Auth:** JWT required · Role: `ADMIN` or `SUPER_ADMIN`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | long | **Yes** | ID of the project to delete |

**Response `204 No Content`:** Empty body. The operation also succeeds when the ID does not exist or was already deleted.

---

## `PATCH /api/v1/projects/{id}/featured` — Set Featured Status

**Auth:** JWT required · Role: `ADMIN`
**Content-Type:** `application/json`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `featured` | boolean | No | `true` to feature, `false` to unfeature; omitted defaults to `true` |
| `featuredOrder` | integer | No | Global featured display order; lower values appear first |

```json
{
  "featured": true,
  "featuredOrder": 2
}
```

Setting `featured` to `false` clears `featuredOrder`.

**Response `204 No Content`:** Empty body.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `contentLanguages`, field exceeds max length, or invalid body |
| `401 Unauthorized` | JWT token is missing or invalid |
| `403 Forbidden` | Authenticated user does not have `ADMIN` or `SUPER_ADMIN` role |
| `404 Not Found` | No project found with the given `id` |
| `500 Internal Server Error` | Unexpected server-side failure |
