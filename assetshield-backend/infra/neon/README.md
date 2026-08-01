# Hosting the databases on Neon

Neon is serverless PostgreSQL. This moves the six service databases off the local
Docker Postgres and onto a managed, always-available host — the first step toward
hosting the whole backend. **Nothing in the app code changes**; each service is
pointed at Neon purely through environment variables.

## Why this fits us so well

- Images live in **object storage (Supabase), not Postgres**, so the databases hold
  only rows and hashes. All six fit easily inside Neon's free 0.5 GB.
- Our **database-per-service** isolation maps 1:1 onto Neon: one project, one compute
  endpoint, six databases + six roles. `auth_svc` still cannot see `property_db`.

---

## Step 1 — Create the project and provision

1. Create a Neon project (region close to your users, e.g. `eu-central-1`).
2. Open the **SQL Editor**, connected to the default database (`neondb`) as the
   default owner role (`neondb_owner`).
3. Run [`provision.sql`](./provision.sql) after replacing every `CHANGE_ME_*` with a
   strong password. Keep those passwords — they become the service env vars below.
   **Run it with `psql`, not the Neon SQL Editor** — the editor wraps the paste in a
   single transaction and `CREATE DATABASE` cannot run inside one:
   `psql "postgresql://neondb_owner:<pw>@<HOST>/neondb?sslmode=require" -f provision.sql`

This creates `auth_db … payment_db` and their roles.

## Step 2 — Get the connection host

In the Neon dashboard, copy the connection string. It looks like:

```
postgresql://neondb_owner:***@ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require
```

The host you need is the part between `@` and `/`:
`ep-cool-name-123456.eu-central-1.aws.neon.tech`. **All six databases share this
same host** — only the database name and role differ.

> **Use the DIRECT host, not the `-pooler` one.** Neon's pooled endpoint runs
> PgBouncer in transaction mode, which breaks Hibernate's server-side prepared
> statements and Flyway's locks. For these Java services, always use the direct
> (non-pooler) host. (If you ever must use the pooler, append
> `&prepareThreshold=0` — but keep Flyway on the direct host.)

## Step 3 — Point each service at Neon (env vars only)

Set these per service. `SPRING_DATASOURCE_*` are standard Spring env names and
**override** the values baked into `application.yml`, so no code change is needed.

For local Docker this is already wired up in [`docker-compose.neon.yml`](../../docker-compose.neon.yml)
(reads `NEON_*` vars from `.env`); run the stack against Neon with:
`docker compose --profile core -f docker-compose.yml -f docker-compose.neon.yml up -d`.
The table below is the equivalent for a non-Docker host (e.g. Azure).

Replace `<HOST>` with your direct Neon host from Step 2.

| Service | `SPRING_DATASOURCE_URL` | `SPRING_DATASOURCE_USERNAME` |
|---|---|---|
| auth-service | `jdbc:postgresql://<HOST>/auth_db?sslmode=require` | `auth_svc` |
| property-service | `jdbc:postgresql://<HOST>/property_db?sslmode=require` | `property_svc` |
| damage-service | `jdbc:postgresql://<HOST>/damage_db?sslmode=require` | `damage_svc` |
| marketplace-service | `jdbc:postgresql://<HOST>/marketplace_db?sslmode=require` | `marketplace_svc` |
| notification-service | `jdbc:postgresql://<HOST>/notification_db?sslmode=require` | `notification_svc` |
| payment-service | `jdbc:postgresql://<HOST>/payment_db?sslmode=require` | `payment_svc` |

Plus, for every service:

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_PASSWORD=<the CHANGE_ME_* password you set for that role>
```

> `?sslmode=require` is mandatory — Neon refuses non-SSL connections. The JDBC
> driver already bundled with the services supports it; nothing to add.

## Step 4 — First boot

On first connection each service runs its Flyway migrations against its Neon
database and creates its schema; `ddl-auto=validate` then checks the mapping.
Watch the logs for `Successfully applied N migrations`. After that the schema
exists and subsequent boots are fast.

To smoke-test one service locally against Neon before deploying:

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://<HOST>/auth_db?sslmode=require' \
SPRING_DATASOURCE_USERNAME=auth_svc \
SPRING_DATASOURCE_PASSWORD='<auth password>' \
OTP_CHANNEL=log \
mvn -pl auth-service spring-boot:run
```

If it starts and migrates, Neon is wired correctly.

---

## Good to know

- **Autosuspend:** on the free tier the compute scales to zero after ~5 min idle.
  The first query after idle has a short cold-start (sub-second to a couple of
  seconds). Fine for a demo/pilot; a paid plan removes it.
- **Branches:** Neon can branch the database like git. Handy later for a staging
  copy, not needed now.
- **Backups:** Neon keeps point-in-time history automatically; our local
  `assetshield-backup` container is no longer needed once you're on Neon.
- **Internal isolation is unchanged** — this only swaps *where* Postgres runs. The
  gateway, internal API keys and the 5-field lead projection all behave identically.
