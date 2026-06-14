#!/usr/bin/env bash
# =============================================================================
# Runs the AssetShield security audit suite with newman (no local Node needed —
# uses the postman/newman Docker image). Exits non-zero on ANY failed assertion.
#
#   ./e2e/security/run.sh                       # against localhost:8080, mock mode
#   GATEWAY_URL=http://host:8080 ./e2e/security/run.sh
#   PAYMENTS_MODE=paystack ./e2e/security/run.sh   # payment-gated groups self-skip
#
# Mounts e2e/ into the container so the collection can read e2e/fixtures/*.jpg.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."          # → assetshield-backend
E2E_DIR="$(pwd)/e2e"

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
PAYMENTS_MODE="${PAYMENTS_MODE:-mock}"
# JWT_SECRET is needed to mint a genuinely-expired token for the 401 test.
if [ -z "${JWT_SECRET:-}" ] && [ -f .env ]; then
  JWT_SECRET="$(grep -E '^JWT_SECRET=' .env | head -1 | cut -d= -f2-)"
fi
: "${JWT_SECRET:?JWT_SECRET required (export it or put it in .env)}"

# From inside the container, reach the host gateway.
HOST_URL="$GATEWAY_URL"
case "$GATEWAY_URL" in
  *localhost*|*127.0.0.1*) HOST_URL="http://host.docker.internal:8080" ;;
esac

echo "▸ waiting for gateway health at $GATEWAY_URL/actuator/health"
for i in $(seq 1 30); do
  if curl -fsS "$GATEWAY_URL/actuator/health" >/dev/null 2>&1; then echo "  gateway healthy"; break; fi
  [ "$i" = 30 ] && { echo "!! gateway not healthy after 30 tries"; exit 1; }
  sleep 2
done

# Mint an expired HS256 JWT signed with JWT_SECRET (exp in the past).
echo "▸ minting expired access token"
EXPIRED_TOKEN="$(JWT_SECRET="$JWT_SECRET" python - <<'PY'
import os, time, hmac, hashlib, base64, json
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=')
secret = os.environ['JWT_SECRET'].encode()
header = b64(json.dumps({"alg":"HS256","typ":"JWT"},separators=(',',':')).encode())
now = int(time.time())
payload = b64(json.dumps({"sub":"00000000-0000-0000-0000-000000000000","role":"OWNER",
                          "phone":"+233200000000","iat":now-7200,"exp":now-3600},
                         separators=(',',':')).encode())
sig = b64(hmac.new(secret, header+b'.'+payload, hashlib.sha256).digest())
print((header+b'.'+payload+b'.'+sig).decode())
PY
)"

echo "▸ running newman (payments mode: $PAYMENTS_MODE)"
# On Git Bash, MSYS rewrites container-side paths (/etc/newman) into Windows
# paths; disable that and pass a curl/docker-friendly mount source.
HOST_E2E="$E2E_DIR"
command -v cygpath >/dev/null 2>&1 && HOST_E2E="$(cygpath -m "$E2E_DIR")"
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "$HOST_E2E":/etc/newman \
  -w /etc/newman \
  postman/newman:alpine \
  run security/security-suite.postman_collection.json \
  --environment security/local.postman_environment.json \
  --env-var "gatewayUrl=$HOST_URL" \
  --env-var "paymentsMode=$PAYMENTS_MODE" \
  --env-var "expiredToken=$EXPIRED_TOKEN" \
  --working-dir /etc/newman \
  --delay-request 700 \
  --reporters cli

echo "✓ security suite passed"
