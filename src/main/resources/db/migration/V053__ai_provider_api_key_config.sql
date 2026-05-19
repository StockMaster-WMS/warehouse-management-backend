CREATE TABLE IF NOT EXISTS ai_provider_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    api_key_encrypted TEXT,
    key_preview VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ai_provider_configs_provider
    ON ai_provider_configs (LOWER(provider));
