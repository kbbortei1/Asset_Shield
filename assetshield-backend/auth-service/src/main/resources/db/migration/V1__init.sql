CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number VARCHAR(16) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'OWNER'
    CHECK (role IN ('OWNER','AGENT','ADMIN')),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_OTP'
    CHECK (status IN ('PENDING_OTP','ACTIVE','SUSPENDED')),
  ghana_card_url VARCHAR(512),
  language VARCHAR(5) NOT NULL DEFAULT 'en' CHECK (language IN ('en','tw')),
  purge_requested_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_phone ON users (phone_number) WHERE deleted_at IS NULL;

CREATE TABLE otp_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number VARCHAR(16) NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('REGISTRATION','LOGIN_RECOVERY')),
  attempts SMALLINT NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_phone_active ON otp_codes (phone_number, expires_at) WHERE consumed_at IS NULL;

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  token_hash CHAR(64) NOT NULL UNIQUE,
  family_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;

-- Agent registration details held until the Marketplace service (Day 5)
-- consumes them via the internal API.
CREATE TABLE pending_agent_details (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id),
  insurer_name VARCHAR(120) NOT NULL,
  nic_licence_no VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_pending_agent_licence ON pending_agent_details (nic_licence_no);
