# k6 load smoke — results

Environment: local laptop, Docker Desktop, full `core` stack in the self-contained
`local`/`mock`/`log` profile. **These are latency-shape numbers, not capacity numbers** —
NFR04's 5,000-concurrent target is a *deployed-environment* goal, not a laptop goal
(see the note in [README.md](../../README.md)). The smoke validates the **shape** of
the golden read path under moderate concurrency.

Captured 2026-06-14, full `core` stack, default `local`/`mock`/`log` profile.

| Script | What it proves | Threshold | Result |
|--------|----------------|-----------|--------|
| `smoke.js` | golden read path p95 latency + error budget | `p(95)<500ms` (NFR03), errors `<1%` | ✅ **p95 272.98ms, 0% errors, 100% checks** |
| `spike.js` | gateway/stack survive a 0→200 VU burst | none (observational) | ✅ 29,503 reqs all 2xx, gateway stayed healthy |
| `dossier-timing.js` | 10-pair dossier reaches READY | `≤20s` (FR11) | ✅ **READY in ~2.7s** |

---

## smoke.js  (50 VUs / 2 min)

```
█ THRESHOLDS
  ✓ 'rate>0.99'  rate=100.00%
  http_req_duration  ✓ 'p(95)<500'  p(95)=272.98ms
  http_req_failed    ✓ 'rate<0.01'  rate=0.00%

checks_total.......: 17524   144.82/s    checks_succeeded: 100.00% (17524/17524)
http_req_duration..: avg=92.21ms med=51.65ms p(90)=201.04ms p(95)=272.98ms max=2.6s
http_req_failed....: 0.00%  (0/17524)
iterations.........: 4381    36.21/s
```
✅ Meets NFR03 (p95 < 500 ms) with comfortable headroom; zero errors over ~17.5k checks.

## spike.js  (0→200 VUs / ~60s)

```
status_2xx.........: 29503  491.80/s        (status_429: 0, status_other: 0)
http_req_duration..: avg=270.23ms med=229.04ms p(90)=523.88ms p(95)=630.36ms max=1.79s
http_reqs..........: 29503  491.80/s
vus_max............: 200
```
Observational: under a 0→200 VU burst on `GET /properties` the gateway stayed healthy and
served every request (no 5xx, no dropped connections). No 429s — `/properties` is not a
rate-limited path (the limiter guards the auth endpoints only). p95 rises to ~630 ms under
burst, as expected on a single laptop; this characterises latency shape under load, not
absolute capacity.

## dossier-timing.js  (1 iteration, 10 pairs)

```
dossier <id> reached READY in 2740 ms
  ✓ dossier READY (not FAILED)
  ✓ READY within 20s (FR11)
```
✅ A 10-pair dossier (10 distinct hash-verified damage photos paired to 10 documented
assets) is generated and reaches READY in ~2.7 s — well within the FR11 ≤20 s budget.

---

**Note on NFR04 (5,000 concurrent):** that is a *deployed-environment* target, not a laptop
target. These runs validate latency **shape** and the dossier pipeline, not absolute capacity.
The services are stateless and horizontally scalable; the only per-instance state is the
in-memory auth rate-limiter (would move to a shared store in a multi-instance deployment).
