# ADR 0004 — Keycloak for identity, realm roles for authorisation, acr for step-up

**Status:** accepted

## Decision
Run Keycloak as the OIDC provider, imported from a version-controlled realm file. The API is a
stateless resource server: realm roles (`APPLICANT`, `ANALYST`, `ADMIN`) become Spring authorities,
and endpoints are denied by default. Step-up authentication uses the `acr` claim: risky actions
demand a higher authentication level and Keycloak runs the second factor.

## Rationale
A real identity provider, reproducible from a file, demonstrates the IAM work the role is about.
Hand-rolled JWTs would prove nothing about federation, token lifetimes or step-up.

## Consequences
- The realm file is part of the repository, so the environment is reproducible in one command.
- AWS Cognito remains the deployment fallback; only the issuer URI changes.
