-- In-app problem reports (support tickets). Any authenticated user can file one;
-- admins triage them. reporter_user_id is a soft reference to users.id.
CREATE TABLE problem_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_user_id UUID NOT NULL,
  category VARCHAR(20) NOT NULL
    CHECK (category IN ('BUG','PAYMENT','ACCOUNT','SUGGESTION','OTHER')),
  message VARCHAR(2000) NOT NULL,
  context VARCHAR(200),
  status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ,
  resolved_by_user_id UUID
);
CREATE INDEX ix_reports_status ON problem_reports (status, created_at DESC);
