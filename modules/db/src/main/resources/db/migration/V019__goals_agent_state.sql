-- Histmersion V019 — SERVER AS SINGLE SOURCE OF TRUTH (plan §1.5): goals and agent_state tables.
--
-- goals: research programs / topic goals (replaces GOAL.md). Each goal has a slug, title,
-- status (planned|seeding|compiled|reviewed|done), a notes field for progress, and the same
-- tags/regions/period as sources for map-level queries.
--
-- agent_state: key-value JSONB store for state that currently lives in local memory files
-- (like agent preferences, session state, discovered patterns). The agent can get/set keys
-- and they survive across sessions.

-- ── goals ───────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS goals (
  slug        text PRIMARY KEY,
  title       text NOT NULL,
  status      text NOT NULL DEFAULT 'planned',  -- planned|seeding|compiled|reviewed|done
  notes       text,
  tags        text[] NOT NULL DEFAULT '{}',
  regions     text[] NOT NULL DEFAULT '{}',
  period      int8range,
  parent_slug text REFERENCES goals(slug) ON DELETE SET NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS goals_status ON goals (status);
CREATE INDEX IF NOT EXISTS goals_parent ON goals (parent_slug) WHERE parent_slug IS NOT NULL;

-- ── agent_state ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_state (
  key         text PRIMARY KEY,
  value       jsonb NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE goals IS 'Research programs and topic goals — replaces GOAL.md. Status: planned → seeding → compiled → reviewed → done.';
COMMENT ON TABLE agent_state IS 'Key-value JSONB store for agent state that must survive across sessions — replaces local memory files.';
