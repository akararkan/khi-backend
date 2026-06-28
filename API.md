# KHI Website — External API Requirements & Implementation Status

This document defines the REST API contract required by **khi-website** and
records its implementation status in **khi_backend**.

> **Backend status:** ✅ All backend endpoints and data fields requested by the
> original specification are implemented.
>
> **Verification:** ✅ `./mvnw test` passed on 28 June 2026:
> **21 tests, 0 failures, 0 errors, 0 skipped**.
>
> **Scope note:** Backend completion does not mean the separate frontend has
> been wired to every endpoint. Frontend integration and production
> infrastructure are tracked separately under
> [Remaining external work](#remaining-external-work-not-skipped-backend-requirements).

## Status legend

- ✅ Implemented and available in the backend
- 🧪 Covered by an automated contract/integration test
- ⚠️ Implemented, with an operational or cross-project caveat
- ⬜ Not implemented

## Table of contents

- [Configuration and conventions](#configuration-and-conventions)
- [Backend coverage summary](#backend-coverage-summary)
- [Required endpoint checklist](#required-endpoint-checklist)
- [Previously missing site features](#previously-missing-site-features)
- [Resolved integration mismatches](#resolved-integration-mismatches)
- [Security](#security)
- [Verification](#verification)
- [Remaining external work](#remaining-external-work-not-skipped-backend-requirements)
- [Source references](#source-references)

---

## Configuration and conventions

The following variables belong to the public website deployment, not this
Spring Boot backend:

| Variable | Required | Purpose | Backend status |
| --- | --- | --- | --- |
| `API_BASE_URL` | Production website | Backend base URL | ⚠️ Frontend environment setting |
| `USE_MOCK_DATA` | No | Enables frontend mock fallback | ⚠️ Frontend environment setting |
| `NEXT_PUBLIC_MEDIA_HOST` | Media | S3/CDN hostname | ⚠️ Frontend environment setting |
| `NEXT_PUBLIC_SITE_URL` | SEO | Canonical site origin | ⚠️ Frontend environment setting |

### Response envelope

✅ Backend responses use the supported envelope:

```json
{
  "success": true,
  "message": "Request completed",
  "data": {}
}
```

✅ List endpoints use Spring pagination where specified:

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20,
  "empty": true
}
```

✅ Pagination is zero-based and accepts `page` and `size`.

### Rich text

✅ All requested rich-text properties are persisted as strings and returned
through the API. Existing content modules support HTML/Tiptap content; plain
Markdown remains valid string content for clients that render Markdown.

Covered fields:

- ✅ About: `ckbContent.body`, `kmrContent.body`
- ✅ Contact: localized `description`
- ✅ News: localized `description`
- ✅ Projects: localized `description`
- ✅ Writings: localized `description`
- ✅ Sound tracks: localized `description`
- ✅ Videos: localized `description`
- ✅ Image collections: localized `description`
- ✅ Services: `contents[].description`
- ✅ Featured: `description`

---

## Backend coverage summary

| Area | Backend API | Required fields | Admin management | Status |
| --- | --- | --- | --- | --- |
| Featured homepage hero | Available | Complete | CRUD | ✅ |
| News | Available | Complete | Existing management | ✅ |
| Projects | Available | Complete | Existing management | ✅ |
| Writings | Available | Complete | Existing management | ✅ |
| Sound tracks | Available | Complete | Existing management | ✅ |
| Videos / short films | Available | Complete | Existing management | ✅ |
| Image collections | Available | Complete | Existing management | ✅ |
| About | Available | Complete, including extended media/founder fields | CRUD | ✅ |
| Team and partners | Available | Complete | CRUD | ✅ |
| Services | Available | Complete, including layout/media fields | CRUD | ✅ |
| Contact offices | Available | Complete, including hero/badge fields | CRUD | ✅ |
| Contact form | Submission + admin workflow | Complete | List/status | ✅ 🧪 |
| Social links | Available | Complete | CRUD | ✅ |
| Donation page/settings | Available | Complete | Settings update | ✅ |
| Financial donation form | Submission + admin workflow | Complete | List/status | ✅ 🧪 |
| Archive donation form | Submission + admin workflow | Complete | List/status | ✅ 🧪 |
| Global search | Available | Cross-content results | Read-only | ✅ |
| Sitemap data | Available | Locale-aware route list | Derived | ✅ 🧪 |
| Media upload/delete | Available | Existing contract | Admin only | ✅ |

---

## Required endpoint checklist

### 1. About

Endpoints:

- ✅ `GET /api/v1/about?page=&size=` — paginated public list
- ✅ `GET /api/v1/about/{identifier}` — accepts numeric ID or localized slug
- ✅ `GET /api/v1/about/slug/{slug}` — explicit slug lookup
- ✅ `POST /api/v1/about` — admin create
- ✅ `PUT /api/v1/about/{id}` — admin update
- ✅ `DELETE /api/v1/about/{id}` — admin delete

Original response requirements:

- ✅ `id`, `slugCkb`, `slugKmr`, `active`, `displayOrder`
- ✅ Localized `title`, `subtitle`, `body`, `metaDescription`
- ✅ Structured `stats[]`

Originally uncovered fields now implemented:

- ✅ Founder names and biographies in both locales
- ✅ `founderImageUrl`
- ✅ `heroVideoUrl`
- ✅ `heroPosterUrl`
- ✅ Team members through `/api/v1/about/team`
- ✅ Partner cards through `/api/v1/about/partners`

### 2. Contact

Endpoints:

- ✅ `GET /api/v1/contact/active?page=&size=` — active paginated offices
- ✅ `GET /api/v1/contact/{id}` — office detail
- ✅ `GET /api/v1/contact/slug/{slug}` — localized slug lookup
- ✅ `GET /api/v1/contact` — office list
- ✅ `POST /api/v1/contact` — admin create
- ✅ `PUT /api/v1/contact/{id}` — admin update
- ✅ `DELETE /api/v1/contact/{id}` — admin delete

Required fields:

- ✅ `id`, `active`, `displayOrder`, `slugCkb`, `slugKmr`
- ✅ `phone`, `secondaryPhone`, `email`
- ✅ `mapEmbedUrl`, `latitude`, `longitude`
- ✅ Localized `title`, `subtitle`, `address`, `workingHours`, `description`

Originally uncovered fields now implemented:

- ✅ `heroImageUrl`
- ✅ `officeType`
- ✅ Localized `badgeCkb` and `badgeKmr`
- ✅ Social platform links through `/api/v1/settings/social`
- ✅ Visitor submissions through `POST /api/v1/contact/messages`

### 3. News

Endpoints:

- ✅ `GET /api/v1/news?page=&size=`
- ✅ `GET /api/v1/news/{id}`
- ✅ `GET /api/v1/news/search?keyword=&page=&size=`
- ✅ Legacy search alias `q` remains supported
- ✅ Existing tag, keyword, category, and subcategory search endpoints

Required contract:

- ✅ IDs, covers, media type, publication date, languages
- ✅ Category and subcategory data
- ✅ Localized titles and descriptions
- ✅ Localized tags and keywords

### 4. Projects

Endpoints:

- ✅ `GET /api/v1/projects/getAll?page=&size=`
- ✅ `GET /api/v1/projects/{id}`
- ✅ `GET /api/v1/projects/search/tag?tag=&page=&size=`
- ✅ `GET /api/v1/projects/search/keyword?keyword=&page=&size=`
- ✅ Existing create, update, and delete operations

Required contract:

- ✅ ID, cover/media type, project date, status, languages
- ✅ Localized title, subtitle, description, and location
- ✅ Localized tags and keywords

### 5. Writings

Endpoints:

- ✅ `GET /api/v1/writings?page=&size=`
- ✅ `GET /api/v1/writings/{id}`
- ✅ `GET /api/v1/writings/series/{seriesId}`
- ✅ `GET /api/v1/writings/series/parents`
- ✅ Writer, tag, and keyword search
- ✅ `GET /api/v1/writings/topics`

Required contract:

- ✅ Localized covers, titles, writers, descriptions, and files
- ✅ File format and page count
- ✅ Genres, institute-published flag, tags, and keywords
- ✅ Series ID/name/order and series book summaries
- ✅ Topic ID and localized topic names
- ✅ Backend genre aliases/unknown values normalize to the website-compatible
  enum set

### 6. Sound tracks

Endpoints:

- ✅ `GET /api/v1/sound-tracks?page=&size=`
- ✅ `GET /api/v1/sound-tracks/{id}`
- ✅ `GET /api/v1/sound-tracks/album-of-memories`
- ✅ `GET /api/v1/sound-tracks/topics`
- ✅ Search by text, state, sound type, topic, tag, and keyword

Required contract:

- ✅ Sound type, track state, album-of-memories flag, covers, languages
- ✅ Localized titles and descriptions
- ✅ Playback files with direct/external/embed URLs
- ✅ File type, duration, brochures, captions, and brochure order
- ✅ Tags, keywords, topic details, and timestamps

### 7. Videos

Endpoints:

- ✅ `GET /api/v1/videos?page=&size=`
- ✅ Filters: `videoType`, `memories`, and `topicId`
- ✅ `GET /api/v1/videos/{id}`
- ✅ `GET /api/v1/videos/topics`
- ✅ Tag and keyword search

Required contract:

- ✅ Video type, memories flag, localized covers, hover cover
- ✅ Topic and language data
- ✅ Localized title, description, director, producer, and location
- ✅ Direct/external/embed playback URLs
- ✅ Video clip items
- ✅ Structured cast members and highlight clips
- ✅ Duration, publication date, tags, keywords, and timestamps

### 8. Image collections

Endpoints:

- ✅ `GET /api/v1/image-collections?page=&size=`
- ✅ Filters: `type` and `topicId`
- ✅ `GET /api/v1/image-collections/{id}`
- ✅ `GET /api/v1/image-collections/slug/{slug}`
- ✅ `GET /api/v1/image-collections/topics`

Required contract:

- ✅ Collection type, localized slugs/covers/content, languages
- ✅ Localized title, description, location, and collector
- ✅ Album images, localized captions, order, width, and height
- ✅ Publication date and topic details

### 9. Services

Endpoints:

- ✅ `GET /api/v1/services?page=&size=` — active public services
- ✅ `GET /api/v1/services/all?page=&size=` — compatibility path
- ✅ `GET /api/v1/services/admin/all?page=&size=` — admin list
- ✅ Detail, type, search, create, update, active-state, and delete operations

Required contract:

- ✅ ID, service type, location, active state, and localized contents
- ✅ Localized content language, title, and rich description

Originally uncovered fields now implemented:

- ✅ `layoutType`
- ✅ `heroVideoUrl` and `heroPosterUrl`
- ✅ `featureImageUrls`
- ✅ `thumbnailUrls`
- ✅ `partnerIds`
- ✅ `navAnchorId`

### 10. Featured homepage hero

Endpoints:

- ✅ `GET /featured?locale=` — exact legacy website path
- ✅ `GET /api/v1/featured?locale=` — versioned path
- ✅ Admin create, update, and delete under `/api/v1/featured`

Required contract:

- ✅ String `id`
- ✅ `type`, `slug`, `title`, `description`
- ✅ Nested `image.url` and optional `image.alt`
- ✅ Locale, display order, and active state

Featured content is explicitly curated in the database. The backend does not
silently replace an empty curated list with unrelated content.

### 11. Media

Endpoints:

- ✅ `POST /api/v1/media/upload`
- ✅ `POST /api/v1/media/upload/multiple`
- ✅ `DELETE /api/v1/media`

Media operations are protected admin operations. Public resources return media
URLs that the website can load from its configured media host.

---

## Previously missing site features

All endpoints suggested by the original document were added:

| Endpoint | Purpose | Status |
| --- | --- | --- |
| `GET /api/v1/featured?locale=` | Curated homepage hero | ✅ |
| `POST /api/v1/contact/messages` | Visitor contact form | ✅ 🧪 |
| `POST /api/v1/donations/financial` | Financial donation intent | ✅ 🧪 |
| `POST /api/v1/donations/archive` | Archive material offer | ✅ 🧪 |
| `GET /api/v1/settings/social` | Global social links | ✅ |
| `GET /api/v1/about/team` | Team members | ✅ |
| `GET /api/v1/about/partners` | Partner cards | ✅ |
| `GET /api/v1/search?q=&locale=` | Cross-content search | ✅ |

Additional supporting endpoints:

- ✅ Donation page configuration:
  `GET/PUT /api/v1/donations/settings`
- ✅ Enabled donation types: `GET /api/v1/donations/types`
- ✅ Admin financial donation list and status workflow
- ✅ Admin archive donation list and status workflow
- ✅ Admin contact-message list and status workflow
- ✅ Social-link CRUD
- ✅ Team-member CRUD
- ✅ Partner CRUD
- ✅ `GET /api/v1/sitemap?locale=` for derived website routes

---

## Resolved integration mismatches

| Original issue | Backend resolution | Status |
| --- | --- | --- |
| Services website path differed from backend | Both `/services` and `/services/all` are supported | ✅ |
| About list expected a different shape | Paginated response is available in the supported envelope | ✅ 🧪 |
| Contact active list expected a different shape | Paginated response is available in the supported envelope | ✅ 🧪 |
| About detail used a slug | Identifier route accepts ID or slug; explicit slug route also exists | ✅ |
| News used `q` while backend used `keyword` | Both parameters are accepted | ✅ |
| Featured endpoint did not exist | Legacy and versioned endpoints were added | ✅ 🧪 |
| Contact page had no submission endpoint | Contact-message workflow was added | ✅ 🧪 |
| Writing genre values could fail validation | Values are normalized to the public enum set | ✅ 🧪 |
| Gallery detail used a slug | Localized slugs and explicit slug lookup were added | ✅ |
| Video homepage filters were missing | `videoType`, `memories`, and `topicId` filters were added | ✅ |
| Services depended on mock layout/media | Layout, media, partner, and anchor fields were added | ✅ |

---

## Security

- ✅ Public GET content endpoints are accessible without authentication.
- ✅ Visitor contact and donation submission endpoints are public.
- ✅ Contact messages and donation records are readable only by
  `ADMIN`/`SUPER_ADMIN`.
- ✅ Status changes for contact and donation records require
  `ADMIN`/`SUPER_ADMIN`.
- ✅ Featured, team, partner, social, and donation-setting mutations require
  `ADMIN`/`SUPER_ADMIN`.
- ✅ Existing publication mutations retain employee/admin authorization.
- ✅ Media management remains restricted.

---

## Verification

Command:

```bash
./mvnw test
```

Result on 28 June 2026:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Contract integration tests directly verify:

- 🧪 Paginated About, active Contact, and Services response envelopes
- 🧪 Public legacy Featured endpoint
- 🧪 Locale-aware Sitemap endpoint
- 🧪 Visitor contact-message submission and initial status
- 🧪 Financial donation submission and initial status
- 🧪 Archive donation submission and initial status

The build also verifies application startup, security/token helpers, genre
normalization, S3 behavior, and media metadata extraction.

---

## Remaining external work (not skipped backend requirements)

The backend contract is complete. The following items require work outside this
backend specification or operational setup before a full production launch:

- ⚠️ **Frontend wiring:** The separate `khi-website` project must call the new
  Featured, Contact, Donation, Social, Team, Partner, Search, and Sitemap APIs
  and remove mock fallbacks where desired. This backend repository cannot
  verify that frontend work.
- ⚠️ **Production content:** Admin users must populate featured slides, team
  members, partners, social links, donation settings, offices, and other
  content. A correctly implemented API may return an empty list until data is
  entered.
- ⚠️ **Payment execution:** The financial donation endpoint records donation
  intent and payment references. It does not charge cards or integrate with a
  bank/payment gateway; the original requested endpoint was an intent form.
- ⚠️ **Notifications:** Contact and donation submissions are persisted and
  manageable by admins, but no outbound email/SMS notification provider was
  requested or configured.
- ⚠️ **Search locale behavior:** `locale` is accepted for website compatibility
  and bilingual fields are returned. Search currently searches the bilingual
  content together rather than restricting results to one locale.
- ⚠️ **Database migrations:** New entities use the project's existing Hibernate
  `ddl-auto: update` strategy. Versioned Flyway/Liquibase production migrations
  are not present.
- ⚠️ **Media infrastructure:** Production S3/CDN credentials, bucket policy,
  CORS, and frontend remote-image host configuration remain deployment tasks.

These caveats do not represent omitted endpoints from the original backend
requirements.

---

## Source references

| Location | Role |
| --- | --- |
| `src/main/java/.../api/` | REST controllers and endpoint mappings |
| `src/main/java/.../dto/` | Request/response contracts |
| `src/main/java/.../model/` | Persisted entities |
| `src/main/java/.../service/` | Business logic |
| `src/main/java/.../repository/` | Query and persistence layer |
| `src/main/java/.../user/configs/SecurityConfig.java` | Public/admin access rules |
| `src/test/java/.../PublicApiContractIntegrationTests.java` | Public API contract tests |
| `API_IMPLEMENTATION.md` | Short implementation summary |

---

*Last audited and updated: 28 June 2026*
