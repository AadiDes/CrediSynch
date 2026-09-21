// Latency finding (docs/FINDINGS.md): the full synchronous decision pipeline, ~20 rps for 2 min
// against the live deployed backend. Run with the k6 Docker image:
//
//   docker run --rm -i -e API_BASE_URL=http://13.200.182.78/api \
//     -e KEYCLOAK_URL=http://13.200.182.78/keycloak \
//     grafana/k6 run - < findings/latency_pipeline.js
//
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const API_BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api';
const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8081';

export const decisionLatencyMs = new Trend('decision_latency_ms');

export const options = {
  scenarios: {
    steady_load: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 30,
      maxVUs: 60,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

let token;

export function setup() {
  const res = http.post(
    `${KEYCLOAK_URL}/realms/credisynch/protocol/openid-connect/token`,
    { grant_type: 'password', client_id: 'credisynch-web', username: 'applicant', password: 'applicant123' },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
  return { token: res.json('access_token') };
}

export default function (data) {
  const ref = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const body = JSON.stringify({
    externalRef: ref,
    channel: 'WEB',
    applicant: { fullName: 'Load Test', email: `${ref}@example.com`, phone: '+919876500000' },
    device: { deviceFingerprint: `k6-device-${ref}` },
    features: { velocity_6h: 2 },
  });
  const res = http.post(`${API_BASE_URL}/v1/applications`, body, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'Idempotency-Key': `k6-${ref}`,
    },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
  if (res.status === 200) {
    const parsed = res.json();
    decisionLatencyMs.add(parsed.latencyMs);
  }
}
