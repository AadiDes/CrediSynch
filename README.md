# CrediSynch

Real-time fraud detection and prevention for digital lending — a working prototype built for the
Synchrony technology hackathon (problem statement 1).

**Live demo:** http://13.200.182.78 — the full analyst console, running against the real decision
pipeline on AWS (Postgres/pgvector, Keycloak, the Spring Boot API and the FastAPI model service).
See [Demo identities](#demo-identities) below for logins, and [`docs/DEMO.md`](docs/DEMO.md) for a
guided click-through.

CrediSynch decides a credit application in one synchronous call, then explains and learns
asynchronously. Its distinguishing idea: **application fraud is a graph problem wearing a tabular
costume**. A single application can look clean while five applications sharing one device, three
phone numbers and a bank account are obviously a ring. CrediSynch scores the application *and* the
linkage, then chooses the cheapest action that contains the risk, instead of declining a customer
outright.

## Architecture

```
React console (Redux Toolkit)
        |  bearer JWT (Keycloak OIDC)
        v
Spring Boot decision API  --HTTP-->  FastAPI model service (LightGBM, SHAP, graph, novelty)
        |                                   |
        |  JDBC                             |
        v                                   v
PostgreSQL + pgvector  <----------  batch jobs (Spring Batch -> Python: rings, retraining)
        ^
        |  async, off the hot path
Gemini (briefs + embeddings) -- app.llm.provider=bedrock switches to AWS Bedrock (Nova + Titan),
                                 same interfaces, no code change - see "LLM provider" below
```

Full rationale in [`docs/architecture.md`](docs/architecture.md); each individual decision is a
dated ADR in [`docs/adr/`](docs/adr/).

## Decision pipeline

| Stage | Responsibility | Budget |
|---|---|---|
| 0 | Gateway checks: JWT + role, rate limit, idempotency key, schema validation | < 10 ms |
| 1 | Deterministic rules: blocklists, velocity caps, impossible values, injection patterns | < 10 ms |
| 2 | Supervised model: fraud probability + SHAP reason codes | 20–40 ms |
| 3 | Graph linkage: shared devices/phones/addresses, ring membership | ~20 ms |
| 4 | Novelty detector: catches fraud vectors the model has never seen | ~5 ms |
| 5 | Decision policy: approve / step-up / approve-restricted / review / decline | < 5 ms |
| 6 | Respond and explain (async): case, analyst brief, similar cases | off the hot path |
| 7 | Learn (batch): labels, retraining, ring detection, drift | nightly |

Stages 0–5 are synchronous with a p95 target of 250 ms. The LLM never sits on the decision path
and never makes the decision.

## Repository layout

```
api/openapi.yaml     API contract, written before the implementation
backend/             Spring Boot 3 (Java 21): decision API, security, policy engine
ml-service/          FastAPI (Python 3.12): scoring, reason codes, graph features, novelty
ml-service/findings/ Phase 5 findings pipeline - every number in docs/FINDINGS.md is reproducible from here
frontend/            React + Redux Toolkit analyst console
infra/               docker-compose (local) and CloudFormation (AWS) - Postgres + pgvector, Keycloak
scripts/             PowerShell helpers: dev-up, verify, get-token, run-*, deploy-aws
docs/adr/            Architecture decision records
docs/FINDINGS.md     Phase 5: model quality, fairness, policy calibration, graph/latency/LLM-safety findings
docs/DEMO.md         ~7-minute guided click-through script for the submission recording (local)
docs/DECK.md         Slide-by-slide content for the submission deck (build in Google Slides)
```

## Prerequisites

Docker Desktop, JDK 21, Maven, Node 22, Python 3.12.
If Maven is missing: `winget install -e --id Apache.Maven`.

## Quickstart

```powershell
git clone https://github.com/AadiDes/CrediSynch.git
cd CrediSynch
Copy-Item .env.example .env          # local dev values only; never commit .env
.\scripts\dev-up.ps1                 # Postgres + pgvector, Keycloak with realm import

.\scripts\run-backend.ps1            # terminal 2 - http://localhost:8080
.\scripts\run-ml.ps1                 # terminal 3 - http://localhost:8000
.\scripts\run-frontend.ps1           # terminal 4 - http://localhost:5173

.\scripts\verify.ps1                 # end-to-end check of the skeleton
```

| Surface | URL |
|---|---|
| Analyst console | http://localhost:5173 |
| API + Swagger UI | http://localhost:8080/swagger-ui.html |
| API health | http://localhost:8080/actuator/health |
| Model service docs | http://localhost:8000/docs |
| Keycloak admin | http://localhost:8081 (admin / admin) |

### Demo identities

| User | Password | Role | Can do |
|---|---|---|---|
| `applicant` | `applicant123` | APPLICANT | Submit applications, answer confirmations |
| `analyst` | `analyst123` | ANALYST | Case queue, case detail, labels |
| `platform-admin` | `admin123` | ADMIN | Platform status, model registry, actuator |

The same three users work against the live demo above - it's the same realm, imported unchanged.
All synthetic: nothing in this repository is a real secret or a real person's data. Configuration
comes from the environment, AWS access comes from `aws login` locally and an instance role in the
cloud, and CI scans every push for leaked credentials.

## LLM provider

The analyst brief (case detail) and the similar-case/merchant-descriptor embeddings are behind two
provider-agnostic interfaces (`CaseNarrativeGenerator`, `EmbeddingClient`), each with a
Bedrock implementation and a Gemini implementation. One property picks which is active:

```
app.llm.provider: bedrock   # default - AWS Bedrock (Nova for briefs, Titan for embeddings)
app.llm.provider: gemini    # Gemini API - what the live demo runs on right now
```

Set via `LLM_PROVIDER` (env) or `app.llm.provider` (`application.yml`). **The live demo runs on
Gemini** because this AWS account's Bedrock model access is pending an account review with an
unpredictable timeline (see [`docs/FINDINGS.md`](docs/FINDINGS.md#module-a-vector-descriptor-matching)
and the Limitations section there) - not a code or architecture gap. Both providers implement
exactly the same contract, fall back to a deterministic template brief on any failure
(`docs/architecture.md`'s documented degraded mode), and are covered by the same test suite with
the provider mocked out. Switching is the one config line above; nothing else changes.

## Testing

```powershell
cd backend;    mvn test          # unit tests (no Docker needed)
cd backend;    mvn verify        # adds Testcontainers integration tests against real pgvector
cd ml-service; .\.venv\Scripts\python.exe -m pytest -q
cd frontend;   npm run build
```

## Training the model

The trained model is already committed under `ml-service/models/` (booster, isotonic calibrator,
feature spec, `metrics.json`) - a fresh clone runs the real model immediately, no training step
required. Retrain only to reproduce the findings or to pick up a new copy of the dataset:

```powershell
.\scripts\train-model.ps1 -Data C:\data\baf\Base.csv   # ~10-15 seconds
# restart run-ml.ps1; /health then reports modelLoaded: true and every score stops being a stub

# Fairness ablation (docs/FINDINGS.md, finding b): retrain without customer_age as an input
cd ml-service; .\.venv\Scripts\python.exe training\train_baf.py --data C:\data\baf\Base.csv --out models_no_age --exclude-age
```

`metrics.json` is the source for the findings doc: PR-AUC, recall at a 5% false-positive budget,
the score threshold that budget implies, and the false-positive-rate ratio across age groups.

## Trying a decision

```powershell
.\scripts\submit-application.ps1 -Scenario clean
.\scripts\submit-application.ps1 -Scenario injection      # routed to a human, not obeyed
.\scripts\submit-application.ps1 -Scenario replay         # idempotent retry
```

### AWS deployment

The live demo above runs on the stack documented in
[`infra/aws/README.md`](infra/aws/README.md): RDS PostgreSQL/pgvector, the same Keycloak realm as
local development, Caddy, ECR, and an SSM-managed EC2 host running all five services
(`docker compose`) - backend, ML, Keycloak, Caddy, and the console itself (nginx serving the Vite
build). Deploy/redeploy with `scripts/deploy-aws.ps1`.

## Findings

Full detail, every number reproducible from a committed script, in
[`docs/FINDINGS.md`](docs/FINDINGS.md).

| Finding | Result |
|---|---|
| Model quality (temporal test split) | PR-AUC 0.157, ROC-AUC 0.881, recall 49.8% @ 5% FPR |
| Fairness ablation (drop `customer_age`) | Costs 8.7% relative recall; disparity only 3.36x -> 2.53x - proxies persist, kept age |
| Policy action mix | `APPROVE_RESTRICTED` captures 73% of test-set fraud - cost model working as designed |
| Graph linkage | 25% of synthetic ring members escalated by shared-identity evidence alone; ring detector: 1.00 precision/recall |
| Latency, live, 20 req/s for 2 min | Pipeline p95 **53ms** (4.7x under the 250ms budget), 0 failures across 4,800+ requests |
| LLM safety | 6/6 injection payloads caught, 8/8 briefs grounded (0 hallucinated features) |
| LLM brief latency | p50 7.4s on first view only (unavoidable, the live LLM call); persisted after that, so every later view is instant |
| VECTOR descriptor matching | Implemented, unit-tested, deployed; live backfill blocked by a Gemini free-tier daily quota - degrades to trigram with no correctness impact |

## Data

The supervised model trains on the Bank Account Fraud (BAF) dataset suite (NeurIPS 2022), which
models online bank-account-opening fraud. It is licensed for non-commercial use, so it is cited but
never committed or redistributed; `data/` is git-ignored. Entity-linkage data (devices, phones,
addresses) is synthetic and generated by this repository.

## Security posture

- OIDC via Keycloak, stateless bearer tokens, deny-by-default authorisation, role checks tested.
- Identifiers used for linkage are stored as keyed HMAC digests, never as raw PII.
- Append-only `decisions` and `audit_log` tables: every decision keeps its model version, feature
  snapshot and reason codes.
- Applicant free text is treated as untrusted data by the AI layer; an injection attempt is itself
  a fraud signal.
- No secrets in the repository; gitleaks runs on every push.

## Status

All five phases are complete and live at the demo URL above:

- **Phase 1** - contract, schema, identity and authorisation.
- **Phase 2** - decision core: rule chain, calibrated model with reason codes, cost-based policy
  bands, idempotent submission, append-only decision log and audit trail, metrics on Prometheus.
- **Phase 3** - graph linkage stage (shared-identity floor) and restricted approvals (Module A:
  merchant-locked cards, velocity caps, customer confirmation).
- **Phase 4** - the AI layer (analyst brief + similar-case embeddings, Bedrock/Gemini-switchable)
  and the analyst console (case queue, detail, labels).
- **Phase 5** - findings (model quality, fairness, policy calibration, graph/latency/LLM-safety)
  and VECTOR descriptor matching. See [`docs/FINDINGS.md`](docs/FINDINGS.md).

See `docs/adr/` for why each choice was made.

## Known limitations

The full list with numbers behind each is in
[`docs/FINDINGS.md`](docs/FINDINGS.md#g-limitations). In short:

- **AWS Bedrock is pending an account review**, not broken - the live demo runs on Gemini; both
  implementations exist, are tested, and are one config line apart.
- **Fairness is diagnosed, not solved.** Dropping `customer_age` costs real recall and only
  partially closes the false-positive disparity; other features proxy for it.
- **The analyst brief is slow on its first generation** (p50 ~7s, a live LLM call) - now persisted
  after that first view, so it's a one-time cost per case, not a repeat one.
- **VECTOR merchant matching** is implemented and deployed but its one-time catalog backfill is
  currently blocked by a Gemini free-tier daily quota; the system runs correctly on trigram in the
  meantime and the backfill completes automatically once the quota resets.
- **Graph-ring and BAF-as-lending-proxy caveats** - see the findings doc for the honest version of
  both.
