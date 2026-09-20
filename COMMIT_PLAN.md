# Phase 1 commit sequence

Run these from the repository root after copying the files in. Small, meaningful commits make the
history readable to the reviewers.

```powershell
git add .gitignore .gitattributes .editorconfig .env.example LICENSE
git commit -m "chore: repository scaffolding, line endings and ignore rules"

git add api/openapi.yaml
git commit -m "feat(api): contract-first OpenAPI spec for decisioning, cases and transactions"

git add infra/
git commit -m "feat(infra): postgres with pgvector and keycloak realm import via docker compose"

git add backend/
git commit -m "feat(api): spring boot skeleton with oidc resource server, rbac and flyway schema"

git add ml-service/
git commit -m "feat(ml): model service contract with deterministic stub and tests"

git add frontend/
git commit -m "feat(web): react console with redux toolkit, keycloak login and role-gated panels"

git add .github/ scripts/
git commit -m "ci: unit tests, testcontainers, contract lint, secret scan and dev scripts"

git add docs/ README.md COMMIT_PLAN.md
git commit -m "docs: architecture, decision records and quickstart"

git push -u origin main
git tag v0.1-skeleton && git push --tags
```

# Phase 2 commit sequence

```powershell
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-test.yml
git commit -m "refactor(api): sql-first persistence and cost-based policy configuration"

git add backend/src/main/java/com/credisynch/api/common/EntityHasher.java backend/src/main/java/com/credisynch/api/config/AppProperties.java
git commit -m "feat(api): keyed hashing for linkage identifiers"

git add backend/src/main/java/com/credisynch/api/decision/rules backend/src/test/java/com/credisynch/api/RuleChainTest.java
git commit -m "feat(decision): rule chain with injection, disposable email and eligibility rules"

git add backend/src/main/java/com/credisynch/api/scoring backend/src/main/java/com/credisynch/api/persistence
git commit -m "feat(decision): scoring client with degraded mode and append-only repositories"

git add backend/src/main/java/com/credisynch/api/decision backend/src/test/java/com/credisynch/api/PolicyEngineTest.java backend/src/test/java/com/credisynch/api/DecisionControllerTest.java
git commit -m "feat(decision): cost-based policy engine, idempotent submission endpoint and metrics"

git add ml-service/
git commit -m "feat(ml): BAF training pipeline with temporal split, calibration and TreeSHAP reason codes"

git add scripts/ docs/ README.md COMMIT_PLAN.md
git commit -m "docs: phase 2 scripts, decision record and usage"

git push
git tag v0.2-decision-core && git push --tags
```
