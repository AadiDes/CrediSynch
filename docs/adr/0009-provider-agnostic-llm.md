# ADR 0009 — Provider-agnostic LLM interfaces; Gemini as the interim substitute for Bedrock

**Status:** accepted

## Context
ADR 0005 named Amazon Bedrock specifically. Bedrock foundation-model access for this AWS account
entered a new-account review with no committed timeline, and the submission deadline could not
move. Blocking the AI layer - and the demo - on that review was not an option.

## Decision
Split `CaseNarrativeGenerator` and `EmbeddingClient` into interfaces with two implementations each,
Bedrock and Gemini, selected by one property (`app.llm.provider`, env `LLM_PROVIDER`) via
`@ConditionalOnProperty` so exactly one bean of each type exists at a time - never both, never
neither. Both implementations share the same versioned prompt (`CaseBriefPrompt`), the same
grounding rules from ADR 0005, and the same template-brief fallback on any failure. The live demo
runs `app.llm.provider=gemini`; `bedrock` remains the default in `application.yml` for when
account access clears.

## Rationale
ADR 0005's actual commitment was to the *boundary* - explain, never decide, never on the hot path
- not to a specific vendor. Making that boundary an interface rather than a Bedrock-specific class
means the vendor is an implementation detail the rest of the system never sees: `CaseService`,
the guardrail logic, and every test that mocks `CaseNarrativeGenerator` are unaffected by which
provider is active.

## Consequences
- Switching provider is one config value, not a deploy of different code.
- Every guardrail and test in ADR 0005 needed to be re-verified against Gemini independently
  (`docs/FINDINGS.md` finding f) - a shared interface does not guarantee identical behaviour from
  a different model, only a compatible contract.
- Two implementations of everything doubles the LLM-facing surface area to maintain; accepted as
  the cost of not being blocked by a single vendor's provisioning timeline.
- Gemini's free tier has its own limits (daily embedding quota, observed exhausted live during
  this project's own load testing - `docs/FINDINGS.md`'s Module A section) that Bedrock's
  provisioned-account model would not have; a production deployment on Gemini would need a paid
  tier.
