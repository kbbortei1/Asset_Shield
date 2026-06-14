// k6 dossier timing (FR11 / NFR): build a 10-pair completed report, request the
// dossier (mock auto-settles), poll status, and assert READY within 20s.
// Runs as a single iteration (1 VU). Requires mock payments (the default demo
// profile). Seeded asset IDs come from e2e/load/run.sh via ASSET_IDS.
//   run via e2e/load/run.sh dossier-timing
import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, fail } from 'k6';
import { sleep } from 'k6';

const BASE = `${__ENV.BASE_URL}/api/v1`;
const H = { Authorization: `Bearer ${__ENV.OWNER_TOKEN}` };
const JSONH = Object.assign({ 'Content-Type': 'application/json' }, H);
const PID = __ENV.PROPERTY_ID;
const ASSET_IDS = (__ENV.ASSET_IDS || '').split(',').filter(Boolean);
// 10 DISTINCT images (each a unique sha256 → no DUPLICATE_PHOTO_HASH within the
// report). open() + hashing must run in init context (top level), not default().
const PHOTOS = [];
for (let i = 0; i < 10; i++) {
  const bytes = open(`./fixtures/load-${i}.jpg`, 'b');
  PHOTOS.push({ bytes, hash: crypto.sha256(bytes, 'hex') });
}

export const options = {
  scenarios: { timing: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '90s' } },
  thresholds: { checks: ['rate==1'] },
};

export default function () {
  if (ASSET_IDS.length < 10) fail(`need >=10 seeded assets, got ${ASSET_IDS.length}`);

  // 1. create report
  let r = http.post(`${BASE}/properties/${PID}/damage-reports`,
    JSON.stringify({ disasterType: 'FLOOD', description: 'load timing', occurredAt: '2026-06-10T02:30:00Z' }),
    { headers: JSONH });
  check(r, { 'report created': (x) => x.status === 201 });
  const reportId = r.json().data.id;

  // 2. upload 10 distinct damage photos + pair each to a seeded asset
  for (let i = 0; i < 10; i++) {
    const meta = JSON.stringify({
      sha256Hash: PHOTOS[i].hash, gpsLat: 5.546112, gpsLng: -0.211668,
      capturedAt: '2026-06-10T07:00:00Z', description: `loss ${i}`,
    });
    const up = http.post(`${BASE}/damage-reports/${reportId}/photos`, {
      file: http.file(PHOTOS[i].bytes, `d${i}.jpg`, 'image/jpeg'),
      metadata: meta,
    }, { headers: H });
    check(up, { [`photo ${i} uploaded`]: (x) => x.status === 201 });
    const photoId = up.json().data.photo.id;
    const pair = http.post(`${BASE}/damage-reports/${reportId}/pairs`,
      JSON.stringify({ damagePhotoId: photoId, assetId: ASSET_IDS[i], pairingMethod: 'MANUAL' }),
      { headers: JSONH });
    check(pair, { [`pair ${i} created`]: (x) => x.status === 201 });
  }

  // 3. complete
  r = http.put(`${BASE}/damage-reports/${reportId}/complete`, null, { headers: H });
  check(r, { 'report completed': (x) => x.status === 200 });

  // 4. generate dossier and start the clock
  r = http.post(`${BASE}/damage-reports/${reportId}/generate-dossier`, null, { headers: H });
  check(r, { 'dossier requested': (x) => x.status === 201 });
  const dossierId = r.json().data.dossierId;
  const start = Date.now();

  // 5. poll status until READY, asserting <= 20s
  let status = 'PENDING_PAYMENT';
  for (let i = 0; i < 40; i++) {
    const s = http.get(`${BASE}/dossiers/${dossierId}/status`, { headers: H });
    status = s.json().data.status;
    if (status === 'READY' || status === 'FAILED') break;
    sleep(0.5);
  }
  const elapsedMs = Date.now() - start;
  console.log(`dossier ${dossierId} reached ${status} in ${elapsedMs} ms`);
  check(null, {
    'dossier READY (not FAILED)': () => status === 'READY',
    'READY within 20s (FR11)': () => status === 'READY' && elapsedMs <= 20000,
  });
}
