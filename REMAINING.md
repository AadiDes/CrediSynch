# Remaining work

Snapshot as of 2026-09-22, ~20:55 IST. `main` is green on CI (latest commit `49d0f08`), pushed to
`origin/main`. The recording/submission is done. The ring-detection pipeline fix is complete,
tested, and verified end-to-end both locally and now on the live AWS deploy.

## AWS deploy: done, verified live

The background `scripts\deploy-aws.ps1` run hung twice (~1h45m each time, zero CPU/network
activity) at "Building and pushing backend image..." when launched in a detached/spawned
PowerShell window. Root cause was never pinned down precisely (build, ECR login, and push all
worked fine in isolation and in a direct foreground run), so treat unattended detached-window runs
of this script with suspicion going forward — prefer running it in a foreground/visible shell you
can actually watch.

What actually shipped: backend and ml-service images were built/pushed via a direct foreground run
(interrupted mid-way by Claude Code's background-process reaper reclaiming memory, not a script
failure); frontend build/push and the CloudFormation/SSM refresh were finished with
`scripts\deploy-aws.ps1 -SkipImages`, which completed cleanly (`SSM command Success`).

Verified for real, not just from the log:
- `curl https://13-200-182-78.sslip.io/actuator/health` → `{"status":"UP",...}` (note: **not**
  `/api/actuator/health` — that path 401s because Caddy forwards `/api/*` to the backend with the
  prefix intact and Spring Security's `permitAll` only covers bare `/actuator/health`; worth fixing
  this doc/the Caddy route later, not urgent)
- `curl https://13-200-182-78.sslip.io/ml/health` → `{"status":"UP","model_loaded":true,...}`
- Submitted 4 applications sharing a device fingerprint through the live API
  (`https://13-200-182-78.sslip.io/api/v1/applications`) using tokens from the live Keycloak
  (`scripts\get-token.ps1 applicant applicant123 -BaseUrl https://13-200-182-78.sslip.io/keycloak`).
  All 4 landed in the same ring (`GET /api/v1/cases/{caseId}` showed a shared `ringId`, `size: 7`
  after merging with pre-existing linked applications, `algorithm: CONNECTED_COMPONENTS`,
  `sharedEntities` populated with real counts) — confirms the fix is live and the console's ring
  badge has real data to render.

Throwaway helper script (`scripts\_deploy-aws-run.ps1`, untracked) and a diagnostic ECR test tag
(`credisynch-phase2/backend:push-sanity-test`, local+remote) have been deleted.

## Demo recording: done

Everything in the recording script (the three `submit-application.ps1` scenarios, the queue,
reason codes, graph evidence, ring badge, labels, the admin-vs-analyst 403 demonstration) was
verified working against a freshly reset local stack right before recording: `clean` correctly came
back `STEP_UP`, `injection` came back `REVIEW` with `PROMPT_INJECTION_ATTEMPT` in `rulesFired`,
`replay` printed `PASS`, and 5 clean rings (size 4 each, zero cross-contamination) were seeded and
confirmed with a real browser screenshot of the queue and a ring case detail.

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
- AWS deployment: in progress, see the top of this file for live status and
  what to verify once it completes.
