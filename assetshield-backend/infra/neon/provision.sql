-- ============================================================================
-- AssetShield GH — Neon provisioning
-- ----------------------------------------------------------------------------
-- Recreates the local database-per-service layout (infra/postgres/init/01-init.sh)
-- on a single Neon project: one database + one LOGIN role per service, so a
-- service can only ever reach its own data.
--
-- IMPORTANT — two Neon/PG gotchas this file accounts for:
--   (1) CREATE DATABASE cannot run inside a transaction block, and Neon's SQL
--       Editor runs a multi-statement paste as ONE transaction. So do NOT paste
--       this whole file into the SQL Editor. Run it with psql (autocommit), or
--       run the CREATE DATABASE lines one at a time.
--   (2) On PostgreSQL 16+, to CREATE DATABASE ... OWNER <role> you must be a
--       MEMBER of <role>. Hence the GRANT ... TO neondb_owner lines before, and
--       the REVOKE ... after (to restore isolation).
--
-- HOW TO RUN (once), the reliable way — via psql from any machine:
--   psql "postgresql://neondb_owner:<pw>@<HOST>/neondb?sslmode=require" -f provision.sql
-- Replace every 'CHANGE_ME_*' with a strong, unique password FIRST and keep them
-- safe — they become the service datasource passwords (see infra/neon/README.md).
-- ============================================================================

-- ── roles: LOGIN only, no superuser / createdb / createrole ────────────────
CREATE ROLE auth_svc         WITH LOGIN PASSWORD 'CHANGE_ME_auth';
CREATE ROLE property_svc     WITH LOGIN PASSWORD 'CHANGE_ME_property';
CREATE ROLE damage_svc       WITH LOGIN PASSWORD 'CHANGE_ME_damage';
CREATE ROLE marketplace_svc  WITH LOGIN PASSWORD 'CHANGE_ME_marketplace';
CREATE ROLE notification_svc WITH LOGIN PASSWORD 'CHANGE_ME_notification';
CREATE ROLE payment_svc      WITH LOGIN PASSWORD 'CHANGE_ME_payment';

-- ── PG16: become a member of each role so we may create databases it owns ───
GRANT auth_svc         TO neondb_owner;
GRANT property_svc     TO neondb_owner;
GRANT damage_svc       TO neondb_owner;
GRANT marketplace_svc  TO neondb_owner;
GRANT notification_svc TO neondb_owner;
GRANT payment_svc      TO neondb_owner;

-- ── databases: one per service, owned by its role ──────────────────────────
-- Because the role OWNS its database it can create objects in the public schema
-- (no extra GRANT needed — the PG15 public-schema restriction only bites
-- non-owners). Flyway then builds the schema on first boot; ddl-auto=validate
-- checks it. NOTE: run these six one at a time if using the Neon SQL Editor.
CREATE DATABASE auth_db         OWNER auth_svc;
CREATE DATABASE property_db     OWNER property_svc;
CREATE DATABASE damage_db       OWNER damage_svc;
CREATE DATABASE marketplace_db  OWNER marketplace_svc;
CREATE DATABASE notification_db OWNER notification_svc;
CREATE DATABASE payment_db      OWNER payment_svc;

-- ── lock down: nobody but the owner may even CONNECT ───────────────────────
REVOKE CONNECT ON DATABASE auth_db         FROM PUBLIC;
REVOKE CONNECT ON DATABASE property_db     FROM PUBLIC;
REVOKE CONNECT ON DATABASE damage_db       FROM PUBLIC;
REVOKE CONNECT ON DATABASE marketplace_db  FROM PUBLIC;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE payment_db      FROM PUBLIC;

-- ── restore isolation: drop the temporary role memberships ─────────────────
REVOKE auth_svc         FROM neondb_owner;
REVOKE property_svc     FROM neondb_owner;
REVOKE damage_svc       FROM neondb_owner;
REVOKE marketplace_svc  FROM neondb_owner;
REVOKE notification_svc FROM neondb_owner;
REVOKE payment_svc      FROM neondb_owner;

-- Done. Six isolated databases + roles. Each service migrates its own schema via
-- Flyway on first connect — no manual DDL needed here.
