-- Multi-photo assets: an asset (e.g. "Kitchen") can hold 1..15 photos.
-- Each photo keeps its OWN gps + sha256 + timestamp (per-photo tamper-evidence
-- and de-duplication). The parent asset keeps photo #0 mirrored into its
-- existing photo_url/sha256_hash/gps/captured_at columns as the COVER, so every
-- existing feature (lists, pairing, dossier, CSV) keeps working untouched.
CREATE TABLE asset_photos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id UUID NOT NULL REFERENCES assets(id),
  property_id UUID NOT NULL REFERENCES properties(id),
  photo_url VARCHAR(512) NOT NULL,
  sha256_hash CHAR(64) NOT NULL,
  gps_lat NUMERIC(9,6),
  gps_lng NUMERIC(9,6),
  captured_at TIMESTAMPTZ NOT NULL,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

-- Per-property photo uniqueness now lives here (was on assets.sha256_hash):
-- a given photo's bytes may document a property only once.
CREATE UNIQUE INDEX ux_asset_photos_property_hash ON asset_photos (property_id, sha256_hash)
  WHERE deleted_at IS NULL;
CREATE INDEX ix_asset_photos_asset ON asset_photos (asset_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_asset_photos_property ON asset_photos (property_id) WHERE deleted_at IS NULL;
-- Fraud signal: same bytes documented anywhere in the system.
CREATE INDEX ix_asset_photos_hash ON asset_photos (sha256_hash) WHERE deleted_at IS NULL;

-- Backfill: every existing asset becomes an asset with a single photo (#0),
-- copied straight from its current columns. Soft-deleted assets carry their
-- deleted_at across so counts and uniqueness stay consistent.
INSERT INTO asset_photos
  (asset_id, property_id, photo_url, sha256_hash, gps_lat, gps_lng, captured_at, position, created_at, deleted_at)
SELECT id, property_id, photo_url, sha256_hash, gps_lat, gps_lng, captured_at, 0, created_at, deleted_at
FROM assets;
