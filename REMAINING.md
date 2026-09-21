# Remaining work

Snapshot as of 2026-09-22. `main` is green on CI (commit `693d073`), pushed to
`origin/main`. The ring-detection pipeline fix is complete, tested, and verified
end-to-end locally; it has **not** been deployed to the AWS live demo yet.

## Before recording: check these

- **The AWS live demo (`https://13-200-182-78.sslip.io`) does not have the
  ring-detection fix.** It still has the old code where `rings`/`ring_members`
  are never populated. If you record against the live URL, a case can show
  "Graph evidence: shares an identity with N other applications" but will
  **never** show the "Part of a detected ring" line, no matter what you submit.
  Either record the analyst-console portion against `localhost:5173` (works
  correctly today, verified with screenshots) or deploy first with
  `.\scripts\deploy-aws.ps1` (needs `aws login --profile aadi-dev` first; the
  session had expired when this was written).
- **The local demo database has accumulated a lot of test traffic.** This
  session alone added 56 applications and 7 rings while verifying the fix
  (including the project's own `graph_ablation.py` seed script). That's good
  for showing rings, but it also means identities like the demo applicant
  "Asha Rao" (used by `submit-application.ps1`'s fixed email/address) now
  look linked to many prior submissions, so the `-Scenario clean` run may
  come back `REVIEW` instead of a clean `APPROVE` on camera. To get a truly
  clean demo state:
  ```powershell
  docker compose -f infra\docker-compose.yml down -v   # wipes local Postgres/Keycloak data
  .\scripts\dev-up.ps1
  # restart backend/ml-service/frontend, then optionally reseed one ring:
  cd ml-service
  $env:KEYCLOAK_URL = "http://localhost:8081"; $env:API_BASE_URL = "http://localhost:8080"
  .\.venv\Scripts\python.exe findings\graph_ablation.py
  ```
  This is destructive to local dev data only (all synthetic), not run automatically.
- **The Analyst Brief will likely show the template fallback, not a live LLM
  narrative, when recording locally.** `LLM_PROVIDER` defaults to `bedrock`,
  and this dev machine has no working AWS Bedrock session, so briefs degrade
  to the deterministic template (confirmed: `briefModel: "template-v1"` in
  testing). `docs/DEMO.md` already anticipates this exact situation ("say so
  and point at the fallback text instead of waiting on camera"), so this is
  safe to talk through rather than something broken. To show a real LLM
  brief instead, set `LLM_PROVIDER=gemini` in `.env` and restart the backend,
  but `docs/FINDINGS.md` notes the Gemini free-tier daily quota was already
  exhausted once this project; it may or may not have reset.
- **"Similar cases" will mostly show "None found" locally**, for the same
  reason (embeddings need a working LLM/embedding provider). This is an
  optional section in `docs/DEMO.md`'s own script, not a required beat.
- Everything else in `docs/DEMO.md`'s script (the three `submit-application.ps1`
  scenarios, the queue, reason codes, graph evidence, ring badge, labels, the
  admin-vs-analyst 403 demonstration) was verified working against the local
  stack during this session, including a real browser screenshot of the ring
  badge and case detail.

## Project-level gaps (pre-existing, documented honestly in `docs/FINDINGS.md`)

These are not new findings from this session; they are already recorded in
`docs/FINDINGS.md`'s Limitations section (g) and repeated here only because
they are worth knowing before answering questions on camera:

- **Step-up authentication is decided and labelled, not enforced.** No
  Keycloak conditional flow, no `acr`-claim check before finalizing anything.
- **Fairness is diagnosed, not solved.** Dropping `customer_age` costs 8.7%
  relative recall and only partially closes the false-positive disparity
  (3.36x to 2.53x).
- **AWS Bedrock is pending an account review**, not broken. The live demo
  runs on Gemini; both implementations exist and are tested.
- **VECTOR merchant-descriptor matching** is implemented and deployed but its
  one-time catalog backfill is blocked by a Gemini free-tier quota; degrades
  to trigram matching with no correctness impact.
- **BAF is a proxy dataset**, not real credit-application fraud data.

## This session's fix, for context

- Root cause: `entity_links` and the synchronous per-decision graph floor
  (`GraphLinkageService`) worked correctly, but nothing ever populated the
  `rings`/`ring_members` tables. `ml-service/findings/graph_ablation_detect.py`
  was a standalone research script a human had to run manually against a CSV
  export; it was never part of the running application. The analyst
  console's ring badge was already correctly built to read `rings`, it just
  never had data to read.
- Fix: `RingDetectionService` (backend, `@Async`, triggered after each
  decision's transaction commits) finds an application's full connected
  component via a recursive SQL query, sends the edges to a new
  `POST /graph/rings` endpoint on the ml-service (NetworkX connected
  components, per ADR 0002's "graph algorithms are Python-native" decision),
  and persists/grows the ring via a new `RingRepository`.
- Verified for real: submitted linked applications through the live local
  decision API, confirmed rings persisted via direct SQL, confirmed the
  Cases API returns them, confirmed the console renders them (screenshots),
  and re-ran the project's own documented demo seed script
  (`graph_ablation.py`, 5 rings + 15 noise) end-to-end with perfect
  precision (zero noise contamination).
- One real bug caught by CI (not by local testing, since Testcontainers
  cannot reach Docker Desktop on this machine due to an unrelated
  Docker-Desktop/npipe incompatibility): the recursive query's anchor
  depended on `entity_links` already having a row for the seed application,
  so an application with zero shared identifiers returned an empty cluster
  instead of itself. Fixed and verified directly against Postgres; CI is
  green on the fix commit.

## Housekeeping

- `RingDetectionIT` (Testcontainers) cannot be run locally on this machine;
  `SchemaMigrationIT` (pre-existing, unrelated to this session's work) has
  the identical failure. Both pass in GitHub Actions CI. Worth investigating
  the Docker Desktop/Testcontainers npipe incompatibility separately if
  local integration-test runs matter going forward; not blocking anything.
- `RingResponse.sharedEntities` was previously hardcoded to an empty list;
  now computed from real data (which entity types are actually shared across
  a ring's members). Small improvement, not required by the original spec,
  done while touching the same code path.
- The architecture diagram (`README.md`, `docs/architecture.md`) still says
  "batch jobs (Spring Batch -> Python: rings, retraining)". Ring detection
  is now real, but it runs via Spring's `@Async` calling the ml-service HTTP
  endpoint, not actual Spring Batch, and nightly retraining is still
  aspirational (not implemented at all). Worth a small doc correction later;
  left alone this session since it wasn't the ask.
- AWS deployment: not attempted this session (would rebuild and push three
  Docker images and redeploy the live EC2 host via SSM; agreed with the user
  to leave local-only for now and go straight to recording).
