CREATE TABLE IF NOT EXISTS ${dispatch-schema}.access_token (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES ${dispatch-schema}.app_user (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_access_token_hash    ON ${dispatch-schema}.access_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_access_token_user_id ON ${dispatch-schema}.access_token (user_id);
CREATE INDEX IF NOT EXISTS idx_access_token_expires ON ${dispatch-schema}.access_token (expires_at);

GRANT ALL ON ${dispatch-schema}.access_token TO "${dispatch-schema}";
