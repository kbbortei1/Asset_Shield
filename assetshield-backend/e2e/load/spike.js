// k6 spike: 0 → 200 VUs in 30s on GET /properties to observe how the gateway
// and the stack behave under a sudden burst. Intentionally has NO pass/fail
// threshold — it documents behaviour. NOTE: the gateway's token-bucket rate
// limiter applies to the public AUTH paths only (login/register/refresh…), so
// /properties is NOT throttled here — this spike characterises raw read
// throughput and latency under burst, not the limiter. (A burst against
// /auth/login would instead surface 429 RATE_LIMITED once the per-IP bucket
// drains; see e2e/security for the limiter's correctness test.)
//   run via e2e/load/run.sh spike
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE = `${__ENV.BASE_URL}/api/v1`;
const OWNER = { headers: { Authorization: `Bearer ${__ENV.OWNER_TOKEN}` } };

const status2xx = new Counter('status_2xx');
const status429 = new Counter('status_429');
const statusOther = new Counter('status_other');

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 200 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 0 },
      ],
    },
  },
};

export default function () {
  const r = http.get(`${BASE}/properties?size=20`, OWNER);
  if (r.status >= 200 && r.status < 300) status2xx.add(1);
  else if (r.status === 429) status429.add(1);
  else statusOther.add(1);
}
