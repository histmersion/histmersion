-- V098: the autopilot's focus (docs/research-process-managers.md "As built: the focus"). With the
-- autopilot on, at most `agents.autopilot.max-active-goals` research goals are ACTIVE for model work at
-- a time; the others wait with their model-using items held (reason `focus`). One row per goal the focus
-- has touched or a person pinned: whether it is active now (since when, and why it was chosen), when and
-- why it last left the active set, and a person's pin and priority. `agent_autopilot.max_active_goals`
-- is a person's override of the configured cap (NULL = the config's). V092 and V095 stay unused.
CREATE TABLE goal_focus (
  slug       text PRIMARY KEY REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  active     boolean NOT NULL DEFAULT false,
  pinned     boolean NOT NULL DEFAULT false,
  priority   int NOT NULL DEFAULT 0,
  since      timestamptz,
  why        text,
  left_at    timestamptz,
  left_why   text,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX goal_focus_active ON goal_focus (active) WHERE active;
ALTER TABLE agent_autopilot ADD COLUMN max_active_goals int;
