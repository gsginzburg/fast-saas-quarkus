CREATE TABLE IF NOT EXISTS test (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    value       INTEGER,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
