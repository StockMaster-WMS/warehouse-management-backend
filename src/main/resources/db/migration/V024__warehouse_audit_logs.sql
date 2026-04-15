CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    service_name VARCHAR(80) NOT NULL,
    module VARCHAR(80) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    action VARCHAR(160) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    entity_name VARCHAR(255),
    actor_id UUID,
    actor_name VARCHAR(120),
    actor_email VARCHAR(180),
    reason VARCHAR(500),
    before_snapshot TEXT,
    after_snapshot TEXT,
    metadata TEXT,
    ip_address VARCHAR(80),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_module_created_at ON audit_logs (module, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs (actor_id, created_at DESC);
