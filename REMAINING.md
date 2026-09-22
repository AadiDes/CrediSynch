# Remaining work

Snapshot as of 2026-09-22, ~16:00 IST. `main` is green on CI (latest commit `49d0f08`), pushed to
`origin/main`. The recording/submission is done. The ring-detection pipeline fix is complete,
tested, and verified end-to-end locally.

## AWS deploy: in progress right now

Started via `scripts\deploy-aws.ps1` in a separate, visible PowerShell window (still open). It logs
to `%TEMP%\credisynch-deploy.log`. **This process is independent of any Claude Code session** - it
keeps running even if this conversation is cleared or closed; check the log file or the terminal
window directly, or ask a fresh session to check `%TEMP%\credisynch-deploy.log` and pick up from
here.

Steps, in order, and where it was last observed:
1. ✅ CloudFormation changeset/stack update (no infra changes, `UPDATE_COMPLETE`)
2. ✅ Backend Maven package (`BUILD SUCCESS`)
3. ✅ ECR login
4. ⏳ Building and pushing the backend Docker image (last observed step)
5. ⬜ Building and pushing the ml-service Docker image
6. ⬜ Building and pushing the frontend Docker image
7. ⬜ SSM command to refresh the EC2 host's running containers (polls for up to 5 minutes)

**Once it finishes**, look for `Phase 2 AWS deployment complete.` in the log, then verify for real,
don't just trust the log:
```powershell
# health checks
curl https://13-200-182-78.sslip.io/api/actuator/health
curl https://13-200-182-78.sslip.io/ml/health

# then the actual thing that mattered: submit a linked ring through the LIVE api and confirm
# a ring badge shows up in the LIVE console at https://13-200-182-78.sslip.io, the same way it
# was verified locally earlier this session (submit 4 applications sharing a device fingerprint,
# wait ~5-10s, check the case detail for "Part of a detected ring").
```
If the log shows an error instead (ECR push failure, SSM command failure, etc.), the stack itself
is safe either way, CloudFormation only updates app containers here, not the database, so a failed
image push just means the live demo keeps running the old code; nothing is broken, just re-run
`scripts\deploy-aws.ps1` after fixing whatever failed.

**After a successful deploy**, delete the two throwaway helper scripts committed to nothing (not
tracked in git, safe to just delete): `scripts\_deploy-aws-run.ps1`.

## Demo recording: done

Everything in `docs/DEMO.md`'s script (the three `submit-application.ps1` scenarios, the queue,
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
