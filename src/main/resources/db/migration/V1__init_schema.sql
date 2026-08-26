CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Living state. `version` backs JPA optimistic locking (@Version) — a stale
-- write throws OptimisticLockException, which the API layer maps to 409.
CREATE TABLE memory_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path        TEXT NOT NULL,
    category    TEXT NOT NULL,
    content     TEXT NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    created_by  TEXT NOT NULL,
    updated_by  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

-- Partial index: path is unique among active entries only, so a deleted
-- path can be reused without a manual purge.
CREATE UNIQUE INDEX ux_memory_entry_path_active ON memory_entry (path) WHERE deleted_at IS NULL;
CREATE INDEX ix_memory_entry_category ON memory_entry (category) WHERE deleted_at IS NULL;

-- Immutable audit trail. One row per mutation, populated by trigger below —
-- so it stays correct even if a caller writes to memory_entry directly.
CREATE TABLE memory_version (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id   UUID NOT NULL REFERENCES memory_entry(id),
    version     BIGINT NOT NULL,
    path        TEXT NOT NULL,
    category    TEXT NOT NULL,
    content     TEXT NOT NULL,
    operation   TEXT NOT NULL CHECK (operation IN ('CREATED', 'MODIFIED', 'DELETED')),
    actor       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_memory_version_memory_id ON memory_version (memory_id);

-- Delete is soft (deleted_at set via normal UPDATE), so this single
-- INSERT/UPDATE trigger captures created/modified/deleted without needing
-- a separate AFTER DELETE handler (which has no clean way to know the actor).
CREATE OR REPLACE FUNCTION record_memory_version() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO memory_version (memory_id, version, path, category, content, operation, actor)
        VALUES (NEW.id, NEW.version, NEW.path, NEW.category, NEW.content, 'CREATED', NEW.created_by);
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO memory_version (memory_id, version, path, category, content, operation, actor)
        VALUES (
            NEW.id, NEW.version, NEW.path, NEW.category, NEW.content,
            CASE WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
                 THEN 'DELETED' ELSE 'MODIFIED' END,
            NEW.updated_by
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memory_entry_version
    AFTER INSERT OR UPDATE ON memory_entry
    FOR EACH ROW EXECUTE FUNCTION record_memory_version();
