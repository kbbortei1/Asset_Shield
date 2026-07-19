-- Payments moved to the dedicated payment-service (own database). The
-- last_payment_id columns keep referencing payment ids for idempotent
-- settlement replay detection, but as plain cross-service ids: the local
-- FKs (and the now-unowned payments table) go away.
ALTER TABLE agent_subscriptions DROP CONSTRAINT IF EXISTS agent_subscriptions_last_payment_id_fkey;
ALTER TABLE user_subscriptions DROP CONSTRAINT IF EXISTS user_subscriptions_last_payment_id_fkey;

-- The legacy local payments table is dead weight here; payment-service owns
-- the authoritative payments store now.
DROP TABLE IF EXISTS payments;
