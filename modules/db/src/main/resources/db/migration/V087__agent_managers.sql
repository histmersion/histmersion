-- Persistent process managers (docs/research-process-managers.md §2). V086 is the list-paging
-- branch's; Flyway does not need contiguous numbers, so either may be applied first on a fresh
-- database — but a database that has 87 applied refuses a later 86 (validate), so the live corpus
-- must take both from one build.

-- A manager: a persistent driver of work items (the library-wide review manager is 'review').
CREATE TABLE agent_managers (
  id text PRIMARY KEY,               -- 'review', 'seed:<goal>', 'watch:<goal>'
  kind text NOT NULL,                -- review | seed | watch | enrich
  goal_slug text REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  state text NOT NULL DEFAULT 'active',  -- active | paused | stopped (person-set)
  pause_runs boolean NOT NULL DEFAULT false,
  config jsonb NOT NULL DEFAULT '{}', cursor jsonb NOT NULL DEFAULT '{}',
  status_note text,                  -- derived: 'starved: …', 'at capacity', 'idle', 'N pending'
  cron text, next_tick_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());

-- One unit of work a manager drives to an end (an article until its review converges).
CREATE TABLE manager_items (
  id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  kind text NOT NULL, target text NOT NULL,
  status text NOT NULL DEFAULT 'pending', -- pending | held | active | done | stopped | failed | cancelled
  priority int NOT NULL DEFAULT 0, attempts int NOT NULL DEFAULT 0, failures int NOT NULL DEFAULT 0,
  run_id text REFERENCES jobs(id) ON DELETE SET NULL, evaluated_run text,
  not_before timestamptz, hold jsonb,      -- {until?, untilSource?, reason, hint?}
  state jsonb NOT NULL DEFAULT '{}',      -- per-run open-major history, reopen counts, notes
  outcome text, origin text NOT NULL,     -- selection | person | chain | hint
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (manager_id, kind, target));
CREATE INDEX manager_items_next ON manager_items (manager_id, status, priority DESC, id);

-- What the person told a manager (manager-wide or about one item; standing or once).
CREATE TABLE manager_hints (id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  item_id bigint REFERENCES manager_items(id) ON DELETE CASCADE,
  text text NOT NULL, effect jsonb, author text NOT NULL, standing boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(), retired_at timestamptz);

-- Which runs a hint reached (its notes were snapshotted into the run's params).
CREATE TABLE manager_hint_uses (hint_id bigint REFERENCES manager_hints(id) ON DELETE CASCADE,
  run_id text REFERENCES jobs(id) ON DELETE CASCADE, PRIMARY KEY (hint_id, run_id));

-- The manager's feed: what it started, settled, evaluated, stopped, held, and why.
CREATE TABLE manager_events (id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  item_id bigint, run_id text, at timestamptz NOT NULL DEFAULT now(),
  kind text NOT NULL, -- started | settled | evaluated | stopped | escalated | held | hint | paused | stall | starved
  text text NOT NULL);
CREATE INDEX manager_events_recent ON manager_events (manager_id, id DESC);

ALTER TABLE jobs ADD COLUMN manager_id text, ADD COLUMN manager_item bigint, ADD COLUMN heartbeat_at timestamptz;

-- One agent server per database: the holder of 'agents' runs the engine, scheduler and managers.
CREATE TABLE agent_leases (name text PRIMARY KEY, owner text NOT NULL, until timestamptz NOT NULL);
