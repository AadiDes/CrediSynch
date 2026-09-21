# ADR 0005 — The LLM explains, it never decides, and never on the hot path

**Status:** accepted

## Decision
Amazon Bedrock is used for two jobs: writing the analyst brief for a case, and embedding case
narratives for similar-case retrieval. It is called asynchronously, after the decision is returned.
The action is always chosen by deterministic rules, the model score and the policy thresholds.

Guardrails: prompt templates are versioned in the repository; applicant free text is inserted as
delimited, untrusted data; the brief must cite only reason codes supplied in the prompt; outputs
are schema-validated; a prompt-injection attempt is recorded as a fraud signal.

## Rationale
A regulated lender must be able to reproduce and defend a decision. A non-deterministic model on
the decision path breaks reproducibility, latency and accountability at once.

## Consequences
- Bedrock downtime degrades the brief, never the decision.
- The system remains explainable through reason codes, with the narrative as a convenience.

**Update (ADR 0009):** "Amazon Bedrock" above names the vendor this ADR was written against; the
actual commitment - explain, never decide, never on the hot path - is vendor-neutral. ADR 0009
makes that boundary an interface with a Gemini implementation alongside Bedrock's, for exactly the
reasons this ADR already gives.
