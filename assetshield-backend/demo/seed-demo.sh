#!/usr/bin/env bash
# =============================================================================
# AssetShield GH — CodeQuest demo seed
#
# Drives the FULL product story through the PUBLIC API at the gateway, so every
# hash is verified server-side, every photo is a real stored object, and the
# dossier is a real generated PDF. Idempotent via fixed demo phone numbers:
# re-running logs in instead of re-registering and reuses existing entities.
#
# Requires the stack up in the self-contained demo profile:
#   docker compose --profile core up --build      (PAYMENTS_MODE=mock etc.)
#
# Usage:
#   ./demo/seed-demo.sh                # against http://localhost:8080
#   GATEWAY=http://host:8080 ./demo/seed-demo.sh
# =============================================================================
set -euo pipefail

GW="${GATEWAY:-http://localhost:8080}/api/v1"
POSIX_FIX="$(cd "$(dirname "$0")/.." && pwd)/e2e/fixtures"   # for sha256sum (POSIX path)
FIX="$POSIX_FIX"
# On Git Bash, curl is Windows curl.exe and can't read MSYS /c/... paths in
# -F file=@; cygpath -m yields a curl-friendly C:/... path. No-op on Linux/Mac.
command -v cygpath >/dev/null 2>&1 && FIX="$(cygpath -m "$FIX")"
# sha256 of a fixture file (each evidence photo needs a unique hash, else the
# per-property/per-report duplicate-hash guards reject it).
h() { sha256sum "$POSIX_FIX/$1" | cut -d' ' -f1; }

# Fixed demo credentials (idempotent across runs) ----------------------------
OWNER_PHONE="+233200000001"; OWNER_PW="Ama#Demo2026";   OWNER_NAME="Ama Mensah"
MEMBER_PHONE="+233200000002"; MEMBER_PW="Kofi#Demo2026"; MEMBER_NAME="Kofi Mensah"
AGENT_PHONE="+233200000003"; AGENT_PW="Kojo#Demo2026";   AGENT_NAME="Kojo Asante"
ADMIN_PHONE="${SUPERADMIN_PHONE:-+233200000000}"
ADMIN_PW="${SUPERADMIN_PASSWORD:-SuperAdmin#2026}"
OTP="${OTP_DEV_CODE:-123456}"

# Fixture hashes (must match e2e/fixtures/*.jpg; regenerate via GenFixtures) --
H_ASSET1="a3ba97e55531671a32b6c36d9738a81c053b29b2a8b847a7e4812673f9f1fad0"
H_ASSET2="7f3106937ea19481efd3d2ded728c4d0fb078488f86341d03051670e35018c77"
H_DAMAGE="69221e80e3361da98407fd264e718dd202775fc9b8a28471ed27f70e3f5bce90"

c_green=$'\e[32m'; c_blue=$'\e[34m'; c_dim=$'\e[2m'; c_off=$'\e[0m'
step() { printf '%s▸ %s%s\n' "$c_blue" "$1" "$c_off"; }
info() { printf '%s  %s%s\n' "$c_dim" "$1" "$c_off"; }

# jq-free JSON field extraction via python -----------------------------------
jget() { python -c "import sys,json
d=json.load(sys.stdin)
for k in '''$1'''.split('.'):
    if k=='': continue
    d=d[int(k)] if isinstance(d,list) else d.get(k)
    if d is None: break
print(d if d is not None else '')"; }

api() { # METHOD PATH [JSON_BODY] [BEARER]
  local m="$1" p="$2" body="${3:-}" tok="${4:-}"
  local args=(-s -X "$m" "$GW$p")
  [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok")
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  curl "${args[@]}"
}

# Register (idempotent) then verify-OTP then login → echoes the access token --
register_login() { # PHONE PASSWORD NAME  [register-agent extra json or ""]
  local phone="$1" pw="$2" name="$3" agent="${4:-}"
  if [ -n "$agent" ]; then
    api POST /auth/register-agent "{\"phoneNumber\":\"$phone\",\"password\":\"$pw\",\"fullName\":\"$name\",$agent}" >/dev/null || true
  else
    api POST /auth/register "{\"phoneNumber\":\"$phone\",\"password\":\"$pw\",\"fullName\":\"$name\"}" >/dev/null || true
  fi
  # verify-otp is a no-op (and harmless) for an already-verified user
  api POST /auth/verify-otp "{\"phoneNumber\":\"$phone\",\"code\":\"$OTP\"}" >/dev/null 2>&1 || true
  api POST /auth/login "{\"phoneNumber\":\"$phone\",\"password\":\"$pw\"}" | jget data.accessToken
}

upload_asset() { # TOKEN PROPERTY_ID FILE HASH CATEGORY VALUE DESC
  curl -s -X POST "$GW/properties/$2/assets" -H "Authorization: Bearer $1" \
    -F "file=@$FIX/$3;type=image/jpeg" \
    -F "metadata={\"sha256Hash\":\"$4\",\"gpsLat\":5.546111,\"gpsLng\":-0.211667,\"capturedAt\":\"2026-06-01T09:00:00Z\",\"description\":\"$7\",\"estimatedValue\":$6,\"category\":\"$5\"};type=application/json"
}

echo "════════════════════════════════════════════════════════════════════"
echo " AssetShield GH — seeding CodeQuest demo data → $GW"
echo "════════════════════════════════════════════════════════════════════"

step "1/9  Accounts (register-or-login)"
OWNER_AT="$(register_login "$OWNER_PHONE" "$OWNER_PW" "$OWNER_NAME")"
MEMBER_AT="$(register_login "$MEMBER_PHONE" "$MEMBER_PW" "$MEMBER_NAME")"
AGENT_AT="$(register_login "$AGENT_PHONE" "$AGENT_PW" "$AGENT_NAME" "\"insurerName\":\"Hollard Ghana\",\"nicLicenceNo\":\"NIC-DEMO-0003\"")"
ADMIN_AT="$(api POST /auth/login "{\"phoneNumber\":\"$ADMIN_PHONE\",\"password\":\"$ADMIN_PW\"}" | jget data.accessToken)"
OWNER_ID="$(api GET /users/me '' "$OWNER_AT" | jget data.id)"
info "owner=$OWNER_NAME  member=$MEMBER_NAME  agent=$AGENT_NAME"
[ -n "$ADMIN_AT" ] || { echo "!! superadmin login failed — check SUPERADMIN_* env"; exit 1; }

step "2/9  Property (get-or-create): Ama's Fabrics — Kantamanto"
PID="$(api GET '/properties?size=50' '' "$OWNER_AT" \
  | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((p['id'] for p in d if p['name'].startswith(\"Ama's Fabrics\")), ''))")"
if [ -z "$PID" ]; then
  PID="$(api POST /properties \
    '{"name":"Ama'\''s Fabrics - Kantamanto","type":"COMMERCIAL","gpsLat":5.546111,"gpsLng":-0.211667,"locality":"Kantamanto"}' \
    "$OWNER_AT" | jget data.id)"
fi
info "propertyId=$PID"

step "3/9  Evidence assets (6 across 3 categories)"
ASSET_COUNT="$(api GET "/properties/$PID/assets?size=50" '' "$OWNER_AT" | jget data.totalElements)"
if [ "${ASSET_COUNT:-0}" -lt 6 ]; then
  # Six DISTINCT evidence photos (load-0..5) so none is rejected as a duplicate.
  upload_asset "$OWNER_AT" "$PID" load-0.jpg "$(h load-0.jpg)" CLOTHING_STOCK 8000.00 "Kente and wax-print stock bales" >/dev/null
  upload_asset "$OWNER_AT" "$PID" load-1.jpg "$(h load-1.jpg)" CLOTHING_STOCK 6500.00 "Ready-made garments rack"        >/dev/null
  upload_asset "$OWNER_AT" "$PID" load-2.jpg "$(h load-2.jpg)" ELECTRONICS    3200.00 "Industrial sewing machine"       >/dev/null
  upload_asset "$OWNER_AT" "$PID" load-3.jpg "$(h load-3.jpg)" ELECTRONICS    1800.00 "Steam press unit"                >/dev/null
  upload_asset "$OWNER_AT" "$PID" load-4.jpg "$(h load-4.jpg)" FURNITURE      2400.00 "Display shelving"                >/dev/null
  upload_asset "$OWNER_AT" "$PID" load-5.jpg "$(h load-5.jpg)" FURNITURE      1200.00 "Cutting tables (x3)"             >/dev/null
fi
# Three documented assets to pair the damage photos against
read -r PAIR_A PAIR_B PAIR_C <<<"$(api GET "/properties/$PID/assets?size=50" '' "$OWNER_AT" \
  | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(' '.join(a['id'] for a in d[:3]))")"
info "assets present; pairing targets=$PAIR_A,$PAIR_B,$PAIR_C"

step "4/9  Household member (invite + accept, canExport=true)"
api POST "/properties/$PID/invite" "{\"inviteePhone\":\"$MEMBER_PHONE\",\"canExport\":true}" "$OWNER_AT" >/dev/null || true
INV_ID="$(api GET /users/me/invitations '' "$MEMBER_AT" | jget data.items.0.invitationId)"
[ -n "$INV_ID" ] && api PUT "/invitations/$INV_ID/respond" '{"accept":true}' "$MEMBER_AT" >/dev/null || true
info "member joined household"

step "5/9  Marketplace opt-in"
api PUT "/properties/$PID/offers-optin" '{"openToOffers":true}' "$OWNER_AT" >/dev/null
info "property is open to offers"

step "6/9  Damage report → FIRE (reuse READY dossier if present)"
RPT="$(api GET '/users/me/damage-reports?size=50' '' "$OWNER_AT" \
  | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((r['id'] for r in d if r.get('status')=='COMPLETED'), ''))")"
if [ -z "$RPT" ]; then
  RPT="$(api POST "/properties/$PID/damage-reports" \
    '{"disasterType":"FIRE","description":"Kantamanto night fire - Block C","occurredAt":"2026-06-10T02:30:00Z"}' \
    "$OWNER_AT" | jget data.id)"
  # 3 DISTINCT damage photos (load-6/7/8) around the shop GPS
  P1="$(curl -s -X POST "$GW/damage-reports/$RPT/photos" -H "Authorization: Bearer $OWNER_AT" \
    -F "file=@$FIX/load-6.jpg;type=image/jpeg" \
    -F "metadata={\"sha256Hash\":\"$(h load-6.jpg)\",\"gpsLat\":5.546112,\"gpsLng\":-0.211668,\"capturedAt\":\"2026-06-10T07:00:00Z\",\"description\":\"Burnt stock - front\"};type=application/json" | jget data.photo.id)"
  P2="$(curl -s -X POST "$GW/damage-reports/$RPT/photos" -H "Authorization: Bearer $OWNER_AT" \
    -F "file=@$FIX/load-7.jpg;type=image/jpeg" \
    -F "metadata={\"sha256Hash\":\"$(h load-7.jpg)\",\"gpsLat\":5.546110,\"gpsLng\":-0.211666,\"capturedAt\":\"2026-06-10T07:02:00Z\",\"description\":\"Scorched shelving\"};type=application/json" | jget data.photo.id)"
  P3="$(curl -s -X POST "$GW/damage-reports/$RPT/photos" -H "Authorization: Bearer $OWNER_AT" \
    -F "file=@$FIX/load-8.jpg;type=image/jpeg" \
    -F "metadata={\"sha256Hash\":\"$(h load-8.jpg)\",\"gpsLat\":5.546114,\"gpsLng\":-0.211669,\"capturedAt\":\"2026-06-10T07:04:00Z\",\"description\":\"Water damage from hoses\"};type=application/json" | jget data.photo.id)"
  # 3 pairs: 2 GPS_AUTO (photos near documented assets) + 1 MANUAL
  api POST "/damage-reports/$RPT/pairs" "{\"damagePhotoId\":\"$P1\",\"assetId\":\"$PAIR_A\",\"pairingMethod\":\"GPS_AUTO\"}" "$OWNER_AT" >/dev/null || true
  api POST "/damage-reports/$RPT/pairs" "{\"damagePhotoId\":\"$P2\",\"assetId\":\"$PAIR_B\",\"pairingMethod\":\"GPS_AUTO\"}" "$OWNER_AT" >/dev/null || true
  api POST "/damage-reports/$RPT/pairs" "{\"damagePhotoId\":\"$P3\",\"assetId\":\"$PAIR_C\",\"pairingMethod\":\"MANUAL\"}" "$OWNER_AT" >/dev/null || true
  api PUT "/damage-reports/$RPT/complete" '' "$OWNER_AT" >/dev/null
fi
info "reportId=$RPT (COMPLETED)"

# Dossier: request → mock auto-settle → poll READY
DOS="$(api GET '/users/me/dossiers?size=50' '' "$OWNER_AT" \
  | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((x['id'] for x in d if x.get('status')=='READY'), ''))")"
if [ -z "$DOS" ]; then
  DOS="$(api POST "/damage-reports/$RPT/generate-dossier" '' "$OWNER_AT" | jget data.dossierId)"
  info "dossier requested ($DOS) — awaiting mock payment settle…"
  for i in $(seq 1 20); do
    ST="$(api GET "/dossiers/$DOS/status" '' "$OWNER_AT" | jget data.status)"
    [ "$ST" = "READY" ] && break
    [ "$ST" = "FAILED" ] && { echo "!! dossier generation FAILED"; exit 1; }
    sleep 1
  done
fi
DOS_STATUS="$(api GET "/dossiers/$DOS/status" '' "$OWNER_AT" | jget data.status)"
info "dossierId=$DOS status=$DOS_STATUS"

step "7/9  Agent verification (superadmin approves Kojo / Hollard)"
AGENT_REC="$(api GET '/admin/agents?size=50' '' "$ADMIN_AT" \
  | python -c "import sys,json
d=json.load(sys.stdin)['data']['items']
print(next((a['agentId'] for a in d if a['phoneNumber']=='$AGENT_PHONE'), ''))")"
if [ -n "$AGENT_REC" ]; then
  api PUT "/admin/agents/$AGENT_REC/verify" '{"approve":true}' "$ADMIN_AT" >/dev/null || true
fi
info "agent verified"

step "8/9  Agent subscribes (mock) and expresses interest"
SUB_STATUS="$(api GET /agents/me/subscription '' "$AGENT_AT" | jget data.status)"
if [ "$SUB_STATUS" != "ACTIVE" ]; then
  api POST /agents/me/subscription '' "$AGENT_AT" >/dev/null || true
  for i in $(seq 1 15); do
    [ "$(api GET /agents/me/subscription '' "$AGENT_AT" | jget data.status)" = "ACTIVE" ] && break
    sleep 1
  done
fi
# Express interest (404-safe if already expressed)
api POST "/leads/$PID/express-interest" '' "$AGENT_AT" >/dev/null || true
INT_ID="$(api GET /users/me/agent-interests '' "$OWNER_AT" | jget data.items.0.interestId)"
if [ -n "$INT_ID" ]; then
  api PUT "/agent-interests/$INT_ID/respond" '{"accept":true}' "$OWNER_AT" >/dev/null || true
fi
info "interest accepted (interestId=$INT_ID)"

step "9/9  Owner shares dossier → agent verifies integrity → sends quote"
api POST "/dossiers/$DOS/share-to-agent" "{\"agentInterestId\":\"$INT_ID\"}" "$OWNER_AT" >/dev/null || true
INTEGRITY="$(api GET "/dossiers/$DOS/verify" '' "$AGENT_AT" | jget data.tamperEvident)"
# One pending quote: GH₵40,000 cover / GH₵120 premium / 12 months
EXISTING_Q="$(api GET '/users/me/quotes?size=50' '' "$OWNER_AT" | jget data.totalElements)"
if [ "${EXISTING_Q:-0}" -lt 1 ]; then
  api POST "/dossiers/$DOS/quote" '{"coverageAmount":40000.00,"premium":120.00,"termMonths":12}' "$AGENT_AT" >/dev/null || true
fi
info "dossier shared; agent integrity check=${INTEGRITY:-n/a}; quote sent"

SHARE_TOKEN="$(api POST "/dossiers/$DOS/rotate-share-token" '' "$OWNER_AT" | jget data.shareToken)"
SHARE_URL="${GATEWAY:-http://localhost:8080}/api/v1/dossiers/shared/$SHARE_TOKEN"

cat <<CARD

${c_green}╔══════════════════════════════════════════════════════════════════╗
║                  AssetShield GH — DEMO READY                       ║
╚══════════════════════════════════════════════════════════════════╝${c_off}

  Base URL ........ $GW
  Swagger ......... ${GATEWAY:-http://localhost:8080}/swagger-ui.html

  OWNER  (Ama Mensah) ......... $OWNER_PHONE  /  $OWNER_PW
  MEMBER (Kofi Mensah) ........ $MEMBER_PHONE  /  $MEMBER_PW   (canExport)
  AGENT  (Kojo / Hollard) ..... $AGENT_PHONE  /  $AGENT_PW
  ADMIN  (superadmin) ......... $ADMIN_PHONE  /  $ADMIN_PW

  Property ........ Ama's Fabrics — Kantamanto   ($PID)
  Damage report ... FIRE, COMPLETED              ($RPT)
  Dossier ......... $DOS_STATUS                        ($DOS)
  Public share .... $SHARE_URL

  ${c_blue}Suggested 5-beat narrative${c_off}
   1. DOCUMENT  — Ama logs in, browses the property + 6 evidence assets
                  (each photo SHA-256 verified server-side on capture).
   2. DISASTER  — open the COMPLETED FIRE report: 3 geotagged damage photos,
                  one paired to a documented asset (frozen before/after).
   3. DOSSIER   — show the READY, payment-gated PDF (manifest hash, page count);
                  open the public share link above in a logged-out browser.
   4. CONSENT   — Kojo (verified, subscribed) expressed interest; Ama accepted,
                  then shared the dossier. Kojo verifies integrity + sends a quote.
   5. CONTROL   — Ama rotates the share token (old link dies) / revokes the
                  agent share → agent's API access is sealed immediately.

CARD
echo "${c_green}Done.${c_off}"
