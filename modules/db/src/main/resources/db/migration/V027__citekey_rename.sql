-- V027: a citekey can be RENAMED, and the old one keeps working.
--
-- A citekey is the corpus's id AND the reader's first guess at what a source is: `page2024_history`
-- for a 1959 book tells every reader the wrong year. Renaming one used to mean a hand-written
-- UPDATE of every table that stores it — nine tables, three of them without a foreign key — so it
-- was never done. Two changes make it one statement plus bookkeeping (`SourceRepo.rename`):
--
--   1. Every foreign key into `sources(citekey)`, `findings(id)` (finding ids are `<citekey>#<n>`)
--      and `assets(citekey, name)` gains ON UPDATE CASCADE, so `UPDATE sources SET citekey = …`
--      carries findings, assessments, attestations, chunks, assets, both edge tables and
--      `unresolved_edges.src` along with it — and `UPDATE findings SET id = …` carries
--      `finding_places`. The ON DELETE action each key already had is kept: the new definition is
--      the old one, as Postgres prints it, plus the update action.
--   2. `source_aliases` remembers every old citekey, so links, bookmarks, `[[old]]` in text written
--      elsewhere and the exported Markdown mirror keep resolving to the renamed source.

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.conname, c.conrelid::regclass AS tbl, pg_get_constraintdef(c.oid) AS def
    FROM pg_constraint c
    WHERE c.contype = 'f'
      AND c.confrelid IN ('sources'::regclass, 'findings'::regclass, 'assets'::regclass)
      AND c.confupdtype <> 'c'                       -- not already ON UPDATE CASCADE
  LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I, ADD CONSTRAINT %I %s ON UPDATE CASCADE',
                   r.tbl, r.conname, r.conname, r.def);
  END LOOP;
END
$$;

-- An alias is an OLD citekey of a stored source. Its target follows further renames (CASCADE on
-- update) and it disappears with the source. An alias is never also a citekey: the rename refuses a
-- new citekey that is an alias of another source, and a write to an alias is refused or redirected.
CREATE TABLE IF NOT EXISTS source_aliases (
  alias      text PRIMARY KEY,
  citekey    text NOT NULL REFERENCES sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS source_aliases_citekey ON source_aliases (citekey);
