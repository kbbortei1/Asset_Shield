#!/usr/bin/env bash
# =============================================================================
# Seeds the load-test fixtures once and writes e2e/load/.seed.env with the
# tokens / IDs the k6 scripts consume. Uses the same public-API seeding pattern
# as the security suite and demo seed (register-or-login, mock payments).
# Idempotent via fixed load-test phone numbers.
#   ./e2e/load/seed.sh        (stack must be up in mock mode)
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."          # → assetshield-backend
FIX="$(pwd)/e2e/fixtures"
# Git Bash curl.exe can't read MSYS /c/... paths in -F file=@; cygpath -m gives
# a curl-friendly C:/... path. No-op on Linux/Mac.
command -v cygpath >/dev/null 2>&1 && FIX="$(cygpath -m "$FIX")"
GW="${GATEWAY:-http://localhost:8080}/api/v1"

OWNER_PHONE="+233200000011"; OWNER_PW="Load#Owner2026"
AGENT_PHONE="+233200000013"; AGENT_PW="Load#Agent2026"
ADMIN_PHONE="${SUPERADMIN_PHONE:-+233200000000}"; ADMIN_PW="${SUPERADMIN_PASSWORD:-SuperAdmin#2026}"
OTP="${OTP_DEV_CODE:-123456}"
H1="a3ba97e55531671a32b6c36d9738a81c053b29b2a8b847a7e4812673f9f1fad0"

jget() { python -c "import sys,json
d=json.load(sys.stdin)
for k in '''$1'''.split('.'):
    if k=='': continue
    d=d[int(k)] if isinstance(d,list) else d.get(k)
    if d is None: break
print(d if d is not None else '')"; }
api() { local m="$1" p="$2" b="${3:-}" t="${4:-}"; local a=(-s -X "$m" "$GW$p")
  [ -n "$t" ] && a+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && a+=(-H 'Content-Type: application/json' -d "$b")
  curl "${a[@]}"; }
reglog() { local ph="$1" pw="$2" nm="$3" ag="${4:-}"
  if [ -n "$ag" ]; then api POST /auth/register-agent "{\"phoneNumber\":\"$ph\",\"password\":\"$pw\",\"fullName\":\"$nm\",$ag}" >/dev/null || true
  else api POST /auth/register "{\"phoneNumber\":\"$ph\",\"password\":\"$pw\",\"fullName\":\"$nm\"}" >/dev/null || true; fi
  api POST /auth/verify-otp "{\"phoneNumber\":\"$ph\",\"code\":\"$OTP\"}" >/dev/null 2>&1 || true
  api POST /auth/login "{\"phoneNumber\":\"$ph\",\"password\":\"$pw\"}" | jget data.accessToken; }

echo "▸ seeding load fixtures → $GW"
OWNER_AT="$(reglog "$OWNER_PHONE" "$OWNER_PW" "Load Owner")"
AGENT_AT="$(reglog "$AGENT_PHONE" "$AGENT_PW" "Load Agent" "\"insurerName\":\"Load Assurance\",\"nicLicenceNo\":\"NIC-LOAD-1\"")"
ADMIN_AT="$(api POST /auth/login "{\"phoneNumber\":\"$ADMIN_PHONE\",\"password\":\"$ADMIN_PW\"}" | jget data.accessToken)"

# property (get-or-create)
PID="$(api GET '/properties?size=50' '' "$OWNER_AT" | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((p['id'] for p in d if p['name']=='Load Test Property'),''))")"
[ -z "$PID" ] && PID="$(api POST /properties '{"name":"Load Test Property","type":"COMMERCIAL","gpsLat":5.546111,"gpsLng":-0.211667,"locality":"Kantamanto"}' "$OWNER_AT" | jget data.id)"

# Ensure 10 DISTINCT assets exist (each load-N.jpg has a unique sha256 so the
# per-property duplicate-hash guard doesn't reject them). Idempotent: re-uploads
# 409 on an already-seeded property and we just reuse the existing rows.
POSIX_FIX="$(pwd)/e2e/fixtures"
for n in $(seq 0 9); do
  H="$(sha256sum "$POSIX_FIX/load-$n.jpg" | cut -d' ' -f1)"
  curl -s -o /dev/null -X POST "$GW/properties/$PID/assets" -H "Authorization: Bearer $OWNER_AT" \
    -F "file=@$FIX/load-$n.jpg;type=image/jpeg" \
    -F "metadata={\"sha256Hash\":\"$H\",\"gpsLat\":5.546111,\"gpsLng\":-0.211667,\"capturedAt\":\"2026-06-01T09:00:00Z\",\"description\":\"load asset $n\",\"estimatedValue\":100.00,\"category\":\"ELECTRONICS\"};type=application/json" || true
done
mapfile -t IDS < <(api GET "/properties/$PID/assets?size=100" '' "$OWNER_AT" | python -c "import sys,json
print('\n'.join(a['id'] for a in json.load(sys.stdin)['data']['items']))")
ASSET_IDS="$(IFS=,; echo "${IDS[*]:0:10}")"

# verify+subscribe agent, opt-in, accepted interest so leads/agents endpoints are hot
AGENT_REC="$(api GET '/admin/agents?size=100' '' "$ADMIN_AT" | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((a['agentId'] for a in d if a['phoneNumber']=='$AGENT_PHONE'),''))")"
[ -n "$AGENT_REC" ] && api PUT "/admin/agents/$AGENT_REC/verify" '{"approve":true}' "$ADMIN_AT" >/dev/null || true
if [ "$(api GET /agents/me/subscription '' "$AGENT_AT" | jget data.status)" != "ACTIVE" ]; then
  api POST /agents/me/subscription '' "$AGENT_AT" >/dev/null || true
  for _ in $(seq 1 15); do [ "$(api GET /agents/me/subscription '' "$AGENT_AT" | jget data.status)" = "ACTIVE" ] && break; sleep 1; done
fi
api PUT "/properties/$PID/offers-optin" '{"openToOffers":true}' "$OWNER_AT" >/dev/null || true
api POST "/leads/$PID/express-interest" '' "$AGENT_AT" >/dev/null || true

cat > e2e/load/.seed.env <<EOF
BASE_URL=${GATEWAY:-http://localhost:8080}
OWNER_TOKEN=$OWNER_AT
AGENT_TOKEN=$AGENT_AT
PROPERTY_ID=$PID
ASSET_IDS=$ASSET_IDS
EOF
echo "▸ wrote e2e/load/.seed.env (property=$PID, assets=10)"
