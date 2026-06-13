-- Day 5: marketplace sync bookkeeping. A NULL consumed_at means the agent's
-- details have not yet been accepted by marketplace-service; the 60 s re-push
-- job retries until a 2xx (or a licence-conflict 409, resolved manually).
ALTER TABLE pending_agent_details ADD COLUMN consumed_at TIMESTAMPTZ;
