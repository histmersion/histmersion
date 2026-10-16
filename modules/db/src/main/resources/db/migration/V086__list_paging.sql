-- Histmersion V086 — keyset paging of the jobs, agent-runs and approvals lists (core `Paging`).
--
-- The lists are newest first, `ORDER BY created_at DESC, id DESC`, and an older page is asked for with
-- `(created_at, id) < (that row's)`. Each index below ends in `(created_at, id)` so a page — with or
-- without a state filter — is one backward index scan that stops after `limit + 1` rows, and the
-- per-state counts (`GROUP BY status`) read the leading column alone. Each replaces the narrower
-- index V010/V035/V041 made, which it covers.

CREATE INDEX IF NOT EXISTS jobs_created_page   ON jobs (created_at, id);
CREATE INDEX IF NOT EXISTS jobs_status_page    ON jobs (status, created_at, id);
DROP INDEX IF EXISTS jobs_status;
-- agent runs are the jobs of kind `agent`, filtered by status (the runs window) or by goal
CREATE INDEX IF NOT EXISTS jobs_kind_page        ON jobs (kind, created_at, id);
CREATE INDEX IF NOT EXISTS jobs_kind_status_page ON jobs (kind, status, created_at, id);
DROP INDEX IF EXISTS jobs_kind;

CREATE INDEX IF NOT EXISTS approvals_created_page ON approvals (created_at, id);
CREATE INDEX IF NOT EXISTS approvals_state_page   ON approvals (state, created_at, id);
DROP INDEX IF EXISTS approvals_state;
