#!/usr/bin/env python3
"""
Generates AssetShield.postman_collection.json — the frontend team's reference
collection. Every public endpoint, organised per service, with example bodies,
auth notes, and golden-path scripts that capture tokens / IDs so the collection
runs top-to-bottom by clicking through.

Run:  python e2e/postman/build-postman.py
"""
import json
import pathlib

BEARER = [{"key": "Authorization", "value": "Bearer {{accessToken}}"}]


def url(path):
    p, _, q = path.partition("?")
    o = {"raw": "{{baseUrl}}/" + path, "host": ["{{baseUrl}}"], "path": p.split("/")}
    if q:
        o["query"] = [{"key": k, "value": v} for k, v in (kv.partition("=")[::2] for kv in q.split("&"))]
    return o


def item(name, method, path, *, body=None, headers=None, auth_none=False, desc="", capture=None, formdata=None):
    req = {"method": method, "header": list(headers or []), "url": url(path), "description": desc}
    if auth_none:
        req["auth"] = {"type": "noauth"}
    elif headers is None:
        req["header"] = list(BEARER)
    if body is not None:
        req["header"].append({"key": "Content-Type", "value": "application/json"})
        req["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2),
                       "options": {"raw": {"language": "json"}}}
    if formdata is not None:
        req["body"] = {"mode": "formdata", "formdata": formdata}
    it = {"name": name, "request": req}
    if capture:
        it["event"] = [{"listen": "test", "script": {"type": "text/javascript",
                        "exec": capture.strip("\n").split("\n")}}]
    return it


def folder(name, items, desc=""):
    return {"name": name, "description": desc, "item": items}


# ── Auth ─────────────────────────────────────────────────────────────────────
auth = folder("Auth & Profile", [
    item("Register", "POST", "auth/register", auth_none=True,
         body={"phoneNumber": "+233201112233", "password": "Passw0rd#1", "fullName": "Akosua Owusu"},
         desc="Public. Starts registration and sends an OTP. Errors: PHONE_EXISTS(409), VALIDATION(400)."),
    item("Register agent", "POST", "auth/register-agent", auth_none=True,
         body={"phoneNumber": "+233201112244", "password": "Passw0rd#1", "fullName": "Kojo Asante",
               "insurerName": "Hollard Ghana", "nicLicenceNo": "NIC-12345"},
         desc="Public. Creates an AGENT (starts PENDING verification). Errors: PHONE_EXISTS(409)."),
    item("Verify OTP", "POST", "auth/verify-otp", auth_none=True,
         body={"phoneNumber": "+233201112233", "code": "123456"},
         capture="""
var d = pm.response.json().data;
if (d && d.accessToken) {
  pm.environment.set('accessToken', d.accessToken);
  pm.environment.set('refreshToken', d.refreshToken);
}""",
         desc="Public. Dev OTP is OTP_DEV_CODE (123456). Returns tokens. Errors: OTP_INVALID(400), OTP_EXPIRED(400)."),
    item("Resend OTP", "POST", "auth/resend-otp", auth_none=True,
         body={"phoneNumber": "+233201112233"}, desc="Public. Rate-limited at the gateway."),
    item("Login", "POST", "auth/login", auth_none=True,
         body={"phoneNumber": "+233201112233", "password": "Passw0rd#1"},
         capture="""
var d = pm.response.json().data;
if (d && d.accessToken) {
  pm.environment.set('accessToken', d.accessToken);
  pm.environment.set('refreshToken', d.refreshToken);
}""",
         desc="Public. Returns access(1h)+refresh(14d). Wrong creds → 401 (identical body for unknown phone)."),
    item("Refresh token", "POST", "auth/refresh", auth_none=True,
         body={"refreshToken": "{{refreshToken}}"},
         capture="""
var d = pm.response.json().data;
if (d && d.refreshToken) {
  pm.environment.set('accessToken', d.accessToken);
  pm.environment.set('refreshToken', d.refreshToken);
}""",
         desc="Public. Rotates the refresh token. Reuse of a rotated token → 401 REFRESH_REUSED (whole family revoked)."),
    item("Logout", "POST", "auth/logout",
         body={"refreshToken": "{{refreshToken}}"},
         desc="User. Revokes the refresh family."),
    item("Get my profile", "GET", "users/me", desc="User."),
    item("Update my profile", "PUT", "users/me", body={"fullName": "Akosua O. Owusu", "language": "en"},
         desc="User."),
    item("Upload Ghana Card", "POST", "users/me/ghana-card",
         headers=BEARER,
         formdata=[{"key": "file", "type": "file", "src": ""}],
         desc="User. multipart image/jpeg|png. Used for KYC."),
    item("Delete my account", "DELETE", "users/me",
         desc="User. Act-843 erasure request (soft-delete + purge schedule)."),
    item("Create admin (ADMIN)", "POST", "admin/admins",
         body={"phoneNumber": "+233200000111", "password": "Admin#2026", "fullName": "New Admin"},
         desc="ADMIN only. OWNER/AGENT → 403."),
])

# ── Property ─────────────────────────────────────────────────────────────────
prop = folder("Properties, Assets & Household", [
    item("Create property", "POST", "properties",
         body={"name": "Ama's Fabrics", "type": "COMMERCIAL", "gpsLat": 5.546111, "gpsLng": -0.211667,
               "locality": "Kantamanto"},
         capture="var d=pm.response.json().data; if(d&&d.id) pm.environment.set('propertyId', d.id);",
         desc="Owner. type ∈ RESIDENTIAL|COMMERCIAL|RENTAL. FREE tier: max 1 property → FREE_TIER_LIMIT(403)."),
    item("List my properties", "GET", "properties?page=0&size=20", desc="User. Owned + member properties."),
    item("Property detail", "GET", "properties/{{propertyId}}", desc="Owner/member. Per-category dashboard."),
    item("Update property", "PUT", "properties/{{propertyId}}",
         body={"name": "Ama's Fabrics — Kantamanto", "locality": "Kantamanto"}, desc="Owner only."),
    item("Delete property", "DELETE", "properties/{{propertyId}}", desc="Owner only. Soft-deletes assets+receipts."),
    item("Toggle marketplace opt-in", "PUT", "properties/{{propertyId}}/offers-optin",
         body={"openToOffers": True}, desc="Owner. Makes the property a marketplace lead."),
    item("Upload evidence asset", "POST", "properties/{{propertyId}}/assets",
         headers=BEARER,
         formdata=[{"key": "file", "type": "file", "src": ""},
                   {"key": "metadata", "type": "text",
                    "value": json.dumps({"sha256Hash": "<64-hex of the exact bytes>", "gpsLat": 5.546111,
                                         "gpsLng": -0.211667, "capturedAt": "2026-06-01T09:00:00Z",
                                         "description": "Sewing machine", "estimatedValue": 1500.00,
                                         "category": "ELECTRONICS"}),
                    "contentType": "application/json"}],
         capture="var d=pm.response.json().data; if(d&&d.id) pm.environment.set('assetId', d.id);",
         desc="Owner/export-member. multipart file + metadata JSON. sha256 MUST match the bytes → else 400 "
              "HASH_MISMATCH. Duplicate hash → 409 DUPLICATE_ASSET_HASH. See FRONTEND_HANDOFF photo recipe."),
    item("List property assets", "GET", "properties/{{propertyId}}/assets?page=0&size=20",
         desc="User. Optional &category=ELECTRONICS."),
    item("Invite household member", "POST", "properties/{{propertyId}}/invite",
         body={"inviteePhone": "+233202223344", "canExport": True}, desc="Owner only."),
    item("List members", "GET", "properties/{{propertyId}}/members", desc="Owner only."),
    item("Remove member", "DELETE", "properties/{{propertyId}}/members/{{memberUserId}}", desc="Owner only."),
    item("Asset detail", "GET", "assets/{{assetId}}", desc="Owner/member."),
    item("Update asset", "PUT", "assets/{{assetId}}",
         body={"description": "Brother industrial sewing machine", "estimatedValue": 1800.00},
         desc="Owner/export-member."),
    item("Delete asset", "DELETE", "assets/{{assetId}}", desc="Owner/export-member."),
    item("Upload asset receipt", "POST", "assets/{{assetId}}/receipts",
         headers=BEARER, formdata=[{"key": "file", "type": "file", "src": ""}],
         desc="Owner/export-member. multipart image/pdf."),
    item("My invitations", "GET", "users/me/invitations",
         capture="var its=(pm.response.json().data||{}).items||[]; if(its[0]) pm.environment.set('invitationId', its[0].invitationId);",
         desc="User. Pending household invitations."),
    item("Respond to invitation", "PUT", "invitations/{{invitationId}}/respond",
         body={"accept": True}, desc="Invitee."),
    item("Public file (token)", "GET", "public/files/{{fileToken}}", auth_none=True,
         desc="Public, token-gated download (local storage provider). Signed URLs expire ~15 min."),
])

# ── Damage ───────────────────────────────────────────────────────────────────
damage = folder("Damage Reports & Dossiers", [
    item("Create damage report", "POST", "properties/{{propertyId}}/damage-reports",
         body={"disasterType": "FIRE", "description": "Kantamanto night fire",
               "occurredAt": "2026-06-10T02:30:00Z"},
         capture="var d=pm.response.json().data; if(d&&d.id) pm.environment.set('reportId', d.id);",
         desc="Owner/export-member. disasterType ∈ FIRE|FLOOD|THEFT|STORM|OTHER. occurredAt not in future."),
    item("List property reports", "GET", "properties/{{propertyId}}/damage-reports?page=0&size=20", desc="User."),
    item("My damage reports", "GET", "users/me/damage-reports?page=0&size=20", desc="User."),
    item("Report detail", "GET", "damage-reports/{{reportId}}", desc="User. Photos + pairs (frozen before-blocks)."),
    item("Upload damage photo", "POST", "damage-reports/{{reportId}}/photos",
         headers=BEARER,
         formdata=[{"key": "file", "type": "file", "src": ""},
                   {"key": "metadata", "type": "text",
                    "value": json.dumps({"sha256Hash": "<64-hex>", "gpsLat": 5.546112, "gpsLng": -0.211668,
                                         "capturedAt": "2026-06-10T07:00:00Z", "description": "Burnt stock"}),
                    "contentType": "application/json"}],
         capture="var d=pm.response.json().data; if(d&&d.id) pm.environment.set('photoId', d.id);",
         desc="Owner/export-member. Returns GPS pairing suggestions. sha256 verified → 400 HASH_MISMATCH."),
    item("Pairing suggestions", "GET",
         "damage-reports/{{reportId}}/photos/{{photoId}}/pairing-suggestions?radiusM=25", desc="User."),
    item("Pair photo to asset", "POST", "damage-reports/{{reportId}}/pairs",
         body={"damagePhotoId": "{{photoId}}", "assetId": "{{assetId}}", "pairingMethod": "GPS_AUTO"},
         desc="Owner/export-member. pairingMethod ∈ GPS_AUTO|MANUAL. Freezes the asset snapshot."),
    item("Remove pair", "DELETE", "damage-reports/{{reportId}}/pairs/{{pairId}}", desc="Owner/export-member."),
    item("Complete report", "PUT", "damage-reports/{{reportId}}/complete",
         desc="Owner/export-member. Freezes everything + computes total loss. IMMUTABLE afterwards."),
    item("Generate dossier (pay)", "POST", "damage-reports/{{reportId}}/generate-dossier",
         capture="var d=pm.response.json().data; if(d&&d.dossierId){pm.environment.set('dossierId',d.dossierId); if(d.payment&&d.payment.reference) pm.environment.set('paymentReference', d.payment.reference);}",
         desc="Owner. Starts the fee checkout. PENDING_PAYMENT→GENERATING→READY. mock auto-settles ~2s."),
    item("Dossier status", "GET", "dossiers/{{dossierId}}/status", desc="Owner. Poll after paying."),
    item("Dossier download URL", "GET", "dossiers/{{dossierId}}/download",
         desc="Owner. READY only (402 before payment). Signed URL expires ~15 min."),
    item("Public shared dossier", "GET", "dossiers/shared/{{shareToken}}", auth_none=True,
         desc="Public. READY dossiers only. 404 after the owner rotates the token."),
    item("Rotate share token", "POST", "dossiers/{{dossierId}}/rotate-share-token",
         capture="var d=pm.response.json().data; if(d&&d.shareToken) pm.environment.set('shareToken', d.shareToken);",
         desc="Owner. Kills any leaked public link."),
    item("Retry generation", "POST", "dossiers/{{dossierId}}/retry-generation",
         desc="Owner. FAILED + paid only."),
    item("My dossiers", "GET", "users/me/dossiers?page=0&size=20", desc="User."),
    item("Public damage file (token)", "GET", "public/damage-files/{{fileToken}}", auth_none=True,
         desc="Public, token-gated damage photo download."),
])

# ── Marketplace ──────────────────────────────────────────────────────────────
market = folder("Marketplace, Consent & Payments", [
    item("Buy PRO subscription", "POST", "subscriptions/pro",
         desc="Owner. Starts PRO checkout (unlocks >1 property)."),
    item("My subscription (owner)", "GET", "users/me/subscription", desc="Owner."),
    item("List agents (ADMIN)", "GET", "admin/agents?status=PENDING_VERIFICATION&size=20",
         capture="var its=(pm.response.json().data||{}).items||[]; if(its[0]) pm.environment.set('agentRecordId', its[0].agentId);",
         desc="ADMIN. status ∈ PENDING_VERIFICATION|VERIFIED|REJECTED."),
    item("Verify agent (ADMIN)", "PUT", "admin/agents/{{agentRecordId}}/verify",
         body={"approve": True, "rejectionReason": None}, desc="ADMIN. approve=false needs rejectionReason."),
    item("Agent home", "GET", "agents/me", desc="Agent."),
    item("Agent subscription status", "GET", "agents/me/subscription", desc="Agent."),
    item("Agent subscribe", "POST", "agents/me/subscription",
         desc="Agent (verified). Starts the agent subscription checkout. mock auto-settles → ACTIVE."),
    item("Agent leads", "GET", "agents/me/leads?page=0&size=20",
         desc="Agent (verified+subscribed). 5-field projection only — no PII."),
    item("Agent interests", "GET", "agents/me/interests?page=0&size=20", desc="Agent."),
    item("Agent shared dossiers", "GET", "agents/me/shared-dossiers?page=0&size=20", desc="Agent."),
    item("Express interest", "POST", "leads/{{propertyId}}/express-interest",
         capture="var d=pm.response.json().data; if(d&&d.interestId) pm.environment.set('interestId', d.interestId);",
         desc="Agent (verified+subscribed). 404 unless the property is opted in (no existence leak)."),
    item("My agent-interests (owner)", "GET", "users/me/agent-interests?page=0&size=20",
         capture="var its=(pm.response.json().data||{}).items||[]; if(its[0]) pm.environment.set('interestId', its[0].interestId);",
         desc="Owner. Agents who expressed interest."),
    item("Respond to interest", "PUT", "agent-interests/{{interestId}}/respond",
         body={"accept": True}, desc="Owner. Accepting opens the consent channel."),
    item("Revoke interest/connection", "DELETE", "agent-interests/{{interestId}}",
         desc="Owner. Cascade-revokes shares; agent API access sealed immediately."),
    item("Share dossier to agent", "POST", "dossiers/{{dossierId}}/share-to-agent",
         body={"agentInterestId": "{{interestId}}"},
         desc="Owner. Consent-gated share of a READY dossier to an accepted agent."),
    item("Revoke dossier share", "DELETE", "dossiers/{{dossierId}}/share-to-agent/{{agentRecordId}}",
         desc="Owner."),
    item("Verify dossier integrity (agent)", "GET", "dossiers/{{dossierId}}/verify",
         desc="Agent (shared). Recomputes the manifest hash to prove tamper-evidence."),
    item("Send quote (agent)", "POST", "dossiers/{{dossierId}}/quote",
         body={"coverageAmount": 40000.00, "premium": 120.00, "termMonths": 12},
         desc="Agent (shared). termMonths 1–60."),
    item("My quotes (owner)", "GET", "users/me/quotes?page=0&size=20",
         capture="var its=(pm.response.json().data||{}).items||[]; if(its[0]) pm.environment.set('quoteId', its[0].quoteId||its[0].id);",
         desc="Owner."),
    item("Respond to quote", "PUT", "quotes/{{quoteId}}/respond", body={"accept": True}, desc="Owner."),
    item("Verify payment", "POST", "payments/{{paymentReference}}/verify",
         desc="Payer. Call after returning from the Paystack checkout."),
    item("Payment details", "GET", "payments/{{paymentReference}}", desc="Payer."),
    item("Paystack webhook", "POST", "payments/webhook", auth_none=True,
         body={"event": "charge.success", "data": {"reference": "ref"}},
         desc="Public. HMAC-SHA512 over raw bytes via x-paystack-signature. Bad/no signature → 401."),
])

# ── Notification ─────────────────────────────────────────────────────────────
notif = folder("Notifications & Tips", [
    item("Register device token", "PUT", "users/me/device-token",
         body={"fcmToken": "<fcm-device-token>", "platform": "ANDROID"},
         desc="User. Call on login and on FCM token refresh. platform ∈ ANDROID|IOS."),
    item("Delete device token", "DELETE", "users/me/device-token",
         body={"fcmToken": "<fcm-device-token>"}, desc="User. Call on logout."),
    item("Get notification prefs", "GET", "users/me/notification-preferences", desc="User."),
    item("Update notification prefs", "PUT", "users/me/notification-preferences",
         body={"tipsFrequency": "DAILY"},
         desc="User. tipsFrequency ∈ DAILY|WEEKLY|OFF."),
    item("Notification history", "GET", "users/me/notifications?page=0&size=20", desc="User."),
    item("Tips feed", "GET", "tips/feed?page=0&size=20", desc="User. Ghana-specific safety tips (English)."),
    item("Tips for a property", "GET", "properties/{{propertyId}}/tips?page=0&size=20", desc="Owner/member."),
    item("Mark tip read", "PUT", "tips/{{tipId}}/read", desc="User."),
])

collection = {
    "info": {
        "name": "AssetShield GH · API",
        "description": "Frontend reference collection for AssetShield GH. Base URL points at the gateway; "
                       "collection-level auth sends Bearer {{accessToken}} (public endpoints override to none). "
                       "Run Auth → Login (or Register → Verify OTP) first to capture tokens, then click through "
                       "Properties → Damage → Marketplace → Notifications. See FRONTEND_HANDOFF.md.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
    "variable": [{"key": "baseUrl", "value": "{{gatewayUrl}}/api/v1"}],
    "item": [auth, prop, damage, market, notif],
}

out = pathlib.Path(__file__).parent / "AssetShield.postman_collection.json"
out.write_text(json.dumps(collection, indent=2, ensure_ascii=False), encoding="utf-8")
n = sum(len(f["item"]) for f in collection["item"])
print(f"wrote {out}  ({out.stat().st_size} bytes, {n} endpoints)")
