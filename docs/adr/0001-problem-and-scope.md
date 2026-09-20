# ADR 0001 — Problem statement and scope

**Status:** accepted

## Context
Three problem statements were offered. The hiring team sits in the Information Security and IAM
agile program; Synchrony's exposure in digital lending is concentrated at instant approval, where
synthetic identities and account takeover attack hardest.

## Decision
Build problem statement 1 (real-time fraud detection and prevention), with three layers:
a layered decision engine, a graph stage that detects fraud rings, and risk-based step-up
authentication as the cheap preventive action.

## Consequences
- The security layer is a first-class feature, not a checkbox.
- The graph stage is the differentiator; if it slipped, the engine would still be complete.
- Credit-risk modelling is explicitly out of scope: this system decides fraud, not creditworthiness.
