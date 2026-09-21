# Architecture

## Runtime shape

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
                                 same interfaces, no code change - see README's "LLM provider" section
```

## Why this split

The decision service is Java because the decision path is orchestration, authorisation and
policy — exactly what Spring Boot is good at, and what the target team runs. Scoring is Python
because the model, SHAP explanations and graph algorithms live there. The boundary is a single
HTTP contract (`/score`), so either side can be replaced without touching the other.

## Latency budget

Stages 0–5 must fit in 250 ms at p95. Everything expensive — the analyst brief, the similar-case
search, ring detection, retraining — is asynchronous or batch. This is the same pattern as
acknowledging a webhook immediately and dispatching the slow work in the background.

## Degraded modes

| Failure | Behaviour |
|---|---|
| Model service unavailable | Rules-only decision, conservative action (review), `degradedMode: true` on the decision |
| LLM provider unavailable (Bedrock or Gemini) | Template-generated brief; the decision is unaffected because the LLM never decides |
| Keycloak unavailable | API rejects all calls (fail closed); the console shows an explicit sign-in error |
