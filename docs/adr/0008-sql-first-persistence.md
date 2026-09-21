# ADR 0008 — SQL-first persistence with JdbcTemplate, not an ORM

**Status:** accepted

## Context
The schema is owned by Flyway and is deliberately append-only in places (`decisions`, `audit_log`),
uses Postgres-specific types (`jsonb`, `vector`) and is queried with explicit indexes in mind.

## Decision
Use `spring-boot-starter-jdbc` with `JdbcTemplate` and hand-written SQL in repository classes.

## Rationale
An ORM would add a second, implicit definition of the schema that can drift from the migrations,
and it hides exactly the thing this system is judged on: which index a hot-path query uses.
Explicit SQL close to the migration is the same discipline as generating type-safe queries from
the schema, which is how the author has built this before.

## Consequences
- No lazy-loading surprises and no `ddl-auto` validation errors on Postgres-specific types.
- More boilerplate per query, which is acceptable at this number of queries.
