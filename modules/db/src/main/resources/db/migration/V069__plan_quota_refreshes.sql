-- V069: every fresh reading a plan pool took on purpose (docs/research-plan-quotas.md §8) — when a
-- run wanted the pool while its reading was stale or missing, for a run paused on the pool, or on
-- `agent quota refresh`. `mode` is how (`probe` = one minimal `claude -p` turn, `app-server` =
-- `account/rateLimits/read`), `trigger` why, `ok` whether a reading came back, `detail` what.
-- `agent quota` counts today's rows ("refreshed N times today") and shows the last one.
CREATE TABLE IF NOT EXISTS plan_quota_refreshes (
  id      bigserial PRIMARY KEY,
  pool    text NOT NULL,
  at      timestamptz NOT NULL DEFAULT now(),
  mode    text NOT NULL,
  trigger text NOT NULL,
  ok      boolean NOT NULL,
  detail  text NOT NULL
);
CREATE INDEX IF NOT EXISTS plan_quota_refreshes_pool_at ON plan_quota_refreshes (pool, at DESC);
