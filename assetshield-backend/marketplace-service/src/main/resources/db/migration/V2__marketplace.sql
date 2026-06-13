CREATE TABLE insurance_agents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE,
  insurer_name VARCHAR(120) NOT NULL,
  nic_licence_no VARCHAR(50) NOT NULL,
  verification_status VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION'
    CHECK (verification_status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED')),
  verified_by_user_id UUID,
  verified_at TIMESTAMPTZ,
  rejection_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_agents_licence ON insurance_agents (nic_licence_no);

CREATE TABLE agent_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id UUID NOT NULL REFERENCES insurance_agents(id),
  plan VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' CHECK (plan IN ('MONTHLY')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
  started_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  last_payment_id UUID REFERENCES payments(id),
  -- at most one expiry warning per subscription (daily warning job)
  expiry_warned_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_agent_sub_active ON agent_subscriptions (agent_id) WHERE status = 'ACTIVE';

CREATE TABLE agent_interests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id UUID NOT NULL REFERENCES insurance_agents(id),
  property_id UUID NOT NULL,
  owner_user_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED')),
  responded_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_interest_pending ON agent_interests (agent_id, property_id)
  WHERE status = 'PENDING';
CREATE INDEX ix_interests_owner ON agent_interests (owner_user_id, status);
CREATE INDEX ix_interests_agent ON agent_interests (agent_id, status);

CREATE TABLE dossier_shares (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  dossier_id UUID NOT NULL,
  agent_id UUID NOT NULL REFERENCES insurance_agents(id),
  agent_interest_id UUID NOT NULL REFERENCES agent_interests(id),
  shared_by_user_id UUID NOT NULL,
  consent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_share_active ON dossier_shares (dossier_id, agent_id)
  WHERE revoked_at IS NULL;

CREATE TABLE policy_quotes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_interest_id UUID NOT NULL REFERENCES agent_interests(id),
  dossier_share_id UUID NOT NULL REFERENCES dossier_shares(id),
  coverage_amount NUMERIC(12,2) NOT NULL CHECK (coverage_amount > 0),
  premium NUMERIC(12,2) NOT NULL CHECK (premium > 0),
  term_months SMALLINT NOT NULL CHECK (term_months BETWEEN 1 AND 60),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
  responded_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FREE is the absence of an ACTIVE PRO row — no FREE rows are stored.
CREATE TABLE user_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  tier VARCHAR(10) NOT NULL DEFAULT 'PRO' CHECK (tier IN ('PRO')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
  started_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  last_payment_id UUID REFERENCES payments(id),
  -- at most one expiry warning per subscription (daily warning job)
  expiry_warned_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_user_sub_active ON user_subscriptions (user_id) WHERE status = 'ACTIVE';
