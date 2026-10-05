-- V065: whether a role's chain may fall from a plan pool below its floor to a PAID key (the chain's
-- next model on an API key). Default no: the run pauses until the plan's reset (`plan-quota`), so a
-- weekly floor never silently becomes an API bill. A row here wins over `agents.quota.fallthrough`.
CREATE TABLE IF NOT EXISTS agent_role_fallthrough (
  role        text PRIMARY KEY,
  fallthrough boolean NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);
