# Donate API — External (Public)

**Base URL:** `/api/v1/donations`
**Platform:** Spring Boot 3 · No Auth Required · Bilingual (CKB / KMR)
**Created:** 2026-07-22 — powers `/[locale]/donate` in khi-website.

> **Intent only.** Donations are **registrations of intent** — there is no payment processing,
> checkout, or gateway. Donors transfer funds manually using the FIB / FastPay numbers shown in the
> closing section (fetched from settings). These endpoints just record the offer/intent.

> **Response envelope.** Every response is `{ "success", "message", "data" }`. Read your payload
> from `data` when `success === true`.

---

## Locales

| URL | Notes |
|-----|-------|
| `/ckb/donate` | Sorani (Central Kurdish) |
| `/ku/donate` | Kurmanji |

Most page copy is i18n on the site. The backend supplies the **hero image**, **bank display
numbers**, **type visibility**, and stores **form submissions**.

---

## Endpoint Summary (public)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/donations/settings` | Hero image, bank display numbers, enable flags |
| `GET` | `/api/v1/donations/types` | Which donation categories are enabled |
| `POST` | `/api/v1/donations/archive` | Submit an archive material **offer** |
| `POST` | `/api/v1/donations/financial` | Submit a financial **intent** |

> Admin endpoints (settings write, submission inboxes, status updates) require an `ADMIN` token and
> are documented in the internal Donate API doc.

**Anchor targets** (hero CTAs / participation cards link to these):

| Element | ID |
|---------|-----|
| Archive form | `#archive-form` |
| Financial form | `#financial-form` |

---

## `GET /api/v1/donations/settings`

**Auth:** None.

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

| Field | Type | Description |
|-------|------|-------------|
| `heroImageUrl` | string | Hero background image URL (may be null → site uses mock) |
| `accountNumber` | string | Displayed as the **FIB account** number (copy-to-clipboard) |
| `iban` | string | Displayed as the **FastPay** number (frontend maps `iban` → `fastpayNumber`) |
| `financialDonationsEnabled` | boolean | `false` hides the financial card + form path |
| `archiveDonationsEnabled` | boolean | `false` hides the archive-related cards |
| `titleCkb` / `titleKmr` / `descriptionCkb` / `descriptionKmr` | string | Reserved for CMS copy (site uses i18n today) |
| `bankName` / `accountName` / `swiftCode` / `paymentInstructions*` | string | Not displayed yet (may be null) |

---

## `GET /api/v1/donations/types`

**Auth:** None. Returns exactly two category toggles.

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

**Visibility logic (frontend):**
- `archiveDonationsEnabled === false` → all archive cards hidden.
- `financialDonationsEnabled === false` → financial card hidden.
- Empty types array → all cards shown (fallback).
- Otherwise a card group shows only when its `code` has `enabled: true`.

> Card titles/descriptions come from i18n (`Donate.types.items.{id}.*`); the API's `titleCkb`/`titleKmr` are placeholders.

---

## `POST /api/v1/donations/archive`

**Auth:** None. **Content-Type:** `application/json`. Returns `201 Created`.

| Field | Type | Required | Notes |
|-------|------|:--------:|-------|
| `donorName` | string | ✅ | |
| `email` | string | — | Optional; send `""` if not collected. Must be valid if non-blank |
| `phone` | string | — | The form collects it; backend accepts it optionally |
| `materialType` | enum | ✅ | `PHOTOGRAPH` · `MANUSCRIPT` · `DOCUMENT` · `AUDIO` · `VIDEO` · `OTHER` |
| `title` | string | — | Optional display/credit name |
| `description` | string | — | Optional free-text note / brief history |
| `estimatedDate` | string | — | Optional (e.g. `"1950"`) |
| `attachmentUrl` | string | — | Optional URL (direct file upload not wired yet) |

**Frontend material-type mapping:** `cassetteAudio → AUDIO`, `photograph → PHOTOGRAPH`, `manuscript → MANUSCRIPT`, `document → DOCUMENT`, `video → VIDEO`, `other → OTHER`.

**Request → Response:**
```json
{ "donorName": "Ahmed Hassan", "email": "", "phone": "07701234567", "materialType": "PHOTOGRAPH",
  "title": "Historic Erbil family photo", "description": "Black-and-white photograph from the 1960s." }
```
```json
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

---

## `POST /api/v1/donations/financial`

**Auth:** None. **Content-Type:** `application/json`. Returns `201 Created`.

| Field | Type | Required | Notes |
|-------|------|:--------:|-------|
| `donorName` | string | ✅ | |
| `email` | string | — | Optional; send `""`. Must be valid if non-blank |
| `phone` | string | — | Optional |
| `amount` | number | ✅ | Must be `>= 0.01` |
| `currency` | string | ✅ | `IQD` or `USD` (uppercased) |
| `paymentMethod` | string | ✅ | Send `BANK_TRANSFER` (both FIB & FastPay map to it) |
| `transactionReference` | string | — | Optional |
| `message` | string | — | Optional, ≤ 5000 chars |

**Amount presets** (`25,000` / `50,000` / `100,000`) are static in the UI — not from the API. Any custom amount is allowed.

**Request → Response:**
```json
{ "donorName": "Sara Mohammed", "email": "", "amount": 50000, "currency": "IQD", "paymentMethod": "BANK_TRANSFER" }
```
```json
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

After success, the UI shows a confirmation and the closing section displays the FIB / FastPay numbers from settings for the manual transfer.

---

## How the site uses the API

1. `GET /donations/settings` → hero image, bank numbers, enable flags.
2. `GET /donations/types` → filter the type cards.
3. Render the page with i18n copy + API media/settings.
4. Archive form → `POST /donations/archive`.
5. Financial form → `POST /donations/financial`.
6. Closing section shows `accountNumber` (FIB) + `iban` (FastPay) — copy-to-clipboard.

**Dev fallback:** if the API is empty/unavailable, the site renders mock data from `src/lib/mock/donate.ts`.

---

## Error Responses

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing `donorName` / `amount` / `currency` / `paymentMethod` / `materialType`; `amount < 0.01`; `currency` not `IQD`/`USD`; invalid `materialType`; malformed `email`; the relevant donation type is disabled |
| `500 Internal Server Error` | Unexpected server-side failure |
