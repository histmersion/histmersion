-- V060: model chains and allowances saved from the CLI, REST, MCP or web UI
-- (docs/research-embedded-agents.md §15). Config holds the defaults (`agents.roles.*`,
-- `agents.allowances`); a row here wins for its role or target until it is reset (deleted).
--
-- A role's chain: the models tried in order, each a validated `provider:model` reference.
CREATE TABLE IF NOT EXISTS agent_role_chains (
  role       text PRIMARY KEY,
  models     text[] NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- An allowance: the rolling-window limits of one model (`anthropic:claude-sonnet-4-5`) or of every
-- model of a provider (`claude-code:*`), as a list of {amount, measure, window}, and the reserve (%)
-- below which the model is no longer used. No limit = unlimited (overrides a config allowance).
CREATE TABLE IF NOT EXISTS agent_allowances (
  target      text PRIMARY KEY,
  limits      jsonb NOT NULL DEFAULT '[]',
  reserve_pct int   NOT NULL DEFAULT 0 CHECK (reserve_pct BETWEEN 0 AND 99),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- What an allowance counts is summed from the steps: by model over a time window.
CREATE INDEX IF NOT EXISTS agent_steps_model_finished ON agent_steps (model, finished_at);
