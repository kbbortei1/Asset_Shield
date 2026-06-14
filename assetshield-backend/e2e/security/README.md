# Security audit suite

A **newman-runnable** Postman collection that asserts AssetShield GH's P0 security
invariants **through the gateway**, exactly as a real attacker reaches the system.
These are end-to-end edge tests; they complement (do not replace) the per-service
unit/integration tests.

```bash
# stack up in the self-contained demo profile (mock payments), then:
./e2e/security/run.sh            # newman in Docker; exits non-zero on any failure
PAYMENTS_MODE=paystack ./e2e/security/run.sh   # real-provider mode (see note)
```

No local Node is needed — `run.sh` uses the `postman/newman` Docker image and mounts
`e2e/` so the collection can read the fixture JPEGs. `run.ps1` is the Windows twin.

## What it checks

| Folder | Invariant |
|--------|-----------|
| 0 · Setup | Self-seeds fresh users (timestamp-unique phones), a property, assets, a completed report + READY dossier, a verified+subscribed agent with an accepted interest and a shared dossier. Idempotent across runs. |
| 1 · Lead projection leak | Agent leads expose **exactly** `{propertyId, ownerDisplayName, propertyName, propertyType, locality}` and the raw body contains none of `gpsLat/phoneNumber/estimatedValue/sha256Hash`. |
| 2 · Privacy 404 equivalence | Express-interest on a real non-opted-in property and on a random UUID return **byte-identical** 404 envelopes (no existence leak). |
| 3 · Revocation enforcement | After the owner revokes the connection, the agent's dossier verify/quote calls return 404 — the API is sealed immediately. |
| 4 · Role boundary sweep | OWNER token against admin + agent-gated routes → 403/404 (never 200); AGENT acting on a resource it doesn't own → 404; expired token → 401 `TOKEN_EXPIRED`; garbage Bearer → 401. |
| 5 · Hash integrity | Mismatched sha256 → 400 `HASH_MISMATCH`, and **no asset row is created** (count unchanged). |
| 6 · Webhook forgery | Wrong signature → 401; missing signature → 401. |
| 7 · Internal API exposure | `/internal/**` via the gateway → 404 for every service (no edge route exists). |
| 8 · IDOR sweep | An unrelated principal against another user's property/asset/report/dossier → 403/404 on every one. |
| 9 · Auth hygiene | Reused rotated refresh → 401 **and** the replacement is also dead (family revocation); wrong-password vs unknown-phone login → identical 401 bodies. |
| 10 · Share-token behavior | Public dossier link works logged out; after rotation the old token → 404. |

## Two-mode runs

Run it **twice**:

1. **Default demo (`mock`)** — the bulletproof CodeQuest path. *All* folders run and pass.
2. **Real-provider (`paystack`)** — `PAYMENTS_MODE=paystack ./e2e/security/run.sh`. Storage
   round-trips to Supabase and Paystack is the live wire. Because Paystack test-mode
   checkouts don't auto-settle, the **payment-gated** steps (a READY dossier, an ACTIVE
   subscription) can't complete unattended, so folders that need them
   (1, 2, 3, and 10, plus the dossier-share setup) **self-skip** via `pm.execution.skipRequest()`
   and the suite prints a console note. (Folder 2's privacy-404 check needs an ACTIVE
   agent subscription to reach the 404 path — without it the subscription gate returns
   403 first — so it's gated too.) Every signature/role/IDOR/hash/webhook/auth invariant
   (4, 5, 6, 7, 8, 9) runs identically and must pass in both modes.

## Notes & known semantics

- **Signed-URL tail.** Revocation seals the *API* instantly, but a signed download URL
  already minted to the agent stays valid until its TTL (15 min) expires — this is
  inherent to pre-signed URLs. The suite asserts the API is sealed; the 15-min URL tail
  is an accepted, documented property, mitigated by the short TTL and share-token rotation.
- **Webhook forgery & settlement.** The forged webhook calls are rejected at signature
  verification *before any payment lookup*, so they cannot settle a payment. In `mock`
  mode the mock provider auto-settles legitimate payments after ~2s independently of the
  webhook, which is why the invariant asserted here is **rejection of the forgery**, not a
  race against auto-settle.
- **Fixtures.** `e2e/fixtures/*.jpg` are deterministic JPEGs written by `GenFixtures.java`
  (ImageIO, so they decode in the dossier PDF builder). Their sha256 values live in
  `local.postman_environment.json`; `wrongHash` is the deliberate mismatch constant.
- **Expired token.** `run.sh` mints a genuinely-expired HS256 token from `JWT_SECRET`
  (read from `.env`) and injects it as `expiredToken`, so the 401 test exercises real
  expiry rather than a malformed string.
