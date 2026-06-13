CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE device_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  fcm_token VARCHAR(512) NOT NULL,
  platform VARCHAR(10) NOT NULL DEFAULT 'ANDROID' CHECK (platform IN ('ANDROID','IOS')),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_device_token ON device_tokens (fcm_token) WHERE revoked_at IS NULL;
CREATE INDEX ix_device_user ON device_tokens (user_id) WHERE revoked_at IS NULL;

CREATE TABLE notification_preferences (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE,
  tips_frequency VARCHAR(10) NOT NULL DEFAULT 'WEEKLY'
    CHECK (tips_frequency IN ('DAILY','WEEKLY','OFF')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tip_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tip_text VARCHAR(600) NOT NULL,
  category VARCHAR(20) NOT NULL CHECK (category IN ('FIRE','FLOOD','THEFT','GENERAL','SEASONAL')),
  language VARCHAR(5) NOT NULL DEFAULT 'en' CHECK (language IN ('en','tw')),
  applies_property_type VARCHAR(20) CHECK (applies_property_type IN ('RESIDENTIAL','COMMERCIAL','RENTAL')),
  applies_asset_category VARCHAR(20) CHECK (applies_asset_category IN
    ('ELECTRONICS','FURNITURE','CLOTHING_STOCK','MACHINERY','DOCUMENTS','OTHER')),
  applies_season VARCHAR(15) CHECK (applies_season IN ('HARMATTAN','RAINY','ANY')),
  applies_flood_zone BOOLEAN,
  min_category_value NUMERIC(12,2),
  priority SMALLINT NOT NULL DEFAULT 5,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE flood_zones (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(80) NOT NULL,
  min_lat NUMERIC(9,6) NOT NULL, max_lat NUMERIC(9,6) NOT NULL,
  min_lng NUMERIC(9,6) NOT NULL, max_lng NUMERIC(9,6) NOT NULL
);

CREATE TABLE tips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  property_id UUID NOT NULL,
  tip_template_id UUID NOT NULL REFERENCES tip_templates(id),
  tip_text VARCHAR(600) NOT NULL,
  category VARCHAR(20) NOT NULL,
  delivered_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_tip_once ON tips (user_id, property_id, tip_template_id);
CREATE INDEX ix_tips_feed ON tips (user_id, created_at DESC);
CREATE INDEX ix_tips_undelivered ON tips (user_id) WHERE delivered_at IS NULL;

CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  type VARCHAR(30) NOT NULL CHECK (type IN
    ('TIP','REDOC_REMINDER','DOSSIER_READY','AGENT_INTEREST','INTEREST_RESPONSE',
     'INTEREST_REVOKED','SHARE_CREATED','SHARE_REVOKED','QUOTE_ISSUED','QUOTE_RESPONSE',
     'SUBSCRIPTION_EXPIRY','HOUSEHOLD_INVITE','AGENT_VERIFIED','AGENT_REJECTED')),
  title VARCHAR(120) NOT NULL,
  body VARCHAR(500) NOT NULL,
  payload JSONB,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notif_user ON notifications (user_id, created_at DESC);

CREATE TABLE redoc_reminders (
  property_id UUID PRIMARY KEY,
  reminded_at TIMESTAMPTZ NOT NULL
);
