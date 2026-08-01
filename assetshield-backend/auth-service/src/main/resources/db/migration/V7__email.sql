-- Email is now collected at registration and is the OTP / two-step-verification
-- channel. Phone stays the identity (that's what our market-trader users have);
-- email is the reliable delivery + account-recovery channel.
--
-- Nullable so the existing pre-launch rows migrate cleanly; new registrations
-- require it at the application layer. Unique per active account, mirroring the
-- phone-number partial unique index.
ALTER TABLE users ADD COLUMN email VARCHAR(255);

CREATE UNIQUE INDEX ux_users_email ON users (email)
  WHERE email IS NOT NULL AND deleted_at IS NULL;
