-- PostgreSQL does not index FK columns automatically; these are the read paths
-- that currently scan.

-- Quotes are listed through the interest (owner "my quotes") and created
-- against a share (agent "quotes I've issued").
CREATE INDEX ix_quotes_interest ON policy_quotes (agent_interest_id);
CREATE INDEX ix_quotes_share ON policy_quotes (dossier_share_id);

-- Agent's shared-dossier list filters by agent; the existing partial unique
-- leads with dossier_id so it can't serve this.
CREATE INDEX ix_shares_agent ON dossier_shares (agent_id) WHERE revoked_at IS NULL;

-- Owner opt-out revokes/inspects interests by property (optin-changed event,
-- lead detail).
CREATE INDEX ix_interests_property ON agent_interests (property_id);
