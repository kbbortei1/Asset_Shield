CREATE TABLE dossiers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  damage_report_id UUID NOT NULL REFERENCES damage_reports(id),
  requested_by_user_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT'
    CHECK (status IN ('PENDING_PAYMENT','GENERATING','READY','FAILED')),
  payment_id UUID,
  file_url VARCHAR(512),
  manifest_hash CHAR(64),
  total_estimated_loss NUMERIC(12,2),
  page_count SMALLINT,
  share_token UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  generated_at TIMESTAMPTZ,
  failure_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_dossiers_user ON dossiers (requested_by_user_id);
CREATE INDEX ix_dossiers_report ON dossiers (damage_report_id);
