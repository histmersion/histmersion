-- Histmersion V046 — goals become plannable (docs/research-embedded-agents.md §5.1, §14).
--
-- A goal gains the plan a person APPROVED (`plan`, the verified proposal of a `plan-goal` run, kept as
-- a record of what was created from it) and a default cost cap for the runs started for it (by a
-- person or by its schedules). `goal_links` ties a goal to what it is about: sources, articles, search
-- queries (what an acquisition sweep looks for) and queue items (what a plan asked to acquire).
ALTER TABLE goals ADD COLUMN IF NOT EXISTS plan       jsonb;
ALTER TABLE goals ADD COLUMN IF NOT EXISTS budget_usd numeric(12,6);

CREATE TABLE IF NOT EXISTS goal_links (
  goal_slug  text NOT NULL REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  kind       text NOT NULL,                       -- source | article | query | queue-item
  ref        text NOT NULL,                       -- a citekey, a slug, the query text, a queue item id
  note       text,                                -- why it is linked (a plan's reason, a person's note)
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (goal_slug, kind, ref)
);
CREATE INDEX IF NOT EXISTS goal_links_ref ON goal_links (kind, ref);
