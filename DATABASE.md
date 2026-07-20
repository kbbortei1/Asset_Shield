# AssetShield GH — Database Architecture

Six PostgreSQL databases, one per service, on a single dev cluster (database-per-service
pattern). **Hard foreign keys exist only *inside* a database**; anything that crosses a
service boundary is a *soft reference* — a plain UUID owned by the other service, validated
at the API layer, never by the schema. This is deliberate: cross-database FKs are impossible
in Postgres and cross-service FKs would couple deployments and block independent migrations.

Migrations are Flyway-owned (`ddl-auto: validate` everywhere — Hibernate never touches DDL).

---

## auth_db (auth-service)

```mermaid
erDiagram
    users {
        uuid id PK
        varchar phone_number "UX partial: WHERE deleted_at IS NULL"
        varchar password_hash
        varchar role "OWNER|AGENT|ADMIN"
        varchar status "PENDING_OTP|ACTIVE|SUSPENDED"
        timestamptz deleted_at "soft delete frees the phone"
        bigint version "optimistic lock"
    }
    otp_codes {
        uuid id PK
        varchar phone_number
        varchar purpose "REGISTRATION|LOGIN_RECOVERY"
        varchar code_hash
        timestamptz consumed_at
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        char token_hash UK
        uuid family_id "reuse-detection family"
        timestamptz revoked_at
    }
    pending_agent_details {
        uuid id PK
        uuid user_id FK "UNIQUE"
        varchar nic_licence_no UK
        timestamptz consumed_at "marketplace sync marker"
    }
    users ||--o{ refresh_tokens : "user_id"
    users ||--o| pending_agent_details : "user_id"
```

OTP codes are keyed by phone (not user) on purpose: they exist before the user is ACTIVE.

## property_db (property-service)

```mermaid
erDiagram
    properties {
        uuid id PK
        uuid owner_user_id "soft ref -> auth.users"
        numeric gps_lat
        numeric gps_lng
        boolean open_to_offers "partial index for lead projection"
        timestamptz deleted_at
    }
    assets {
        uuid id PK
        uuid property_id FK
        char sha256_hash "UX (property_id, hash) partial"
        numeric gps_lat "pairing radius search"
        numeric gps_lng
        timestamptz captured_at
    }
    asset_receipts {
        uuid id PK
        uuid asset_id FK
        char sha256_hash
    }
    household_invitations {
        uuid id PK
        uuid property_id FK
        varchar invitee_phone "UX (property, phone) WHERE PENDING"
        uuid invitee_user_id "soft ref -> auth.users"
    }
    household_memberships {
        uuid id PK
        uuid property_id FK
        uuid member_user_id "UX (property, member) WHERE active"
    }
    properties ||--o{ assets : "property_id"
    assets ||--o{ asset_receipts : "asset_id"
    properties ||--o{ household_invitations : "property_id"
    properties ||--o{ household_memberships : "property_id"
```

`(property_id, sha256_hash)` partial-unique is the duplicate-photo guard the offline queue
relies on (replaying an upload 409s instead of duplicating).

## damage_db (damage-service)

```mermaid
erDiagram
    damage_reports {
        uuid id PK
        uuid property_id "soft ref -> property.properties"
        varchar status "DRAFT -> COMPLETED (immutable)"
        numeric total_estimated_loss "computed at completion"
    }
    damage_photos {
        uuid id PK
        uuid damage_report_id FK
        char sha256_hash "UX (report, hash) partial"
        numeric gps_lat
        numeric gps_lng
    }
    photo_pairs {
        uuid id PK
        uuid damage_report_id FK
        uuid damage_photo_id FK
        uuid asset_id "soft ref -> property.assets"
        jsonb asset_snapshot "frozen copy at pairing time"
        numeric distance_meters
    }
    dossiers {
        uuid id PK
        uuid damage_report_id FK
        uuid payment_id "soft ref -> payment.payments"
        char manifest_hash "tamper-evidence seal"
        uuid share_token UK "rotatable public link"
    }
    damage_reports ||--o{ damage_photos : "damage_report_id"
    damage_reports ||--o{ photo_pairs : "damage_report_id"
    damage_photos ||--o{ photo_pairs : "damage_photo_id"
    damage_reports ||--o{ dossiers : "damage_report_id"
```

`asset_snapshot` (JSONB) is **intentional denormalization**: evidence must reflect the asset
*as it was when paired*, even if the live asset is later edited or deleted. Normalizing it
away would corrupt the evidence chain.

## marketplace_db (marketplace-service)

```mermaid
erDiagram
    insurance_agents {
        uuid id PK
        uuid user_id "UNIQUE, soft ref -> auth.users"
        varchar nic_licence_no UK
        varchar verification_status
    }
    agent_subscriptions {
        uuid id PK
        uuid agent_id FK "UX WHERE status=ACTIVE"
        uuid last_payment_id "soft ref -> payment.payments (idempotency)"
        timestamptz expires_at
    }
    user_subscriptions {
        uuid id PK
        uuid user_id "soft ref, UX WHERE ACTIVE"
        uuid last_payment_id "soft ref -> payment.payments"
    }
    agent_interests {
        uuid id PK
        uuid agent_id FK
        uuid property_id "soft ref -> property.properties"
        uuid owner_user_id "soft ref -> auth.users"
        varchar status "UX (agent, property) WHERE PENDING"
    }
    dossier_shares {
        uuid id PK
        uuid dossier_id "soft ref -> damage.dossiers"
        uuid agent_id FK
        uuid agent_interest_id FK
        timestamptz revoked_at "UX (dossier, agent) WHERE active"
    }
    policy_quotes {
        uuid id PK
        uuid agent_interest_id FK
        uuid dossier_share_id FK
        numeric coverage_amount
        smallint term_months "CHECK 1..60"
    }
    insurance_agents ||--o{ agent_subscriptions : "agent_id"
    insurance_agents ||--o{ agent_interests : "agent_id"
    insurance_agents ||--o{ dossier_shares : "agent_id"
    agent_interests ||--o{ dossier_shares : "agent_interest_id"
    agent_interests ||--o{ policy_quotes : "agent_interest_id"
    dossier_shares ||--o{ policy_quotes : "dossier_share_id"
```

FREE tier is the *absence* of an ACTIVE `user_subscriptions` row — no FREE rows stored.

## payment_db (payment-service)

```mermaid
erDiagram
    payments {
        uuid id PK
        uuid user_id "soft ref -> auth.users"
        varchar purpose "PRO_SUBSCRIPTION|DOSSIER_FEE|AGENT_SUBSCRIPTION"
        varchar provider_reference UK "ASGH-XXX-hex, Paystack reference"
        uuid reference_entity_id "soft ref: dossier / agent / user by purpose"
        varchar status "INITIATED|SUCCESS|FAILED"
        timestamptz dispatched_at "NULL on SUCCESS => reconciler retries"
        jsonb raw_webhook
    }
```

`reference_entity_id` is a **purpose-discriminated soft reference**: for `DOSSIER_FEE` it is
a damage.dossiers id, for `AGENT_SUBSCRIPTION` a marketplace.insurance_agents id, for
`PRO_SUBSCRIPTION` the auth user id. Settlement dispatch routes on `purpose`.

## notification_db (notification-service)

```mermaid
erDiagram
    device_tokens {
        uuid id PK
        uuid user_id "soft ref -> auth.users"
        varchar fcm_token "UX WHERE active"
    }
    notification_preferences {
        uuid id PK
        uuid user_id UK
    }
    tip_templates {
        uuid id PK
        varchar category
        varchar applies_season "HARMATTAN|RAINY|ANY"
    }
    tips {
        uuid id PK
        uuid user_id "soft ref"
        uuid property_id "soft ref -> property.properties"
        uuid tip_template_id FK
        varchar tip_text "frozen copy at delivery"
        timestamptz read_at
    }
    flood_zones {
        uuid id PK
        numeric min_lat
        numeric max_lat
    }
    notifications {
        uuid id PK
        uuid user_id "soft ref"
        varchar type "14 event types"
        jsonb payload "deep-link data"
    }
    redoc_reminders {
        uuid property_id PK "soft ref"
    }
    tip_templates ||--o{ tips : "tip_template_id"
```

---

## Cross-service reference map

```mermaid
flowchart LR
    subgraph auth_db
      users
    end
    subgraph property_db
      properties
      assets
    end
    subgraph damage_db
      damage_reports
      photo_pairs
      dossiers
    end
    subgraph marketplace_db
      insurance_agents
      agent_interests
      dossier_shares
      subscriptions[agent/user_subscriptions]
    end
    subgraph payment_db
      payments
    end
    subgraph notification_db
      tips_notifs[tips / notifications / device_tokens]
    end

    properties -- owner_user_id --> users
    damage_reports -- property_id --> properties
    photo_pairs -- "asset_id (+ frozen snapshot)" --> assets
    dossiers -- payment_id --> payments
    insurance_agents -- user_id --> users
    agent_interests -- property_id --> properties
    dossier_shares -- dossier_id --> dossiers
    subscriptions -- last_payment_id --> payments
    payments -- "reference_entity_id (by purpose)" --> dossiers
    payments -- "reference_entity_id (by purpose)" --> insurance_agents
    tips_notifs -- "user_id / property_id" --> users
```

Every arrow is a soft UUID reference validated by internal APIs (X-Internal-Api-Key),
never a database constraint. Consistency across boundaries is eventual and idempotent:
settlement replays are no-ops (`last_payment_id`), agent sync retries until consumed
(`pending_agent_details.consumed_at`), payment dispatch retries until acknowledged
(`payments.dispatched_at`).

---

## Design review findings (2026-07)

### Sound by design (do not "fix")
- **No cross-service FKs** — correct for database-per-service; the one violation ever made
  (marketplace `last_payment_id → payments` FK surviving the payment-service extraction)
  broke settlement and was dropped in `marketplace V3`.
- **Deliberate denormalization for evidence immutability**: `photo_pairs.asset_snapshot`,
  `tips.tip_text`, `dossiers.total_estimated_loss` are frozen copies. Normalizing them
  would let later edits rewrite history.
- **Partial unique indexes as state machines**: one ACTIVE subscription per agent/user, one
  PENDING interest per (agent, property), one active share per (dossier, agent), phone
  uniqueness only among non-deleted users (frees numbers on account deletion).
- **Optimistic locking** (`version`) on every mutable aggregate root.
- Otherwise the schema is 3NF: no repeating groups, no partial or transitive dependencies
  on non-key attributes.

### Gaps found → fixed by new migrations
| Migration | Fix | Why |
|---|---|---|
| `payment V2__payment_indexes` | `ix_payments_user (user_id, created_at DESC)` | billing history endpoint full-scanned |
| `payment V2__payment_indexes` | partial `ix_payments_undispatched` | reconciler scan; index holds only unhealthy rows |
| `marketplace V4__marketplace_indexes` | `ix_quotes_interest`, `ix_quotes_share` | Postgres does **not** auto-index FK columns; quote lists scanned |
| `marketplace V4__marketplace_indexes` | `ix_shares_agent` (partial, active) | agent's shared-dossier list had no agent-leading index |
| `marketplace V4__marketplace_indexes` | `ix_interests_property` | owner opt-out / lead detail path |
| `auth V3__otp_lookup_index` | `(phone, purpose, created_at DESC)` partial, replaces old | actual lookup includes purpose + recency sort |

### Future (not blocking)
- `assets` GPS proximity search uses a btree on (lat, lng) — fine at current scale; at
  10k+ assets per area, move to PostGIS `GIST` on a geography column.
- Cross-service purge saga: deleting an auth user leaves property/damage/marketplace rows
  keyed to a dead user id (documented limitation of the immediate-deactivation design).
- Table partitioning for `notifications`/`tips` by month once they exceed ~1M rows.
