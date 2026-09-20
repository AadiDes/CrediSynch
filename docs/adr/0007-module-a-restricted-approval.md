# ADR 0007 — Approve-restricted: merchant-locked cards and customer confirmation

**Status:** accepted

## Context
Benchmarking against Capital One, a direct competitor in retail cards: customers can bind a virtual
card number to a single merchant, and the bank's assistant asks customers in real time to confirm
suspicious purchases, using their answers to improve its models.

## Decision
Adapt both ideas for Synchrony's partner model as a fourth policy action, `APPROVE_RESTRICTED`:
a gray-band applicant is approved with the card locked to the partner where they applied, a reduced
limit and a velocity cap, all lifting as trust accrues. Purchase descriptors are resolved to the
locked partner by embedding similarity in pgvector. Customers are asked "was this you?" and their
answers become labels within minutes instead of weeks.

## Rationale
With loss L, exposure fraction e and friction cost c_r, a restricted approval costs c_r + p·e·L.
Under the worked assumptions in the deck it is the cheapest action across a wide probability band
that would otherwise have been declined, which is exactly the false-positive reduction the problem
statement asks for.

## Consequences
- Adds a post-approval transaction path, so the system covers onboarding *and* early lifecycle.
- Card issuance is simulated; no card network is involved.
