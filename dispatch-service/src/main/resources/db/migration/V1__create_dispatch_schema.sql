CREATE SCHEMA IF NOT EXISTS ${dispatch-schema};

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.cluster (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    url        VARCHAR(1000) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.tenant (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    cluster_id UUID         NOT NULL REFERENCES ${dispatch-schema}.cluster (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.app_user (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(500),
    first_name    VARCHAR(255),
    last_name     VARCHAR(255),
    user_type     VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.tenant_user (
    tenant_id  UUID        NOT NULL REFERENCES ${dispatch-schema}.tenant (id),
    user_id    UUID        NOT NULL REFERENCES ${dispatch-schema}.app_user (id),
    role       VARCHAR(50) NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.credential (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES ${dispatch-schema}.app_user (id),
    provider    VARCHAR(50) NOT NULL,
    external_id VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${dispatch-schema}.refresh_token (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES ${dispatch-schema}.app_user (id),
    token_hash VARCHAR(500) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rt_token_hash ON ${dispatch-schema}.refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_rt_user_id    ON ${dispatch-schema}.refresh_token (user_id);
