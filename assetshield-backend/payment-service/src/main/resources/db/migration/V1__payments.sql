CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  purpose VARCHAR(25) NOT NULL
    CHECK (purpose IN ('PRO_SUBSCRIPTION','DOSSIER_FEE','AGENT_SUBSCRIPTION')),
  amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  currency CHAR(3) NOT NULL DEFAULT 'GHS',
  provider VARCHAR(20) NOT NULL DEFAULT 'PAYSTACK',
  provider_reference VARCHAR(100) NOT NULL UNIQUE,
  reference_entity_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'INITIATED'
    CHECK (status IN ('INITIATED','SUCCESS','FAILED')),
  webhook_received_at TIMESTAMPTZ,
  raw_webhook JSONB,
  -- set once the downstream purpose-handler acknowledged the settlement;
  -- NULL on a SUCCESS row means the reconciler must re-dispatch
  dispatched_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_entity ON payments (reference_entity_id);
