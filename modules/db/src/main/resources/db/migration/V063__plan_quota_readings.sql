-- V063: the subscription pools' windows as last read (docs/research-plan-quotas.md §5,
-- docs/research-agent-runtimes.md §2.5). A Claude or ChatGPT plan tells how much is left only as a
-- percentage of each window plus its reset; every run's `rate_limit_event`, the Claude quota-guard
-- mod and `codex app-server` (account/rateLimits/read) write the newest reading per window here.
--
-- pool     claude-plan | chatgpt-plan
-- limit_id the provider's bucket (claude, codex, a fallback model's)
-- kind     five_hour | seven_day (Claude), or the window's length, 300m | 10080m (Codex)
-- model    '' = the whole account; else the model a bucket belongs to
CREATE TABLE IF NOT EXISTS plan_quota_readings (
  pool        text NOT NULL,
  limit_id    text NOT NULL,
  kind        text NOT NULL,
  model       text NOT NULL DEFAULT '',
  used_pct    double precision NOT NULL,
  resets_at   timestamptz,
  rejected    boolean NOT NULL DEFAULT false,
  observed_at timestamptz NOT NULL DEFAULT now(),
  source      text NOT NULL,
  PRIMARY KEY (pool, limit_id, kind, model)
);
