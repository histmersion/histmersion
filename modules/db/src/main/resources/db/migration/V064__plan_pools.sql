-- V064: per plan pool, what a person set and what the provider refused. `held` is the "hold all agent
-- use of this subscription" switch, checked before anything else; `floors` saved over config
-- (`agents.quota.<pool>.floors`; NULL = config's); `refusal` a provider's own no (Codex
-- `ordinaryUsageAllowed: false`) with when it lifts; `plan_type` as the provider names the plan.
CREATE TABLE IF NOT EXISTS plan_pools (
  pool          text PRIMARY KEY,
  held          boolean NOT NULL DEFAULT false,
  floors        text,
  refusal       text,
  refusal_until timestamptz,
  plan_type     text,
  updated_at    timestamptz NOT NULL DEFAULT now()
);
