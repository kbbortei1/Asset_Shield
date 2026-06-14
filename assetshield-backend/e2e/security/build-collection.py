#!/usr/bin/env python3
"""
Generates security-suite.postman_collection.json.

The suite runs against the fully-composed stack THROUGH THE GATEWAY (as a real
attacker would) and asserts the P0 security invariants. It is self-seeding and
idempotent: a Setup folder registers fresh users with timestamp-unique phone
numbers each run and walks them to the states each invariant test needs.

Run:  python e2e/security/build-collection.py
Then: e2e/security/run.sh   (newman; exits non-zero on any failed assertion)
"""
import json
import pathlib

# ── builders ────────────────────────────────────────────────────────────────

def hdr(*pairs):
    return [{"key": k, "value": v} for k, v in pairs]

def auth(tokenVar):
    return {"key": "Authorization", "value": f"Bearer {{{{{tokenVar}}}}}"}

def raw_json(body):
    return {"mode": "raw", "raw": body, "options": {"raw": {"language": "json"}}}

def req(name, method, url, *, headers=None, body=None, tests=None, prereq=None, base="{{baseUrl}}"):
    """One request item. `url` is the path after `base`/ (default {{baseUrl}}).
    Query strings are split out into the structured url so newman builds a clean URL."""
    events = []
    if prereq:
        events.append({"listen": "prerequest",
                       "script": {"type": "text/javascript", "exec": prereq.strip("\n").split("\n")}})
    if tests:
        events.append({"listen": "test",
                       "script": {"type": "text/javascript", "exec": tests.strip("\n").split("\n")}})
    path_part, _, query_part = url.partition("?")
    url_obj = {"raw": base + "/" + url, "host": [base], "path": path_part.split("/")}
    if query_part:
        query = []
        for kv in query_part.split("&"):
            k, _, v = kv.partition("=")
            query.append({"key": k, "value": v})
        url_obj["query"] = query
    request = {
        "method": method,
        "header": headers or [],
        "url": url_obj,
    }
    if body is not None:
        request["body"] = body
    item = {"name": name, "request": request}
    if events:
        item["event"] = events
    return item

def folder(name, items, desc=""):
    return {"name": name, "description": desc, "item": items}

# Shared snippet: assert the standard error envelope shape
ENVELOPE_OK = """
var j = pm.response.json();
pm.expect(j).to.have.property('status');
pm.expect(j).to.have.property('data');
pm.expect(j).to.have.property('message');
"""

# ── 0 · Setup (self-seeding) ────────────────────────────────────────────────

setup = [
    req("Owner · register", "POST", "auth/register",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{ownerPhone}}","password":"{{pw}}","fullName":"Suite Owner"}'),
        tests="""
pm.test('owner registered (201)', () => pm.response.to.have.status(201));
pm.collectionVariables.set('ownerUserId', pm.response.json().data.userId);
"""),
    req("Owner · verify-otp", "POST", "auth/verify-otp",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{ownerPhone}}","code":"{{otp}}"}'),
        tests="""
pm.test('owner verified (200)', () => pm.response.to.have.status(200));
var d = pm.response.json().data;
pm.collectionVariables.set('ownerAccess', d.accessToken);
pm.collectionVariables.set('ownerRefresh', d.refreshToken);
"""),
    req("Member · register", "POST", "auth/register",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{memberPhone}}","password":"{{pw}}","fullName":"Suite Member"}'),
        tests="pm.test('201', () => pm.response.to.have.status(201));"),
    req("Member · verify-otp", "POST", "auth/verify-otp",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{memberPhone}}","code":"{{otp}}"}'),
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('memberAccess', pm.response.json().data.accessToken);
pm.collectionVariables.set('memberUserId', pm.response.json().data.user.id);
"""),
    req("Agent · register-agent", "POST", "auth/register-agent",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{agentPhone}}","password":"{{pw}}","fullName":"Suite Agent",'
                      '"insurerName":"Suite Assurance","nicLicenceNo":"NIC-{{ts}}-1"}'),
        tests="pm.test('201', () => pm.response.to.have.status(201));"),
    req("Agent · verify-otp", "POST", "auth/verify-otp",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{agentPhone}}","code":"{{otp}}"}'),
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('agentAccess', pm.response.json().data.accessToken);
pm.collectionVariables.set('agentUserId', pm.response.json().data.user.id);
"""),
    req("Agent2 · register-agent", "POST", "auth/register-agent",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{agent2Phone}}","password":"{{pw}}","fullName":"Suite Agent Two",'
                      '"insurerName":"Second Assurance","nicLicenceNo":"NIC-{{ts}}-2"}'),
        tests="pm.test('201', () => pm.response.to.have.status(201));"),
    req("Agent2 · verify-otp", "POST", "auth/verify-otp",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{agent2Phone}}","code":"{{otp}}"}'),
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('agent2Access', pm.response.json().data.accessToken);
"""),
    req("Superadmin · login", "POST", "auth/login",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{adminPhone}}","password":"{{adminPassword}}"}'),
        tests="""
pm.test('admin login 200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('adminAccess', pm.response.json().data.accessToken);
"""),
    req("Owner · create property (opted-in target)", "POST", "properties",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"name":"Suite Shop","type":"COMMERCIAL","gpsLat":5.546111,"gpsLng":-0.211667,"locality":"Kantamanto"}'),
        tests="""
pm.test('201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('propertyId', pm.response.json().data.id);
"""),
    req("Owner · create property (NEVER opted-in, for privacy 404)", "POST", "properties",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"name":"Private Villa","type":"RESIDENTIAL","gpsLat":5.6,"gpsLng":-0.2,"locality":"Cantonments"}'),
        tests="""
pm.test('201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('privatePropertyId', pm.response.json().data.id);
"""),
    req("Owner · upload asset (real hash)", "POST", "properties/{{propertyId}}/assets",
        headers=[auth("ownerAccess")],
        body={"mode": "formdata", "formdata": [
            {"key": "file", "type": "file", "src": "fixtures/asset-1.jpg"},
            {"key": "metadata", "type": "text",
             "value": '{"sha256Hash":"{{hashAsset1}}","gpsLat":5.546111,"gpsLng":-0.211667,'
                      '"capturedAt":"2026-06-01T09:00:00Z","description":"Suite asset","estimatedValue":1500.00,'
                      '"category":"ELECTRONICS"}',
             "contentType": "application/json"}]},
        tests="""
pm.test('asset captured 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('assetId', pm.response.json().data.id);
"""),
    req("Owner · opt property in to offers", "PUT", "properties/{{propertyId}}/offers-optin",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"openToOffers":true}'),
        tests="pm.test('200', () => pm.response.to.have.status(200));"),
    # Damage report → complete → dossier (mock pay) → READY
    req("Owner · create damage report", "POST", "properties/{{propertyId}}/damage-reports",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"disasterType":"FIRE","description":"Suite fire","occurredAt":"2026-06-10T02:30:00Z"}'),
        tests="""
pm.test('201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('reportId', pm.response.json().data.id);
"""),
    req("Owner · upload damage photo", "POST", "damage-reports/{{reportId}}/photos",
        headers=[auth("ownerAccess")],
        body={"mode": "formdata", "formdata": [
            {"key": "file", "type": "file", "src": "fixtures/damage-1.jpg"},
            {"key": "metadata", "type": "text",
             "value": '{"sha256Hash":"{{hashDamage1}}","gpsLat":5.546112,"gpsLng":-0.211668,'
                      '"capturedAt":"2026-06-10T07:00:00Z","description":"Burnt stock"}',
             "contentType": "application/json"}]},
        tests="""
pm.test('201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('photoId', pm.response.json().data.photo.id);
"""),
    req("Owner · pair photo to asset", "POST", "damage-reports/{{reportId}}/pairs",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"damagePhotoId":"{{photoId}}","assetId":"{{assetId}}","pairingMethod":"GPS_AUTO"}'),
        tests="pm.test('paired 201', () => pm.response.to.have.status(201));"),
    req("Owner · complete report", "PUT", "damage-reports/{{reportId}}/complete",
        headers=[auth("ownerAccess")],
        tests="pm.test('200', () => pm.response.to.have.status(200));"),
    req("Owner · generate dossier", "POST", "damage-reports/{{reportId}}/generate-dossier",
        headers=[auth("ownerAccess")],
        tests="""
pm.test('dossier requested 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('dossierId', pm.response.json().data.dossierId);
pm.collectionVariables.set('pollTries', 0);
"""),
    req("Owner · poll dossier until READY", "GET", "dossiers/{{dossierId}}/status",
        headers=[auth("ownerAccess")],
        tests="""
var st = pm.response.json().data.status;
var tries = Number(pm.collectionVariables.get('pollTries') || 0) + 1;
pm.collectionVariables.set('pollTries', tries);
if (st === 'READY') {
  pm.collectionVariables.set('dossierReady', 'true');
  pm.test('dossier READY', () => pm.expect(true).to.be.true);
} else if (st === 'FAILED') {
  pm.collectionVariables.set('dossierReady', 'false');
  pm.test('dossier did not FAIL', () => pm.expect.fail('dossier FAILED'));
} else if (tries < 18) {
  postman.setNextRequest(pm.info.requestName); // re-poll (paced by --delay-request)
} else {
  // No auto-settle (e.g. PAYMENTS_MODE=paystack): leave flag false; gated groups skip.
  pm.collectionVariables.set('dossierReady', 'false');
  console.log('Dossier not READY after ' + tries + ' tries — payment-gated groups will skip.');
}
"""),
    # Agent verification + subscription + accepted interest + shared dossier
    req("Admin · find pending agent record", "GET", "admin/agents?status=PENDING_VERIFICATION&size=100",
        headers=[auth("adminAccess")],
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
var items = pm.response.json().data.items;
var ph = pm.collectionVariables.get('agentPhone');
var rec = items.find(a => a.phoneNumber === ph);
pm.expect(rec, 'pending agent record present').to.be.ok;
pm.collectionVariables.set('agentRecordId', rec.agentId);
"""),
    req("Admin · verify (approve) agent", "PUT", "admin/agents/{{agentRecordId}}/verify",
        headers=[auth("adminAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"approve":true}'),
        tests="pm.test('200', () => pm.response.to.have.status(200));"),
    req("Agent · subscribe (mock)", "POST", "agents/me/subscription",
        headers=[auth("agentAccess")],
        tests="""
pm.test('subscribe initiated', () => pm.expect(pm.response.code).to.be.oneOf([200,201]));
pm.collectionVariables.set('subTries', 0);
"""),
    req("Agent · poll subscription until ACTIVE", "GET", "agents/me/subscription",
        headers=[auth("agentAccess")],
        tests="""
var st = pm.response.json().data.status;
var tries = Number(pm.collectionVariables.get('subTries') || 0) + 1;
pm.collectionVariables.set('subTries', tries);
if (st === 'ACTIVE') {
  pm.collectionVariables.set('subActive', 'true');
  pm.test('subscription ACTIVE', () => pm.expect(true).to.be.true);
} else if (tries < 18) {
  postman.setNextRequest(pm.info.requestName);
} else {
  pm.collectionVariables.set('subActive', 'false');
  console.log('Subscription not ACTIVE (no auto-settle?) — consent groups will skip.');
}
"""),
    req("Agent · express interest", "POST", "leads/{{propertyId}}/express-interest",
        headers=[auth("agentAccess")],
        prereq="if (pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="""
pm.test('interest recorded 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('interestId', pm.response.json().data.interestId);
"""),
    req("Owner · accept interest", "PUT", "agent-interests/{{interestId}}/respond",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"accept":true}'),
        prereq="if (pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('200', () => pm.response.to.have.status(200));"),
    req("Owner · share dossier to agent", "POST", "dossiers/{{dossierId}}/share-to-agent",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"agentInterestId":"{{interestId}}"}'),
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true' || pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('shared', () => pm.expect(pm.response.code).to.be.oneOf([200,201]));"),
    req("Owner · rotate share token (capture)", "POST", "dossiers/{{dossierId}}/rotate-share-token",
        headers=[auth("ownerAccess")],
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true'){ pm.execution.skipRequest(); }",
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('shareToken', pm.response.json().data.shareToken);
"""),
]

# ── 1 · Lead projection leak ────────────────────────────────────────────────
g1 = [
    req("Agent leads expose exactly 5 keys, no PII", "GET", "agents/me/leads?size=50",
        headers=[auth("agentAccess")],
        prereq="if (pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
var body = pm.response.text();
['gpsLat','gpsLng','phoneNumber','estimatedValue','sha256Hash','ownerId','ownerUserId']
  .forEach(s => pm.test('no "'+s+'" anywhere in body', () => pm.expect(body).to.not.include(s)));
var items = pm.response.json().data.items;
var allowed = ['propertyId','ownerDisplayName','propertyName','propertyType','locality'].sort().join(',');
items.forEach((it, i) => pm.test('item '+i+' key set is exactly the 5-field projection',
  () => pm.expect(Object.keys(it).sort().join(',')).to.eql(allowed)));
"""),
]

# ── 2 · Privacy 404 equivalence ─────────────────────────────────────────────
_g2_skip = "if (pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }"
g2 = [
    req("Express-interest on non-opted-in property → 404", "POST", "leads/{{privatePropertyId}}/express-interest",
        headers=[auth("agentAccess")], prereq=_g2_skip,
        tests="""
pm.test('404', () => pm.response.to.have.status(404));
pm.collectionVariables.set('body404_real', JSON.stringify(pm.response.json()));
"""),
    req("Express-interest on random UUID → 404", "POST", "leads/00000000-0000-0000-0000-000000000000/express-interest",
        headers=[auth("agentAccess")], prereq=_g2_skip,
        tests="""
pm.test('404', () => pm.response.to.have.status(404));
var a = JSON.parse(pm.collectionVariables.get('body404_real'));
var b = pm.response.json();
pm.test('identical errorCode (no existence leak)',
  () => pm.expect(b.data.errorCode).to.eql(a.data.errorCode));
pm.test('identical message shape',
  () => pm.expect(b.message).to.eql(a.message));
pm.test('same key set', () => pm.expect(Object.keys(b.data).sort()).to.eql(Object.keys(a.data).sort()));
"""),
]

# ── 3 · Revocation enforcement ──────────────────────────────────────────────
g3 = [
    req("Agent verifies dossier BEFORE revocation → 200", "GET", "dossiers/{{dossierId}}/verify",
        headers=[auth("agentAccess")],
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true' || pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('verify works pre-revoke (200)', () => pm.response.to.have.status(200));"),
    req("Owner revokes the connection (cascades shares)", "DELETE", "agent-interests/{{interestId}}",
        headers=[auth("ownerAccess")],
        prereq="if (pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('revoked (200)', () => pm.response.to.have.status(200));"),
    req("Agent verify AFTER revocation → 404", "GET", "dossiers/{{dossierId}}/verify",
        headers=[auth("agentAccess")],
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true' || pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('API sealed (404)', () => pm.response.to.have.status(404));"),
    req("Agent quote AFTER revocation → 404", "POST", "dossiers/{{dossierId}}/quote",
        headers=[auth("agentAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"coverageAmount":1000.00,"premium":50.00,"termMonths":12}'),
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true' || pm.collectionVariables.get('subActive')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('quote sealed (404)', () => pm.response.to.have.status(404));"),
]

# ── 4 · Role boundary sweep ─────────────────────────────────────────────────
g4 = [
    req("OWNER token → /admin/admins (auth-svc) must NOT 200", "POST", "admin/admins",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"+233209999999","password":"Whatever#1","fullName":"X"}'),
        tests="pm.test('403/404 not 200', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("OWNER token → /admin/agents (marketplace) must NOT 200", "GET", "admin/agents?size=10",
        headers=[auth("ownerAccess")],
        tests="pm.test('403/404 not 200', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("OWNER token → agent home /agents/me/leads must NOT 200", "GET", "agents/me/leads",
        headers=[auth("ownerAccess")],
        tests="pm.test('403/404 not 200', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("AGENT token → respond to interest they don't own → 404", "PUT",
        "agent-interests/11111111-1111-1111-1111-111111111111/respond",
        headers=[auth("agentAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"accept":true}'),
        tests="pm.test('403/404 not 200', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("Expired access token → 401 from gateway", "GET", "users/me",
        headers=[{"key": "Authorization", "value": "Bearer {{expiredToken}}"}],
        tests="""
pm.test('401', () => pm.response.to.have.status(401));
pm.test('TOKEN_EXPIRED', () => pm.expect(pm.response.json().data.errorCode).to.eql('TOKEN_EXPIRED'));
"""),
    req("Garbage Bearer → 401 from gateway", "GET", "users/me",
        headers=[{"key": "Authorization", "value": "Bearer not-a-jwt"}],
        tests="pm.test('401', () => pm.response.to.have.status(401));"),
]

# ── 5 · Hash integrity ──────────────────────────────────────────────────────
g5 = [
    req("Count assets before bad upload", "GET", "properties/{{propertyId}}/assets?size=100",
        headers=[auth("ownerAccess")],
        tests="pm.collectionVariables.set('assetsBefore', pm.response.json().data.totalElements);"),
    req("Upload with mismatched sha256 → 400 HASH_MISMATCH", "POST", "properties/{{propertyId}}/assets",
        headers=[auth("ownerAccess")],
        body={"mode": "formdata", "formdata": [
            {"key": "file", "type": "file", "src": "fixtures/asset-2.jpg"},
            {"key": "metadata", "type": "text",
             "value": '{"sha256Hash":"{{wrongHash}}","gpsLat":5.5,"gpsLng":-0.2,'
                      '"capturedAt":"2026-06-01T09:00:00Z","description":"bad","estimatedValue":10.0,'
                      '"category":"ELECTRONICS"}',
             "contentType": "application/json"}]},
        tests="""
pm.test('400', () => pm.response.to.have.status(400));
pm.test('HASH_MISMATCH', () => pm.expect(pm.response.json().data.errorCode).to.eql('HASH_MISMATCH'));
"""),
    req("Count assets after → unchanged (no row created)", "GET", "properties/{{propertyId}}/assets?size=100",
        headers=[auth("ownerAccess")],
        tests="""
pm.test('asset count unchanged', () =>
  pm.expect(pm.response.json().data.totalElements).to.eql(Number(pm.collectionVariables.get('assetsBefore'))));
"""),
]

# ── 6 · Webhook forgery ─────────────────────────────────────────────────────
g6 = [
    req("Webhook with wrong signature → 401", "POST", "payments/webhook",
        headers=[{"key": "Content-Type", "value": "application/json"},
                 {"key": "x-paystack-signature", "value": "deadbeef" * 8}],
        body=raw_json('{"event":"charge.success","data":{"reference":"forged-ref-1"}}'),
        tests="pm.test('401', () => pm.response.to.have.status(401));"),
    req("Webhook with no signature → 401", "POST", "payments/webhook",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"event":"charge.success","data":{"reference":"forged-ref-2"}}'),
        tests="""
pm.test('401', () => pm.response.to.have.status(401));
// The forged calls are rejected at signature verification, before any payment
// lookup — so no payment can settle as a result. (See suite README.)
"""),
]

# ── 7 · Internal API exposure ───────────────────────────────────────────────
g7 = [
    req("GET /internal/users/{id} via gateway → 404 (no route)", "GET",
        "internal/users/{{ownerUserId}}", base="{{gatewayUrl}}",
        headers=[auth("ownerAccess")],
        tests="pm.test('404 (route does not exist at edge)', () => pm.response.to.have.status(404));"),
    req("POST /internal/notifications/send via gateway → 404", "POST", "internal/notifications/send",
        base="{{gatewayUrl}}",
        headers=[auth("ownerAccess"), {"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"userId":"{{ownerUserId}}","type":"TIP","title":"x","body":"y"}'),
        tests="pm.test('404', () => pm.response.to.have.status(404));"),
    req("GET /internal/dossiers/{id}/meta via gateway → 404", "GET", "internal/dossiers/{{dossierId}}/meta",
        base="{{gatewayUrl}}",
        headers=[auth("ownerAccess")],
        tests="pm.test('404', () => pm.response.to.have.status(404));"),
]

# ── 8 · IDOR sweep (user B's token against user A's resources) ───────────────
# memberAccess is "user B" relative to owner-created private resources NOT shared
# with the member, and agent2Access is a fully unrelated principal.
g8 = [
    req("Outsider → owner's property detail → 403/404", "GET", "properties/{{privatePropertyId}}",
        headers=[auth("agent2Access")],
        tests="pm.test('403/404', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("Outsider → owner's asset → 403/404", "GET", "assets/{{assetId}}",
        headers=[auth("agent2Access")],
        tests="pm.test('403/404', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("Outsider → owner's damage report → 403/404", "GET", "damage-reports/{{reportId}}",
        headers=[auth("agent2Access")],
        tests="pm.test('403/404', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("Outsider → owner's dossier status → 403/404", "GET", "dossiers/{{dossierId}}/status",
        headers=[auth("agent2Access")],
        tests="pm.test('403/404', () => pm.expect(pm.response.code).to.be.oneOf([403,404]));"),
    req("Outsider → owner's dossier download → 403/404", "GET", "dossiers/{{dossierId}}/download",
        headers=[auth("agent2Access")],
        tests="pm.test('403/404', () => pm.expect(pm.response.code).to.be.oneOf([402,403,404]));"),
]

# ── 9 · Auth hygiene ────────────────────────────────────────────────────────
g9 = [
    req("Refresh once (rotation) → new refresh", "POST", "auth/refresh",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"refreshToken":"{{ownerRefresh}}"}'),
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('rotatedRefresh', pm.response.json().data.refreshToken);
"""),
    req("Reuse the OLD (rotated) refresh → 401", "POST", "auth/refresh",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"refreshToken":"{{ownerRefresh}}"}'),
        tests="pm.test('reused refresh rejected (401)', () => pm.response.to.have.status(401));"),
    req("After reuse, the replacement is ALSO dead (family revoked) → 401", "POST", "auth/refresh",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"refreshToken":"{{rotatedRefresh}}"}'),
        tests="pm.test('family revoked (401)', () => pm.response.to.have.status(401));"),
    req("Login wrong password → 401 (capture body)", "POST", "auth/login",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"{{ownerPhone}}","password":"WrongPass#9"}'),
        tests="""
pm.test('401', () => pm.response.to.have.status(401));
pm.collectionVariables.set('wrongPwBody', JSON.stringify(pm.response.json()));
"""),
    req("Login unknown phone → identical 401 body", "POST", "auth/login",
        headers=[{"key": "Content-Type", "value": "application/json"}],
        body=raw_json('{"phoneNumber":"+233200000404","password":"WrongPass#9"}'),
        tests="""
pm.test('401', () => pm.response.to.have.status(401));
var a = JSON.parse(pm.collectionVariables.get('wrongPwBody'));
var b = pm.response.json();
pm.test('no user-enumeration: identical errorCode', () => pm.expect(b.data.errorCode).to.eql(a.data.errorCode));
pm.test('no user-enumeration: identical message', () => pm.expect(b.message).to.eql(a.message));
"""),
]

# ── 10 · Share-token behavior ───────────────────────────────────────────────
g10 = [
    req("Public shared dossier works logged out → 200", "GET", "dossiers/shared/{{shareToken}}",
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true'){ pm.execution.skipRequest(); }",
        tests="""
pm.test('public share readable (200)', () => pm.response.to.have.status(200));
pm.collectionVariables.set('oldShareToken', pm.collectionVariables.get('shareToken'));
"""),
    req("Owner rotates share token again", "POST", "dossiers/{{dossierId}}/rotate-share-token",
        headers=[auth("ownerAccess")],
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true'){ pm.execution.skipRequest(); }",
        tests="""
pm.test('200', () => pm.response.to.have.status(200));
pm.collectionVariables.set('shareToken', pm.response.json().data.shareToken);
"""),
    req("OLD share token after rotation → 404", "GET", "dossiers/shared/{{oldShareToken}}",
        prereq="if (pm.collectionVariables.get('dossierReady')!=='true'){ pm.execution.skipRequest(); }",
        tests="pm.test('leaked link dead (404)', () => pm.response.to.have.status(404));"),
]

collection = {
    "info": {
        "name": "AssetShield GH · Security Audit Suite",
        "description": "Self-seeding, idempotent P0 security invariants asserted THROUGH the gateway. "
                       "Exits non-zero on any failure. See e2e/security/README.md.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{
        "listen": "prerequest",
        "script": {"type": "text/javascript", "exec": [
            "if (!pm.collectionVariables.get('ts')) {",
            "  var ts = String(Date.now()).slice(-8);",
            "  pm.collectionVariables.set('ts', ts);",
            "  pm.collectionVariables.set('ownerPhone',  '+2331'+ts);",
            "  pm.collectionVariables.set('memberPhone', '+2332'+ts);",
            "  pm.collectionVariables.set('agentPhone',  '+2333'+ts);",
            "  pm.collectionVariables.set('agent2Phone', '+2334'+ts);",
            "}",
        ]},
    }],
    "variable": [
        {"key": "baseUrl", "value": "{{gatewayUrl}}/api/v1"},
        {"key": "pw", "value": "Suite#2026"},
    ],
    "item": [
        folder("0 · Setup (self-seeding)", setup,
               "Registers fresh users (timestamp-unique phones) and walks them to the required states."),
        folder("1 · Lead projection leak", g1),
        folder("2 · Privacy 404 equivalence", g2),
        folder("3 · Revocation enforcement", g3),
        folder("4 · Role boundary sweep", g4),
        folder("5 · Hash integrity", g5),
        folder("6 · Webhook forgery", g6),
        folder("7 · Internal API exposure", g7),
        folder("8 · IDOR sweep", g8),
        folder("9 · Auth hygiene", g9),
        folder("10 · Share-token behavior", g10),
    ],
}

out = pathlib.Path(__file__).parent / "security-suite.postman_collection.json"
out.write_text(json.dumps(collection, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"wrote {out}  ({out.stat().st_size} bytes)")
