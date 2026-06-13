# About Module

> Bilingual (CKB / KMR) About pages with Tiptap HTML bodies and structured stats. Public reads, admin writes.

## Table of Contents
- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — About](#02--data-model--about-about_pages)
- [03 · Data Model — AboutContent (embedded)](#03--data-model--aboutcontent-embedded)
- [04 · Data Model — StatItem](#04--data-model--statitem)
- [05 · Authentication & Roles](#05--authentication--roles)
- [06 · Public API](#06--public-api)
- [07 · Internal API (Admin)](#07--internal-api-admin)
- [08 · DTO Reference](#08--dto-reference)
- [09 · Error Responses](#09--error-responses)
- [10 · Notes (Tiptap & media)](#10--notes-tiptap--media)

---

## 01 · Module Overview

The About module exposes the bilingual "About" pages of the Kurdish Heritage Institute platform. Each page carries a Sorani (CKB) and an optional Kurmanji (KMR) variant of the same content, plus a structured stats array. All visual media (images, video, audio, downloadable files) is embedded inline inside the per-language Tiptap HTML `body` — the entity itself has no standalone hero, gallery, thumbnail, or media-type columns.

- **Base path:** `/api/v1/about`
- **Controller:** `ak.dev.khi_backend.khi_app.api.about.AboutController`
- **Service:** `ak.dev.khi_backend.khi_app.service.about.AboutService`
- **Entity table:** `about_pages`
- **Response envelope:** the controller returns the raw DTO (or a list of DTOs) directly. There is **no** `ApiResponse<T>` `{success,message,data}` wrapper here — the response body IS the DTO.

### Endpoint Summary

| Method | Path                       | Description                                     | Auth                |
|--------|----------------------------|-------------------------------------------------|---------------------|
| GET    | `/api/v1/about`            | List all active About pages                     | Public              |
| GET    | `/api/v1/about/{slug}`     | Fetch a single page by CKB or KMR slug          | Public              |
| POST   | `/api/v1/about`            | Create a new About page                         | ADMIN, SUPER_ADMIN  |
| PUT    | `/api/v1/about/{id}`       | Replace the content of an existing About page   | ADMIN, SUPER_ADMIN  |
| DELETE | `/api/v1/about/{id}`       | Delete an About page (returns `204 No Content`) | ADMIN, SUPER_ADMIN  |

---

## 02 · Data Model — About (`about_pages`)

JPA entity: `ak.dev.khi_backend.khi_app.model.about.About`.

**Table:** `about_pages`

**Indexes**

| Index name              | Column     |
|-------------------------|------------|
| `idx_about_slug_ckb`    | `slug_ckb` |
| `idx_about_slug_kmr`    | `slug_kmr` |
| `idx_about_active`      | `active`   |

**Columns**

| Java field      | DB column                | SQL / JPA notes                                                                                                                                                            | Required | Notes                                                                 |
|-----------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|-----------------------------------------------------------------------|
| `id`            | `id`                     | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, `Long`                                                                                                          | auto     | Surrogate primary key.                                                |
| `slugCkb`       | `slug_ckb`               | `@NotBlank`, `@Column(name="slug_ckb", unique=true, nullable=false, length=200)`                                                                                            | yes      | Sorani route identifier; globally unique.                              |
| `slugKmr`       | `slug_kmr`               | `@Column(name="slug_kmr", unique=true, nullable=true, length=200)`                                                                                                          | no       | Kurmanji route identifier; nullable but globally unique when present. |
| `active`        | `active`                 | primitive `boolean`, `@Builder.Default = true`                                                                                                                              | yes      | Public list endpoint filters on `active == true`.                     |
| `displayOrder`  | `display_order`          | `@Column(name="display_order")`, `Integer`, `@Builder.Default = 0`                                                                                                          | no       | Integer ordering hint for front-end sort.                              |
| `ckbContent`    | (embedded, see §03)      | `@Embedded` with `@AttributeOverrides` mapping to `title_ckb`, `subtitle_ckb`, `meta_description_ckb`, `body_ckb`                                                            | no       | Sorani text bundle (see §03).                                          |
| `kmrContent`    | (embedded, see §03)      | `@Embedded` with `@AttributeOverrides` mapping to `title_kmr`, `subtitle_kmr`, `meta_description_kmr`, `body_kmr`                                                            | no       | Kurmanji text bundle (see §03).                                        |
| `stats`         | `stats`                  | `@JdbcTypeCode(SqlTypes.JSON)`, `@Column(name="stats", columnDefinition="jsonb")`, `List<StatItem>`, `@Builder.Default = new ArrayList<>()`                                  | no       | Stored as a JSONB array; order preserved.                              |
| `createdAt`     | `created_at`             | `@CreationTimestamp`, `@Column(name="created_at", updatable=false)`, `LocalDateTime`                                                                                        | auto     | Set once on insert.                                                   |
| `updatedAt`     | `updated_at`             | `@UpdateTimestamp`, `@Column(name="updated_at")`, `LocalDateTime`                                                                                                           | auto     | Refreshed on every update.                                            |

**Notes**

- About has **no** standalone media columns — every image / video / audio / file lives inside the bilingual Tiptap `body` HTML.
- `TiptapHtmlProcessor.process(...)` runs on every `body` before persist and hoists any inline base64 payload to S3, then rewrites the tag to the S3 URL.

---

## 03 · Data Model — AboutContent (embedded)

JPA `@Embeddable`: `ak.dev.khi_backend.khi_app.model.about.AboutContent`.

Holds the per-language text bundle. The owning `About` entity embeds it twice — once for CKB, once for KMR — with `@AttributeOverrides` rewriting the column names.

| Java field        | Base `@Column` definition                                  | CKB column                | KMR column                | Required | Notes                                                              |
|-------------------|------------------------------------------------------------|---------------------------|---------------------------|----------|--------------------------------------------------------------------|
| `title`           | `@Column(length = 300)`                                    | `title_ckb` (len 300)     | `title_kmr` (len 300)     | no       | Page H1 title.                                                     |
| `subtitle`        | `@Column(length = 500)`                                    | `subtitle_ckb` (len 500)  | `subtitle_kmr` (len 500)  | no       | Sub-headline below the title.                                      |
| `metaDescription` | `@Column(name="meta_description", columnDefinition="TEXT")`| `meta_description_ckb` (len 2500) | `meta_description_kmr` (len 2500) | no | SEO description; overridden to `length=2500` per language.       |
| `body`            | `@Column(name="body", columnDefinition="TEXT")`            | `body_ckb` (TEXT)         | `body_kmr` (TEXT)         | no       | Tiptap HTML; all inline `<img>`, `<video>`, `<audio>`, `<a href>` tags reference S3. |

> The CKB-side override widens `metaDescription` to `length=2500`; the KMR-side override does the same. The base `columnDefinition="TEXT"` declaration on the embeddable is preserved by the per-side overrides via the same `@AttributeOverride` mechanism declared on `About`.

---

## 04 · Data Model — StatItem

POJO: `ak.dev.khi_backend.khi_app.model.about.StatItem implements Serializable`.

`StatItem` is **not** a JPA entity — it is a plain POJO that is persisted as one element of the `about_pages.stats` JSONB array.

| Java field  | Type   | Required | Example          | Description                            |
|-------------|--------|----------|------------------|----------------------------------------|
| `labelCkb`  | String | no       | `"کتێب"`         | Sorani label.                          |
| `labelKmr`  | String | no       | `"Pirtûk"`       | Kurmanji label.                        |
| `value`     | String | no       | `"5,000+"`       | Display value (kept as a string so `+`, `%`, commas etc. are preserved). |

The service filters out stat entries where **all three** of `value`, `labelCkb`, and `labelKmr` are blank.

---

## 05 · Authentication & Roles

| Endpoint                       | Auth         | Roles                |
|--------------------------------|--------------|----------------------|
| `GET /api/v1/about`            | public       | —                    |
| `GET /api/v1/about/{slug}`     | public       | —                    |
| `POST /api/v1/about`           | required     | `ADMIN`, `SUPER_ADMIN` |
| `PUT /api/v1/about/{id}`       | required     | `ADMIN`, `SUPER_ADMIN` |
| `DELETE /api/v1/about/{id}`    | required     | `ADMIN`, `SUPER_ADMIN` |

Admin endpoints expect a Bearer JWT in the `Authorization` header. A request missing the token returns `401 UNAUTHORIZED`; an authenticated user without the required role returns `403 FORBIDDEN`.

---

## 06 · Public API

### 6.1 — `GET /api/v1/about`

List all About pages where `active == true`. No pagination, no filtering — the server reads all rows and filters in memory (`AboutService.getAllActive()`).

**Request**

| Parameter | In   | Required | Description |
|-----------|------|----------|-------------|
| —         | —    | —        | No parameters. |

**Response** — `200 OK`, body is a JSON array of `AboutResponse` (see §08).

**curl**

```bash
curl -X GET 'https://api.khi.example.com/api/v1/about'
```

**Example response**

```json
[
  {
    "id": 1,
    "slugCkb": "دەربارە",
    "slugKmr": "derbare",
    "ckbContent": {
      "title": "دەربارەی دەزگای میراتی کوردی",
      "subtitle": "پاراستن و بڵاوکردنەوەی میراتی نووسراوی کوردی",
      "metaDescription": "دەزگای میراتی کوردی پێگەیەکی فێرکاری و توێژینەوەیە کە لە سەر پاراستنی میراتی نووسراوی کوردی کار دەکات.",
      "body": "<h2>چیرۆکی ئێمە</h2><p>دەزگاکە لە ساڵی 2010 دامەزراوە...</p><img src=\"https://cdn.khi.example.com/media/about/team-ckb.jpg\" alt=\"تیمی ئێمە\"/>"
    },
    "kmrContent": {
      "title": "Derbarê Saziya Mîrateya Kurdî",
      "subtitle": "Parastin û belavkirina mîrateya nivîskî ya kurdî",
      "metaDescription": "Saziya Mîrateya Kurdî navendeke perwerde û lêkolînê ye ku li ser parastina mîrateya nivîskî ya kurdî dixebite.",
      "body": "<h2>Çîroka Me</h2><p>Sazî di sala 2010 de hate damezrandin...</p><img src=\"https://cdn.khi.example.com/media/about/team-kmr.jpg\" alt=\"Tîma me\"/>"
    },
    "active": true,
    "stats": [
      { "labelCkb": "کتێب",     "labelKmr": "Pirtûk",   "value": "5,000+" },
      { "labelCkb": "بەشداربوو", "labelKmr": "Beşdar",   "value": "120"    },
      { "labelCkb": "ساڵ",      "labelKmr": "Sal",      "value": "14"     }
    ],
    "createdAt": "2024-01-15 09:32:11",
    "updatedAt": "2024-04-02 17:08:44"
  }
]
```

---

### 6.2 — `GET /api/v1/about/{slug}`

Resolve a single page by either its Sorani or Kurmanji slug (`AboutRepository.findBySlugCkbOrSlugKmr(slug, slug)`).

**Request**

| Parameter | In   | Type   | Required | Description                            |
|-----------|------|--------|----------|----------------------------------------|
| `slug`    | path | string | yes      | Either `slugCkb` or `slugKmr` of the page. |

**Response** — `200 OK`, body is a single `AboutResponse`. If no row matches, the service throws `EntityNotFoundException` → `404 NOT_FOUND` envelope.

**curl**

```bash
curl -X GET 'https://api.khi.example.com/api/v1/about/derbare'
```

**Example response**

```json
{
  "id": 1,
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",
  "ckbContent": {
    "title": "دەربارەی دەزگای میراتی کوردی",
    "subtitle": "پاراستن و بڵاوکردنەوەی میراتی نووسراوی کوردی",
    "metaDescription": "دەزگای میراتی کوردی پێگەیەکی فێرکاری و توێژینەوەیە...",
    "body": "<h2>چیرۆکی ئێمە</h2><p>دەزگاکە لە ساڵی 2010 دامەزراوە...</p>"
  },
  "kmrContent": {
    "title": "Derbarê Saziya Mîrateya Kurdî",
    "subtitle": "Parastin û belavkirina mîrateya nivîskî ya kurdî",
    "metaDescription": "Saziya Mîrateya Kurdî navendeke perwerde û lêkolînê ye...",
    "body": "<h2>Çîroka Me</h2><p>Sazî di sala 2010 de hate damezrandin...</p>"
  },
  "active": true,
  "stats": [
    { "labelCkb": "کتێب",     "labelKmr": "Pirtûk",   "value": "5,000+" },
    { "labelCkb": "بەشداربوو", "labelKmr": "Beşdar",   "value": "120"    }
  ],
  "createdAt": "2024-01-15 09:32:11",
  "updatedAt": "2024-04-02 17:08:44"
}
```

---

## 07 · Internal API (Admin)

All admin endpoints require `Authorization: Bearer <jwt>` and a role of `ADMIN` or `SUPER_ADMIN`.

### 7.1 — `POST /api/v1/about`

Create a new About page. The service trims `slugCkb`, normalises blank `slugKmr` to `null`, validates uniqueness (CKB and KMR slugs must each be globally unique, and `slugCkb != slugKmr`), runs each `body` through `TiptapHtmlProcessor`, persists, and returns the saved row. New pages are forced to `active = true`.

**Headers**

| Header           | Value                             |
|------------------|-----------------------------------|
| `Authorization`  | `Bearer <jwt>`                    |
| `Content-Type`   | `application/json`                |

**Body schema (`AboutDTOs.AboutRequest`)**

| Field         | Type                  | Required | Notes                                                                                                    |
|---------------|-----------------------|----------|----------------------------------------------------------------------------------------------------------|
| `slugCkb`     | string                | yes      | Sorani URL slug. Must be non-blank, globally unique. Trimmed by the service.                              |
| `slugKmr`     | string                | no       | Kurmanji URL slug. Blank → `null`. If present, must be globally unique and **must not equal `slugCkb`**. |
| `ckbContent`  | `AboutContentRequest` | no       | Sorani text bundle. `null` is allowed and is stored as an empty `AboutContent`.                          |
| `kmrContent`  | `AboutContentRequest` | no       | Kurmanji text bundle. Same null semantics.                                                               |
| `stats`       | `StatItemDto[]`       | no       | Structured stats. Entries with all three fields blank are filtered out. `null` / empty → stored as `[]`. |

`AboutContentRequest` fields (apply per language):

| Field             | Type   | Required | Notes                                                                       |
|-------------------|--------|----------|-----------------------------------------------------------------------------|
| `title`           | string | no       | Page H1.                                                                    |
| `subtitle`        | string | no       | Page sub-headline.                                                          |
| `metaDescription` | string | no       | SEO description (stored as TEXT, override length 2500 per language).        |
| `body`            | string | no       | Tiptap HTML. Run through `TiptapHtmlProcessor.process(...)` on save.        |

`StatItemDto` fields:

| Field      | Type   | Required | Notes                                                  |
|------------|--------|----------|--------------------------------------------------------|
| `labelCkb` | string | no       | Sorani label.                                          |
| `labelKmr` | string | no       | Kurmanji label.                                        |
| `value`    | string | no       | Display value (kept as string).                        |

**Response** — `201 CREATED`, body is the persisted `AboutResponse`.

**curl**

```bash
curl -X POST 'https://api.khi.example.com/api/v1/about' \
  -H 'Authorization: Bearer eyJhbGciOiJI...' \
  -H 'Content-Type: application/json' \
  -d '{
    "slugCkb": "دەربارە",
    "slugKmr": "derbare",
    "ckbContent": {
      "title": "دەربارەی دەزگای میراتی کوردی",
      "subtitle": "پاراستن و بڵاوکردنەوەی میراتی نووسراوی کوردی",
      "metaDescription": "دەزگای میراتی کوردی پێگەیەکی فێرکاری و توێژینەوەیە کە لە سەر پاراستنی میراتی نووسراوی کوردی کار دەکات.",
      "body": "<h2>چیرۆکی ئێمە</h2><p>دەزگاکە لە ساڵی 2010 دامەزراوە و خزمەتی توێژەران و فێرخوازانی کوردی دەکات.</p><img src=\"https://cdn.khi.example.com/media/about/team-ckb.jpg\" alt=\"تیمی ئێمە\"/>"
    },
    "kmrContent": {
      "title": "Derbarê Saziya Mîrateya Kurdî",
      "subtitle": "Parastin û belavkirina mîrateya nivîskî ya kurdî",
      "metaDescription": "Saziya Mîrateya Kurdî navendeke perwerde û lêkolînê ye ku li ser parastina mîrateya nivîskî ya kurdî dixebite.",
      "body": "<h2>Çîroka Me</h2><p>Sazî di sala 2010 de hate damezrandin û xizmeta lêkolîner û xwendekarên kurdî dike.</p><img src=\"https://cdn.khi.example.com/media/about/team-kmr.jpg\" alt=\"Tîma me\"/>"
    },
    "stats": [
      { "labelCkb": "کتێب",     "labelKmr": "Pirtûk", "value": "5,000+" },
      { "labelCkb": "بەشداربوو", "labelKmr": "Beşdar", "value": "120"    },
      { "labelCkb": "ساڵ",      "labelKmr": "Sal",    "value": "14"     }
    ]
  }'
```

**Example response (`201 CREATED`)**

```json
{
  "id": 7,
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",
  "ckbContent": {
    "title": "دەربارەی دەزگای میراتی کوردی",
    "subtitle": "پاراستن و بڵاوکردنەوەی میراتی نووسراوی کوردی",
    "metaDescription": "دەزگای میراتی کوردی پێگەیەکی فێرکاری و توێژینەوەیە...",
    "body": "<h2>چیرۆکی ئێمە</h2><p>دەزگاکە لە ساڵی 2010 دامەزراوە...</p><img src=\"https://cdn.khi.example.com/media/about/team-ckb.jpg\" alt=\"تیمی ئێمە\"/>"
  },
  "kmrContent": {
    "title": "Derbarê Saziya Mîrateya Kurdî",
    "subtitle": "Parastin û belavkirina mîrateya nivîskî ya kurdî",
    "metaDescription": "Saziya Mîrateya Kurdî navendeke perwerde û lêkolînê ye...",
    "body": "<h2>Çîroka Me</h2><p>Sazî di sala 2010 de hate damezrandin...</p><img src=\"https://cdn.khi.example.com/media/about/team-kmr.jpg\" alt=\"Tîma me\"/>"
  },
  "active": true,
  "stats": [
    { "labelCkb": "کتێب",     "labelKmr": "Pirtûk", "value": "5,000+" },
    { "labelCkb": "بەشداربوو", "labelKmr": "Beşdar", "value": "120"    },
    { "labelCkb": "ساڵ",      "labelKmr": "Sal",    "value": "14"     }
  ],
  "createdAt": "2026-05-31 10:14:02",
  "updatedAt": "2026-05-31 10:14:02"
}
```

---

### 7.2 — `PUT /api/v1/about/{id}`

Replace the content of an existing About page. The request body uses the same `AboutRequest` schema as create (see §7.1). The service loads the row by `id`, re-runs slug-uniqueness checks (excluding the current row), reprocesses both `body` strings via `TiptapHtmlProcessor`, rebuilds the stats list, and saves. `active` is left untouched and `displayOrder` is not part of the DTO.

**Path parameters**

| Parameter | Type | Required | Description                |
|-----------|------|----------|----------------------------|
| `id`      | Long | yes      | Primary key of the About row. |

**Headers**

| Header           | Value                             |
|------------------|-----------------------------------|
| `Authorization`  | `Bearer <jwt>`                    |
| `Content-Type`   | `application/json`                |

**Body** — `AboutDTOs.AboutRequest`, identical schema to §7.1.

**Response** — `200 OK`, body is the updated `AboutResponse`.

**curl**

```bash
curl -X PUT 'https://api.khi.example.com/api/v1/about/7' \
  -H 'Authorization: Bearer eyJhbGciOiJI...' \
  -H 'Content-Type: application/json' \
  -d '{
    "slugCkb": "دەربارە",
    "slugKmr": "derbare",
    "ckbContent": {
      "title": "دەربارەی دەزگای میراتی کوردی",
      "subtitle": "پاراستن، توێژینەوە و بڵاوکردنەوەی میراتی نووسراوی کوردی",
      "metaDescription": "دەزگای میراتی کوردی...",
      "body": "<h2>چیرۆکی ئێمە</h2><p>وەشانی نوێکراوە.</p>"
    },
    "kmrContent": {
      "title": "Derbarê Saziya Mîrateya Kurdî",
      "subtitle": "Parastin, lêkolîn û belavkirina mîrateya nivîskî ya kurdî",
      "metaDescription": "Saziya Mîrateya Kurdî...",
      "body": "<h2>Çîroka Me</h2><p>Guhertoya nû.</p>"
    },
    "stats": [
      { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,250+" }
    ]
  }'
```

**Example response (`200 OK`)**

```json
{
  "id": 7,
  "slugCkb": "دەربارە",
  "slugKmr": "derbare",
  "ckbContent": {
    "title": "دەربارەی دەزگای میراتی کوردی",
    "subtitle": "پاراستن، توێژینەوە و بڵاوکردنەوەی میراتی نووسراوی کوردی",
    "metaDescription": "دەزگای میراتی کوردی...",
    "body": "<h2>چیرۆکی ئێمە</h2><p>وەشانی نوێکراوە.</p>"
  },
  "kmrContent": {
    "title": "Derbarê Saziya Mîrateya Kurdî",
    "subtitle": "Parastin, lêkolîn û belavkirina mîrateya nivîskî ya kurdî",
    "metaDescription": "Saziya Mîrateya Kurdî...",
    "body": "<h2>Çîroka Me</h2><p>Guhertoya nû.</p>"
  },
  "active": true,
  "stats": [
    { "labelCkb": "کتێب", "labelKmr": "Pirtûk", "value": "5,250+" }
  ],
  "createdAt": "2026-05-31 10:14:02",
  "updatedAt": "2026-05-31 11:02:48"
}
```

---

### 7.3 — `DELETE /api/v1/about/{id}`

Hard-delete the About row.

**Path parameters**

| Parameter | Type | Required | Description                |
|-----------|------|----------|----------------------------|
| `id`      | Long | yes      | Primary key of the About row. |

**Headers**

| Header           | Value             |
|------------------|-------------------|
| `Authorization`  | `Bearer <jwt>`    |

**Response** — `204 No Content`, empty body.

**curl**

```bash
curl -X DELETE 'https://api.khi.example.com/api/v1/about/7' \
  -H 'Authorization: Bearer eyJhbGciOiJI...'
```

If the `id` does not exist, the service throws `EntityNotFoundException` → `404 NOT_FOUND` envelope.

---

## 08 · DTO Reference

All DTOs live in `ak.dev.khi_backend.khi_app.dto.about.AboutDTOs` (Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`).

### 8.1 — `AboutDTOs.AboutRequest`

| Field         | Type                  | Required | Description                                                            |
|---------------|-----------------------|----------|------------------------------------------------------------------------|
| `slugCkb`     | `String`              | yes      | Sorani URL slug. Trimmed before persistence. Must be globally unique.  |
| `slugKmr`     | `String`              | no       | Kurmanji URL slug. Blank → `null`. Globally unique when present; must differ from `slugCkb`. |
| `ckbContent`  | `AboutContentRequest` | no       | Sorani per-language bundle.                                            |
| `kmrContent`  | `AboutContentRequest` | no       | Kurmanji per-language bundle.                                          |
| `stats`       | `List<StatItemDto>`   | no       | Structured stats array. Blank entries are filtered out.                |

### 8.2 — `AboutDTOs.AboutContentRequest`

| Field             | Type     | Required | Description                                                |
|-------------------|----------|----------|------------------------------------------------------------|
| `title`           | `String` | no       | Page H1.                                                   |
| `subtitle`        | `String` | no       | Sub-headline.                                              |
| `metaDescription` | `String` | no       | SEO meta description (TEXT, length override 2500/lang).    |
| `body`            | `String` | no       | Tiptap HTML; processed by `TiptapHtmlProcessor` on save.   |

### 8.3 — `AboutDTOs.StatItemDto`

| Field      | Type     | Required | Description       |
|------------|----------|----------|-------------------|
| `labelCkb` | `String` | no       | Sorani label.     |
| `labelKmr` | `String` | no       | Kurmanji label.   |
| `value`    | `String` | no       | Display value.    |

### 8.4 — `AboutDTOs.AboutResponse`

| Field         | Type                   | Description                                                 |
|---------------|------------------------|-------------------------------------------------------------|
| `id`          | `Long`                 | Primary key.                                                |
| `slugCkb`     | `String`               | Sorani slug.                                                |
| `slugKmr`     | `String`               | Kurmanji slug (may be `null`).                              |
| `ckbContent`  | `AboutContentResponse` | Sorani bundle (or `null` if the embedded fields are empty). |
| `kmrContent`  | `AboutContentResponse` | Kurmanji bundle (or `null`).                                |
| `active`      | `boolean`              | Whether the page is publicly listed.                        |
| `stats`       | `List<StatItemDto>`    | Stats array (empty list if none).                           |
| `createdAt`   | `String`               | Formatted `yyyy-MM-dd HH:mm:ss` (server local time).        |
| `updatedAt`   | `String`               | Formatted `yyyy-MM-dd HH:mm:ss`.                            |

### 8.5 — `AboutDTOs.AboutContentResponse`

| Field             | Type     | Description                                |
|-------------------|----------|--------------------------------------------|
| `title`           | `String` | Page H1.                                   |
| `subtitle`        | `String` | Sub-headline.                              |
| `metaDescription` | `String` | SEO meta description.                      |
| `body`            | `String` | Tiptap HTML body.                          |

---

## 09 · Error Responses

Errors are emitted by the global exception handler in the standard bilingual `ApiErrorResponse` envelope (this envelope is shared platform-wide — note that successful About responses do **not** wrap their payload, but error responses always do).

**Envelope shape**

| Field          | Type                   | Description                                                |
|----------------|------------------------|------------------------------------------------------------|
| `timestamp`    | string (ISO-8601)      | When the error was produced.                               |
| `status`       | integer                | HTTP status code.                                          |
| `path`         | string                 | Request path that failed.                                  |
| `method`       | string                 | HTTP method.                                               |
| `traceId`      | string                 | Correlation id for log lookup.                             |
| `code`         | string                 | Machine-readable error code (see table below).             |
| `message`      | string                 | Default human message (English).                           |
| `messageEn`    | string                 | English message.                                           |
| `messageKu`    | string                 | Kurdish (Sorani) message.                                  |
| `fieldErrors`  | array of `{field,message,messageEn,messageKu}` | Per-field validation errors (when `code = VALIDATION_ERROR`). |
| `details`      | object (free-form)     | Extra context (e.g. `slug`, `id` of the offending row).    |

**Common codes for the About module**

| `code`             | HTTP | When it happens                                                                                  |
|--------------------|------|--------------------------------------------------------------------------------------------------|
| `NOT_FOUND`        | 404  | `GET /{slug}` resolved nothing; `PUT /{id}` or `DELETE /{id}` referenced a missing row.          |
| `CONFLICT`         | 409  | `slugCkb` already used by another row, or `slugKmr` already used by another row.                 |
| `VALIDATION_ERROR` | 400  | `slugCkb` blank, `slugCkb == slugKmr`, or any other `IllegalArgumentException` / bean-validation failure. |
| `UNAUTHORIZED`     | 401  | Missing or invalid JWT on an admin endpoint.                                                     |
| `FORBIDDEN`        | 403  | Authenticated user lacks the `ADMIN` / `SUPER_ADMIN` role.                                       |

**Example — slug conflict**

```json
{
  "timestamp": "2026-05-31T10:17:44.812Z",
  "status": 409,
  "path": "/api/v1/about",
  "method": "POST",
  "traceId": "5e2a2e3c-8b1a-4f3d-9c10-9d4f0d2c11a7",
  "code": "CONFLICT",
  "message": "CKB slug already exists: دەربارە",
  "messageEn": "An About page with this CKB slug already exists.",
  "messageKu": "پەڕەی دەربارە بەم slug-ـی سۆرانییە پێشتر هەیە.",
  "fieldErrors": [],
  "details": { "field": "slugCkb", "value": "دەربارە" }
}
```

**Example — not found**

```json
{
  "timestamp": "2026-05-31T10:18:02.114Z",
  "status": 404,
  "path": "/api/v1/about/unknown-slug",
  "method": "GET",
  "traceId": "9b7d5f2e-1c44-49ce-a0d0-4e2a1b3c44a8",
  "code": "NOT_FOUND",
  "message": "About page not found: unknown-slug",
  "messageEn": "No About page matched the supplied slug.",
  "messageKu": "هیچ پەڕەیەکی دەربارە بەم slug-ـە نەدۆزرایەوە.",
  "fieldErrors": [],
  "details": { "slug": "unknown-slug" }
}
```

**Example — validation error (CKB slug missing)**

```json
{
  "timestamp": "2026-05-31T10:20:31.002Z",
  "status": 400,
  "path": "/api/v1/about",
  "method": "POST",
  "traceId": "2a44ce91-3b7e-4a2a-8c89-7f5d1c0a1b22",
  "code": "VALIDATION_ERROR",
  "message": "CKB slug is required",
  "messageEn": "The CKB slug is required.",
  "messageKu": "slug-ـی سۆرانی پێویستە.",
  "fieldErrors": [
    {
      "field": "slugCkb",
      "message": "CKB slug is required",
      "messageEn": "The CKB slug is required.",
      "messageKu": "slug-ـی سۆرانی پێویستە."
    }
  ],
  "details": {}
}
```

**Example — unauthorized**

```json
{
  "timestamp": "2026-05-31T10:21:11.881Z",
  "status": 401,
  "path": "/api/v1/about",
  "method": "POST",
  "traceId": "a4c1f7e2-6c9d-4d0a-bf21-4a36c0e9d2bb",
  "code": "UNAUTHORIZED",
  "message": "Authentication is required.",
  "messageEn": "Authentication is required.",
  "messageKu": "پێویستە بچیتە ژوورەوە.",
  "fieldErrors": [],
  "details": {}
}
```

**Example — forbidden**

```json
{
  "timestamp": "2026-05-31T10:21:55.412Z",
  "status": 403,
  "path": "/api/v1/about/7",
  "method": "DELETE",
  "traceId": "0c2db4d0-2c10-42d2-8d75-7d5f9a3a0bd9",
  "code": "FORBIDDEN",
  "message": "Insufficient privileges.",
  "messageEn": "You do not have permission to perform this action.",
  "messageKu": "دەسەڵاتت نییە بۆ ئەنجامدانی ئەم کارە.",
  "fieldErrors": [],
  "details": { "requiredRoles": ["ADMIN", "SUPER_ADMIN"] }
}
```

---

## 10 · Notes (Tiptap & media)

- **No standalone media columns.** The About entity carries no hero image, hero media type, hero thumbnail, or gallery column. Every image, video, audio clip, and downloadable file is an inline `<img>`, `<video>`, `<audio>`, or `<a href>` tag inside the per-language `body` HTML.
- **Inline-media flow.** The frontend uploads each binary asset once via the shared `POST /api/v1/media/upload` endpoint, takes the returned CDN/S3 URL, drops it into the Tiptap editor, and finally submits the full `AboutRequest` JSON to this controller.
- **Server-side sanitisation.** `AboutService.buildAboutContent(...)` calls `TiptapHtmlProcessor.process(body)` on every save. The processor sanitises incoming HTML and acts as a safety net: any inline `data:` / base64 payload still present in the editor output is hoisted to S3 and the tag rewritten to the resulting URL, so the `body_ckb` / `body_kmr` columns never store raw binary blobs.
- **Active flag.** `GET /api/v1/about` filters rows in-memory on `active == true`. New pages are created with `active = true` and `PUT` does not modify the flag — toggling `active` is not exposed via the current DTO.
- **Display order.** `display_order` (`Integer`, default `0`) exists on the entity for front-end sorting but is **not** included in `AboutRequest` or `AboutResponse` and cannot be set via the current API.
- **Slug rules.** `slugCkb` is required, trimmed, and globally unique. `slugKmr` is optional; if supplied it must be unique and must not equal `slugCkb`. Blank `slugKmr` is normalised to `null`.
- **Stats persistence.** `stats` is a JSONB column populated from `List<StatItem>` (POJO, no JPA mapping). The service drops entries whose `value`, `labelCkb`, and `labelKmr` are all blank.
- **Timestamps in responses are strings** formatted as `yyyy-MM-dd HH:mm:ss` (server local time), not ISO-8601.
