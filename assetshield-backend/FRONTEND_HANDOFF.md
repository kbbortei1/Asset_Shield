# AssetShield GH — Frontend Handoff

Everything a React Native / Expo team needs to integrate the backend **without
asking the backend developer anything**. The companion artifacts:

- **Swagger** (live, dev): `http://<host>:8080/swagger-ui.html` — five-service dropdown.
- **Postman**: import `e2e/postman/AssetShield.postman_collection.json` + `e2e/postman/local.postman_environment.json`.
- **Demo data**: `demo/seed-demo.sh` seeds a full, demoable story.

---

## 1. Quick start

```bash
git clone <repo> && cd assetshield-backend
cp .env.example .env            # dev defaults work out of the box
docker compose --profile core up --build      # 7 containers, self-contained
# base URL for the app:
#   http://<host>:8080/api/v1
# swagger:    http://<host>:8080/swagger-ui.html
# seed demo:  ./demo/seed-demo.sh
```

Only the **gateway (8080)** is published. Everything is reachable under
`http://<host>:8080/api/v1/...`. On a phone/emulator use your machine's LAN IP,
not `localhost`. Dev mode is fully offline: `local` storage, `mock` payments,
`log` push, OTP returned as a fixed dev code.

---

## 2. Auth flow

```
register / register-agent  ──►  verify-otp (OTP_DEV_CODE=123456 in dev)  ──►  {accessToken, refreshToken}
login  ──►  {accessToken (1h), refreshToken (14d)}
```

- **Token storage**: keep both tokens in `expo-secure-store` (never `AsyncStorage`).
  Keep the access token in memory for requests; read refresh from SecureStore only
  when refreshing.
- **Access token** is a JWT (HS256), 1-hour lifetime; send as `Authorization: Bearer <access>`.
- **Refresh token** is an opaque 14-day token and is **rotated on every refresh**.
  Persist the new one immediately and discard the old. Reusing an old (rotated)
  refresh token returns **401 `REFRESH_REUSED`** and revokes the entire family —
  treat that as "session compromised → force re-login".

### 401-retry-once-after-refresh interceptor (pseudocode)

```ts
let refreshing: Promise<string> | null = null;

async function authedFetch(path, init = {}, retry = true) {
  const access = await getAccessToken();
  const res = await fetch(`${BASE}${path}`, withBearer(init, access));
  if (res.status !== 401 || !retry) return res;

  const body = await res.clone().json().catch(() => ({}));
  const code = body?.data?.errorCode;
  if (code === 'TOKEN_EXPIRED') {
    refreshing ??= doRefresh();           // single-flight; concurrent 401s share it
    try {
      const fresh = await refreshing;     // POST /auth/refresh { refreshToken }
      return authedFetch(path, init, false);   // retry exactly once
    } catch {
      await clearSession(); routeToLogin();      // refresh failed → re-login
      return res;
    } finally { refreshing = null; }
  }
  if (code === 'REFRESH_REUSED') { await clearSession(); routeToLogin(); }
  return res;   // TOKEN_INVALID / FORBIDDEN etc. are not retried
}
```

- **Logout**: `POST /auth/logout { refreshToken }` then wipe SecureStore + FCM token.
- **`OTP_REQUIRED`**: returned when a not-yet-verified user tries to log in — route the
  UI to the OTP screen and call `resend-otp` if needed.
- **`OTP_THROTTLED` / `RATE_LIMITED`**: the gateway throttles auth endpoints (per-IP,
  ~30/min). Back off and show "try again shortly".

---

## 3. Conventions

**Envelope** — every response (success or error):

```json
{ "status": "success|error", "data": { ... } | { "errorCode": "..." }, "message": "human text" }
```

On error, `data.errorCode` is the machine code; `message` is display-safe-ish but
prefer mapping `errorCode` → your own copy. **404-instead-of-403**: privacy-sensitive
resources return **404** (not 403) when you may not even learn they exist (e.g.
expressing interest in a property that isn't opted in, or any resource you don't own).

**Pagination** — list endpoints take `?page=&size=` and return:

```json
{ "items": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

**Timestamps** — ISO-8601 UTC (`2026-06-10T07:00:00Z`), microsecond precision.
**IDs** — UUID v4 strings.

### Error catalogue

| errorCode | HTTP | Where | UI handling |
|-----------|------|-------|-------------|
| `VALIDATION_FAILED` | 400 | all | Field errors; show inline. |
| `TOKEN_EXPIRED` | 401 | gateway | Refresh once, retry (see interceptor). |
| `TOKEN_INVALID` | 401 | gateway | Force re-login. |
| `REFRESH_INVALID` / `REFRESH_EXPIRED` | 401 | auth | Force re-login. |
| `REFRESH_REUSED` | 401 | auth | Session compromised → wipe + re-login. |
| `OTP_REQUIRED` | 401/403 | auth | Route to OTP screen. |
| `OTP_INVALID` / `OTP_EXPIRED` | 400 | auth | Ask to re-enter / resend. |
| `OTP_THROTTLED` | 429 | auth | Back off; disable resend briefly. |
| `BAD_CREDENTIALS` | 401 | auth | "Phone or password is incorrect" (don't reveal which). |
| `PHONE_EXISTS` | 409 | auth | "This number already has an account → log in". |
| `LICENCE_EXISTS` | 409 | auth/market | Agent NIC licence already registered. |
| `RATE_LIMITED` | 429 | gateway | Back off + retry. |
| `FORBIDDEN` | 403 | all | "You don't have access". |
| `RESOURCE_NOT_FOUND` | 404 | all | Generic not-found (also used for privacy 404). |
| `FREE_TIER_LIMIT` | 403 | property | Prompt PRO upgrade (FREE = 1 property). |
| `HASH_MISMATCH` | 400 | property/damage | Re-hash the exact bytes & re-upload (see §4). |
| `DUPLICATE_ASSET_HASH` | 409 | property | This exact photo is already documented (success-equivalent for an offline queue). |
| `DUPLICATE_PHOTO_HASH` | 409 | damage | Same, for damage photos. |
| `DUPLICATE_PENDING_INVITE` | 409 | property | Invite already pending. |
| `ALREADY_MEMBER` | 409 | property | Already in the household. |
| `ALREADY_RESPONDED` | 409 | property/market | Invite/interest already decided. |
| `NOT_OWNER` / `NOT_MEMBER` | 403/404 | property/damage | Not your resource. |
| `FILE_TOO_LARGE` | 413 | property/auth/damage | Compress; show size limit. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | property/auth/damage | Only image/jpeg|png. |
| `INVALID_STATE_TRANSITION` | 409 | damage | e.g. editing a COMPLETED report (immutable). |
| `EMPTY_REPORT` | 400 | damage | Add ≥1 photo before completing. |
| `PAYMENT_REQUIRED` | 402 | damage | Dossier not paid yet → run payment flow. |
| `DOSSIER_EXISTS` | 409 | damage | A dossier already exists for the report. |
| `GENERATION_IN_PROGRESS` | 409 | damage | Poll status; don't re-request. |
| `GENERATION_FAILED` | 409 | damage | Offer "retry generation" (paid). |
| `AGENT_NOT_VERIFIED` | 403 | market | Agent awaiting admin verification. |
| `SUBSCRIPTION_INACTIVE` | 403 | market | Agent must subscribe first. |
| `DUPLICATE_PENDING_INTEREST` | 409 | market | Interest already expressed. |
| `ALREADY_SHARED` / `ALREADY_DECIDED` | 409 | market | Already shared/decided. |
| `SHARE_REVOKED` | 403/404 | market | Owner revoked consent. |
| `PAYMENT_INIT_FAILED` | 502 | market | Payment provider error; retry. |
| `INTERNAL_ERROR` | 500 | all | Generic "something went wrong". |

---

## 4. Photo upload recipe (the critical one)

Every evidence/damage photo is integrity-checked: you send a `sha256Hash` and the
server recomputes it over the received bytes. **Mismatch → 400 `HASH_MISMATCH`.**

**The golden rule: hash the EXACT bytes you upload, hash LAST, upload immediately.**

```ts
import * as Crypto from 'expo-crypto';
import * as FileSystem from 'expo-file-system';

// 1. Capture/pick the image. If you resize/compress, do it NOW, before hashing.
//    Re-encoding (resize/compress) or an image picker's EXIF-stripping CHANGES the
//    bytes — anything you do AFTER hashing invalidates the hash.
const finalUri = await maybeResize(pickedUri);     // do all mutation here

// 2. Read the exact bytes and hash them.
const b64 = await FileSystem.readAsStringAsync(finalUri, { encoding: 'base64' });
const sha256 = await Crypto.digestStringAsync(
  Crypto.CryptoDigestAlgorithm.SHA256,
  b64,
  { encoding: Crypto.CryptoEncoding.HEX }   // NOTE: hashing the base64 string; see below
);
```

> ⚠️ **Hash the same representation the server hashes.** The server hashes the **raw
> binary bytes** of the uploaded file. With Expo, the reliable approach is to compute
> the SHA-256 over the **decoded bytes** (e.g. via `expo-crypto`'s array/Uint8Array
> digest of the base64-decoded buffer), not over the base64 *text*. Verify once
> against a known fixture: `e2e/fixtures/asset-1.jpg` →
> `a3ba97e55531671a32b6c36d9738a81c053b29b2a8b847a7e4812673f9f1fad0`. If your client
> hash matches that for that file, your pipeline is correct.

```ts
// 3. Multipart upload: `file` part + `metadata` JSON part. Upload immediately.
const form = new FormData();
form.append('file', { uri: finalUri, name: 'asset.jpg', type: 'image/jpeg' } as any);
form.append('metadata', JSON.stringify({
  sha256Hash: sha256,
  gpsLat: 5.546111, gpsLng: -0.211667,
  capturedAt: new Date().toISOString(),
  description: 'Sewing machine',
  estimatedValue: 1500.00,            // asset only (omit for damage photos)
  category: 'ELECTRONICS',            // asset only
}));

const res = await fetch(`${BASE}/properties/${propertyId}/assets`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${access}` },   // do NOT set Content-Type; let fetch set the boundary
  body: form,
});
```

- **Damage photos**: same multipart shape at `POST /damage-reports/{id}/photos`, metadata =
  `{ sha256Hash, gpsLat, gpsLng, capturedAt, description }` (no value/category).
- **⚠️ Response shapes differ — read the id from the right place:**
  - Asset upload → **flat**: `data.id`, `data.photoUrl`, `data.sha256Hash`, …
  - Damage photo upload → **nested**: `data.photo.id` (+ `data.photo.photoUrl`, …) plus
    `data.pairingSuggestions: [...]` — the GPS-matched documented assets you can pair the
    photo to. Reading `data.id` for a damage photo returns `undefined` (a common trip-up).
- **Pairing**: `POST /damage-reports/{id}/pairs { damagePhotoId, assetId, pairingMethod }`
  where `pairingMethod ∈ GPS_AUTO | MANUAL`. Each photo can be paired once.
- **409 `DUPLICATE_ASSET_HASH` / `DUPLICATE_PHOTO_HASH`**: the exact image is already
  stored (per property / per report). For a normal UX, tell the user "already added". For
  an **offline queue** (below) treat it as success.
- Only `image/jpeg` and `image/png` are accepted; oversize → 413 `FILE_TOO_LARGE`.

---

## 5. Offline queue contract

The app must let users document/capture without connectivity (FR19) and sync when
back online (target ~60s after connectivity returns).

- **Queue locally (SQLite)**: the file URI + the computed `sha256Hash` + metadata +
  the target endpoint. **Compute the hash at capture time** (while you still hold the
  exact bytes) and store it — never re-hash later.
- **Retry safety is built in**: uploads are idempotent by content hash. A retried
  upload whose photo already landed returns **409 DUPLICATE_*** — treat that as
  **success** and dequeue. So "send, and on 2xx *or* 409-duplicate, remove from queue".
- Surface unsynced count in the UI; flush on `NetInfo` "reachable".

---

## 6. Payment flow

Dossiers are payment-gated. Both an owner PRO subscription and the agent subscription
use the same pattern.

```
POST /damage-reports/{id}/generate-dossier
   → { dossierId, status: PENDING_PAYMENT, payment: { reference, authorizationUrl } }
open authorizationUrl in expo-web-browser  (Paystack checkout)
   ↳ on return:  POST /payments/{reference}/verify
poll GET /dossiers/{id}/status   (PENDING_PAYMENT → GENERATING → READY|FAILED)
   ↳ or await the DOSSIER_READY push, then:
GET /dossiers/{id}/download  → { downloadUrl, fileName }   (signed, ~15 min TTL)
```

- **mock mode (dev/demo)**: the payment **auto-settles ~2s** after generate; the
  `authorizationUrl` is a stub. Just poll status → it becomes READY without any browser
  step. `POST /payments/{reference}/verify` always returns SUCCESS.
- **paystack test mode**: use a Paystack **test card** in the WebBrowser; then call
  verify and poll. Real settlement also arrives via the server webhook.
- Before READY, `GET /dossiers/{id}/download` → **402 `PAYMENT_REQUIRED`**.

---

## 7. Push (FCM)

- **Register** the device token on login **and** whenever FCM rotates it:
  `PUT /users/me/device-token { fcmToken, platform: "ANDROID"|"IOS" }`.
- **Delete** on logout: `DELETE /users/me/device-token { fcmToken }`.
- **Preferences**: `GET/PUT /users/me/notification-preferences` —
  body is `{ tipsFrequency: "DAILY"|"WEEKLY"|"OFF" }`.
- In dev (`FCM_MODE=log`) nothing lands on a device — pushes are logged server-side;
  use `GET /users/me/notifications` to drive the in-app inbox.

Each notification carries a `type` and a string→string `payload` for deep-linking.
The **14 types** (the `NotificationType` enum) and their actual payload keys:

| type | recipient | payload keys | deep-link to |
|------|-----------|--------------|--------------|
| `TIP` | owner/member | `propertyId` (when property-specific) | tips feed |
| `REDOC_REMINDER` | owner | `propertyId` | property (re-document) |
| `DOSSIER_READY` | owner | `dossierId` | dossier detail / download |
| `HOUSEHOLD_INVITE` | invitee | `invitationId`, `propertyId` | invitations |
| `AGENT_INTEREST` | owner | `interestId`, `propertyId` | owner interests |
| `INTEREST_RESPONSE` | agent | `interestId`, `status` | agent interests |
| `INTEREST_REVOKED` | agent | `interestId`, `status` | agent interests |
| `SHARE_CREATED` | agent | `dossierId`, `shareId` | agent shared dossiers |
| `SHARE_REVOKED` | agent | `dossierId` | agent shared dossiers |
| `QUOTE_ISSUED` | owner | `quoteId` | owner quotes |
| `QUOTE_RESPONSE` | agent | `quoteId`, `status` | agent quotes |
| `AGENT_VERIFIED` | agent | `agentId`, `status` | agent home |
| `AGENT_REJECTED` | agent | `agentId`, `status` | agent home |
| `SUBSCRIPTION_EXPIRY` | agent | `subscriptionId` | subscription |

> Payload values are strings; treat keys defensively (presence can vary) and always
> handle a missing/unknown `type` by routing to the inbox (`GET /users/me/notifications`).
> `status` carries the enum name (e.g. `ACCEPTED`/`DECLINED`, `VERIFIED`/`REJECTED`).

---

## 8. Per-service endpoint tables

Auth column: **public** (no token) · **user** (any logged-in) · **owner/member/agent/admin**
(role- or ownership-gated; failures are 403 or privacy-404).

### Auth & Profile  (`auth-service`)
| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/auth/register` | public | Start registration (sends OTP) |
| POST | `/auth/register-agent` | public | Register an agent (PENDING) |
| POST | `/auth/verify-otp` | public | Verify OTP → tokens |
| POST | `/auth/resend-otp` | public | Resend OTP |
| POST | `/auth/login` | public | Login → tokens |
| POST | `/auth/refresh` | public | Rotate tokens |
| POST | `/auth/logout` | user | Revoke refresh family |
| GET | `/users/me` | user | My profile |
| PUT | `/users/me` | user | Update profile |
| POST | `/users/me/ghana-card` | user | Upload KYC card (multipart) |
| DELETE | `/users/me` | user | Erasure request |
| POST | `/admin/admins` | admin | Create an admin |

### Properties, Assets & Household  (`property-service`)
| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/properties` | owner | Create property |
| GET | `/properties` | user | List owned + member properties |
| GET | `/properties/{id}` | owner/member | Detail + dashboard |
| PUT | `/properties/{id}` | owner | Update |
| DELETE | `/properties/{id}` | owner | Soft delete |
| PUT | `/properties/{id}/offers-optin` | owner | Toggle marketplace opt-in |
| POST | `/properties/{id}/assets` | owner/member | Upload asset (multipart) |
| GET | `/properties/{id}/assets` | owner/member | List assets |
| POST | `/properties/{id}/invite` | owner | Invite member |
| GET | `/properties/{id}/members` | owner | List members |
| DELETE | `/properties/{id}/members/{userId}` | owner | Remove member |
| GET | `/assets/{id}` | owner/member | Asset detail |
| PUT | `/assets/{id}` | owner/member | Update asset |
| DELETE | `/assets/{id}` | owner/member | Delete asset |
| POST | `/assets/{id}/receipts` | owner/member | Upload receipt (multipart) |
| GET | `/users/me/invitations` | user | My invitations |
| PUT | `/invitations/{id}/respond` | user | Accept/decline invite |
| GET | `/public/files/{token}` | public | Token-gated download |

### Damage Reports & Dossiers  (`damage-service`)
| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/properties/{propertyId}/damage-reports` | owner/member | Open a report |
| GET | `/properties/{propertyId}/damage-reports` | owner/member | List reports |
| GET | `/users/me/damage-reports` | user | My reports |
| GET | `/damage-reports/{id}` | owner/member | Report detail |
| POST | `/damage-reports/{id}/photos` | owner/member | Upload damage photo (multipart) |
| GET | `/damage-reports/{id}/photos/{photoId}/pairing-suggestions` | owner/member | Re-run GPS pairing |
| POST | `/damage-reports/{id}/pairs` | owner/member | Pair photo↔asset |
| DELETE | `/damage-reports/{id}/pairs/{pairId}` | owner/member | Remove pair |
| PUT | `/damage-reports/{id}/complete` | owner/member | Freeze + compute loss |
| POST | `/damage-reports/{id}/generate-dossier` | owner | Start fee checkout |
| GET | `/dossiers/{id}/status` | owner | Poll status |
| GET | `/dossiers/{id}/download` | owner | Signed download URL |
| GET | `/dossiers/shared/{shareToken}` | public | Public shared dossier |
| POST | `/dossiers/{id}/rotate-share-token` | owner | Kill leaked links |
| POST | `/dossiers/{id}/retry-generation` | owner | Retry (FAILED+paid) |
| GET | `/users/me/dossiers` | user | My dossiers |
| GET | `/public/damage-files/{token}` | public | Token-gated photo |

### Marketplace, Consent & Payments  (`marketplace-service`)
| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/subscriptions/pro` | owner | Buy PRO |
| GET | `/users/me/subscription` | owner | PRO status |
| GET | `/admin/agents` | admin | Agent queue |
| PUT | `/admin/agents/{id}/verify` | admin | Approve/reject agent |
| GET | `/agents/me` | agent | Agent home |
| GET | `/agents/me/subscription` | agent | Subscription status |
| POST | `/agents/me/subscription` | agent | Subscribe |
| GET | `/agents/me/leads` | agent | Leads (5-field projection) |
| GET | `/agents/me/interests` | agent | My interests |
| GET | `/agents/me/shared-dossiers` | agent | Shared dossiers |
| POST | `/leads/{propertyId}/express-interest` | agent | Express interest (404 unless opted-in) |
| GET | `/users/me/agent-interests` | owner | Interests in my properties |
| PUT | `/agent-interests/{id}/respond` | owner | Accept/decline interest |
| DELETE | `/agent-interests/{id}` | owner | Revoke connection (cascades) |
| POST | `/dossiers/{dossierId}/share-to-agent` | owner | Consent-share dossier |
| DELETE | `/dossiers/{dossierId}/share-to-agent/{agentId}` | owner | Revoke share |
| GET | `/dossiers/{dossierId}/verify` | agent | Verify integrity |
| POST | `/dossiers/{dossierId}/quote` | agent | Send quote |
| GET | `/users/me/quotes` | owner | My quotes |
| PUT | `/quotes/{id}/respond` | owner | Accept/decline quote |
| POST | `/payments/{reference}/verify` | payer | Verify after checkout |
| GET | `/payments/{reference}` | payer | Payment details |
| POST | `/payments/webhook` | public | Paystack webhook (signed) |

### Notifications & Tips  (`notification-service`)
| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| PUT | `/users/me/device-token` | user | Register FCM token |
| DELETE | `/users/me/device-token` | user | Remove FCM token |
| GET | `/users/me/notification-preferences` | user | Get prefs |
| PUT | `/users/me/notification-preferences` | user | Update prefs |
| GET | `/users/me/notifications` | user | Inbox/history |
| GET | `/tips/feed` | user | Safety tips feed (English) |
| GET | `/properties/{id}/tips` | owner/member | Property-specific tips |
| PUT | `/tips/{id}/read` | user | Mark tip read |

---

## 9. Flows as sequence narratives (the five demo beats)

1. **Document** — `register` → `verify-otp` → `POST /properties` →
   `POST /properties/{id}/assets` (×N, hashed) → `GET /properties/{id}`.
2. **Disaster** — `POST /properties/{id}/damage-reports` →
   `POST /damage-reports/{id}/photos` (×N) → `POST /damage-reports/{id}/pairs` →
   `PUT /damage-reports/{id}/complete`.
3. **Dossier** — `POST /damage-reports/{id}/generate-dossier` → (pay) →
   poll `GET /dossiers/{id}/status` → `GET /dossiers/{id}/download`.
4. **Consent** — agent `POST /leads/{id}/express-interest` → owner
   `PUT /agent-interests/{id}/respond {accept:true}` → owner
   `POST /dossiers/{id}/share-to-agent` → agent `GET /dossiers/{id}/verify` →
   agent `POST /dossiers/{id}/quote`.
5. **Control / revoke** — owner `POST /dossiers/{id}/rotate-share-token` (old public
   link dies) and/or `DELETE /agent-interests/{id}` (agent API access sealed instantly).

---

## 10. Gotchas

- **Signed URLs expire in ~15 min** — re-fetch `/download` (or `/public/files/...`),
  don't cache the URL. The object stays; only the URL is short-lived.
- **Leads are 5 fields by design** — `{propertyId, ownerDisplayName, propertyName,
  propertyType, locality}`. No GPS, phone, value, or hashes. Don't build UI expecting more.
- **404, not 403, for privacy** — expressing interest in a non-opted-in property, or
  touching a resource you don't own, returns 404. Don't infer existence from the code.
- **Reports are immutable after `complete`** — edits → 409 `INVALID_STATE_TRANSITION`.
  Gate the UI on report status.
- **Share-token rotation** invalidates the previous public link immediately (404).
- **Don't set `Content-Type` on multipart** — let `fetch`/`FormData` set the boundary.
- **Tips are English-only by design** (complete as-is, not a future feature).
