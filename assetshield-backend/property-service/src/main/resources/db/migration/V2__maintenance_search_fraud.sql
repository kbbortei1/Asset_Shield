-- Maintenance dates are user-maintained metadata, not evidence — both editable.
ALTER TABLE assets ADD COLUMN warranty_expires_on DATE;
ALTER TABLE assets ADD COLUMN next_service_on DATE;

-- Cross-property duplicate detection looks up by hash alone;
-- ux_assets_property_hash leads with property_id and cannot serve it.
CREATE INDEX ix_assets_hash ON assets (sha256_hash) WHERE deleted_at IS NULL;

-- Maintenance-due sweeps scan a date range over live assets only.
CREATE INDEX ix_assets_warranty_due ON assets (warranty_expires_on)
  WHERE warranty_expires_on IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_assets_service_due ON assets (next_service_on)
  WHERE next_service_on IS NOT NULL AND deleted_at IS NULL;
