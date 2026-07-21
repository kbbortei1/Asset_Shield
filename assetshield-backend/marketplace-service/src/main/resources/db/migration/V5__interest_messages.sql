-- 1:1 chat between an owner and an agent, scoped to an accepted agent-interest.
CREATE TABLE interest_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_interest_id UUID NOT NULL REFERENCES agent_interests(id),
  sender_user_id UUID NOT NULL,
  sender_role VARCHAR(10) NOT NULL CHECK (sender_role IN ('OWNER','AGENT')),
  body VARCHAR(2000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_thread ON interest_messages (agent_interest_id, created_at);
