-- The active-code lookup is findTopBy(phone, purpose, consumed IS NULL)
-- ORDER BY created_at DESC; the original index lacked purpose and the sort
-- column. The old index is superseded and dropped.
CREATE INDEX ix_otp_active_lookup ON otp_codes (phone_number, purpose, created_at DESC)
  WHERE consumed_at IS NULL;
DROP INDEX IF EXISTS ix_otp_phone_active;
