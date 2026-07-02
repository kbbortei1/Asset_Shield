# AssetShield GH — Frontend Build Notes

React Native / Expo (SDK 54, expo-router, TypeScript) app built against the
backend contract in [FRONTEND_HANDOFF.md](./FRONTEND_HANDOFF.md), skinned with
the Stitch design system ("AssetShield GH Design Brief" + "Companion Concept").
**The backend contract was the scope gate; Stitch was the skin.**

`npx tsc --noEmit` → clean. `npx expo lint` → clean. `npx expo config` → valid.

## Architecture (foundation layers)

- **`theme/`** — colors, typography (Sora/Inter), spacing/radius/elevation, all
  extracted verbatim from the Stitch `designTheme.designMd` (Guardian Teal
  `#0E5A52`, Trust Gold `#F4A93C`, warm surface `#F6F5F1`).
- **`components/ui/`** — Text, Button, Input, Card, Badges (Status / Verified-hash /
  Value pill), Screen, Loading/Empty/Error states, OfflineBanner, Header.
- **`lib/api/`** — one typed client: central envelope unwrap, error-catalogue
  normalization (`ApiError` + every documented code), **single-flight 401-refresh
  interceptor** with refresh-token rotation, SecureStore token storage, and typed
  endpoint modules per service (auth/users/properties/damage/marketplace/notifications).
- **`lib/media/`** — `sha256OfFile` hashes the **exact decoded bytes**
  (`expo-file-system` `File.bytes()` → `expo-crypto` `digest`), verified against the
  handoff fixture (`asset-1.jpg → a3ba97e5…fad0`). Capture → hash LAST → multipart
  upload. (Note: the handoff's base64-string snippet would have failed; raw-bytes is correct.)
- **`lib/offline/`** — SQLite queue; captures enqueue with the hash computed at
  capture time; auto-flush on reconnect via NetInfo; **409 duplicate-hash treated as
  success** on replay.
- **`lib/payments/`** — `runCheckout` (expo-web-browser → verify) + `pollDossierStatus`;
  works in mock mode (auto-settles) and Paystack test mode.
- **`lib/auth/`**, **`lib/push/`**, **`lib/query.ts`** — session provider, FCM
  device-token lifecycle (register on login + rotation, delete on logout),
  notification deep-link routing, React Query client.

## BUILD — delivered (contract-required + Stitch design exists)

Auth: Welcome, Role select, Permissions, Register (owner), Register-agent, OTP,
Login. Owner: Home/Protection-score dashboard, Properties, Property dashboard,
Capture asset, Asset detail, Report damage (select type), Damage capture + GPS
pairing, Report detail (complete), Dossier (pay→poll→download), Share dossier.
Agent: Home, Leads, Lead detail/express-interest, Shared dossier viewer/verify,
Issue quote, Subscription, Activity (shared dossiers + quotes). Cross: Connections
(interests respond/revoke), Quotes (respond), Notifications inbox, Profile,
Billing history, System/offline states (applied app-wide).

## MISSING_DESIGN — built clean & on-brand (contract-required, no Stitch frame)

Login (dedicated), Property create/edit, Assets list (in property dashboard),
Asset detail/edit + receipt upload, Household invite + members, My invitations,
Ghana Card KYC, Notification inbox, Tips feed + property tips, Owner PRO purchase,
Damage report list, Profile edit, Notification preferences, **Admin: agent verify
queue + review**, **Admin: create admin**.

## EXTRA_DESIGN — resolution

"Billing History" had no backing list endpoint; per your instruction it was
**built**, aggregating the billable data the contract *does* expose (subscription
status + dossier fees). Single-payment lookup uses `GET /payments/{reference}`.
The public `/dossiers/shared/{shareToken}` web view and the May "Property Vault"
concepts were excluded (out of app scope / superseded), as agreed.

## Where Stitch and the contract disagreed (contract won)

- **Hashing**: handoff sample hashed the base64 string; the server hashes raw
  bytes. We hash raw bytes (fixture-verified).
- **Leads**: rendered exactly the 5 permitted fields — no GPS/phone/value.
- **Immutable reports**: completed reports show locked UI; no edit affordances.
- **Privacy 404**: revoked/non-opted-in resources are treated as not-found, not
  as access errors.
- **Per-agent dossier un-share** (`DELETE …/share-to-agent/{agentId}`) isn't
  surfaced because the owner has no endpoint to list an `agentRecordId` per share;
  CONTROL is instead delivered via rotate-share-token + revoke-interest (both fully
  supported and cascade access).

## Run

```bash
cp .env.example .env   # set EXPO_PUBLIC_API_URL to your LAN IP :8080/api/v1
npx expo start
```

## Response field names — VERIFIED against backend source

All response DTOs were reconciled against the actual Spring Boot response records
(not guessed). Notable corrections applied across models + screens:

- Page array key is `items` (never `content`); members/invitations return a raw
  `{ items: [...] }` (not paged) — endpoints unwrap to arrays.
- Property: `totalEstimatedValue` (not `totalValue`), `myAccess` (not `role`),
  detail puts counts under `dashboard`; no `protectionScore`.
- Asset: `receiptCount` (list) / `receipts[]` (detail), not `hasReceipt`.
- Marketplace IDs are entity-specific: `interestId`, `quoteId`, `agentId`,
  `dossierId`, `shareId` — never a bare `id`.
- Agent self: `verificationStatus` + `subscription.status` (not `status`/`subscriptionStatus`).
- `QuoteStatus` is `PENDING|ACCEPTED|DECLINED` (not `ISSUED`).
- `/dossiers/{id}/verify` → integrity fields (`tamperEvident`, `manifestHash`,
  `recomputedHash`, `photoCount`, `mismatches`, `verifiedAt`).
- Subscription-init payment handle is FLAT (`data.authorizationUrl`); generate-dossier
  nests under `data.payment`. Owner sub uses `tier`; agent uses `plan`/`status`.
- Notifications expose no read flag (only `status`/`sentAt`); tips use `tipText` +
  `readAt` (no `title`/`body`/boolean).
- Enums corrected: AssetCategory (`CLOTHING_STOCK`/`MACHINERY`, no APPLIANCES/STOCK/
  EQUIPMENT/JEWELRY), DisasterType (no COLLAPSE), PropertyType adds `RENTAL`.

Still worth a live smoke once `demo/seed-demo.sh` is running: the damage **report
detail** `photos`/`pairs` array names and the admin `/admin/agents` list element
shape (mapped permissively — uses `agentId ?? id`, `verificationStatus ?? status`).

## Logo

In-app brand mark is a crisp **vector** shield + gold aperture
([components/brand/ShieldLogo.tsx](./components/brand/ShieldLogo.tsx)), used on
welcome/login. For the exact supplied raster artwork on the **launcher icon** and
**native splash**, drop the PNG at `assets/images/logo.png` and point app.json
`icon` (and the `expo-splash-screen` `image`) at it — the repo currently still has
the default Expo template icon for those two native slots.
