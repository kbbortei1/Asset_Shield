#!/usr/bin/env bash
# =============================================================================
# Runs a k6 load script (smoke | spike | dossier-timing) via the grafana/k6
# Docker image. Seeds first (idempotent) unless e2e/load/.seed.env exists.
#   ./e2e/load/run.sh smoke
#   ./e2e/load/run.sh spike
#   ./e2e/load/run.sh dossier-timing
#   RESEED=1 ./e2e/load/run.sh smoke      # force re-seed
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."          # → assetshield-backend
LOAD_DIR="$(pwd)/e2e/load"
FIX_DIR="$(pwd)/e2e/fixtures"
SCRIPT="${1:-smoke}"
case "$SCRIPT" in smoke|spike|dossier-timing) ;; *) echo "usage: run.sh smoke|spike|dossier-timing"; exit 2;; esac

GATEWAY_URL="${GATEWAY:-http://localhost:8080}"
echo "▸ waiting for gateway health"
for i in $(seq 1 30); do curl -fsS "$GATEWAY_URL/actuator/health" >/dev/null 2>&1 && break; [ "$i" = 30 ] && { echo "gateway not healthy"; exit 1; }; sleep 2; done

if [ "${RESEED:-0}" = "1" ] || [ ! -f e2e/load/.seed.env ]; then
  GATEWAY="$GATEWAY_URL" ./e2e/load/seed.sh
fi
set -a; . e2e/load/.seed.env; set +a

# Inside the container, reach the host gateway.
case "$BASE_URL" in *localhost*|*127.0.0.1*) CONTAINER_BASE="http://host.docker.internal:8080" ;; *) CONTAINER_BASE="$BASE_URL" ;; esac

echo "▸ k6 $SCRIPT  (base=$CONTAINER_BASE)"
# Git Bash: stop MSYS rewriting container paths; pass docker-friendly mount srcs.
HOST_LOAD="$LOAD_DIR"; HOST_FIX="$FIX_DIR"
if command -v cygpath >/dev/null 2>&1; then HOST_LOAD="$(cygpath -m "$LOAD_DIR")"; HOST_FIX="$(cygpath -m "$FIX_DIR")"; fi
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm -i \
  --add-host=host.docker.internal:host-gateway \
  -v "$HOST_LOAD":/scripts -v "$HOST_FIX":/scripts/fixtures -w /scripts \
  -e BASE_URL="$CONTAINER_BASE" \
  -e OWNER_TOKEN="$OWNER_TOKEN" \
  -e AGENT_TOKEN="$AGENT_TOKEN" \
  -e PROPERTY_ID="$PROPERTY_ID" \
  -e ASSET_IDS="$ASSET_IDS" \
  grafana/k6 run "/scripts/$SCRIPT.js"
