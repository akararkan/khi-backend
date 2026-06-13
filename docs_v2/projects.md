# Projects Module

> Bilingual (CKB / KMR) projects with Tiptap-aware JSON-only writes, cover image URLs, tags/keywords, and audit logs. Public reads, role-gated writes.

## Table of Contents

| #  | Section                                |
|----|----------------------------------------|
| 01 | Module Overview                        |
| 02 | Data Model — Project                   |
| 03 | Data Model — ProjectContentBlock       |
| 04 | Data Model — ProjectKeyword            |
| 05 | Data Model — ProjectTag                |
| 06 | Data Model — ProjectLog                |
| 07 | Enums                                  |
| 08 | Authentication & Roles                 |
| 09 | Public API                             |
| 10 | Internal API                           |
| 11 | DTO Reference                          |
| 12 | Response Envelope                      |
| 13 | Error Responses                        |
| 14 | Notes                                  |

---

## 01 · Module Overview

The Projects module exposes Tiptap-aware bilingual project resources. All write endpoints accept and return `application/json` only — the multipart upload path was removed during the Tiptap migration. The frontend uploads the cover image and any inline assets first via `POST /api/v1/media/upload`, then sends the resulting URLs in the JSON body (top-level `coverUrl`, plus inline `<img>` / `<video>` / `<audio>` tags embedded in the Tiptap HTML stored inside `ckbContent.description` / `kmrContent.description`).

| Attribute            | Value                                                                   |
|----------------------|-------------------------------------------------------------------------|
| Base path            | `/api/v1/projects`                                                      |
| Content type (write) | `application/json`                                                      |
| Content type (read)  | `application/json`                                                      |
| Envelope             | `ApiResponse<T>` — `{ success, message, data }`                         |
| Cache                | Spring `@Cacheable("projects")`; evicted on create / update / delete    |
| Auditing             | `ProjectLog` rows written on `CREATE` and `UPDATE`                      |
| Bilingual fields     | Suffixes `*Ckb` (Sorani) and `*Kmr` (Kurmanji)                          |
| Content storage      | Tiptap HTML stored in `description_ckb` / `description_kmr` TEXT columns |

### Endpoint Summary

| Method | Path                                       | Description                                  | Auth     | Roles                          |
|--------|--------------------------------------------|----------------------------------------------|----------|--------------------------------|
| GET    | `/api/v1/projects/getAll`                  | Paginated list of all projects               | Public   | —                              |
| GET    | `/api/v1/projects/search/tag`              | Paginated list filtered by tag name          | Public   | —                              |
| GET    | `/api/v1/projects/search/keyword`          | Paginated list filtered by keyword name      | Public   | —                              |
| POST   | `/api/v1/projects/create`                  | Create a new project                         | Required | EMPLOYEE, ADMIN, SUPER_ADMIN   |
| PUT    | `/api/v1/projects/update/{id}`             | Update an existing project                   | Required | EMPLOYEE, ADMIN, SUPER_ADMIN   |
| DELETE | `/api/v1/projects/delete/{id}`             | Delete a project (cascades audit logs)       | Required | ADMIN, SUPER_ADMIN             |

---

## 02 · Data Model — Project

JPA entity declared in `ak.dev.khi_backend.khi_app.model.project.Project`. Extends `AuditableEntity` (provides `createdAt`, `updatedAt`, `createdBy`, `updatedBy`).

```java
@Entity
@Table(
    name = "projects",
    indexes = {
        @Index(name = "idx_projects_type_ckb", columnList = "project_type_ckb"),
        @Index(name = "idx_projects_type_kmr", columnList = "project_type_kmr"),
        @Index(name = "idx_projects_status",   columnList = "status"),
        @Index(name = "idx_projects_date",     columnList = "project_date")
    }
)
```

### Fields

| Field               | Java Type                 | Column / Mapping                                                                                  | Notes                                                              |
|---------------------|---------------------------|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `id`                | `Long`                    | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`                                         | Primary key.                                                       |
| `coverUrl`          | `String`                  | `@Column(name = "cover_url", length = 1024)`                                                      | Cover asset URL (image / video / audio).                           |
| `coverMediaType`    | `MediaKind`               | `@Enumerated(EnumType.STRING) @Column(name = "cover_media_type", length = 16)` default `IMAGE`    | Discriminator for `coverUrl`.                                      |
| `coverThumbnailUrl` | `String`                  | `@Column(name = "cover_thumbnail_url", length = 1024)`                                            | Optional poster/cover-art URL.                                     |
| `mediaGallery`      | `List<MediaItem>`         | `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "media_gallery", columnDefinition = "jsonb")`        | Mixed-type gallery stored as JSONB.                                |
| `ckbContent`        | `ProjectContentBlock`     | `@Embedded` with overrides → `title_ckb`, `description_ckb` (TEXT), `location_ckb`                | Sorani content. `description` holds Tiptap HTML.                   |
| `kmrContent`        | `ProjectContentBlock`     | `@Embedded` with overrides → `title_kmr`, `description_kmr` (TEXT), `location_kmr`                | Kurmanji content. `description` holds Tiptap HTML.                 |
| `projectTypeCkb`    | `String`                  | `@Column(name = "project_type_ckb", length = 128)`                                                | Required when CKB language is present.                             |
| `projectTypeKmr`    | `String`                  | `@Column(name = "project_type_kmr", length = 128)`                                                | Required when KMR language is present.                             |
| `status`            | `ProjectStatus`           | `@Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)` default `ONGOING` | Lifecycle status.                                                  |
| `contentLanguages`  | `Set<Language>`           | `@ElementCollection` → table `project_content_languages` (`project_id`, `language` length 10)     | Controls which content blocks are required.                        |
| `tagsCkb`           | `Set<ProjectTag>`         | `@ManyToMany` join table `project_tag_map_ckb` (`project_id`, `tag_id`), `@BatchSize(50)`         | CKB-side tag attachments.                                          |
| `tagsKmr`           | `Set<ProjectTag>`         | `@ManyToMany` join table `project_tag_map_kmr` (`project_id`, `tag_id`), `@BatchSize(50)`         | KMR-side tag attachments.                                          |
| `keywordsCkb`       | `Set<ProjectKeyword>`     | `@ManyToMany` join table `project_keyword_map_ckb` (`project_id`, `keyword_id`), `@BatchSize(50)` | CKB-side keyword attachments.                                      |
| `keywordsKmr`       | `Set<ProjectKeyword>`     | `@ManyToMany` join table `project_keyword_map_kmr` (`project_id`, `keyword_id`), `@BatchSize(50)` | KMR-side keyword attachments.                                      |
| `projectDate`       | `LocalDate`               | `@Column(name = "project_date")`                                                                  | Free-form project date.                                            |
| inherited audit     | `Instant` / `String`      | `createdAt`, `updatedAt`, `createdBy`, `updatedBy` from `AuditableEntity`                         | Populated automatically.                                           |

> The legacy `project_media` table and the `contentsCkb` / `contentsKmr` string lists have been removed. Inline images / audio / video now live inside the Tiptap HTML stored in `ckbContent.description` and `kmrContent.description`. Uploads go through `POST /api/v1/media/upload`.

---

## 03 · Data Model — ProjectContentBlock

`@Embeddable` value object reused inside `Project` for each language.

```java
@Embeddable
public class ProjectContentBlock {
    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 255)
    private String location;
}
```

### Fields

| Field         | Java Type | Column / Mapping                                       | Notes                                                                                          |
|---------------|-----------|--------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `title`       | `String`  | `@Column(name = "title", length = 255)`                | Project title in the embedding language.                                                       |
| `description` | `String`  | `@Column(name = "description", columnDefinition = "TEXT")` | Tiptap HTML output. Sanitized server-side by `TiptapHtmlProcessor` before persistence.         |
| `location`    | `String`  | `@Column(name = "location", length = 255)`             | Project location in the embedding language.                                                    |

Column name overrides per language are supplied by the `@AttributeOverrides` block on `Project` (`title_ckb`, `description_ckb`, `location_ckb`, and the `_kmr` counterparts).

---

## 04 · Data Model — ProjectKeyword

Free-form, **normalized** keyword catalogue. Names are de-duplicated case-insensitively (`findByNameIgnoreCase`) and reused across projects.

```java
@Entity
@Table(
    name = "project_keywords",
    uniqueConstraints = @UniqueConstraint(name = "uq_project_keywords_name", columnNames = "name")
)
public class ProjectKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 191)
    private String name;
}
```

### Fields

| Field  | Java Type | Column / Mapping                                                  | Notes                                              |
|--------|-----------|-------------------------------------------------------------------|----------------------------------------------------|
| `id`   | `Long`    | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`         | Primary key.                                       |
| `name` | `String`  | `@Column(nullable = false, length = 191)` + UNIQUE `name`         | Globally unique keyword; shared across CKB / KMR.  |

---

## 05 · Data Model — ProjectTag

Free-form, **normalized** tag catalogue, mirroring `ProjectKeyword`.

```java
@Entity
@Table(
    name = "project_tags",
    uniqueConstraints = @UniqueConstraint(name = "uq_project_tags_name", columnNames = "name")
)
public class ProjectTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;
}
```

### Fields

| Field  | Java Type | Column / Mapping                                                  | Notes                                              |
|--------|-----------|-------------------------------------------------------------------|----------------------------------------------------|
| `id`   | `Long`    | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`         | Primary key.                                       |
| `name` | `String`  | `@Column(nullable = false, length = 128)` + UNIQUE `name`         | Globally unique tag; shared across CKB / KMR.      |

---

## 06 · Data Model — ProjectLog

Append-only audit row. `CREATE` and `UPDATE` write a `SUMMARY` line per service operation. Delete cascades remove the project's logs.

```java
@Entity
@Table(
    name = "project_log",
    indexes = {
        @Index(name = "idx_project_log_project_id", columnList = "project_id"),
        @Index(name = "idx_project_log_action",     columnList = "action"),
        @Index(name = "idx_project_log_created_at", columnList = "created_at")
    }
)
public class ProjectLog { ... }
```

### Fields

| Field       | Java Type        | Column / Mapping                                                                                                      | Notes                                                                                  |
|-------------|------------------|-----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `id`        | `Long`           | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`                                                             | Primary key.                                                                           |
| `project`   | `Project`        | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "project_id", nullable = false, foreignKey = "fk_project_log_project")` + `@OnDelete(action = CASCADE)` | Parent project. PostgreSQL emits `ON DELETE CASCADE` for the FK.                       |
| `action`    | `String`         | `@Column(nullable = false, length = 50)`                                                                              | `CREATE` / `UPDATE` / `DELETE` / `ADD_MEDIA` / `REMOVE_MEDIA` / …                       |
| `fieldName` | `String`         | `@Column(name = "field_name", length = 50)`                                                                           | Changed field name, or `SUMMARY` for service-level entries.                            |
| `oldValue`  | `String`         | `@Column(name = "old_value", columnDefinition = "text")`                                                              | Previous value (nullable).                                                             |
| `newValue`  | `String`         | `@Column(name = "new_value", columnDefinition = "text")`                                                              | New value (nullable).                                                                  |
| `createdAt` | `LocalDateTime`  | `@Column(name = "created_at", nullable = false)`                                                                      | Set by the service to `LocalDateTime.now()` at write time.                             |

---

## 07 · Enums

### `ak.dev.khi_backend.khi_app.enums.project.ProjectStatus`

```java
public enum ProjectStatus {
    ONGOING,   // بەردەوام
    COMPLETED  // تەواو
}
```

| Value       | Meaning (CKB)   | Description                                  |
|-------------|-----------------|----------------------------------------------|
| `ONGOING`   | بەردەوام        | Project is in progress. Default on create.   |
| `COMPLETED` | تەواو           | Project is finished.                         |

### `ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType`

```java
public enum ProjectMediaType {
    IMAGE,      // -> testweb/images/
    VIDEO,      // -> testweb/video/
    AUDIO,      // -> testweb/audio/
    DOCUMENT,   // -> testweb/files/
    PDF,        // -> testweb/files/
    TEXT        // -> testweb/files/
}
```

| Value      | Storage prefix    | Notes                                  |
|------------|-------------------|----------------------------------------|
| `IMAGE`    | `testweb/images/` | Raster / vector images.                |
| `VIDEO`    | `testweb/video/`  | Video assets.                          |
| `AUDIO`    | `testweb/audio/`  | Audio assets.                          |
| `DOCUMENT` | `testweb/files/`  | Generic documents.                     |
| `PDF`      | `testweb/files/`  | PDF documents.                         |
| `TEXT`     | `testweb/files/`  | Plain-text files.                      |

> The Project entity itself uses the shared `MediaKind` enum (`IMAGE`, `VIDEO`, `AUDIO`, …) for `coverMediaType` and gallery items. `ProjectMediaType` is a legacy companion enum kept for storage routing.

### Related shared enum — `Language`

`Set<Language>` is used by `Project.contentLanguages`. The CKB / KMR symbols are the only members consumed by this module.

| Value | Meaning            |
|-------|--------------------|
| `CKB` | Sorani Kurdish.    |
| `KMR` | Kurmanji Kurdish.  |

---

## 08 · Authentication & Roles

Security rules are enforced globally by `SecurityConfig`. Auth uses `Authorization: Bearer <JWT>`.

| Method | Path                                  | Auth     | Allowed Roles                  |
|--------|---------------------------------------|----------|--------------------------------|
| GET    | `/api/v1/projects/getAll`             | Public   | —                              |
| GET    | `/api/v1/projects/search/tag`         | Public   | —                              |
| GET    | `/api/v1/projects/search/keyword`     | Public   | —                              |
| POST   | `/api/v1/projects/create`             | Required | EMPLOYEE, ADMIN, SUPER_ADMIN   |
| PUT    | `/api/v1/projects/update/{id}`        | Required | EMPLOYEE, ADMIN, SUPER_ADMIN   |
| DELETE | `/api/v1/projects/delete/{id}`        | Required | ADMIN, SUPER_ADMIN             |

Anonymous calls to a write endpoint return `401 UNAUTHORIZED`. Authenticated callers without the required role return `403 FORBIDDEN`.

---

## 09 · Public API

### 9.1 `GET /api/v1/projects/getAll`

Returns a paginated list of all projects ordered by the service's hydration ordering.

**Query parameters**

| Name   | Type | Required | Default | Description                                |
|--------|------|----------|---------|--------------------------------------------|
| `page` | int  | No       | `0`     | Zero-based page index.                     |
| `size` | int  | No       | `20`    | Page size.                                 |

**Success response** — `200 OK`

```json
{
  "success": true,
  "message": "Projects fetched successfully",
  "data": {
    "content": [
      {
        "id": 17,
        "coverUrl": "https://cdn.khi.dev/testweb/images/2026/cover-17.webp",
        "coverMediaType": "IMAGE",
        "coverThumbnailUrl": null,
        "mediaGallery": [],
        "projectTypeCkb": "بەرنامەی کۆمەڵایەتی",
        "projectTypeKmr": "Bernameya Civakî",
        "status": "ONGOING",
        "projectDate": "2026-04-12",
        "contentLanguages": ["CKB", "KMR"],
        "ckbContent": {
          "title": "پڕۆژەی پەروەردەی منداڵان",
          "description": "<p>ئامانجی پڕۆژەکە دابینکردنی دەرفەتی فێربوونە بۆ منداڵان لە گوندەکانی دەشتی هەولێر.</p>",
          "location": "هەولێر"
        },
        "kmrContent": {
          "title": "Projeya Perwerdeya Zarokan",
          "description": "<p>Armanca projeyê peydakirina derfetên fêrbûnê ye ji bo zarokên gundên deşta Hewlêrê.</p>",
          "location": "Hewlêr"
        },
        "tagsCkb": ["پەروەردە", "منداڵان"],
        "tagsKmr": ["perwerde", "zarok"],
        "keywordsCkb": ["خوێندن", "گوند"],
        "keywordsKmr": ["xwendin", "gund"],
        "createdAt": "2026-04-12T08:22:11Z",
        "updatedAt": null,
        "createdBy": "employee@khi.dev",
        "updatedBy": null
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
    "totalElements": 137,
    "totalPages": 7,
    "number": 0,
    "size": 20,
    "first": true,
    "last": false,
    "numberOfElements": 20,
    "empty": false
  }
}
```

**curl**

```bash
curl -sS "https://api.khi.dev/api/v1/projects/getAll?page=0&size=20"
```

---

### 9.2 `GET /api/v1/projects/search/tag`

Returns the page of projects that have the given tag attached on either the CKB or KMR side.

**Query parameters**

| Name   | Type   | Required | Default | Description                                              |
|--------|--------|----------|---------|----------------------------------------------------------|
| `tag`  | string | Yes      | —       | Tag name. Trimmed; `400` when blank.                     |
| `page` | int    | No       | `0`     | Zero-based page index.                                   |
| `size` | int    | No       | `20`    | Page size.                                               |

**Success response** — `200 OK`

```json
{
  "success": true,
  "message": "Search by tag completed",
  "data": {
    "content": [ /* ProjectResponse... */ ],
    "totalElements": 4,
    "totalPages": 1,
    "number": 0,
    "size": 20,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

**curl**

```bash
curl -sS "https://api.khi.dev/api/v1/projects/search/tag?tag=%D9%BE%DB%95%D8%B1%D9%88%DB%95%D8%B1%D8%AF%DB%95&page=0&size=20"
```

---

### 9.3 `GET /api/v1/projects/search/keyword`

Returns the page of projects that have the given keyword attached on either the CKB or KMR side.

**Query parameters**

| Name      | Type   | Required | Default | Description                                            |
|-----------|--------|----------|---------|--------------------------------------------------------|
| `keyword` | string | Yes      | —       | Keyword name. Trimmed; `400` when blank.               |
| `page`    | int    | No       | `0`     | Zero-based page index.                                 |
| `size`    | int    | No       | `20`    | Page size.                                             |

**Success response** — `200 OK`

```json
{
  "success": true,
  "message": "Search by keyword completed",
  "data": {
    "content": [ /* ProjectResponse... */ ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

**curl**

```bash
curl -sS "https://api.khi.dev/api/v1/projects/search/keyword?keyword=%D8%AE%D9%88%DB%8E%D9%86%D8%AF%D9%86&page=0&size=20"
```

---

## 10 · Internal API

All write endpoints require `Authorization: Bearer <JWT>` and produce `application/json`.

### 10.1 `POST /api/v1/projects/create`

Creates a new project. Body is bound with `@Valid @RequestBody ProjectCreateRequest`.

| Aspect       | Value                                  |
|--------------|----------------------------------------|
| Roles        | EMPLOYEE, ADMIN, SUPER_ADMIN           |
| Content type | `application/json`                     |
| Returns      | `201 CREATED` with `ProjectResponse`   |

**Request body** — `ProjectCreateRequest` (see §11).

**Sample request**

```json
{
  "coverUrl": "https://cdn.khi.dev/testweb/images/2026/cover-edu.webp",
  "coverMediaType": "IMAGE",
  "coverThumbnailUrl": null,
  "mediaGallery": [
    {
      "url": "https://cdn.khi.dev/testweb/images/2026/edu-1.webp",
      "kind": "IMAGE",
      "thumbnailUrl": null,
      "captionCkb": "وێنەی چالاکی فێرکاری",
      "captionKmr": "Wêneya çalakiya fêrkariyê",
      "sortOrder": 0
    }
  ],
  "projectTypeCkb": "بەرنامەی پەروەردە",
  "projectTypeKmr": "Bernameya Perwerdeyê",
  "status": "ONGOING",
  "contentLanguages": ["CKB", "KMR"],
  "projectDate": "2026-05-31",
  "ckbContent": {
    "title": "پڕۆژەی پەروەردەی منداڵان لە دەشتی هەولێر",
    "description": "<h2>دەستپێک</h2><p>ئەم پڕۆژەیە بۆ دابینکردنی دەرفەتی فێربوون بۆ منداڵانی گوندەکانە.</p><figure><img src=\"https://cdn.khi.dev/testweb/images/2026/edu-inline-1.webp\" alt=\"چالاکی فێرکاری\"/></figure>",
    "location": "هەولێر"
  },
  "kmrContent": {
    "title": "Projeya Perwerdeya Zarokan li Deşta Hewlêrê",
    "description": "<h2>Destpêk</h2><p>Ev proje ji bo peydakirina derfetên fêrbûnê ji bo zarokên gundan e.</p>",
    "location": "Hewlêr"
  },
  "tagsCkb": ["پەروەردە", "منداڵان"],
  "tagsKmr": ["perwerde", "zarok"],
  "keywordsCkb": ["خوێندن", "گوند"],
  "keywordsKmr": ["xwendin", "gund"]
}
```

**Sample response** — `201 CREATED`

```json
{
  "success": true,
  "message": "Project created successfully",
  "data": {
    "id": 412,
    "coverUrl": "https://cdn.khi.dev/testweb/images/2026/cover-edu.webp",
    "coverMediaType": "IMAGE",
    "coverThumbnailUrl": null,
    "mediaGallery": [
      {
        "url": "https://cdn.khi.dev/testweb/images/2026/edu-1.webp",
        "kind": "IMAGE",
        "thumbnailUrl": null,
        "captionCkb": "وێنەی چالاکی فێرکاری",
        "captionKmr": "Wêneya çalakiya fêrkariyê",
        "sortOrder": 0
      }
    ],
    "projectTypeCkb": "بەرنامەی پەروەردە",
    "projectTypeKmr": "Bernameya Perwerdeyê",
    "status": "ONGOING",
    "projectDate": "2026-05-31",
    "contentLanguages": ["CKB", "KMR"],
    "ckbContent": {
      "title": "پڕۆژەی پەروەردەی منداڵان لە دەشتی هەولێر",
      "description": "<h2>دەستپێک</h2><p>...</p>",
      "location": "هەولێر"
    },
    "kmrContent": {
      "title": "Projeya Perwerdeya Zarokan li Deşta Hewlêrê",
      "description": "<h2>Destpêk</h2><p>...</p>",
      "location": "Hewlêr"
    },
    "tagsCkb": ["پەروەردە", "منداڵان"],
    "tagsKmr": ["perwerde", "zarok"],
    "keywordsCkb": ["خوێندن", "گوند"],
    "keywordsKmr": ["xwendin", "gund"],
    "createdAt": "2026-05-31T09:14:02Z",
    "updatedAt": null,
    "createdBy": "employee@khi.dev",
    "updatedBy": null
  }
}
```

**curl**

```bash
curl -sS -X POST "https://api.khi.dev/api/v1/projects/create" \
  -H "Authorization: Bearer ${JWT}" \
  -H "Content-Type: application/json" \
  --data @project.json
```

---

### 10.2 `PUT /api/v1/projects/update/{id}`

Replaces the project's fields with the supplied payload. Tags / keywords are cleared and re-attached from the request. Body is bound with `@Valid @RequestBody ProjectCreateRequest`.

| Aspect       | Value                                  |
|--------------|----------------------------------------|
| Roles        | EMPLOYEE, ADMIN, SUPER_ADMIN           |
| Path params  | `id` — project primary key (`Long`)    |
| Content type | `application/json`                     |
| Returns      | `200 OK` with `ProjectResponse`        |

**Sample request** — same shape as §10.1.

**Sample response** — `200 OK`

```json
{
  "success": true,
  "message": "Project updated successfully",
  "data": {
    "id": 412,
    "coverUrl": "https://cdn.khi.dev/testweb/images/2026/cover-edu-v2.webp",
    "coverMediaType": "IMAGE",
    "status": "COMPLETED",
    "projectDate": "2026-05-31",
    "contentLanguages": ["CKB"],
    "ckbContent": {
      "title": "پڕۆژەی پەروەردەی منداڵان (نوێکراوە)",
      "description": "<p>...</p>",
      "location": "هەولێر"
    },
    "kmrContent": null,
    "tagsCkb": ["پەروەردە"],
    "tagsKmr": [],
    "keywordsCkb": ["خوێندن"],
    "keywordsKmr": [],
    "createdAt": "2026-05-31T09:14:02Z",
    "updatedAt": "2026-05-31T10:02:55Z",
    "createdBy": "employee@khi.dev",
    "updatedBy": "admin@khi.dev"
  }
}
```

**curl**

```bash
curl -sS -X PUT "https://api.khi.dev/api/v1/projects/update/412" \
  -H "Authorization: Bearer ${JWT}" \
  -H "Content-Type: application/json" \
  --data @project.json
```

---

### 10.3 `DELETE /api/v1/projects/delete/{id}`

Deletes the project. Audit logs for the project are deleted first (`projectLogRepository.deleteByProject(...)`), then the project row itself; the `ON DELETE CASCADE` FK on `project_log` is the final safety net.

| Aspect       | Value                                  |
|--------------|----------------------------------------|
| Roles        | ADMIN, SUPER_ADMIN                     |
| Path params  | `id` — project primary key (`Long`)    |
| Content type | `application/json`                     |
| Returns      | `200 OK` with `data: null`             |

**Sample response** — `200 OK`

```json
{
  "success": true,
  "message": "Project deleted successfully",
  "data": null
}
```

**curl**

```bash
curl -sS -X DELETE "https://api.khi.dev/api/v1/projects/delete/412" \
  -H "Authorization: Bearer ${JWT}"
```

---

## 11 · DTO Reference

### 11.1 `ProjectCreateRequest`

Used for both create and update. Class-level validation (`@Valid` in the controller) triggers bean-validation on the annotated fields below; richer cross-field rules are enforced server-side by `ProjectService.validate(...)`.

| Field               | Type                       | Bean-validation annotation                                                                       | Required (server-side rule)                                       | Description                                                                                                                                  |
|---------------------|----------------------------|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `coverUrl`          | `String`                   | `@Size(max = 1024, message = "Cover URL must not exceed 1024 characters")`                       | **Yes** — `validate(...)` requires it (`coverRequired()`).        | URL returned by `POST /api/v1/media/upload`. Top-level cover for the project card.                                                            |
| `coverMediaType`    | `MediaKind`                | —                                                                                                 | No (defaults to `IMAGE` server-side).                             | Discriminator for `coverUrl`. Values: `IMAGE`, `VIDEO`, `AUDIO`.                                                                              |
| `coverThumbnailUrl` | `String`                   | `@Size(max = 1024, message = "Cover thumbnail URL must not exceed 1024 characters")`             | No                                                                | Optional poster (VIDEO) or cover art (AUDIO) URL.                                                                                            |
| `mediaGallery`      | `List<MediaItem>`          | —                                                                                                 | No                                                                | Mixed-type gallery rendered beside the cover. Each `MediaItem` carries `url`, `kind`, `thumbnailUrl`, `captionCkb`, `captionKmr`, `sortOrder`. |
| `projectTypeCkb`    | `String`                   | `@Size(max = 128, message = "CKB project type must not exceed 128 characters")`                  | **Yes when** `contentLanguages` contains `CKB` (`ckbTypeRequired()`). | CKB project type label.                                                                                                                      |
| `projectTypeKmr`    | `String`                   | `@Size(max = 128, message = "KMR project type must not exceed 128 characters")`                  | **Yes when** `contentLanguages` contains `KMR` (`kmrTypeRequired()`). | KMR project type label.                                                                                                                      |
| `status`            | `ProjectStatus`            | —                                                                                                 | No (defaults to `ONGOING` server-side).                           | `ONGOING` or `COMPLETED`.                                                                                                                    |
| `contentLanguages`  | `Set<Language>`            | `@NotEmpty(message = "At least one content language is required")`                               | **Yes**                                                           | Determines which content blocks (`ckbContent`, `kmrContent`) are required.                                                                   |
| `projectDate`       | `LocalDate`                | —                                                                                                 | No                                                                | Project date (ISO `YYYY-MM-DD`).                                                                                                             |
| `ckbContent`        | `ProjectContentBlock`      | —                                                                                                 | **Yes when** `contentLanguages` contains `CKB`. `title` must be non-blank (`ckbTitleRequired()`). | Sorani content. `description` accepts Tiptap HTML.                                                                                            |
| `kmrContent`        | `ProjectContentBlock`      | —                                                                                                 | **Yes when** `contentLanguages` contains `KMR`. `title` must be non-blank (`kmrTitleRequired()`). | Kurmanji content. `description` accepts Tiptap HTML.                                                                                          |
| `tagsCkb`           | `List<String>`             | —                                                                                                 | No                                                                | Free-form CKB tag names. Names are trimmed, lower-cased for lookup, and re-used if they already exist.                                       |
| `tagsKmr`           | `List<String>`             | —                                                                                                 | No                                                                | Free-form KMR tag names.                                                                                                                     |
| `keywordsCkb`       | `List<String>`             | —                                                                                                 | No                                                                | Free-form CKB keyword names.                                                                                                                 |
| `keywordsKmr`       | `List<String>`             | —                                                                                                 | No                                                                | Free-form KMR keyword names.                                                                                                                 |

#### Embedded — `ProjectContentBlock` (used for `ckbContent` / `kmrContent`)

| Field         | Type     | Notes                                                                                          |
|---------------|----------|------------------------------------------------------------------------------------------------|
| `title`       | `String` | Required for the language when that language is in `contentLanguages`.                         |
| `description` | `String` | Tiptap HTML. Processed by `TiptapHtmlProcessor` before persistence.                            |
| `location`    | `String` | Free-form location string in the language.                                                     |

#### Embedded — `MediaItem` (used for `mediaGallery`)

| Field          | Type        | Notes                                                                                |
|----------------|-------------|--------------------------------------------------------------------------------------|
| `url`          | `String`    | Required for an item to be retained (blank items are dropped server-side).           |
| `kind`         | `MediaKind` | Defaults to `IMAGE` if omitted.                                                      |
| `thumbnailUrl` | `String`    | Optional poster.                                                                     |
| `captionCkb`   | `String`    | Optional CKB caption.                                                                |
| `captionKmr`   | `String`    | Optional KMR caption.                                                                |
| `sortOrder`    | `Integer`   | Defaults to the item's incoming index if omitted; sorted ascending after build.      |

### 11.2 `ProjectResponse`

| Field               | Type                                  | Description                                                                          |
|---------------------|---------------------------------------|--------------------------------------------------------------------------------------|
| `id`                | `Long`                                | Project primary key.                                                                 |
| `coverUrl`          | `String`                              | Cover asset URL.                                                                     |
| `coverMediaType`    | `MediaKind`                           | Discriminator for `coverUrl`. Defaults to `IMAGE` in the response builder.           |
| `coverThumbnailUrl` | `String`                              | Optional cover thumbnail / poster.                                                   |
| `mediaGallery`      | `List<MediaItem>`                     | Gallery items, in `sortOrder` ascending.                                             |
| `projectTypeCkb`    | `String`                              | CKB project type label.                                                              |
| `projectTypeKmr`    | `String`                              | KMR project type label.                                                              |
| `status`            | `ProjectStatus`                       | `ONGOING` or `COMPLETED`.                                                            |
| `projectDate`       | `LocalDate`                           | Project date.                                                                        |
| `contentLanguages`  | `Set<Language>`                       | Languages declared on the project.                                                   |
| `ckbContent`        | `ProjectResponse.ProjectContentBlockDto` | CKB title / Tiptap HTML / location. `null` when CKB is not present.                  |
| `kmrContent`        | `ProjectResponse.ProjectContentBlockDto` | KMR title / Tiptap HTML / location. `null` when KMR is not present.                  |
| `tagsCkb`           | `List<String>`                        | CKB tag names.                                                                       |
| `tagsKmr`           | `List<String>`                        | KMR tag names.                                                                       |
| `keywordsCkb`       | `List<String>`                        | CKB keyword names.                                                                   |
| `keywordsKmr`       | `List<String>`                        | KMR keyword names.                                                                   |
| `createdAt`         | `Instant`                             | Creation timestamp (converted from the entity's `createdAt`).                        |
| `updatedAt`         | `Instant`                             | Last update timestamp.                                                               |
| `createdBy`         | `String`                              | Username / email of the creator.                                                     |
| `updatedBy`         | `String`                              | Username / email of the last updater.                                                |

#### Nested — `ProjectResponse.ProjectContentBlockDto`

| Field         | Type     | Description                                |
|---------------|----------|--------------------------------------------|
| `title`       | `String` | Localized title.                           |
| `description` | `String` | Tiptap HTML output.                        |
| `location`    | `String` | Localized location.                        |

---

## 12 · Response Envelope

Every controller method returns `ResponseEntity<ApiResponse<T>>`:

```json
{
  "success": true,
  "message": "Human-readable status",
  "data": { /* T or null */ }
}
```

### Spring Data `Page<T>` shape (used by the list / search endpoints)

```json
{
  "content": [ /* T... */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": false, "unsorted": true, "empty": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 0,
  "totalPages": 0,
  "last": true,
  "first": true,
  "number": 0,
  "size": 20,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "numberOfElements": 0,
  "empty": true
}
```

---

## 13 · Error Responses

Errors flow through the global handlers and ship as a bilingual `ApiErrorResponse`. Generic shape:

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "messageCkb": "پڕۆژەکە نەدۆزرایەوە",
  "messageKmr": "Proje nehate dîtin",
  "status": 404,
  "traceId": "8b6f3b3d-0e91-4f78-9c4c-1c3f0d9b9b62",
  "fieldErrors": null,
  "timestamp": "2026-05-31T10:04:11Z"
}
```

### Common codes

| HTTP status | Code                     | When                                                                                                |
|-------------|--------------------------|------------------------------------------------------------------------------------------------------|
| 400         | `VALIDATION_ERROR`       | Bean-validation failure on the request body (e.g. blank `contentLanguages`, oversized `coverUrl`).   |
| 400         | `PROJECT_VALIDATION`     | Service-level cross-field rule failed (missing CKB/KMR type, title, or cover URL).                   |
| 400         | `PROJECT_MEDIA_INVALID`  | Gallery / cover URL payload could not be processed.                                                  |
| 401         | `UNAUTHORIZED`           | Missing or invalid JWT on a write endpoint.                                                          |
| 403         | `FORBIDDEN`              | Authenticated, but role is not EMPLOYEE / ADMIN / SUPER_ADMIN (write) or ADMIN / SUPER_ADMIN (delete). |
| 404         | `PROJECT_NOT_FOUND`      | `findByIdWithGraph(id)` returned empty on update / delete.                                           |
| 409         | `PROJECT_CONFLICT`       | `DataIntegrityViolationException` on create / update (e.g. duplicate constraint).                    |
| 500         | `PROJECT_CREATE_FAILED` / `PROJECT_UPDATE_FAILED` / `PROJECT_DELETE_FAILED` | Unexpected service failure; see `traceId` in logs. |

`VALIDATION_ERROR` additionally populates `fieldErrors` with per-field bean-validation messages.

---

## 14 · Notes

- **Two-step uploads.** Inline media (images, audio, video) and the cover asset must be uploaded first via `POST /api/v1/media/upload`. The URLs returned by that endpoint are then written into the JSON body — `coverUrl` at the top level, and inline `<img>` / `<audio>` / `<video>` tags inside the Tiptap HTML carried by `ckbContent.description` and `kmrContent.description`.
- **`contentLanguages` drives required fields.** Including `CKB` makes `projectTypeCkb` and `ckbContent.title` required; including `KMR` makes `projectTypeKmr` and `kmrContent.title` required. Languages omitted from the set have their corresponding content block nulled on update.
- **Tags vs keywords — both free-form, both normalized.** Tags live in `project_tags` (`name` length 128, UNIQUE). Keywords live in `project_keywords` (`name` length 191, UNIQUE). For both, the service trims incoming names, looks them up case-insensitively (`findByNameIgnoreCase`), and creates the row only when missing. Each project carries separate CKB- and KMR-language attachments through dedicated join tables (`project_tag_map_ckb`, `project_tag_map_kmr`, `project_keyword_map_ckb`, `project_keyword_map_kmr`).
- **Tiptap sanitization.** `description` on both content blocks is passed through `TiptapHtmlProcessor.process(...)` before persistence to normalize and sanitize the HTML.
- **Auditing.** Every successful `create` and `update` writes one `ProjectLog` row with `action = "CREATE" | "UPDATE"`, `fieldName = "SUMMARY"`, and a human-readable `newValue`. Delete drops the project's log rows explicitly and the `ON DELETE CASCADE` FK guarantees DB-level cleanup as a backstop.
- **Caching.** All read endpoints are cached under the `projects` Spring cache region with keys derived from the operation and pagination. Every write (`create`, `update`, `delete`) evicts the entire region (`allEntries = true`).
- **Pagination defaults.** `page = 0`, `size = 20`. Search endpoints (`/search/tag`, `/search/keyword`) return `400 BAD_REQUEST` (`tag.required` / `keyword.required`) when the filter parameter is blank.
