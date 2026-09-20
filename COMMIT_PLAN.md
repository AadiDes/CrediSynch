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
