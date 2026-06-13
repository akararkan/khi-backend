# Contact Module

> Bilingual (CKB / KMR) Contact pages with Tiptap HTML bodies. Public reads + active-only public list. Authenticated writes.

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — Contact](#02--data-model--contact)
- [03 · Data Model — ContactContent (embedded)](#03--data-model--contactcontent-embedded)
- [04 · Authentication & Roles](#04--authentication--roles)
- [05 · Public API](#05--public-api)
- [06 · Internal API (Authenticated)](#06--internal-api-authenticated)
- [07 · DTO Reference](#07--dto-reference)
- [08 · Response Envelope](#08--response-envelope)
- [09 · Error Responses](#09--error-responses)
- [10 · Notes](#10--notes)

---

## 01 · Module Overview

The Contact module manages bilingual contact pages (Sorani / CKB and Kurmanji / KMR). Each page exposes per-language title, subtitle, address, working hours, and a Tiptap HTML description — plus language-agnostic contact details (phone numbers, email, map embed URL, latitude/longitude). All visual media (images, video, audio, documents) is embedded inline inside the bilingual Tiptap `description` HTML; the entity carries no standalone hero or gallery field.

- **Base path:** `/api/v1/contact`
- **Controller:** `ak.dev.khi_backend.khi_app.api.contact.ContactController`
- **Service:** `ak.dev.khi_backend.khi_app.service.contact.ContactService`
- **Entity:** `ak.dev.khi_backend.khi_app.model.contact.Contact` (table `contact_pages`)
- **Embeddable:** `ak.dev.khi_backend.khi_app.model.contact.ContactContent`
- **Response wrapper:** `ApiResponse<T>` on every endpoint

### Endpoint Summary

| Method | Path                              | Description                                        | Auth                           |
| ------ | --------------------------------- | -------------------------------------------------- | ------------------------------ |
| GET    | `/api/v1/contact`                 | List all Contact pages (active + inactive)         | Public (GET rule)              |
| GET    | `/api/v1/contact/active`          | List only active Contact pages, ordered            | Public                         |
| GET    | `/api/v1/contact/{id}`            | Get a Contact page by its numeric ID               | Public                         |
| GET    | `/api/v1/contact/slug/{slug}`     | Get a Contact page by its CKB or KMR slug          | Public                         |
| POST   | `/api/v1/contact`                 | Create a new Contact page                          | Authenticated (any role)       |
| PUT    | `/api/v1/contact/{id}`            | Full update of an existing Contact page            | Authenticated (any role)       |
| DELETE | `/api/v1/contact/{id}`            | Delete a Contact page                              | Authenticated (any role)       |

---

## 02 · Data Model — Contact

Annotated with `@Entity` and `@Table(name = "contact_pages")`. Indexed on `slug_ckb`, `slug_kmr`, and `active`.

| Field            | Java Type          | Column / Mapping                                                                                       | Constraints                                                                                                          | Notes                                                                                                                          |
| ---------------- | ------------------ | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `id`             | `Long`             | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`                                              | Primary key                                                                                                          | Auto-generated.                                                                                                                |
| `slugCkb`        | `String`           | `@Column(name = "slug_ckb", unique = true, nullable = false, length = 200)`                            | `@NotBlank`, unique, non-null                                                                                        | Sorani route identifier. Required.                                                                                             |
| `slugKmr`        | `String`           | `@Column(name = "slug_kmr", unique = true, nullable = true, length = 200)`                             | Unique, nullable                                                                                                     | Kurmanji route identifier. Optional. Must differ from `slugCkb` when present.                                                  |
| `active`         | `boolean`          | default column name `active`                                                                           | `@Builder.Default = true`                                                                                            | Publication flag. Indexed.                                                                                                     |
| `displayOrder`   | `Integer`          | `@Column(name = "display_order")`                                                                      | `@Builder.Default = 0`                                                                                                | Sort key for the active list (`findAllByActiveTrueOrderByDisplayOrderAsc`).                                                    |
| `ckbContent`     | `ContactContent`   | `@Embedded` with `@AttributeOverrides` mapping each field to `*_ckb` columns (see below)               | Embedded                                                                                                             | Sorani per-language content block.                                                                                             |
| `kmrContent`     | `ContactContent`   | `@Embedded` with `@AttributeOverrides` mapping each field to `*_kmr` columns (see below)               | Embedded                                                                                                             | Kurmanji per-language content block.                                                                                           |
| `phone`          | `String`           | `@Column(name = "phone", length = 60)`                                                                 | —                                                                                                                    | Primary phone (e.g. `+964 770 123 4567`).                                                                                       |
| `secondaryPhone` | `String`           | `@Column(name = "secondary_phone", length = 60)`                                                       | —                                                                                                                    | Secondary / additional phone.                                                                                                  |
| `email`          | `String`           | `@Column(name = "email", length = 200)`                                                                | —                                                                                                                    | Primary contact email.                                                                                                         |
| `mapEmbedUrl`    | `String`           | `@Column(name = "map_embed_url", columnDefinition = "TEXT")`                                           | —                                                                                                                    | Google Maps embed URL or any iframe-compatible map URL.                                                                        |
| `latitude`       | `Double`           | `@Column(name = "latitude")`                                                                           | —                                                                                                                    | Latitude for custom marker or Open Maps link.                                                                                  |
| `longitude`      | `Double`           | `@Column(name = "longitude")`                                                                          | —                                                                                                                    | Longitude for custom marker or Open Maps link.                                                                                 |
| `createdAt`      | `LocalDateTime`    | `@CreationTimestamp @Column(name = "created_at", updatable = false)`                                   | Immutable                                                                                                            | Set at insert. Serialized as `yyyy-MM-dd HH:mm:ss` string in responses.                                                        |
| `updatedAt`      | `LocalDateTime`    | `@UpdateTimestamp @Column(name = "updated_at")`                                                        | —                                                                                                                    | Refreshed on every update. Serialized as `yyyy-MM-dd HH:mm:ss` string in responses.                                            |

### CKB attribute overrides (`@Embedded ckbContent`)

| Embeddable field | Column          | Definition                              |
| ---------------- | --------------- | --------------------------------------- |
| `title`          | `title_ckb`     | `length = 300`                          |
| `subtitle`       | `subtitle_ckb`  | `length = 500`                          |
| `address`        | `address_ckb`   | `length = 500`                          |
| `workingHours`   | `working_hours_ckb` | `length = 300`                      |
| `description`    | `description_ckb`   | `columnDefinition = "TEXT"`         |

### KMR attribute overrides (`@Embedded kmrContent`)

| Embeddable field | Column          | Definition                              |
| ---------------- | --------------- | --------------------------------------- |
| `title`          | `title_kmr`     | `length = 300`                          |
| `subtitle`       | `subtitle_kmr`  | `length = 500`                          |
| `address`        | `address_kmr`   | `length = 500`                          |
| `workingHours`   | `working_hours_kmr` | `length = 300`                      |
| `description`    | `description_kmr`   | `columnDefinition = "TEXT"`         |

### Indexes

| Index name                | Column      |
| ------------------------- | ----------- |
| `idx_contact_slug_ckb`    | `slug_ckb`  |
| `idx_contact_slug_kmr`    | `slug_kmr`  |
| `idx_contact_active`      | `active`    |

---

## 03 · Data Model — ContactContent (embedded)

`@Embeddable` bilingual block embedded into `Contact` twice — once for CKB, once for KMR. The base `@Column` definitions are listed below; per-language column names are applied via `@AttributeOverrides` on `Contact` (see tables in section 02).

| Field          | Java Type | Base `@Column`                                          | Notes                                                                                                                                                                            |
| -------------- | --------- | ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `title`        | `String`  | `@Column(length = 300)`                                 | Page title in this language.                                                                                                                                                     |
| `subtitle`     | `String`  | `@Column(length = 500)`                                 | Short subtitle / call-to-action text.                                                                                                                                            |
| `address`      | `String`  | `@Column(length = 500)`                                 | Physical address in this language.                                                                                                                                               |
| `workingHours` | `String`  | `@Column(name = "working_hours", length = 300)`         | Working / office hours in this language.                                                                                                                                         |
| `description`  | `String`  | `@Column(name = "description", columnDefinition = "TEXT")` | Tiptap HTML — full editor output. Inline `<img>`, `<video>`, `<audio>`, `<a href>` reference S3 URLs. `TiptapHtmlProcessor` rewrites any inline base64 payload to S3 on save. |

---

## 04 · Authentication & Roles

Per the project's `SecurityConfig`:

- All `GET /api/v1/**` requests are **public** by the default GET rule.
- There is **no path-specific matcher** for `/api/v1/contact/**` writes. They fall through to `.anyRequest().authenticated()`, which means **any authenticated principal** can perform writes.

| Endpoint                           | Auth required | Allowed roles                                  |
| ---------------------------------- | ------------- | ---------------------------------------------- |
| `GET /api/v1/contact`              | No            | Public                                         |
| `GET /api/v1/contact/active`       | No            | Public                                         |
| `GET /api/v1/contact/{id}`         | No            | Public                                         |
| `GET /api/v1/contact/slug/{slug}`  | No            | Public                                         |
| `POST /api/v1/contact`             | Yes           | Any authenticated user — `GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN` |
| `PUT /api/v1/contact/{id}`         | Yes           | Any authenticated user — `GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN` |
| `DELETE /api/v1/contact/{id}`      | Yes           | Any authenticated user — `GUEST`, `EMPLOYEE`, `ADMIN`, `SUPER_ADMIN` |

> Note: Contact write endpoints are NOT ADMIN-gated in `SecurityConfig`. If admin-only access is required, a dedicated matcher must be added.

---

## 05 · Public API

All responses are wrapped in `ApiResponse<T>` (see section 08).

### 5.1 · `GET /api/v1/contact`

List every Contact page (active and inactive). Provided as an admin-convenience listing; it remains public because GETs are not gated by `SecurityConfig`.

- **Auth:** Public
- **Path params:** none
- **Query params:** none
- **Returns:** `ApiResponse<List<ContactResponse>>`

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Contact pages fetched",
  "data": [
    {
      "id": 1,
      "slugCkb": "peywendi-pe-kirdin",
      "slugKmr": "peywendi-pe-kirin",
      "ckbContent": {
        "title": "پەیوەندیمان پێوە بکە",
        "subtitle": "ئامادە بە بۆ یارمەتیدانت",
        "address": "هەولێر، شەقامی ١٠٠ مەتری",
        "workingHours": "شەممە - پێنجشەممە، ٨:٠٠ - ١٧:٠٠",
        "description": "<p>پەیوەندیمان پێوە بکە بە …</p>"
      },
      "kmrContent": {
        "title": "Bi me re têkilî daynin",
        "subtitle": "Em amade ne ji bo arîkariyê",
        "address": "Hewlêr, Kolana 100 metre",
        "workingHours": "Şemî - Pêncşem, 8:00 - 17:00",
        "description": "<p>Bi me re têkilî daynin …</p>"
      },
      "phone": "+964 770 123 4567",
      "secondaryPhone": "+964 750 987 6543",
      "email": "info@khi.example",
      "mapEmbedUrl": "https://www.google.com/maps/embed?pb=…",
      "latitude": 36.1911,
      "longitude": 44.0094,
      "active": true,
      "createdAt": "2026-01-15 09:24:11",
      "updatedAt": "2026-02-02 16:05:48"
    }
  ]
}
```

---

### 5.2 · `GET /api/v1/contact/active`

List only active Contact pages, ordered by `displayOrder` ascending (repository method `findAllByActiveTrueOrderByDisplayOrderAsc`).

- **Auth:** Public
- **Path params:** none
- **Query params:** none
- **Returns:** `ApiResponse<List<ContactResponse>>`

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Active contact pages fetched",
  "data": [
    {
      "id": 1,
      "slugCkb": "peywendi-pe-kirdin",
      "slugKmr": "peywendi-pe-kirin",
      "ckbContent": { "title": "پەیوەندیمان پێوە بکە", "subtitle": null, "address": "...", "workingHours": "...", "description": "<p>…</p>" },
      "kmrContent": { "title": "Bi me re têkilî daynin", "subtitle": null, "address": "...", "workingHours": "...", "description": "<p>…</p>" },
      "phone": "+964 770 123 4567",
      "secondaryPhone": null,
      "email": "info@khi.example",
      "mapEmbedUrl": null,
      "latitude": 36.1911,
      "longitude": 44.0094,
      "active": true,
      "createdAt": "2026-01-15 09:24:11",
      "updatedAt": "2026-01-15 09:24:11"
    }
  ]
}
```

---

### 5.3 · `GET /api/v1/contact/{id}`

Fetch a Contact page by its numeric primary key.

- **Auth:** Public
- **Path params:**
  - `id` — `Long` — primary key of the Contact row.
- **Returns:** `ApiResponse<ContactResponse>`

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Contact page fetched",
  "data": {
    "id": 1,
    "slugCkb": "peywendi-pe-kirdin",
    "slugKmr": "peywendi-pe-kirin",
    "ckbContent": { "title": "پەیوەندیمان پێوە بکە", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "kmrContent": { "title": "Bi me re têkilî daynin", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "phone": "+964 770 123 4567",
    "secondaryPhone": "+964 750 987 6543",
    "email": "info@khi.example",
    "mapEmbedUrl": "https://www.google.com/maps/embed?pb=…",
    "latitude": 36.1911,
    "longitude": 44.0094,
    "active": true,
    "createdAt": "2026-01-15 09:24:11",
    "updatedAt": "2026-02-02 16:05:48"
  }
}
```

---

### 5.4 · `GET /api/v1/contact/slug/{slug}`

Fetch a Contact page by either its CKB slug or its KMR slug — the service queries with `findBySlugCkbOrSlugKmr(slug, slug)`.

- **Auth:** Public
- **Path params:**
  - `slug` — `String` — CKB or KMR slug, URL-encoded if needed.
- **Returns:** `ApiResponse<ContactResponse>`

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Contact page fetched",
  "data": {
    "id": 1,
    "slugCkb": "peywendi-pe-kirdin",
    "slugKmr": "peywendi-pe-kirin",
    "ckbContent": { "title": "پەیوەندیمان پێوە بکە", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "kmrContent": { "title": "Bi me re têkilî daynin", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "phone": "+964 770 123 4567",
    "secondaryPhone": null,
    "email": "info@khi.example",
    "mapEmbedUrl": null,
    "latitude": 36.1911,
    "longitude": 44.0094,
    "active": true,
    "createdAt": "2026-01-15 09:24:11",
    "updatedAt": "2026-01-15 09:24:11"
  }
}
```

---

## 06 · Internal API (Authenticated)

All writes require any authenticated principal (`SecurityConfig.anyRequest().authenticated()` fall-through). The controller uses `Authorization: Bearer <jwt>` per the global JWT setup.

### 6.1 · `POST /api/v1/contact`

Create a new Contact page. The service trims `slugCkb`, normalises blank optional strings to `null`, runs `TiptapHtmlProcessor.process(...)` over each language's `description`, and persists with `active = true` by default.

- **Auth:** Authenticated (any role)
- **Content-Type:** `application/json`
- **Request body:** `ContactRequest` (see section 7.1)
- **Returns:** `ApiResponse<ContactResponse>` with HTTP `201 Created`

**Example request:**

```json
{
  "slugCkb": "peywendi-pe-kirdin",
  "slugKmr": "peywendi-pe-kirin",
  "ckbContent": {
    "title": "پەیوەندیمان پێوە بکە",
    "subtitle": "ئامادە بە بۆ یارمەتیدانت",
    "address": "هەولێر، شەقامی ١٠٠ مەتری",
    "workingHours": "شەممە - پێنجشەممە، ٨:٠٠ - ١٧:٠٠",
    "description": "<p>پەیوەندیمان پێوە بکە لە ڕێگەی …</p><img src=\"https://cdn.khi.example/uploads/contact/hero.jpg\" />"
  },
  "kmrContent": {
    "title": "Bi me re têkilî daynin",
    "subtitle": "Em amade ne ji bo arîkariyê",
    "address": "Hewlêr, Kolana 100 metre",
    "workingHours": "Şemî - Pêncşem, 8:00 - 17:00",
    "description": "<p>Bi me re têkilî daynin …</p>"
  },
  "phone": "+964 770 123 4567",
  "secondaryPhone": "+964 750 987 6543",
  "email": "info@khi.example",
  "mapEmbedUrl": "https://www.google.com/maps/embed?pb=…",
  "latitude": 36.1911,
  "longitude": 44.0094
}
```

**Example response — `201 Created`:**

```json
{
  "success": true,
  "message": "Contact page created successfully",
  "data": {
    "id": 7,
    "slugCkb": "peywendi-pe-kirdin",
    "slugKmr": "peywendi-pe-kirin",
    "ckbContent": { "title": "پەیوەندیمان پێوە بکە", "subtitle": "ئامادە بە بۆ یارمەتیدانت", "address": "هەولێر، شەقامی ١٠٠ مەتری", "workingHours": "شەممە - پێنجشەممە، ٨:٠٠ - ١٧:٠٠", "description": "<p>…</p>" },
    "kmrContent": { "title": "Bi me re têkilî daynin", "subtitle": "Em amade ne ji bo arîkariyê", "address": "Hewlêr, Kolana 100 metre", "workingHours": "Şemî - Pêncşem, 8:00 - 17:00", "description": "<p>…</p>" },
    "phone": "+964 770 123 4567",
    "secondaryPhone": "+964 750 987 6543",
    "email": "info@khi.example",
    "mapEmbedUrl": "https://www.google.com/maps/embed?pb=…",
    "latitude": 36.1911,
    "longitude": 44.0094,
    "active": true,
    "createdAt": "2026-05-31 10:12:33",
    "updatedAt": "2026-05-31 10:12:33"
  }
}
```

---

### 6.2 · `PUT /api/v1/contact/{id}`

Full update of an existing Contact page. The service:

1. Looks up the row by `id` (404 `EntityNotFoundException` if missing).
2. Re-runs slug validation (`validateSlugs`) — see section 9.
3. Replaces every CKB and KMR field, re-running `TiptapHtmlProcessor.process(...)` over each description.
4. Persists and returns the updated entity.

> Note: this is a full-replace style update. `active` is not part of the request DTO and is not modified here — it remains whatever it was before.

- **Auth:** Authenticated (any role)
- **Path params:**
  - `id` — `Long`
- **Content-Type:** `application/json`
- **Request body:** `ContactRequest` (see section 7.1)
- **Returns:** `ApiResponse<ContactResponse>` with HTTP `200 OK`

**Example request:** (same schema as `POST` — section 6.1)

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Contact page updated successfully",
  "data": {
    "id": 7,
    "slugCkb": "peywendi-pe-kirdin",
    "slugKmr": "peywendi-pe-kirin",
    "ckbContent": { "title": "پەیوەندیمان پێوە بکە", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "kmrContent": { "title": "Bi me re têkilî daynin", "subtitle": "...", "address": "...", "workingHours": "...", "description": "<p>…</p>" },
    "phone": "+964 770 123 4567",
    "secondaryPhone": "+964 750 987 6543",
    "email": "info@khi.example",
    "mapEmbedUrl": "https://www.google.com/maps/embed?pb=…",
    "latitude": 36.1911,
    "longitude": 44.0094,
    "active": true,
    "createdAt": "2026-01-15 09:24:11",
    "updatedAt": "2026-05-31 10:18:02"
  }
}
```

---

### 6.3 · `DELETE /api/v1/contact/{id}`

Hard-delete the Contact page row.

- **Auth:** Authenticated (any role)
- **Path params:**
  - `id` — `Long`
- **Returns:** `ApiResponse<Void>` with HTTP `200 OK`

**Example response — `200 OK`:**

```json
{
  "success": true,
  "message": "Contact page deleted successfully",
  "data": null
}
```

---

## 07 · DTO Reference

All DTOs are nested inside `ak.dev.khi_backend.khi_app.dto.contact.ContactDTOs`.

### 7.1 · `ContactDTOs.ContactRequest`

`@Data @NoArgsConstructor @AllArgsConstructor @Builder`

| Field            | Type                     | Required | Notes                                                                                                  |
| ---------------- | ------------------------ | -------- | ------------------------------------------------------------------------------------------------------ |
| `slugCkb`        | `String`                 | Yes      | Sorani URL slug. Service enforces non-blank and uniqueness (`CKB slug is required`, `CKB slug already exists`). |
| `slugKmr`        | `String`                 | No       | Kurmanji URL slug. Unique when present; must differ from `slugCkb`.                                    |
| `ckbContent`     | `ContactContentRequest`  | No       | Sorani per-language block. `null` is replaced with an empty `ContactContent`.                          |
| `kmrContent`     | `ContactContentRequest`  | No       | Kurmanji per-language block. `null` is replaced with an empty `ContactContent`.                        |
| `phone`          | `String`                 | No       | Trimmed; blank becomes `null`.                                                                         |
| `secondaryPhone` | `String`                 | No       | Trimmed; blank becomes `null`.                                                                         |
| `email`          | `String`                 | No       | Trimmed; blank becomes `null`.                                                                         |
| `mapEmbedUrl`    | `String`                 | No       | Trimmed; blank becomes `null`. Stored in a `TEXT` column.                                              |
| `latitude`       | `Double`                 | No       | Passed through verbatim.                                                                               |
| `longitude`      | `Double`                 | No       | Passed through verbatim.                                                                               |

### 7.2 · `ContactDTOs.ContactContentRequest`

`@Data @NoArgsConstructor @AllArgsConstructor @Builder`

| Field          | Type     | Required | Notes                                                                                                                                                                                  |
| -------------- | -------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `title`        | `String` | No       | Max length 300 in DB.                                                                                                                                                                  |
| `subtitle`     | `String` | No       | Max length 500 in DB.                                                                                                                                                                  |
| `address`      | `String` | No       | Max length 500 in DB.                                                                                                                                                                  |
| `workingHours` | `String` | No       | Max length 300 in DB.                                                                                                                                                                  |
| `description`  | `String` | No       | Tiptap HTML. Passed through `TiptapHtmlProcessor.process(...)`, which hoists any inline base64 media to S3 and rewrites the markup. Stored as `TEXT`. |

### 7.3 · `ContactDTOs.ContactResponse`

`@Data @NoArgsConstructor @AllArgsConstructor @Builder`

| Field            | Type                       | Notes                                                                                       |
| ---------------- | -------------------------- | ------------------------------------------------------------------------------------------- |
| `id`             | `Long`                     | Primary key.                                                                                |
| `slugCkb`        | `String`                   | Sorani slug.                                                                                |
| `slugKmr`        | `String`                   | Kurmanji slug — may be `null`.                                                              |
| `ckbContent`     | `ContactContentResponse`   | Sorani per-language block — may be `null` when the embeddable is unset.                     |
| `kmrContent`     | `ContactContentResponse`   | Kurmanji per-language block — may be `null` when the embeddable is unset.                   |
| `phone`          | `String`                   | Primary phone.                                                                              |
| `secondaryPhone` | `String`                   | Secondary phone.                                                                            |
| `email`          | `String`                   | Primary email.                                                                              |
| `mapEmbedUrl`    | `String`                   | Map iframe URL.                                                                             |
| `latitude`       | `Double`                   | Latitude.                                                                                   |
| `longitude`      | `Double`                   | Longitude.                                                                                  |
| `active`         | `boolean`                  | Publication flag.                                                                           |
| `createdAt`      | `String`                   | Formatted via `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`.                          |
| `updatedAt`      | `String`                   | Formatted via `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`.                          |

### 7.4 · `ContactDTOs.ContactContentResponse`

`@Data @NoArgsConstructor @AllArgsConstructor @Builder`

| Field          | Type     | Notes                                  |
| -------------- | -------- | -------------------------------------- |
| `title`        | `String` | Page title for this language.          |
| `subtitle`     | `String` | Page subtitle for this language.       |
| `address`      | `String` | Physical address for this language.    |
| `workingHours` | `String` | Working hours for this language.       |
| `description`  | `String` | Tiptap HTML body for this language.    |

---

## 08 · Response Envelope

Every endpoint wraps its payload in `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Contact pages fetched",
  "data": { /* T — list, object, or null */ }
}
```

| Field     | Type      | Meaning                                                                                       |
| --------- | --------- | --------------------------------------------------------------------------------------------- |
| `success` | `boolean` | `true` for any 2xx response from this controller (`ApiResponse.success(...)`).                |
| `message` | `String`  | Human-readable status string — e.g. `"Contact page fetched"`, `"Contact page created successfully"`. |
| `data`    | `T`       | Endpoint-specific payload. `null` for delete responses (`ApiResponse<Void>`).                 |

The controller invokes `ApiResponse.success(data, message)` for every 2xx path; errors are produced by the global exception handler (see section 9).

---

## 09 · Error Responses

Errors are produced by the project's global `@ControllerAdvice` and serialized as a bilingual `ApiErrorResponse` envelope:

```json
{
  "timestamp": "2026-05-31T10:14:55.221Z",
  "status": 404,
  "path": "/api/v1/contact/999",
  "method": "GET",
  "traceId": "9f3d8b1a-2c4e-4a7c-b5f8-1d9e2a4f6c0b",
  "code": "NOT_FOUND",
  "message": "Contact not found: 999",
  "messageEn": "Contact not found: 999",
  "messageKu": "پەڕەی پەیوەندی نەدۆزرایەوە: 999",
  "fieldErrors": null,
  "details": null
}
```

| Field         | Type                     | Notes                                                                                  |
| ------------- | ------------------------ | -------------------------------------------------------------------------------------- |
| `timestamp`   | `String` (ISO-8601 UTC)  | Time the error was emitted.                                                            |
| `status`      | `int`                    | HTTP status code.                                                                      |
| `path`        | `String`                 | Request URI.                                                                           |
| `method`      | `String`                 | HTTP method.                                                                           |
| `traceId`     | `String`                 | Correlation ID for log lookup.                                                         |
| `code`        | `String`                 | Stable machine-readable code — see common codes below.                                 |
| `message`     | `String`                 | Default human-readable message.                                                        |
| `messageEn`   | `String`                 | English translation.                                                                   |
| `messageKu`   | `String`                 | Kurdish translation.                                                                   |
| `fieldErrors` | `List<FieldError>`/`null`| Per-field validation errors, when applicable.                                          |
| `details`     | `Object` / `null`        | Optional extra context.                                                                |

### Common codes for this module

| HTTP | Code               | When it fires                                                                                              |
| ---- | ------------------ | ---------------------------------------------------------------------------------------------------------- |
| 404  | `NOT_FOUND`        | `getById(id)` / `update(id, …)` / `delete(id)` cannot find the row (`EntityNotFoundException("Contact not found: …")`), or `getBySlug(slug)` cannot match (`"Contact page not found: …"`). |
| 409  | `CONFLICT`         | Duplicate slug. Service throws `IllegalArgumentException` with `"CKB slug already exists: …"` or `"KMR slug already exists: …"`, mapped to a conflict code by the global handler. |
| 400  | `VALIDATION_ERROR` | Missing `slugCkb` (`"CKB slug is required"`), or CKB equals KMR (`"CKB slug and KMR slug must be different: …"`). Also raised for bean-validation failures on the request payload. |
| 401  | `UNAUTHORIZED`     | Write endpoint hit without a valid JWT.                                                                    |

---

## 10 · Notes

- **Inline media:** Contact has no standalone hero or gallery field. The frontend uploads every image / video / audio / file once via the shared `POST /api/v1/media/upload`, then embeds the returned URL into the Tiptap editor.
- **Tiptap processing:** Each `description` is run through `TiptapHtmlProcessor.process(...)` on both create and update. Any base64 payload pasted into the editor is hoisted up to S3 before persistence, so what is stored in `description_ckb` / `description_kmr` is final, S3-referencing HTML.
- **Slug semantics:** `slugCkb` is required and globally unique. `slugKmr` is optional but unique when present and must differ from `slugCkb`. Both are trimmed before persistence; blank `slugKmr` is normalised to `null`.
- **Ordering:** `displayOrder` (default `0`) controls the `GET /active` ordering via `findAllByActiveTrueOrderByDisplayOrderAsc`. The field is not exposed in the request DTO — it is persisted only through whatever mechanism populates the entity directly.
- **Active flag:** `active` defaults to `true` on creation. The `ContactRequest` DTO does not carry it, so the `PUT` endpoint does not toggle it. Activation/deactivation requires a direct entity mutation or a separate endpoint (not present in this controller).
- **Timestamps:** `createdAt` / `updatedAt` are returned as `yyyy-MM-dd HH:mm:ss` strings (UTC offset implicit from the server clock).
