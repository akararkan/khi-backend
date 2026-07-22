# Donate API — Internal (Admin)

**Base URL:** `/api/v1/donations`
**Platform:** Spring Boot 3 · PostgreSQL · JWT · Bilingual (CKB / KMR) · Paginated
**Created:** 2026-07-22 — implements **Donate - Backend API** spec.
**Scope:** Donations are **intent registrations** only. No payment gateway, checkout, webhooks, or money movement — donors transfer manually using the displayed FIB / FastPay numbers.

> **What the module does:** a singleton **settings** record (hero image + bank display numbers +
> enable toggles), two coarse **type toggles** (`FINANCIAL`, `ARCHIVE`), and two public **submission
> inboxes** (archive material offers + financial intents) with an admin review workflow.

---

## 🔄 Implementation notes (what was aligned to the spec)

| Area | Before | Now | Why |
|------|--------|-----|-----|
| **`email` on both forms** | `@NotBlank @Email` — **required** | **Optional** (`@Email` only). Null/blank stored as `""` | The site submits `email: ""`; the old rule **rejected every submission** with `400` |
| **Archive `title` / `description`** | `@NotBlank` — required | **Optional**; stored as `""` when omitted | Spec §3 marks both Optional |
| **`materialType`** | stored as-is, unvalidated | Validated (uppercased) against `PHOTOGRAPH · MANUSCRIPT · DOCUMENT · AUDIO · VIDEO · OTHER` → `400` otherwise | Spec §7 "must be valid enum" |
| **`currency`** | uppercased, unvalidated | Validated against `IQD · USD` → `400` otherwise | Spec §7 |
| **`phone`** | optional | **Kept optional** (site marks it required and always sends it) — flagged, not tightened | Avoids rejecting valid API clients |
| Endpoints / security | — | Already implemented & correct — see auth column below | — |

> **Storage:** `donation_settings` (singleton), `archive_donations`, `financial_donations` tables
> (Hibernate `ddl-auto: update`). `donation_types` is **derived** from the settings flags at read
> time — there is no `donation_types` table.

---

## Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/donations/settings` | Public | Page config: hero image, bank display numbers, enable flags |
| `GET` | `/api/v1/donations/types` | Public | Category toggles (`FINANCIAL`, `ARCHIVE`) |
| `POST` | `/api/v1/donations/financial` | Public | Submit a financial **intent** |
| `POST` | `/api/v1/donations/archive` | Public | Submit an archive material **offer** |
| `PUT` | `/api/v1/donations/settings` | `ADMIN` / `SUPER_ADMIN` | Create/update the singleton settings |
| `GET` | `/api/v1/donations/financial` | `ADMIN` / `SUPER_ADMIN` | List financial intents (paginated) |
| `GET` | `/api/v1/donations/archive` | `ADMIN` / `SUPER_ADMIN` | List archive offers (paginated) |
| `PATCH` | `/api/v1/donations/financial/{id}/status` | `ADMIN` / `SUPER_ADMIN` | Review workflow |
| `PATCH` | `/api/v1/donations/archive/{id}/status` | `ADMIN` / `SUPER_ADMIN` | Review workflow |

**Response envelope** (all endpoints):
```json
{ "success": true, "message": "…", "data": { /* payload */ } }
```
POST submissions return **`201 Created`**; all other endpoints return `200 OK`.

---

## 1. Settings (singleton)

### `GET /api/v1/donations/settings` — Public · `PUT /api/v1/donations/settings` — Admin

`PUT` is an upsert: it updates the single existing row or creates it on first call. Body = the fields below (no `id`). Both booleans default to `true` when omitted.

| Field | Type | Req. | Notes |
|-------|------|:----:|-------|
| `id` | number | — | Response only |
| `titleCkb` / `titleKmr` | string | — | Reserved for CMS-driven hero title (site uses i18n today) |
| `descriptionCkb` / `descriptionKmr` | string | — | Reserved for CMS-driven hero intro |
| `heroImageUrl` | string | rec. | Full URL for the hero background |
| `bankName` | string | — | Admin reference (not displayed yet) |
| `accountName` | string | — | Admin reference |
| `accountNumber` | string | rec. | Shown as **FIB account** in the closing section |
| `iban` | string | rec. | Shown as **FastPay number** (frontend maps `iban` → `fastpayNumber`) |
| `swiftCode` | string | — | Not displayed yet |
| `paymentInstructionsCkb` / `paymentInstructionsKmr` | string | — | Not displayed yet |
| `financialDonationsEnabled` | boolean | rec. | Default `true`. `false` hides the financial card + form and **blocks** `POST /financial` |
| `archiveDonationsEnabled` | boolean | rec. | Default `true`. `false` hides archive cards and **blocks** `POST /archive` |

**`GET` response:**
```json
{
  "success": true,
  "message": "Donation settings fetched",
  "data": {
    "id": 1,
    "titleCkb": "بەخشین", "titleKmr": "Bexşîn",
    "descriptionCkb": "…", "descriptionKmr": "…",
    "heroImageUrl": "https://cdn.example.com/donations/hero.jpg",
    "bankName": "First Iraqi Bank",
    "accountName": "Kurdish Heritage Institute",
    "accountNumber": "2345 8901 4567 1201",
    "iban": "0770 123 4567",
    "swiftCode": null,
    "paymentInstructionsCkb": null, "paymentInstructionsKmr": null,
    "financialDonationsEnabled": true,
    "archiveDonationsEnabled": true
  }
}
```

> When no settings row exists yet, `GET` returns a default object with both flags `true` and other fields null.

---

## 2. Types

### `GET /api/v1/donations/types` — Public

Derived from the settings flags (not a table). Always exactly two rows; `titleCkb`/`titleKmr` are placeholder defaults — the site renders type-card copy from i18n.

```json
{
  "success": true,
  "message": "Donation types fetched",
  "data": [
    { "code": "FINANCIAL", "titleCkb": "بەخشینی دارایی", "titleKmr": "Bexşîna aborî", "enabled": true },
    { "code": "ARCHIVE",   "titleCkb": "بەخشینی ئەرشیفی", "titleKmr": "Bexşîna arşîvê", "enabled": true }
  ]
}
```

| `code` | `enabled` mirrors | Frontend cards affected |
|--------|-------------------|-------------------------|
| `FINANCIAL` | `settings.financialDonationsEnabled` | `financial` |
| `ARCHIVE` | `settings.archiveDonationsEnabled` | `visualArchive`, `documents`, `oralHeritage`, `scientific` |

---

## 3. Financial donation intent

### `POST /api/v1/donations/financial` — Public

Blocked with `400` when `financialDonationsEnabled = false`.

| Field | Type | Req. | Notes |
|-------|------|:----:|-------|
| `donorName` | string | ✅ | Trimmed |
| `email` | string | — | Optional; `""` accepted. Must be a valid email **if** non-blank |
| `phone` | string | — | Optional |
| `amount` | number | ✅ | `>= 0.01` (BigDecimal) |
| `currency` | string | ✅ | `IQD` or `USD` (case-insensitive, stored uppercase) |
| `paymentMethod` | string | ✅ | Free text; site sends `BANK_TRANSFER` (both FIB & FastPay map to it) |
| `transactionReference` | string | — | Optional |
| `message` | string | — | Optional, ≤ 5000 chars |

**Request → Response `201`:**
```json
// request
{ "donorName": "Sara Mohammed", "email": "", "amount": 50000, "currency": "IQD", "paymentMethod": "BANK_TRANSFER" }
```
```json
// response
{
  "success": true,
  "message": "Financial donation received",
  "data": {
    "id": 200, "donorName": "Sara Mohammed", "email": "", "phone": null,
    "amount": 50000, "currency": "IQD", "paymentMethod": "BANK_TRANSFER",
    "transactionReference": null, "message": null,
    "status": "PENDING", "createdAt": "2026-07-22T10:00:00"
  }
}
```

### Admin
- `GET /api/v1/donations/financial?page=0&size=20` — paginated, **newest first** (`createdAt DESC`).
- `PATCH /api/v1/donations/financial/{id}/status` — body `{ "status": "APPROVED" }`.

---

## 4. Archive donation offer

### `POST /api/v1/donations/archive` — Public

Blocked with `400` when `archiveDonationsEnabled = false`.

| Field | Type | Req. | Notes |
|-------|------|:----:|-------|
| `donorName` | string | ✅ | Trimmed |
| `email` | string | — | Optional; `""` accepted |
| `phone` | string | — | Optional (site's form requires it) |
| `materialType` | enum | ✅ | `PHOTOGRAPH` · `MANUSCRIPT` · `DOCUMENT` · `AUDIO` · `VIDEO` · `OTHER` (case-insensitive) |
| `title` | string | — | Optional display/credit name, ≤ 500 chars |
| `description` | string | — | Optional free text, ≤ 10000 chars |
| `estimatedDate` | string | — | Optional (e.g. `"1950"`) |
| `attachmentUrl` | string | — | Optional URL (see file-upload gap below) |

**Request → Response `201`:**
```json
// request
{
  "donorName": "Ahmed Hassan", "email": "", "phone": "07701234567",
  "materialType": "PHOTOGRAPH",
  "title": "Historic Erbil family photo",
  "description": "Black-and-white photograph from the 1960s."
}
```
```json
// response
{
  "success": true,
  "message": "Archive donation offer received",
  "data": {
    "id": 201, "donorName": "Ahmed Hassan", "email": "", "phone": "07701234567",
    "materialType": "PHOTOGRAPH", "title": "Historic Erbil family photo",
    "description": "Black-and-white photograph from the 1960s.",
    "estimatedDate": null, "attachmentUrl": null,
    "status": "PENDING", "createdAt": "2026-07-22T10:00:00"
  }
}
```

### Admin
- `GET /api/v1/donations/archive?page=0&size=20` — paginated, newest first.
- `PATCH /api/v1/donations/archive/{id}/status` — body `{ "status": "APPROVED" }`.

**Paginated `data` shape** (both admin lists):
```json
{ "content": [ /* …Response[] */ ], "totalElements": 42, "totalPages": 3, "number": 0, "size": 20 }
```

---

## 5. Status workflow

`status` starts at **`PENDING`** on submit. `PATCH …/status` accepts (case-insensitive):

`NEW` · `PENDING` · `IN_REVIEW` · `APPROVED` · `COMPLETED` · `REJECTED` · `CLOSED`

The spec's primary workflow is `PENDING → APPROVED / REJECTED`; the extra values are shared with the
contact-message inbox and are accepted here too. An unsupported value returns `400`.

---

## 6. Validation & Errors

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `donorName` / `amount` / `currency` / `paymentMethod` / `materialType`; `amount < 0.01`; bad `currency` (not IQD/USD); bad `materialType`; malformed `email`; unsupported `status`; donations disabled for that type |
| `401 Unauthorized` | JWT missing/invalid on an admin endpoint |
| `403 Forbidden` | Authenticated user lacks `ADMIN` / `SUPER_ADMIN` |
| `404 Not Found` | No donation with the given `{id}` on a status update |
| `500 Internal Server Error` | Unexpected server-side failure |

Validation `400`s carry per-field errors; enum/state `400`s carry `details.reason`.

---

## 7. Known gaps / future (from the spec)

1. **File upload** — the archive form has a file field, but there is no multipart endpoint yet. `attachmentUrl` is accepted as a plain URL (pre-upload elsewhere, then submit). Wire a multipart or two-step upload when the frontend is ready.
2. **`email` always empty** — both forms omit it; now optional here.
3. **`iban` used for FastPay** — naming overload; a dedicated `fastpayNumber` field is a future extension.
4. **Payment-method granularity** — FIB and FastPay both arrive as `BANK_TRANSFER`; admin can't tell them apart. A `preferredPaymentChannel` field is a future extension.
5. **Localized settings titles** — stored (`titleCkb`/`descriptionCkb`…) but the site still uses i18n copy.
6. **Type cards** — content/images are static in the frontend; the API only toggles visibility.
