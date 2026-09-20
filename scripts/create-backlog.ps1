# Creates milestones and issues so delivery is visible as an agile backlog.
# Run once, after `gh auth login`.
$repo = "AadiDes/CrediSynch"

$phases = @(
    @{ title = "Phase 1 - Walking skeleton"; desc = "Contract, schema, identity, CI" },
    @{ title = "Phase 2 - Decision core";    desc = "Model, rules, policy, idempotency, first AWS deploy" },
    @{ title = "Phase 3 - Graph + module A"; desc = "Entity links, rings, novelty, restricted approvals" },
    @{ title = "Phase 4 - AI layer + console"; desc = "Bedrock briefs, pgvector similar cases, analyst UI" },
    @{ title = "Phase 5 - Findings";         desc = "Load test, metrics, fairness, docs" }
)
foreach ($p in $phases) {
    gh api "repos/$repo/milestones" -f title="$($p.title)" -f description="$($p.desc)" 2>$null | Out-Null
    Write-Host "milestone: $($p.title)"
}

$issues = @(
    @{ t = "Contract-first OpenAPI spec for decisioning, cases and transactions"; m = "Phase 1 - Walking skeleton"; l = "phase-1" },
    @{ t = "Flyway schema: applications, decisions, entity links, cases, module A"; m = "Phase 1 - Walking skeleton"; l = "phase-1" },
    @{ t = "Keycloak realm, JWT role mapping and deny-by-default authorisation"; m = "Phase 1 - Walking skeleton"; l = "security" },
    @{ t = "CI: unit tests, Testcontainers, contract lint, secret scan"; m = "Phase 1 - Walking skeleton"; l = "phase-1" },
    @{ t = "Train LightGBM on BAF with a temporal split; report recall at 5% FPR"; m = "Phase 2 - Decision core"; l = "ml" },
    @{ t = "Serve fraud probability and SHAP reason codes from the model service"; m = "Phase 2 - Decision core"; l = "ml" },
    @{ t = "Rule chain, cost-based policy bands and append-only decision log"; m = "Phase 2 - Decision core"; l = "phase-2" },
    @{ t = "Idempotency-Key handling on application submission"; m = "Phase 2 - Decision core"; l = "phase-2" },
    @{ t = "Deploy the skeleton to AWS (EC2, RDS, instance role, OIDC deploy)"; m = "Phase 2 - Decision core"; l = "infra" },
    @{ t = "Synthetic identity generator with injected fraud rings"; m = "Phase 3 - Graph + module A"; l = "graph" },
    @{ t = "HMAC entity links and real-time graph features"; m = "Phase 3 - Graph + module A"; l = "graph" },
    @{ t = "Ring detection batch: connected components, Louvain, k-clique"; m = "Phase 3 - Graph + module A"; l = "graph" },
    @{ t = "Novelty lane: isolation forest for unseen fraud vectors"; m = "Phase 3 - Graph + module A"; l = "ml" },
    @{ t = "Module A: restricted approvals, merchant lock, vector descriptor matching"; m = "Phase 3 - Graph + module A"; l = "phase-3" },
    @{ t = "Module A: customer confirmation loop feeding fast labels"; m = "Phase 3 - Graph + module A"; l = "phase-3" },
    @{ t = "Bedrock brief generation with guardrails and grounding checks"; m = "Phase 4 - AI layer + console"; l = "ai" },
    @{ t = "Similar-case retrieval with pgvector HNSW"; m = "Phase 4 - AI layer + console"; l = "ai" },
    @{ t = "Analyst console: queue, case detail, ring explorer, live feed"; m = "Phase 4 - AI layer + console"; l = "frontend" },
    @{ t = "Prompt-injection test suite over applicant free text"; m = "Phase 4 - AI layer + console"; l = "security" },
    @{ t = "Load test the hot path and publish p50/p95 latency"; m = "Phase 5 - Findings"; l = "phase-5" },
    @{ t = "Graph ablation: detection lift with and without linkage features"; m = "Phase 5 - Findings"; l = "ml" },
    @{ t = "Fairness check: false-positive rate parity across age groups"; m = "Phase 5 - Findings"; l = "ml" }
)
foreach ($i in $issues) {
    gh issue create --repo $repo --title $i.t --body "See docs/adr and the phase plan." --milestone $i.m --label $i.l 2>$null | Out-Null
    Write-Host "issue: $($i.t)"
}
Write-Host "Backlog created. Labels that do not exist yet are skipped by gh; create them in the repo if you want them."
