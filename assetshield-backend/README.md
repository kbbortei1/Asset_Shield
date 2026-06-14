# AssetShield GH — Backend

Pre-loss property evidence and damage documentation platform for Ghanaian
property owners (CodeQuest 2026, KNUST). Microservices behind an API Gateway.

**New here?** Frontend devs start at [`FRONTEND_HANDOFF.md`](FRONTEND_HANDOFF.md);
graders start at [`REQUIREMENTS_COVERAGE.md`](REQUIREMENTS_COVERAGE.md). Live API
docs: `http://localhost:8080/swagger-ui.html`.

## Architecture

```
                         ┌─────────────────────────────────────────────┐
   React Native /        │            assetshield-net (bridge)          │
   Expo client           │                                              │
        │                │   ┌──────────┐   ┌──────────────┐            │
        │  HTTPS :8080    │   │  auth    │   │  property    │            │
        ▼                │   │  :8081   │   │  :8082       │            │
  ┌───────────┐  JWT     │   └────┬─────┘   └────┬─────────┘            │
  │  gateway  │──edge────│        │              │   ┌──────────────┐   │
  │  :8080    │  validate│   ┌────┴─────┐   ┌────┴───│  damage      │   │
  │ (sole     │  X-User-*│   │ marketplace│  │       │  :8083       │   │
  │  ingress) │──route──▶│   │  :8084   │◀──┘       └────┬─────────┘   │
  └───────────┘          │   └────┬─────┘   ┌──────────────┐           │
   rate-limit            │        │         │ notification │           │
   request-id            │        └─────────│  :8085       │           │
   swagger (dev)         │   X-Internal-Api-Key (/internal/**)         │
                         │        ┌──────────────────────┐             │
                         │        │  postgres :5432       │            │
                         │        │  5 DBs · 1 role each   │            │
                         │        └──────────────────────┘             │
                         │   Externals (prod): Supabase(storage) ·      │
                         │   Paystack(pay) · Firebase(FCM)              │
                         └─────────────────────────────────────────────┘
```

Only the **gateway (:8080)** is published to the host; services are reachable only
on the internal network and re-validate the JWT themselves (defense in depth).
`/internal/**` is API-key-guarded and has **no gateway route** (404 from the edge).

## Architecture & port map

| Service | Port | Status | Purpose |
|---|---|---|---|
| gateway | **8080** (published) | ✅ Day 1 | Spring Cloud Gateway: routing, edge JWT validation, per-IP rate limiting, request-id |
| auth-service | 8081 (internal) | ✅ Day 1 | Registration, OTP, login, JWT issuance, refresh rotation, admin management, Ghana Card upload |
| property-service | 8082 (internal) | ✅ Day 2 | Properties, evidence assets (SHA-256 verified photos), receipts, household sharing, marketplace opt-in |
| damage-service | 8083 (internal) | ✅ Days 3–4 | Damage reports, photo evidence, GPS before/after pairing, loss calculation, payment-gated PDF dossiers |
| marketplace-service | 8084 (internal) | ✅ Days 4–5 | Paystack/MoMo payments + webhook settlement; verified agents, opt-in leads, consent-gated dossier shares, policy quotes, agent + PRO subscriptions |
| notification-service | 8085 (internal) | ✅ Day 6 | FCM push dispatch, Ghana-specific AI safety tips, scheduled reminders, in-app notification history |
| postgres | 5433 (published, dev only) | ✅ Day 1 | One database + one role per service |

Only the **gateway** is reachable from the host. Services talk to each other on
the internal Docker network `assetshield-net`. Postgres publishes host port 5433 (container 5432) for
local tooling convenience (dev only) - 5433 avoids clashing with a locally installed PostgreSQL.

**Stack:** Java 25 · Spring Boot 4.0.x · Spring Cloud 2025.1.1 (Oakwood) ·
PostgreSQL 15 · Flyway · JJWT · springdoc-openapi · Testcontainers.
Each service has a fully independent `pom.xml` — no parent pom, no shared modules.

## Quick start (Docker)

```bash
cp .env.example .env        # then edit: real secrets, JWT_SECRET >= 64 chars
docker compose --profile core up --build
```

Order of events: postgres starts and (first run only) provisions the five
databases/roles via `infra/postgres/init/01-init.sh`; auth-service waits for
the postgres healthcheck, migrates its schema with Flyway and seeds the
superadmin from `SUPERADMIN_PHONE`/`SUPERADMIN_PASSWORD` (idempotent — only
when no ACTIVE admin exists); the gateway starts last.

Smoke test through the gateway:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+233244123456","password":"Password#1","fullName":"Ama Mensah"}'

# OTP appears in auth-service logs (mock SMS provider); OTP_DEV_CODE also works:
curl -s -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+233244123456","code":"123456"}'
```

## External providers

The backend is a **self-contained, containerized application**. The
**database is PostgreSQL inside the Docker stack** and ships with the app — there
is no external/managed database. The backend reaches exactly **three** external
services, each with a zero-config dev fallback so the whole system runs and
demos with **no accounts and no internet**:

| Concern | Production provider | Dev/demo fallback | Env switch | Env vars | How to prove |
|---|---|---|---|---|---|
| **File storage** (photos, receipts, Ghana cards, dossier PDFs) | **Supabase Storage** (S3-compatible, private bucket, presigned URLs) | `local` disk volume | `STORAGE_PROVIDER=supabase\|local` | `SUPABASE_S3_ENDPOINT`, `SUPABASE_S3_REGION`, `SUPABASE_S3_ACCESS_KEY_ID`, `SUPABASE_S3_SECRET_ACCESS_KEY`, `SUPABASE_STORAGE_BUCKET` | `StorageProviderContractTest` (guarded) / `infra/smoke/real-providers.sh` |
| **Push** | **Firebase Cloud Messaging** (FCM only) | `log` | `FCM_MODE=firebase\|log` | `FIREBASE_SERVICE_ACCOUNT_PATH` | smoke script with a device token |
| **Payments** | **Paystack** (MoMo/card) | `mock` (auto-settle ~2 s) | `PAYMENTS_MODE=paystack\|mock` | `PAYSTACK_SECRET_KEY` | smoke script (verify or tunnelled webhook) |

**Database is PostgreSQL inside the container stack; Supabase is storage-only;
Firebase is FCM-only.** Full credential-by-credential setup is in
[`PROVIDER_SETUP.md`](../PROVIDER_SETUP.md).

Storage details:
- **`local`** (default, offline demo) — objects on the `storage-data` volume;
  reads are short-lived token URLs (`/api/v1/public/files/{token}`, 15 min)
  streamed by property-service.
- **`supabase`** — Supabase Storage is S3-compatible, accessed with the AWS S3
  SDK v2 (path-style addressing). A single PRIVATE bucket; reads are 15-min
  presigned GET URLs minted locally. Object keys: `assets/{propertyId}/{hash}.jpg`,
  `receipts/{assetId}/{hash}.jpg`, `dossiers/{dossierId}.pdf`,
  `ghana-cards/{userId}.jpg`.

All providers **fail fast** at startup when selected-but-misconfigured (a clear
exception naming the missing variable, never an NPE at first use). The database
always stores **object paths, never URLs** — signed URLs are minted at read time
by the DTO mappers.

### Env-mode switch table

| Mode var | Demo value | Live value |
|---|---|---|
| `STORAGE_PROVIDER` | `local` | `supabase` |
| `FCM_MODE` | `log` | `firebase` |
| `PAYMENTS_MODE` | `mock` | `paystack` |
| `TIER_LOOKUP_MODE` | `stub` | `remote` |
| `MARKETPLACE_EVENTS_MODE` | `log` | `remote` |
| `NOTIFICATIONS_MODE` / `EVENTS_MODE` | `log` | `remote` |

Since Day 5 property-service talks to the live marketplace:
`TIER_LOOKUP_MODE=remote` (free-tier limits from marketplace's tier endpoint;
fails closed to FREE) and `MARKETPLACE_EVENTS_MODE=remote` (opt-in pushes).
Since Day 6, `NOTIFICATIONS_MODE=remote` (property, damage, marketplace) and
`EVENTS_MODE=remote` (property asset-captured events) send everything to
notification-service. Every mode flips back to `stub`/`log` to run a partial
stack — and every remote notify/event call is fire-and-forget: a dead
notification-service never fails the business operation.

## Compose profiles

- `postgres` has **no profile** — it always starts.
- `gateway`, `auth-service`, `property-service`, `damage-service` and `marketplace-service` carry profile **`core`**.
- Future services will get their own profiles, so you can start subsets:
  `docker compose --profile core up` starts today's stack;
  `docker compose up` alone starts only postgres.

## Spring profiles (running from the IDE)

| Profile | Datasource | Use |
|---|---|---|
| `docker` | `postgres:5432` | inside Docker Compose (set automatically) |
| `local` | `localhost:5432` | running a service from the IDE against the composed postgres |

```bash
# postgres up first:  docker compose up -d postgres
cd auth-service
AUTH_DB_PASSWORD=... JWT_SECRET=... INTERNAL_API_KEY=... \
  mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Services **fail fast at startup** if `JWT_SECRET`, `INTERNAL_API_KEY` or their
DB password is missing — no defaults for secrets anywhere.

## Build & test

```bash
mvn -f gateway/pom.xml package
mvn -f auth-service/pom.xml package       # integration tests need Docker (Testcontainers)
mvn -f property-service/pom.xml package   # integration tests need Docker (Testcontainers)
mvn -f damage-service/pom.xml package     # integration tests need Docker (Testcontainers)
mvn -f marketplace-service/pom.xml package # integration tests need Docker (Testcontainers)
```

`mvn test` on auth-service runs unit tests, repository integration tests
(Testcontainers postgres:15, real Flyway migration) and full MockMvc endpoint
flows. The gateway suite covers route forwarding, edge JWT rejection and the
429 rate limit.

## API docs — aggregated Swagger at the edge

With the stack up: **`http://localhost:8080/swagger-ui.html`** serves a five-service
dropdown (auth, property, damage, marketplace, notification). The gateway proxies
each service's `/v3/api-docs` via `/api-docs/{service}`. This is a **dev convenience,
gated by `SWAGGER_ENABLED`** (default `true` in compose) — set `SWAGGER_ENABLED=false`
in any public deployment and the gateway 404s every doc path. For programmatic use,
import the Postman collection in [`e2e/postman/`](e2e/postman/).

## End-to-end tooling, load & demo

All under [`e2e/`](e2e/) and [`demo/`](demo/); the runners use Docker images
(`postman/newman`, `grafana/k6`) so no local Node/k6 is needed.

```bash
# 0. stack up (self-contained demo profile)
docker compose --profile core up --build

# 1. CodeQuest demo data (full story via the public API → real hashes/PDFs)
./demo/seed-demo.sh                      # prints a credential card + share link

# 2. security audit suite (newman, through the gateway; exits non-zero on failure)
./e2e/security/run.sh                    # mock mode (all groups)
PAYMENTS_MODE=paystack ./e2e/security/run.sh   # real-provider mode (payment-gated groups self-skip)

# 3. load smoke (k6)
./e2e/load/run.sh smoke                  # NFR03 p95<500ms, <1% errors
./e2e/load/run.sh spike                  # 0→200 VU burst (observational)
./e2e/load/run.sh dossier-timing         # FR11 dossier READY ≤20s
```

Windows: each has a `.ps1` twin. Details: [`e2e/security/README.md`](e2e/security/README.md),
[`e2e/load/RESULTS.md`](e2e/load/RESULTS.md). Dependency-update roadmap:
[`e2e/DEPENDENCY_REPORT.md`](e2e/DEPENDENCY_REPORT.md).

## Conventions (all services)

- **Envelope** on every response, success and error:
  `{"status":"success|error","data":{...},"message":"..."}` — errors carry
  `data.errorCode` (machine code) and optionally `data.fields` (per-field
  validation messages).
- camelCase JSON · UUID ids · ISO-8601 UTC timestamps.
- Flyway owns every schema; `spring.jpa.hibernate.ddl-auto=validate`.
- Phone format `+233XXXXXXXXX` · password ≥ 8 chars · fullName 2–120 chars.
- The gateway forwards `X-User-Id` / `X-User-Role` from verified JWT claims
  (client-supplied values are stripped); services still re-validate the JWT
  themselves (defense in depth).
- `X-Request-Id` is generated at the edge, forwarded, logged everywhere (MDC).

## Property service highlights

- **Evidence integrity:** asset upload is multipart `file` + `metadata` (JSON
  with the client-computed `sha256Hash`). The server **recomputes SHA-256
  over the received bytes** — mismatch → 400 `HASH_MISMATCH` and nothing is
  stored; an identical live photo on the same property → 409
  `DUPLICATE_ASSET_HASH` (enforced by a partial unique index, race-safe).
- Photo, hash, GPS and `capturedAt` are **immutable**; only description,
  value and category can be edited.
- **Access model** (single `PropertyAccessService`): OWNER (full control) ·
  MEMBER / MEMBER_EXPORT (view + contribute; edit/delete only their own
  uploads). Household flow: owner invites a phone number (7-day expiry, one
  pending invite per phone per property) → invitee accepts → membership;
  owner can revoke any time.
- **Free-tier limits** (when the tier resolves to FREE): 1 property,
  30 photos per property → 422 `FREE_TIER_LIMIT`.
- **Marketplace opt-in:** `PUT /properties/{id}/offers-optin` (owner only)
  flips `openToOffers` + timestamp and pushes the change to marketplace
  (best-effort; opt-out auto-declines pending agent interests there).
- **Internal API** (`/internal/properties/...`, API-key guarded): property
  meta, access resolution, asset meta, GPS radius search
  (bounding box → Haversine), the strictly-six-field `lead-view`
  projection — the only shape the marketplace may consume — and the
  paginated `/internal/properties/leads` list (opted-in, live properties,
  type/locality filters, owner reduced to "first name + last initial").
- Swagger UI (internal network): `http://property-service:8082/swagger-ui.html`.

## Damage service highlights

- **Reports** are a one-way state machine: `DRAFT → COMPLETED`. Every mutation
  (photos, pairs, completion) requires DRAFT; after completion the report and
  everything under it is immutable evidence (400 `INVALID_STATE_TRANSITION`).
- **Damage photos** use the same SHA-256 recompute-or-reject contract as
  assets (400 `HASH_MISMATCH`, nothing stored; duplicate in the same report →
  409 `DUPLICATE_PHOTO_HASH`).
- **GPS pairing:** each photo upload returns `pairingSuggestions` — documented
  assets within `PAIRING_RADIUS_METERS` (default 25 m) of the photo's GPS,
  via property-service's `assets-near` internal API. A failed lookup never
  fails the upload (201 with empty suggestions); pairing can be done manually.
- **Pairs freeze an `asset_snapshot`** (JSONB): the before-photo path, hash,
  value, category and GPS at pairing time. Editing or deleting the asset later
  does NOT change the pair — dossiers stay reproducible.
- **Completion** computes `totalEstimatedLoss` over **distinct** paired assets
  (an asset paired with several photos counts once) and requires ≥ 1 photo
  (400 `EMPTY_REPORT`).
- Authorization is resolved via property-service's internal access endpoint
  (cached 60 s): OWNER / MEMBER_EXPORT mutate, MEMBER views only.
- **Internal API**: `GET /internal/damage-reports/{id}` — full report with
  object paths for Day 4's PDF builder.
- Swagger UI (internal network): `http://damage-service:8083/swagger-ui.html`.

## Dossiers + payments (Day 4)

- **Flow:** `POST /damage-reports/{id}/generate-dossier` (report must be
  COMPLETED) → dossier `PENDING_PAYMENT` + a checkout `authorizationUrl` →
  payment settles (webhook or `/payments/{ref}/verify`) → marketplace calls
  damage's internal `payment-confirmed` → async PDFBox generation →
  `READY` → `GET /dossiers/{id}/download` (15-min signed URL).
  Download before paying → 402 `PAYMENT_REQUIRED`.
- **Manifest hash (tamper evidence), exact algorithm:** take (1) the SHA-256
  of every distinct paired asset snapshot ordered by assetId ascending (UUID
  string form), then (2) the SHA-256 of every damage photo ordered by photo id
  ascending; join the lowercase hex strings with `\n`, encode UTF-8, SHA-256
  the result. The hash, the algorithm and a verification guide are printed in
  the PDF; `GET /internal/dossiers/{id}/verify` re-downloads every stored
  object, re-hashes the bytes and rebuilds the manifest (`tamperEvident:
  true` = intact).
- **Share links:** every dossier has a rotatable `share_token`;
  `GET /api/v1/dossiers/shared/{token}` works logged-out (READY only,
  anything else is an opaque 404). Rotating kills leaked links.
- **Payments:** single idempotent settlement pipeline used by both the
  HMAC-SHA512-verified webhook (raw-bytes signature check) and the
  client-driven verify endpoint; replayed webhooks are no-ops. A 60 s
  reconciler re-dispatches settlements whose downstream confirmation failed.
  References look like `ASGH-DSR-a3f19c0b44de`; amounts go to Paystack in
  pesewas (GHS × 100).
- **Testing Paystack locally:** keep `PAYMENTS_MODE=mock` (zero internet — the
  payment auto-settles after ~2 s), or set `PAYMENTS_MODE=paystack` +
  `PAYSTACK_SECRET_KEY=sk_test_...` and confirm with
  `POST /api/v1/payments/{reference}/verify` after paying, or tunnel the
  webhook (ngrok) to `https://<tunnel>/api/v1/payments/webhook`.

## Marketplace (Day 5)

Consent-first insurance marketplace. Privacy guardrails here are P0: agents
never see anything an owner did not explicitly expose.

- **Agent onboarding:** agent registration (Day 1) holds insurer + NIC licence
  in auth's `pending_agent_details`; on OTP completion auth pushes
  `POST marketplace:/internal/agents/sync` (idempotent on userId, 409 on a
  licence registered to another user), and a 60 s job re-pushes anything
  unconsumed — so agents survive marketplace downtime and pre-Day-5 agents
  sync on first boot. Admin reviews `GET /admin/agents?status=PENDING_VERIFICATION`
  and decides once via `PUT /admin/agents/{id}/verify` (reject needs a reason).
- **Two composable gates:** Gate A — VERIFIED agent (else 403
  `AGENT_NOT_VERIFIED`); Gate B — Gate A + ACTIVE unexpired subscription
  (else 403 `SUBSCRIPTION_INACTIVE`). Lapse never deletes data, only access.
- **Leads (P0 projection):** `GET /agents/me/leads` (Gate B) returns items of
  EXACTLY `{propertyId, ownerDisplayName, propertyName, propertyType,
  locality}` — built solely from property's internal lead projection; a test
  asserts the serialized key set. Express-interest on an unknown, deleted or
  non-opted-in property is an identical **404** (existence is private).
- **Consent chain:** agent expresses interest → owner accepts (only then does
  the agent see the owner's name, and the owner the agent's licence) → owner
  shares a READY dossier (`POST /dossiers/{id}/share-to-agent`, OWNER or
  MEMBER_EXPORT, dossier property must match the interest) → agent lists
  shared dossiers, relays integrity verification (`GET /dossiers/{id}/verify`)
  and issues quotes (`POST /dossiers/{id}/quote`).
- **Revocation (FR28):** consent is revocable and enforced on the agent's
  *next read*. Revoking a share kills dossier access; revoking the connection
  (`DELETE /agent-interests/{id}`) bulk-revokes every share under it in the
  same transaction — subsequent agent reads are 404s. Owner opt-out
  auto-declines PENDING interests; ACCEPTED connections survive until revoked
  individually.
- **Quotes:** owner reviews via `GET /users/me/quotes` and responds once;
  acceptance is the billable referral event, logged at INFO
  (`REFERRAL quote accepted: …`) for revenue reconciliation.
- **Subscriptions:** agent monthly (`AGENT_SUB_GHS`, default 100 GHS) and
  owner PRO (`PRO_SUB_GHS`, default 15 GHS) ride the Day 4 settlement
  pipeline — settle creates an ACTIVE row (+30 days) or extends the current
  expiry (never truncates); replays are no-ops. Hourly sweep expires lapsed
  rows; a daily 08:00 Africa/Accra job warns once per subscription within 7
  days of expiry. FREE is the absence of an ACTIVE PRO row;
  `GET /internal/users/{id}/tier` feeds property-service's live free-tier
  limits.
- Swagger UI (internal network): `http://marketplace-service:8084/swagger-ui.html`.

## Notifications & tips (Day 6)

- **Dispatch pipeline** (`POST /internal/notifications/send`, the single
  entry point all services call): write the history row first (PENDING),
  then push via FCM to every active device of the user — no devices is a
  successful history-only delivery (SENT). Runs async (pool of 2); callers
  get 202 immediately. Dead tokens reported by FCM (UNREGISTERED /
  INVALID_ARGUMENT) are auto-revoked.
- **FCM** behind `FCM_MODE=firebase|log`. Firebase mode reuses the same
  service-account JSON as Firebase storage; log mode powers the offline demo
  and tests. History: `GET /users/me/notifications`.
- **Device tokens:** `PUT/DELETE /users/me/device-token` — upsert with
  cross-user re-registration (a token re-registered by a different account
  moves to it), revocation on logout.
- **Tips rule engine:** 50 seeded Ghana-specific templates (market-stall
  fire prevention, Accra flood zones, Harmattan/rainy season, theft,
  documentation habits) matched against the property's type, asset
  categories/values (property internal `tips-context`), the current
  Africa/Accra season (HARMATTAN Nov–Mar, RAINY Apr–Jul) and four seeded
  Accra flood-zone boxes. Ordering: priority, then specificity, then random;
  a template never repeats for the same user+property (`ux_tip_once`).
  Triggers: asset uploads (debounced per property, default 60 min) and the
  delivery scheduler.
- **Feeds:** `GET /tips/feed` (mine), `GET /properties/{id}/tips` (any
  household member), `PUT /tips/{id}/read` (idempotent).
- **Schedulers** (Africa/Accra, env-overridable crons): tip delivery daily
  07:00 — DAILY users every run, WEEKLY only Mondays, OFF accumulates
  silently; one "You have N new safety tips" push per batch. Redoc reminder
  daily 09:00 — properties stale for 90+ days (property internal
  `stale-documentation`) get one REDOC_REMINDER per 90-day window.
- Swagger UI (internal network): `http://notification-service:8085/swagger-ui.html`.

## Auth service highlights

- **Access token:** HS256 JWT, 1 h TTL, claims `sub` (userId), `role`, `phone`.
- **Refresh token:** 256-bit opaque, stored as SHA-256, 14-day TTL, rotated on
  every refresh; **reuse of a rotated token revokes the whole token family**.
- **OTP:** 6 digits, BCrypt-hashed, 5 min TTL, max 3 attempts, resend throttled
  to 1/60 s. `SMS_PROVIDER=mock` logs codes; `OTP_DEV_CODE` (dev only) is
  accepted as a universal code while an OTP is pending.
- **Internal API** (`/internal/users/...`): never routed by the gateway,
  guarded by the `X-Internal-Api-Key` header (constant-time comparison).
- **Ghana Card:** `POST /users/me/ghana-card` (multipart, jpeg/png ≤ 10 MB)
  stores the image via the storage abstraction; the profile exposes only the
  `ghanaCardUploaded` boolean.
- Swagger UI (auth-service, internal network): `http://auth-service:8081/swagger-ui.html`.

## Repository layout

```
assetshield-backend/
├── docker-compose.yml        # postgres + core profile (gateway, auth, property)
├── .env.example              # every env var, placeholders only
├── infra/postgres/init/      # creates 5 DBs + 5 roles, least-privilege
├── infra/firebase/           # drop firebase-service-account.json here (git-ignored)
├── gateway/                  # Spring Cloud Gateway (webflux server)
├── auth-service/             # complete auth domain (see above)
├── property-service/         # properties, assets, receipts, household sharing
├── damage-service/           # damage reports, photos, GPS pairing, dossier PDFs
├── marketplace-service/      # payments, agents, leads, consent shares, quotes, subscriptions
├── notification-service/     # FCM push, AI safety tips, schedulers, history
├── demo/                     # seed-demo.sh — CodeQuest demo data via the public API
├── e2e/
│   ├── fixtures/             # deterministic JPEGs (+ GenFixtures.java) with known sha256
│   ├── security/             # newman security audit suite (self-seeding)
│   ├── postman/              # frontend reference collection (78 endpoints)
│   ├── load/                 # k6 smoke / spike / dossier-timing
│   └── DEPENDENCY_REPORT.md  # versions:display-dependency-updates (roadmap)
├── FRONTEND_HANDOFF.md       # the frontend team's integration guide
└── REQUIREMENTS_COVERAGE.md  # FR/NFR → implementation → proof → demo-day visibility
```

Day 7 added hardening, the security audit + Postman + k6 suites under `e2e/`,
aggregated Swagger, the demo seed, and the frontend handoff + requirements-coverage
documents. See [`FRONTEND_HANDOFF.md`](FRONTEND_HANDOFF.md) and
[`REQUIREMENTS_COVERAGE.md`](REQUIREMENTS_COVERAGE.md).
