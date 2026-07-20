-- Billing history: GET /users/me/payments orders by created_at DESC per user.
-- Without this the query full-scans payments.
CREATE INDEX ix_payments_user ON payments (user_id, created_at DESC);

-- Reconciler input: SUCCESS rows whose downstream dispatch never acknowledged.
-- Partial index keeps it tiny (only unhealthy rows are indexed).
CREATE INDEX ix_payments_undispatched ON payments (status)
  WHERE status = 'SUCCESS' AND dispatched_at IS NULL;
