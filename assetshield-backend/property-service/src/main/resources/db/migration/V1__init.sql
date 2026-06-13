CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE properties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(20) NOT NULL CHECK (type IN ('RESIDENTIAL','COMMERCIAL','RENTAL')),
  gps_lat NUMERIC(9,6) NOT NULL,
  gps_lng NUMERIC(9,6) NOT NULL,
  locality VARCHAR(120) NOT NULL,
  open_to_offers BOOLEAN NOT NULL DEFAULT FALSE,
  open_to_offers_at TIMESTAMPTZ,
  last_documented_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_properties_owner ON properties (owner_user_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_properties_optin ON properties (open_to_offers)
  WHERE open_to_offers = TRUE AND deleted_at IS NULL;

CREATE TABLE assets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id),
  created_by_user_id UUID NOT NULL,
  photo_url VARCHAR(512) NOT NULL,
  sha256_hash CHAR(64) NOT NULL,
  gps_lat NUMERIC(9,6) NOT NULL,
  gps_lng NUMERIC(9,6) NOT NULL,
  captured_at TIMESTAMPTZ NOT NULL,
  description VARCHAR(500) NOT NULL,
  estimated_value NUMERIC(12,2) NOT NULL CHECK (estimated_value >= 0),
  category VARCHAR(20) NOT NULL CHECK (category IN
    ('ELECTRONICS','FURNITURE','CLOTHING_STOCK','MACHINERY','DOCUMENTS','OTHER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_assets_property_hash ON assets (property_id, sha256_hash)
  WHERE deleted_at IS NULL;
CREATE INDEX ix_assets_property_cat ON assets (property_id, category) WHERE deleted_at IS NULL;
CREATE INDEX ix_assets_gps ON assets (gps_lat, gps_lng) WHERE deleted_at IS NULL;

CREATE TABLE asset_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id UUID NOT NULL REFERENCES assets(id),
  receipt_url VARCHAR(512) NOT NULL,
  sha256_hash CHAR(64) NOT NULL,
  uploaded_by_user_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX ix_receipts_asset ON asset_receipts (asset_id) WHERE deleted_at IS NULL;

CREATE TABLE household_invitations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id),
  invited_by_user_id UUID NOT NULL,
  invitee_phone VARCHAR(16) NOT NULL,
  invitee_user_id UUID,
  can_export BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','CANCELLED')),
  expires_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_invite_pending ON household_invitations (property_id, invitee_phone)
  WHERE status = 'PENDING';

CREATE TABLE household_memberships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id),
  member_user_id UUID NOT NULL,
  granted_by_user_id UUID NOT NULL,
  can_export BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_membership_active ON household_memberships (property_id, member_user_id)
  WHERE revoked_at IS NULL;
