-- CrediSynch core schema.
-- Design notes:
--  * decisions and audit_log are append-only (no UPDATE/DELETE in application code).
--  * PII is never stored raw for linkage: entity_links holds keyed HMAC digests only.
--  * embedding dimension is fixed at 1024 (Titan Text Embeddings V2 default); pgvector columns are dimension-typed.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- applications
CREATE TABLE applications (
    id                  UUID PRIMARY KEY,
    external_ref        VARCHAR(64)  NOT NULL UNIQUE,
    channel             VARCHAR(16)  NOT NULL CHECK (channel IN ('WEB','MOBILE','POS_PARTNER')),
    partner_id          VARCHAR(64),
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    applicant_name_hash VARCHAR(64)  NOT NULL,
    features            JSONB        NOT NULL,
    raw_payload         JSONB        NOT NULL
);
CREATE INDEX idx_applications_submitted_at ON applications (submitted_at DESC);
CREATE INDEX idx_applications_partner ON applications (partner_id);

-- ---------------------------------------------------------------- decisions (append-only)
CREATE TABLE decisions (
    id                  UUID PRIMARY KEY,
    application_id      UUID         NOT NULL REFERENCES applications (id),
    decided_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    action              VARCHAR(24)  NOT NULL CHECK (action IN ('APPROVE','STEP_UP','APPROVE_RESTRICTED','REVIEW','DECLINE')),
    fraud_probability   NUMERIC(8,6),
    graph_risk          NUMERIC(8,6),
    novelty_score       NUMERIC(8,6),
    rules_fired         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    reason_codes        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    model_version       VARCHAR(64)  NOT NULL,
    policy_version      VARCHAR(64)  NOT NULL,
    latency_ms          INTEGER,
    degraded_mode       BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_decisions_application ON decisions (application_id);
CREATE INDEX idx_decisions_decided_at ON decisions (decided_at DESC);

-- ---------------------------------------------------------------- idempotency
CREATE TABLE idempotency_keys (
    idempotency_key     VARCHAR(128) PRIMARY KEY,
    request_digest      VARCHAR(64)  NOT NULL,
    application_id      UUID         REFERENCES applications (id),
    response_body       JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- entity graph
CREATE TABLE entity_links (
    id              BIGSERIAL PRIMARY KEY,
    application_id  UUID        NOT NULL REFERENCES applications (id),
    entity_type     VARCHAR(24) NOT NULL CHECK (entity_type IN ('DEVICE','PHONE','EMAIL','ADDRESS','BANK_ACCOUNT','IP')),
    entity_hash     VARCHAR(64) NOT NULL,
    observed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entity_links_hash ON entity_links (entity_type, entity_hash);
CREATE INDEX idx_entity_links_app ON entity_links (application_id);
CREATE INDEX idx_entity_links_observed ON entity_links (observed_at DESC);

CREATE TABLE rings (
    id              UUID PRIMARY KEY,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    algorithm       VARCHAR(32) NOT NULL,
    size            INTEGER     NOT NULL,
    density         NUMERIC(8,6),
    confirmed_fraud INTEGER     NOT NULL DEFAULT 0,
    summary         TEXT
);

CREATE TABLE ring_members (
    ring_id         UUID NOT NULL REFERENCES rings (id) ON DELETE CASCADE,
    application_id  UUID NOT NULL REFERENCES applications (id),
    PRIMARY KEY (ring_id, application_id)
);

-- ---------------------------------------------------------------- cases and labels
CREATE TABLE cases (
    id              UUID PRIMARY KEY,
    application_id  UUID         NOT NULL REFERENCES applications (id),
    decision_id     UUID         NOT NULL REFERENCES decisions (id),
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_REVIEW','CLOSED')),
    priority        INTEGER      NOT NULL DEFAULT 3,
    opened_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    brief           TEXT,
    brief_model     VARCHAR(64)
);
CREATE INDEX idx_cases_status ON cases (status, priority, opened_at DESC);

CREATE TABLE case_labels (
    id          UUID PRIMARY KEY,
    case_id     UUID         NOT NULL REFERENCES cases (id),
    label       VARCHAR(16)  NOT NULL CHECK (label IN ('FRAUD','LEGITIMATE','UNCERTAIN')),
    label_source VARCHAR(24) NOT NULL CHECK (label_source IN ('ANALYST','CUSTOMER_CONFIRM','CHARGEBACK')),
    labelled_by VARCHAR(128) NOT NULL,
    labelled_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    note        TEXT
);
CREATE INDEX idx_case_labels_case ON case_labels (case_id);

CREATE TABLE case_embeddings (
    case_id     UUID PRIMARY KEY REFERENCES cases (id) ON DELETE CASCADE,
    embedding   vector(1024) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_case_embeddings_hnsw ON case_embeddings USING hnsw (embedding vector_cosine_ops);

-- ---------------------------------------------------------------- module A: restricted approvals
CREATE TABLE merchants (
    id              UUID PRIMARY KEY,
    partner_id      VARCHAR(64)  NOT NULL UNIQUE,
    display_name    VARCHAR(128) NOT NULL,
    category        VARCHAR(64)  NOT NULL,
    descriptor_embedding vector(1024)
);

CREATE TABLE card_accounts (
    id                  UUID PRIMARY KEY,
    application_id      UUID         NOT NULL REFERENCES applications (id),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    credit_limit_minor  BIGINT       NOT NULL,
    locked_partner_id   VARCHAR(64),
    velocity_cap_per_day INTEGER,
    restrictions_lift_at TIMESTAMPTZ,
    opened_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_card_accounts_application ON card_accounts (application_id);

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY,
    card_account_id     UUID         NOT NULL REFERENCES card_accounts (id),
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    amount_minor        BIGINT       NOT NULL,
    raw_descriptor      VARCHAR(256) NOT NULL,
    matched_partner_id  VARCHAR(64),
    match_score         NUMERIC(8,6),
    match_method        VARCHAR(24),
    outcome             VARCHAR(24)  NOT NULL CHECK (outcome IN ('APPROVED','DECLINED_LOCK','DECLINED_VELOCITY','DECLINED_RISK','CONFIRM_PENDING')),
    reason              TEXT
);
CREATE INDEX idx_transactions_account_time ON transactions (card_account_id, occurred_at DESC);

CREATE TABLE customer_confirmations (
    id              UUID PRIMARY KEY,
    transaction_id  UUID         NOT NULL REFERENCES transactions (id),
    asked_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    answered_at     TIMESTAMPTZ,
    answer          VARCHAR(16)  CHECK (answer IN ('YES_IT_WAS_ME','NOT_ME')),
    channel         VARCHAR(16)  NOT NULL DEFAULT 'APP'
);
CREATE INDEX idx_confirmations_txn ON customer_confirmations (transaction_id);

-- ---------------------------------------------------------------- governance
CREATE TABLE model_registry (
    id              UUID PRIMARY KEY,
    model_name      VARCHAR(64)  NOT NULL,
    model_version   VARCHAR(64)  NOT NULL,
    role            VARCHAR(16)  NOT NULL CHECK (role IN ('CHAMPION','CHALLENGER','RETIRED')),
    trained_at      TIMESTAMPTZ  NOT NULL,
    metrics         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    registered_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (model_name, model_version)
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor           VARCHAR(128) NOT NULL,
    actor_roles     VARCHAR(256),
    action          VARCHAR(64)  NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(64),
    correlation_id  VARCHAR(64),
    detail          JSONB        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor);
