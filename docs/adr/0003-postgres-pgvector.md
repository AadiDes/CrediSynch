# ADR 0003 — One Postgres for rows and vectors, embeddings fixed at 1024 dimensions

**Status:** accepted

## Decision
Use a single PostgreSQL instance with the `pgvector` extension for both structured data and
embeddings, with an HNSW index using cosine distance. Fix the embedding dimension at 1024, the
default for Amazon Titan Text Embeddings V2.

## Rationale
pgvector columns are dimension-typed, so switching embedding models later means a migration.
Fixing the dimension up front, and recording it here, avoids that surprise. A separate vector
database would add an operational component for no benefit at this scale.

## Consequences
- A fallback embedding provider must emit 1024 dimensions, or the column must be migrated.
- Similar-case search is a SQL query, so it inherits the same transactions and backups as the rest.
