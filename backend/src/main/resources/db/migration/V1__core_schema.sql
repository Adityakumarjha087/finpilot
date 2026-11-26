-- FinPilot core schema
CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(254) NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    display_name      VARCHAR(100) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    mfa_secret_enc    TEXT,
    mfa_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_logins     INT          NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    opening_balance   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE import_batches (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    imported_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    total_rows   INT          NOT NULL,
    imported     INT          NOT NULL,
    duplicates   INT          NOT NULL,
    failed       INT          NOT NULL
);

CREATE TABLE transactions (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    import_batch_id      BIGINT         REFERENCES import_batches (id) ON DELETE SET NULL,
    posted_at            TIMESTAMPTZ    NOT NULL,
    description          VARCHAR(500)   NOT NULL,
    merchant             VARCHAR(200)   NOT NULL,
    amount               NUMERIC(14, 2) NOT NULL,
    category             VARCHAR(40)    NOT NULL,
    category_source      VARCHAR(10)    NOT NULL,
    category_confidence  DOUBLE PRECISION,
    notes_enc            TEXT,
    dedup_hash           VARCHAR(64)    NOT NULL,
    anomaly_status       VARCHAR(12)    NOT NULL DEFAULT 'NONE',
    anomaly_score        DOUBLE PRECISION,
    anomaly_reasons      TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_transactions_dedup UNIQUE (user_id, dedup_hash)
);
CREATE INDEX idx_transactions_user_date ON transactions (user_id, posted_at DESC);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category);
CREATE INDEX idx_transactions_user_merchant ON transactions (user_id, merchant);

CREATE TABLE category_corrections (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    transaction_id     BIGINT       REFERENCES transactions (id) ON DELETE SET NULL,
    description        VARCHAR(500) NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    merchant           VARCHAR(200) NOT NULL,
    previous_category  VARCHAR(40)  NOT NULL,
    category           VARCHAR(40)  NOT NULL,
    used_in_training   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE merchant_rules (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    merchant   VARCHAR(200) NOT NULL,
    category   VARCHAR(40)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_merchant_rules UNIQUE (user_id, merchant)
);

CREATE TABLE budgets (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category       VARCHAR(40)    NOT NULL,
    monthly_limit  NUMERIC(14, 2) NOT NULL CHECK (monthly_limit > 0),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_budgets UNIQUE (user_id, category)
);

CREATE TABLE goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(120)   NOT NULL,
    target_amount  NUMERIC(14, 2) NOT NULL CHECK (target_amount > 0),
    saved_amount   NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
    target_date    DATE           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT, -- intentionally no FK: the audit trail is append-only and outlives users
    user_email   VARCHAR(254),
    action       VARCHAR(60)  NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(60),
    ip           VARCHAR(64),
    details      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_time ON audit_events (user_id, created_at DESC);

CREATE TABLE assistant_queries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question    VARCHAR(300) NOT NULL,
    sql_text    TEXT         NOT NULL,
    row_count   INT          NOT NULL,
    provider    VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ml_training_runs (
    id                BIGSERIAL PRIMARY KEY,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    corrections       INT         NOT NULL,
    model_version     INT         NOT NULL,
    holdout_accuracy  DOUBLE PRECISION NOT NULL
);
