# Publishment Topics Module (Taxonomy)

> Central topic registry shared by VIDEO, SOUND, IMAGE, WRITING content. One source of truth, mirrored under each consumer controller via `/topics` shortcut endpoints.

## Table of Contents

- [01 · Module Overview](#01--module-overview)
- [02 · Data Model — PublishmentTopic](#02--data-model--publishmenttopic)
- [03 · entityType Values](#03--entitytype-values)
- [04 · Authentication & Roles](#04--authentication--roles)
- [05 · Public API](#05--public-api)
- [06 · Internal API (Authenticated)](#06--internal-api-authenticated)
- [07 · DTO Reference](#07--dto-reference)
- [08 · Mirrored Endpoints](#08--mirrored-endpoints)
- [09 · Error Responses](#09--error-responses)
- [10 · Notes](#10--notes)

---

## 01 · Module Overview

The Publishment Topics module is the canonical taxonomy registry for every long-form content type in the platform. Instead of hard-coding an enum or relying on free-text labels per record, every publishment type (videos, sound tracks, image collections, writings) links to rows in the shared `publishment_topics` table. The `entity_type` discriminator column keeps each domain's topics isolated within the same physical table.

- **Base path:** `/api/v1/topics`
- **Bilingual fields:** `nameCkb` (Sorani / Central Kurdish) and `nameKmr` (Kurmanji / Northern Kurdish).
- **Discriminator (`entityType`):** `VIDEO` | `SOUND` | `IMAGE` | `WRITING`.
- **Response style:** raw `PublishmentTopic` entity (no `ApiResponse<T>` envelope on this controller).
- **Persistence:** Spring Data JPA, MySQL identity column.

### Endpoint Summary

| Method | Path                                    | Purpose                              | Auth          |
| ------ | --------------------------------------- | ------------------------------------ | ------------- |
| GET    | `/api/v1/topics/{entityType}`           | List all topics for an entity type   | Public        |
| GET    | `/api/v1/topics/{entityType}/{id}`      | Fetch a single topic by ID           | Public        |
| POST   | `/api/v1/topics/{entityType}`           | Create a new topic                   | Authenticated |
| PUT    | `/api/v1/topics/{id}`                   | Update an existing topic (bilingual) | Authenticated |
| DELETE | `/api/v1/topics/{id}`                   | Delete a topic                       | Authenticated |

---

## 02 · Data Model — PublishmentTopic

JPA entity → table `publishment_topics`.

| Field        | Java Type       | Column / Annotation                                              | Notes                                              |
| ------------ | --------------- | ---------------------------------------------------------------- | -------------------------------------------------- |
| `id`         | `Long`          | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`    | Primary key, auto-increment.                       |
| `entityType` | `String`        | `@Column(name = "entity_type", nullable = false, length = 20)`   | Discriminator: VIDEO/SOUND/IMAGE/WRITING.          |
| `nameCkb`    | `String`        | `@Column(name = "name_ckb", length = 300)`                       | Sorani display name.                               |
| `nameKmr`    | `String`        | `@Column(name = "name_kmr", length = 300)`                       | Kurmanji display name.                             |
| `createdAt`  | `LocalDateTime` | `@Column(name = "created_at", nullable = false, updatable = false)` | Set in `@PrePersist`.                              |
| `updatedAt`  | `LocalDateTime` | `@Column(name = "updated_at", nullable = false)`                 | Set in `@PrePersist` and refreshed in `@PreUpdate`. |

### Table-level configuration

```java
@Entity
@Table(
        name = "publishment_topics",
        indexes = {
                @Index(name = "idx_topic_entity_type", columnList = "entity_type"),
                @Index(name = "idx_topic_name_ckb",   columnList = "entity_type, name_ckb"),
                @Index(name = "idx_topic_name_kmr",   columnList = "entity_type, name_kmr")
        }
)
@BatchSize(size = 50)
public class PublishmentTopic { ... }
```

### Lifecycle hooks

| Hook          | Behaviour                                                          |
| ------------- | ------------------------------------------------------------------ |
| `@PrePersist` | Stamps `createdAt = updatedAt = LocalDateTime.now()` on insert.    |
| `@PreUpdate`  | Refreshes `updatedAt = LocalDateTime.now()` on every update.       |

### Service-layer normalization

`PublishmentTopicService` upper-cases the `entityType` argument on both **read** (`getAllByType`) and **write** (`create`, `findOrCreate`) paths, so `sound`, `Sound`, `SOUND` all resolve to the same `SOUND` partition.

---

## 03 · entityType Values

The `entity_type` column stores one of four uppercase strings. It is intentionally a `String` (not a JPA enum) so new content domains can be added without schema changes.

| Value     | Used by             | Owner controller                       |
| --------- | ------------------- | -------------------------------------- |
| `VIDEO`   | Videos              | `/api/v1/videos`                       |
| `SOUND`   | Sound Tracks        | `/api/v1/sound-tracks`                 |
| `IMAGE`   | Image Collections   | `/api/v1/image-collections`            |
| `WRITING` | Writings / Articles | `/api/v1/writings`                     |

---

## 04 · Authentication & Roles

Read access is open to the world (covered by the global `GET` rule in `SecurityConfig`). Write access requires only a valid authenticated session — no role check is applied to `/api/v1/topics/**` write methods, so they fall through to `.anyRequest().authenticated()`.

| Method | Path                               | Auth          | Roles      |
| ------ | ---------------------------------- | ------------- | ---------- |
| GET    | `/api/v1/topics/{entityType}`      | Public        | —          |
| GET    | `/api/v1/topics/{entityType}/{id}` | Public        | —          |
| POST   | `/api/v1/topics/{entityType}`      | Authenticated | Any role   |
| PUT    | `/api/v1/topics/{id}`              | Authenticated | Any role   |
| DELETE | `/api/v1/topics/{id}`              | Authenticated | Any role   |

> Writes do not require `ADMIN` / `EDITOR`. Any user holding a valid JWT can create, edit, or delete a topic. Lock this down at the security layer if stricter governance is required.

---

## 05 · Public API

### 5.1 · List Topics by Entity Type

```
GET /api/v1/topics/{entityType}
```

Returns every topic associated with the given publishment family. Internally the service calls `repository.findByEntityType(entityType.toUpperCase())`, so the discriminator is case-insensitive on input.

**Path parameters**

| Name         | Type     | Required | Description                                |
| ------------ | -------- | -------- | ------------------------------------------ |
| `entityType` | `String` | yes      | One of `VIDEO`, `SOUND`, `IMAGE`, `WRITING`. |

**Response 200** — `List<PublishmentTopic>`

```bash
curl -X GET "https://api.example.com/api/v1/topics/SOUND"
```

```json
[
  {
    "id": 12,
    "entityType": "SOUND",
    "nameCkb": "مۆسیقای فۆلکلۆری کوردی",
    "nameKmr": "Muzîka folklorî ya kurdî",
    "createdAt": "2026-04-11T09:14:32",
    "updatedAt": "2026-04-11T09:14:32"
  },
  {
    "id": 13,
    "entityType": "SOUND",
    "nameCkb": "گۆرانی نوێ",
    "nameKmr": "Stranên nû",
    "createdAt": "2026-04-12T15:02:08",
    "updatedAt": "2026-05-03T11:48:21"
  }
]
```

---

### 5.2 · Get Topic by ID

```
GET /api/v1/topics/{entityType}/{id}
```

> **Implementation note (verbatim from source):** the controller method `getById(@PathVariable String entityType, @PathVariable Long id)` delegates to `topicService.getById(id)` and **ignores the `entityType` path argument entirely**. The lookup is performed by ID alone; the `entityType` segment is therefore decorative and any value (even one that does not match the topic's actual `entityType`) will still return the row if the `id` exists.

**Path parameters**

| Name         | Type     | Required | Description                                                                 |
| ------------ | -------- | -------- | --------------------------------------------------------------------------- |
| `entityType` | `String` | yes      | Present in the path for symmetry / RESTful routing — **not validated**.     |
| `id`         | `Long`   | yes      | Primary key of the topic.                                                   |

**Response 200** — `PublishmentTopic`

```bash
curl -X GET "https://api.example.com/api/v1/topics/SOUND/12"
```

```json
{
  "id": 12,
  "entityType": "SOUND",
  "nameCkb": "مۆسیقای فۆلکلۆری کوردی",
  "nameKmr": "Muzîka folklorî ya kurdî",
  "createdAt": "2026-04-11T09:14:32",
  "updatedAt": "2026-04-11T09:14:32"
}
```

If the topic does not exist the service throws `RuntimeException("Topic not found: " + id)`, which is mapped by the global exception handler to a `404 NOT_FOUND` error envelope.

---

## 06 · Internal API (Authenticated)

All write endpoints expect a valid JWT bearer token. Request bodies use the inline `TopicRequest` record (see §07).

### 6.1 · Create Topic

```
POST /api/v1/topics/{entityType}
Content-Type: application/json
Authorization: Bearer <jwt>
```

**Path parameter**

| Name         | Type     | Required | Description                                              |
| ------------ | -------- | -------- | -------------------------------------------------------- |
| `entityType` | `String` | yes      | Target taxonomy bucket. Up-cased server-side.            |

**Body**

```json
{
  "nameCkb": "گۆرانی فۆلکلۆری ھەولێر",
  "nameKmr": "Stranên folklorî yên Hewlêrê"
}
```

**Response 201** — newly persisted `PublishmentTopic`.

```bash
curl -X POST "https://api.example.com/api/v1/topics/SOUND" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
     -H "Content-Type: application/json" \
     -d '{
           "nameCkb": "گۆرانی فۆلکلۆری ھەولێر",
           "nameKmr": "Stranên folklorî yên Hewlêrê"
         }'
```

```json
{
  "id": 47,
  "entityType": "SOUND",
  "nameCkb": "گۆرانی فۆلکلۆری ھەولێر",
  "nameKmr": "Stranên folklorî yên Hewlêrê",
  "createdAt": "2026-05-31T08:22:11",
  "updatedAt": "2026-05-31T08:22:11"
}
```

---

### 6.2 · Update Topic

```
PUT /api/v1/topics/{id}
Content-Type: application/json
Authorization: Bearer <jwt>
```

Both name fields are individually optional in the body — the service only overwrites fields that are non-null. The `entityType` cannot be changed via this endpoint.

**Path parameter**

| Name | Type   | Required | Description                  |
| ---- | ------ | -------- | ---------------------------- |
| `id` | `Long` | yes      | Primary key of the topic.    |

**Body**

```json
{
  "nameCkb": "مۆسیقای کلاسیکی کوردی",
  "nameKmr": "Muzîka klasîk a kurdî"
}
```

**Response 200** — updated `PublishmentTopic`.

```bash
curl -X PUT "https://api.example.com/api/v1/topics/12" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
     -H "Content-Type: application/json" \
     -d '{
           "nameCkb": "مۆسیقای کلاسیکی کوردی",
           "nameKmr": "Muzîka klasîk a kurdî"
         }'
```

```json
{
  "id": 12,
  "entityType": "SOUND",
  "nameCkb": "مۆسیقای کلاسیکی کوردی",
  "nameKmr": "Muzîka klasîk a kurdî",
  "createdAt": "2026-04-11T09:14:32",
  "updatedAt": "2026-05-31T08:24:55"
}
```

---

### 6.3 · Delete Topic

```
DELETE /api/v1/topics/{id}
Authorization: Bearer <jwt>
```

The service first checks `existsById` and throws `RuntimeException("Topic not found: " + id)` (→ `404`) if the topic is missing.

**Path parameter**

| Name | Type   | Required | Description                  |
| ---- | ------ | -------- | ---------------------------- |
| `id` | `Long` | yes      | Primary key of the topic.    |

**Response 204** — empty body.

```bash
curl -X DELETE "https://api.example.com/api/v1/topics/47" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

> Deleting a topic that is still referenced by videos, sound tracks, image collections, or writings will surface a database FK constraint violation, surfaced as a `409 CONFLICT` by the global error handler.

---

## 07 · DTO Reference

### TopicRequest

Defined as an inner `record` on `PublishmentTopicController`:

```java
public record TopicRequest(String nameCkb, String nameKmr) {}
```

| Field     | Type     | Required | Notes                                                                |
| --------- | -------- | -------- | -------------------------------------------------------------------- |
| `nameCkb` | `String` | optional | Sorani display name (max 300 chars, enforced at column level).       |
| `nameKmr` | `String` | optional | Kurmanji display name (max 300 chars, enforced at column level).     |

Either name may be omitted; on **update**, a `null` value is treated as "leave unchanged". On **create**, the entity persists whichever fields are supplied (no service-level validation enforces that at least one is non-null on this endpoint — only the internal `findOrCreate` helper validates that).

### Response shape — `PublishmentTopic`

The controller serializes the JPA entity directly. Consumers should expect the schema documented in §02:

```json
{
  "id": 12,
  "entityType": "SOUND",
  "nameCkb": "...",
  "nameKmr": "...",
  "createdAt": "2026-04-11T09:14:32",
  "updatedAt": "2026-04-11T09:14:32"
}
```

---

## 08 · Mirrored Endpoints

To keep frontends simple, every consumer controller re-exposes a shortcut for listing topics that belong to its own `entityType`. All shortcut endpoints query `PublishmentTopicRepository.findByEntityType(...)` — **single source of truth**.

| Domain            | Shortcut endpoint                          | Backing call                            |
| ----------------- | ------------------------------------------ | --------------------------------------- |
| Videos            | `GET /api/v1/videos/topics`                | `findByEntityType("VIDEO")`             |
| Sound Tracks      | `GET /api/v1/sound-tracks/topics`          | `findByEntityType("SOUND")`             |
| Image Collections | `GET /api/v1/image-collections/topics`     | `findByEntityType("IMAGE")`             |
| Writings          | `GET /api/v1/writings/topics`              | `findByEntityType("WRITING")`           |

In addition, the Videos controller also exposes write shortcuts under the same namespace:

| Method | Path                              | Equivalent canonical call                |
| ------ | --------------------------------- | ---------------------------------------- |
| POST   | `/api/v1/videos/topics`           | `POST /api/v1/topics/VIDEO`              |
| DELETE | `/api/v1/videos/topics/{id}`      | `DELETE /api/v1/topics/{id}`             |

> Use the canonical `/api/v1/topics` endpoints when you need full bilingual entity payloads or cross-type management. Use the mirrored shortcuts when you're already inside a domain's UI flow.

---

## 09 · Error Responses

Errors flow through the platform's global `ApiErrorResponse` envelope (bilingual messages). The topic module surfaces the following:

| HTTP | Code               | When                                                                                  |
| ---- | ------------------ | ------------------------------------------------------------------------------------- |
| 400  | `VALIDATION_ERROR` | Malformed JSON body or invalid `entityType` value supplied to the service layer.      |
| 401  | `UNAUTHORIZED`     | Missing / invalid JWT on `POST`, `PUT`, `DELETE`.                                     |
| 404  | `NOT_FOUND`        | `getById`, `update`, or `delete` could not locate the requested ID.                   |
| 409  | `CONFLICT`         | Deleting a topic still referenced by videos / sounds / images / writings (FK error). |

Example error envelope:

```json
{
  "success": false,
  "code": "NOT_FOUND",
  "status": 404,
  "messageCkb": "بابەتەکە نەدۆزرایەوە",
  "messageKmr": "Mijar nehat dîtin",
  "timestamp": "2026-05-31T08:31:14"
}
```

---

## 10 · Notes

- The shortcut `GET /<module>/topics` endpoints exposed by the four consumer controllers return a **simplified projection** `{ id, nameCkb, nameKmr }` — the `entityType` and audit timestamps are stripped because they are implied by the route.
- The canonical `GET /api/v1/topics/{entityType}` and `GET /api/v1/topics/{entityType}/{id}` endpoints return the **full `PublishmentTopic` entity** including `entityType`, `createdAt`, and `updatedAt`.
- `entityType` is stored as a `VARCHAR(20) NOT NULL` (`@Column(name = "entity_type", nullable = false, length = 20)`), **not** a JPA enum. New content domains can be added without a schema migration — only the discriminator string changes.
- The repository exposes only `findByEntityType(String)`; the Javadoc references autocomplete helpers (`searchByNameCkb`, `searchByNameKmr`) but those query methods are **not declared** on the interface as of this revision.
- The controller-layer `GET /{entityType}/{id}` endpoint accepts but **ignores** the `entityType` path variable — the lookup is by primary key alone. Callers should still pass the correct discriminator for URL hygiene and future-proofing.
- The service uppercases `entityType` on every read and write, so the discriminator is effectively case-insensitive at the API surface but always stored in upper case.
- `@BatchSize(size = 50)` on the entity optimises `@ManyToOne` lazy fetches when listing parent publishments (videos, sounds, …) that each reference a topic.
