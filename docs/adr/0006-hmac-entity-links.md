# ADR 0006 — Link identities on keyed hashes, never raw PII

**Status:** accepted

## Decision
Device fingerprints, phone numbers, emails, addresses and bank references used for graph linkage
are stored as HMAC-SHA256 digests under a server-side key held in AWS Secrets Manager. Raw values
stay in the application payload, encrypted at rest, and are never copied into the graph tables.

## Rationale
Ring detection only needs to know that two applications share a value, not what the value is.
Hashing keeps the signal and drops the liability; the key makes the digests useless if the table
leaks, because they cannot be brute-forced from a phone-number dictionary alone.

## Consequences
- Linkage is exact-match only; fuzzy matching (typo'd addresses) needs normalisation before hashing.
- Rotating the key requires rehashing the link table.
