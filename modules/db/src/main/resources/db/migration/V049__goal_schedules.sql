-- Histmersion V049 — per-goal schedules (docs/research-embedded-agents.md §5.2, §14).
--
-- A goal may have a `currency` schedule (assess + followups over its sources) and an `acquisition`
-- schedule (discover its queries → acquire items). `cron` is a five-field cron expression in UTC
-- (or @hourly/@daily/@weekly/@monthly). Nothing runs unless agents are enabled AND the row is
-- enabled; the daily/monthly cost caps and max-concurrent-runs are checked before each start.
CREATE TABLE IF NOT EXISTS goal_schedules (
  goal_slug   text NOT NULL REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  kind        text NOT NULL,                      -- currency | acquisition
  cron        text NOT NULL,
  enabled     boolean NOT NULL DEFAULT true,
  budget_usd  numeric(12,6),                      -- this schedule's run cap (default: the goal's, then the server's)
  next_run_at timestamptz,
  last_run_at timestamptz,
  last_job    text,
  last_note   text,                               -- what the last tick did (started, skipped: why)
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (goal_slug, kind)
);
CREATE INDEX IF NOT EXISTS goal_schedules_due ON goal_schedules (next_run_at) WHERE enabled;
