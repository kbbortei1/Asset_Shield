CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE damage_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL,
  created_by_user_id UUID NOT NULL,
  disaster_type VARCHAR(20) NOT NULL
    CHECK (disaster_type IN ('FIRE','FLOOD','THEFT','STORM','OTHER')),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','COMPLETED')),
  description VARCHAR(1000),
  occurred_at TIMESTAMPTZ NOT NULL,
  total_estimated_loss NUMERIC(12,2),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_reports_property ON damage_reports (property_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_reports_creator ON damage_reports (created_by_user_id) WHERE deleted_at IS NULL;

CREATE TABLE damage_photos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  damage_report_id UUID NOT NULL REFERENCES damage_reports(id),
  photo_url VARCHAR(512) NOT NULL,
  sha256_hash CHAR(64) NOT NULL,
  gps_lat NUMERIC(9,6) NOT NULL,
  gps_lng NUMERIC(9,6) NOT NULL,
  captured_at TIMESTAMPTZ NOT NULL,
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_dmgphoto_report_hash ON damage_photos (damage_report_id, sha256_hash)
  WHERE deleted_at IS NULL;

CREATE TABLE photo_pairs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  damage_report_id UUID NOT NULL REFERENCES damage_reports(id),
  damage_photo_id UUID NOT NULL REFERENCES damage_photos(id),
  asset_id UUID NOT NULL,
  asset_snapshot JSONB NOT NULL,
  pairing_method VARCHAR(10) NOT NULL CHECK (pairing_method IN ('GPS_AUTO','MANUAL')),
  distance_meters NUMERIC(8,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_pair_unique ON photo_pairs (damage_photo_id, asset_id);
CREATE INDEX ix_pairs_report ON photo_pairs (damage_report_id);
