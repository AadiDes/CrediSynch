// Latency finding (docs/FINDINGS.md): the model service's own scoring latency, isolated from the
// rest of the pipeline, ~20 rps for 2 min against the live deployed ML service.
//
//   docker run --rm -i -e ML_BASE_URL=http://13.200.182.78/ml \
//     grafana/k6 run - < findings/latency_ml.js
//
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const ML_BASE_URL = __ENV.ML_BASE_URL || 'http://localhost:8000';

export const scoredInMs = new Trend('scored_in_ms');

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

export default function () {
  const ref = `k6-ml-${__VU}-${__ITER}-${Date.now()}`;
  const body = JSON.stringify({ application_id: ref, features: { velocity_6h: 2 } });
  const res = http.post(`${ML_BASE_URL}/score`, body, { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'status is 200': (r) => r.status === 200 });
  if (res.status === 200) {
    scoredInMs.add(res.json('scored_in_ms'));
  }
}
