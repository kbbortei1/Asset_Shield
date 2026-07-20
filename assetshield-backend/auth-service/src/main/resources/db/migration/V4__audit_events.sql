-- Append-only security audit trail. Rows are never updated or deleted by the
-- application; actor_user_id is NULL for failed logins against unknown phones.
CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID,
  action VARCHAR(40) NOT NULL,
  target VARCHAR(120),
  detail VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_recent ON audit_events (created_at DESC);
CREATE INDEX ix_audit_actor ON audit_events (actor_user_id, created_at DESC);
