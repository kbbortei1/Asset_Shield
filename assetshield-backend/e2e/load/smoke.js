// k6 golden read-path smoke. 50 VUs / 2 min over the hot read endpoints.
// Thresholds encode NFR03 (p95 < 500ms) and a <1% error budget.
//   run via e2e/load/run.sh smoke   (grafana/k6 Docker image)
import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE = `${__ENV.BASE_URL}/api/v1`;
const OWNER = { headers: { Authorization: `Bearer ${__ENV.OWNER_TOKEN}` } };
const AGENT = { headers: { Authorization: `Bearer ${__ENV.AGENT_TOKEN}` } };
const PID = __ENV.PROPERTY_ID;

export const options = {
  scenarios: {
    golden_reads: { executor: 'constant-vus', vus: 50, duration: '2m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],   // NFR03
    http_req_failed: ['rate<0.01'],     // <1% errors
    checks: ['rate>0.99'],
  },
};

export default function () {
  group('list properties', () => {
    const r = http.get(`${BASE}/properties?size=20`, OWNER);
    check(r, { 'properties 200': (x) => x.status === 200 });
  });
  group('property detail', () => {
    const r = http.get(`${BASE}/properties/${PID}`, OWNER);
    check(r, { 'detail 200': (x) => x.status === 200 });
  });
  group('tips feed', () => {
    const r = http.get(`${BASE}/tips/feed?size=20`, OWNER);
    check(r, { 'tips 200': (x) => x.status === 200 });
  });
  group('agent leads', () => {
    const r = http.get(`${BASE}/agents/me/leads?size=20`, AGENT);
    check(r, { 'leads 200': (x) => x.status === 200 });
  });
  sleep(1);
}
