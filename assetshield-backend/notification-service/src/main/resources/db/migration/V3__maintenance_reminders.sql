-- New notification vocabulary entry for maintenance/warranty reminders.
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN
  ('TIP','REDOC_REMINDER','DOSSIER_READY','AGENT_INTEREST','INTEREST_RESPONSE',
   'INTEREST_REVOKED','SHARE_CREATED','SHARE_REVOKED','QUOTE_ISSUED','QUOTE_RESPONSE',
   'SUBSCRIPTION_EXPIRY','HOUSEHOLD_INVITE','AGENT_VERIFIED','AGENT_REJECTED',
   'MAINTENANCE_DUE'));

-- One reminder per asset+kind per due date: the sweep skips a row whose due_on
-- is unchanged and re-reminds when the user schedules a new date.
CREATE TABLE maintenance_reminders (
  asset_id UUID NOT NULL,
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('WARRANTY','SERVICE')),
  due_on DATE NOT NULL,
  reminded_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (asset_id, kind)
);
