#!/bin/bash
# Runs once on first postgres container start (docker-entrypoint-initdb.d).
# Creates one database + one role per service. Each role gets ALL privileges
# on its OWN database only — no service can reach another service's data.
set -euo pipefail

create_service_db() {
  local db="$1" role="$2" password="$3"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE ROLE ${role} WITH LOGIN PASSWORD '${password}';
    CREATE DATABASE ${db} OWNER ${role};
    REVOKE CONNECT ON DATABASE ${db} FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${role};
EOSQL

  # Postgres 15 removed default CREATE on the public schema for non-owners;
  # grant it explicitly inside the service's own database.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" <<-EOSQL
    GRANT ALL ON SCHEMA public TO ${role};
EOSQL

  echo "Provisioned database '${db}' for role '${role}'"
}

create_service_db auth_db         auth_svc         "$AUTH_DB_PASSWORD"
create_service_db property_db     property_svc     "$PROPERTY_DB_PASSWORD"
create_service_db damage_db       damage_svc       "$DAMAGE_DB_PASSWORD"
create_service_db marketplace_db  marketplace_svc  "$MARKETPLACE_DB_PASSWORD"
create_service_db notification_db notification_svc "$NOTIFICATION_DB_PASSWORD"
