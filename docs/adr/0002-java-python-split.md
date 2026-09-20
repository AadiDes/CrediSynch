# ADR 0002 — Spring Boot decision service, Python model service

**Status:** accepted

## Context
The decision path needs authorisation, validation, idempotency, persistence and policy. The model
path needs LightGBM, SHAP and NetworkX, which are Python-native.

## Decision
Spring Boot 3 / Java 21 owns the decision path. A small FastAPI service owns scoring. They talk
over one versioned HTTP contract.

## Alternatives
Exporting the model to ONNX and scoring inside the JVM removes a network hop, but loses SHAP
reason codes, which the explainability requirement depends on.

## Consequences
- One extra hop (~5 ms on loopback) inside the latency budget.
- The model service can be retrained and redeployed without touching the API.
