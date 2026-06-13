# AssetShield GH — Provider Setup

The backend reaches **exactly three external services**: Supabase (storage
only), Firebase (FCM only), and Paystack (payments). Everything else —
including the **PostgreSQL database** — runs **inside the Docker stack** and
ships with the app; there is no external/managed database to configure.

Each provider has a zero-config development fallback, so you can run and demo
the whole system with **no accounts and no internet**:

| Concern | Dev default | Production provider |
|---|---|---|
| Storage | `STORAGE_PROVIDER=local` | `STORAGE_PROVIDER=supabase` |
| Push    | `FCM_MODE=log`          | `FCM_MODE=firebase`        |
| Payments| `PAYMENTS_MODE=mock`    | `PAYMENTS_MODE=paystack`   |

Fill in the sections below only for the providers you want to switch on. Put
the values in `assetshield-backend/.env` (git-ignored). The matching
placeholders already exist there and in `.env.example`.

---

## 1. Supabase Storage (S3-compatible)

Supabase Storage speaks the S3 API; the backend uses the AWS S3 SDK against it.
Supabase is used for **storage only** — not its database, auth or any other
product.

1. Create a project at <https://supabase.com> (free tier is fine).
2. **Storage → Buckets → New bucket**: name it `assetshield` (must match
   `SUPABASE_STORAGE_BUCKET`). Leave **"Public bucket" OFF** — it must be
   **private**; the backend serves files through short-lived presigned URLs.
3. **Storage → S3 Connection** (a.k.a. "S3 access keys" / "Connection"):
   - Read off the **Endpoint**, e.g.
     `https://<project-ref>.storage.supabase.co/storage/v1/s3` → `SUPABASE_S3_ENDPOINT`.
   - Read off the **Region**, e.g. `us-east-1` → `SUPABASE_S3_REGION`.
   - Click **New access key**. Copy the **Access key ID** →
     `SUPABASE_S3_ACCESS_KEY_ID` and the **Secret access key** (shown once) →
     `SUPABASE_S3_SECRET_ACCESS_KEY`.
4. In `.env` set:
   ```
   STORAGE_PROVIDER=supabase
   SUPABASE_S3_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
   SUPABASE_S3_REGION=us-east-1
   SUPABASE_S3_ACCESS_KEY_ID=...
   SUPABASE_S3_SECRET_ACCESS_KEY=...
   SUPABASE_STORAGE_BUCKET=assetshield
   ```
5. **Prove it:** with the real `.env` loaded, run
   `mvn -pl property-service test -Dtest=StorageProviderContractTest` (the
   `supabaseProviderRoundTrip` case runs because `SUPABASE_S3_ENDPOINT` is set),
   or run the full `infra/smoke/real-providers.sh`.

> The object-key scheme is unchanged: `assets/{propertyId}/{hash}.jpg`,
> `receipts/{assetId}/{hash}.jpg`, `dossiers/{dossierId}.pdf`,
> `ghana-cards/{userId}.jpg`. The single private bucket is namespaced by these
> key prefixes.

---

## 2. Firebase Cloud Messaging (FCM only)

Firebase is used for **push notifications only**. No Firebase Storage, no
storage bucket.

1. Create a project at <https://console.firebase.google.com>.
2. **Project settings → Service accounts → Generate new private key** →
   downloads a JSON file.
3. Save it as `assetshield-backend/infra/firebase/firebase-service-account.json`
   (this directory is mounted read-only into the containers at `/secrets`, and
   `*.json` there is git-ignored).
4. In `.env` set:
   ```
   FCM_MODE=firebase
   FIREBASE_SERVICE_ACCOUNT_PATH=/secrets/firebase-service-account.json
   ```
5. On the client (Expo / Firebase console test), obtain a device registration
   token and register it via `PUT /api/v1/users/me/device-token`.
6. **Prove it:** trigger any notification (e.g. accept an agent interest) and
   confirm the push arrives, or run the smoke script with a device-token arg.

---

## 3. Paystack (payments)

1. Create an account at <https://paystack.com> and open **Settings → API Keys &
   Webhooks**.
2. Copy the **Test Secret Key** (`sk_test_...`) → `PAYSTACK_SECRET_KEY`.
3. In `.env` set:
   ```
   PAYMENTS_MODE=paystack
   PAYSTACK_SECRET_KEY=sk_test_xxxxxxxx
   ```
4. **Settle path — pick one** (localhost cannot receive Paystack's webhook
   directly):
   - **(a) Client-confirm:** after paying at the returned `authorizationUrl`,
     call `POST /api/v1/payments/{reference}/verify`. This hits the same
     idempotent settlement as the webhook. No tunnel needed.
   - **(b) Webhook + tunnel:** expose the gateway with a tunnel
     (`cloudflared tunnel --url http://localhost:8080` or
     `ngrok http 8080`) and set the **Webhook URL** in the Paystack dashboard to
     `https://<tunnel-host>/api/v1/payments/webhook`. Paystack signs each call
     HMAC-SHA512; the backend verifies it over the raw bytes before parsing.
5. Use Paystack **test** MoMo/card details at checkout (see Paystack docs →
   "Test payments"). Amounts are charged in pesewas (GHS × 100).
6. **Prove it:** `infra/smoke/real-providers.sh` runs generate-dossier →
   checkout → verify/webhook → dossier READY → download.

---

## Switching back to the offline demo

Set `STORAGE_PROVIDER=local`, `FCM_MODE=log`, `PAYMENTS_MODE=mock` (the
defaults). No accounts, no internet, no credentials — the full flow still works
and all ~187 tests pass.
