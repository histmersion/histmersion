-- V053: an event id and a goal slug keep resolving after a rename (docs/research-merge-import.md
-- §7.8), as an old citekey does (V027) and an old article slug does (V042).
--
-- `histmersion events rename` / `goals rename` (and the bundle import that follows a rename made on
-- the other side) move the label with one UPDATE — every foreign key cascades since V042 — and keep
-- the old one here. A read by the old label shows the record under its new one; a write to the old
-- label is refused with where it went. An alias is never also a label: the rename refuses a label
-- that is an alias of another record, and renaming back to an old label removes that alias.

CREATE TABLE IF NOT EXISTS event_aliases (
  alias      text PRIMARY KEY,
  event_id   text NOT NULL REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS event_aliases_event ON event_aliases (event_id);

CREATE TABLE IF NOT EXISTS goal_aliases (
  alias      text PRIMARY KEY,
  goal_slug  text NOT NULL REFERENCES goals(slug) ON UPDATE CASCADE ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS goal_aliases_goal ON goal_aliases (goal_slug);
