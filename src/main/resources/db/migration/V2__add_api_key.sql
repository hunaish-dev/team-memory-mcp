CREATE TABLE api_key (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teammate_name TEXT NOT NULL CHECK (teammate_name <> ''),
    key_prefix    TEXT NOT NULL,
    key_hash      TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ
);

-- key_hash is SHA-256 of a 256-bit SecureRandom token, not a human-chosen
-- password — adaptive hashing (bcrypt/argon2) buys nothing against a token
-- with that much entropy, and would tax every single MCP request.
CREATE UNIQUE INDEX ux_api_key_key_hash ON api_key (key_hash);
CREATE INDEX ix_api_key_teammate_name_active ON api_key (teammate_name) WHERE revoked_at IS NULL;
