# Public website API implementation

The backend implements the contract described by `API.md`, including the
previously missing public-site features.

## Compatibility fixes

- `GET /api/v1/about?page=&size=` returns a paginated response.
- `GET /api/v1/about/{identifier}` accepts either an ID or localized slug.
- `GET /api/v1/contact/active?page=&size=` returns a paginated response.
- `GET /api/v1/news/search?keyword=&page=&size=` accepts the website parameter
  while retaining the legacy `q` alias.
- Both `GET /api/v1/services` and `GET /api/v1/services/all` expose active
  public services.
- `GET /api/v1/projects/{id}` exposes project detail.
- Image collections support `type`, `topicId`, numeric ID, and localized slug.
- Video lists support `videoType`, `memories`, and `topicId`.
- Writing genre aliases and unknown backend values are normalized to the public
  website's accepted genre set.

## Added public features

- Featured hero: `GET /featured` and `GET /api/v1/featured`
- Contact submissions: `POST /api/v1/contact/messages`
- Donation content and types: `GET /api/v1/donations/settings` and
  `GET /api/v1/donations/types`
- Donation submissions: `POST /api/v1/donations/financial` and
  `POST /api/v1/donations/archive`
- Social links: `GET /api/v1/settings/social`
- Team and partners: `GET /api/v1/about/team` and
  `GET /api/v1/about/partners`
- Derived sitemap: `GET /api/v1/sitemap?locale=`
- Cross-content search: `GET /api/v1/search?q=&locale=`

Administrative CRUD and status-management endpoints are available on the same
resource paths and are protected by `ADMIN`/`SUPER_ADMIN` security rules.

## Extended content

- About pages now support founder details, founder image, hero video, and
  poster.
- Contact offices now support hero image, office type, and localized badges.
- Services now support layout type, hero media, feature images, thumbnails,
  partner references, and navigation anchors.
- Videos now support structured cast members and highlight clips.
- Donation page copy and payment details are persisted.

Schema changes are managed by the project's existing Hibernate
`ddl-auto: update` configuration. Tests use H2 with PostgreSQL compatibility
mode and `create-drop`.
