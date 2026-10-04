-- V042: an article slug, an event id and a goal slug can be RENAMED (docs/research-merge-import.md
-- §7.6, milestone M4), as a citekey can since V027.
--
-- A bundle import follows a rename made on the other side (the same uid, a new label) instead of
-- refusing it, and `merge apply` names a record kept beside another (`both`, `rename-right`). The key
-- moves with one UPDATE when every foreign key into it cascades:
--
--   * articles(slug)  ← article_versions.slug, article_links.src_slug
--   * events(id)      ← events.parent_id, event_datings.event_id, event_edges.src_id/dst_id,
--                       event_participants.event_id
--   * goals(slug)     ← goals.parent_slug
--
-- The ON DELETE action each key already had is kept: the new definition is the old one, as Postgres
-- prints it, plus the update action (the V027 recipe).
--
-- `article_aliases` remembers every old slug, so `[[old_slug]]` written elsewhere (another collection,
-- the exported Markdown mirror) keeps resolving — as `source_aliases` does for citekeys.

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.conname, c.conrelid::regclass AS tbl, pg_get_constraintdef(c.oid) AS def
    FROM pg_constraint c
    WHERE c.contype = 'f'
      AND c.confrelid IN ('articles'::regclass, 'events'::regclass, 'goals'::regclass)
      AND c.confupdtype <> 'c'                       -- not already ON UPDATE CASCADE
  LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I, ADD CONSTRAINT %I %s ON UPDATE CASCADE',
                   r.tbl, r.conname, r.conname, r.def);
  END LOOP;
END
$$;

-- An alias is an OLD slug of a stored article. It follows further renames and goes with the article.
-- An alias is never also a slug: the rename refuses a slug that is an alias of another article, and
-- renaming back to an old slug removes that alias.
CREATE TABLE IF NOT EXISTS article_aliases (
  alias      text PRIMARY KEY,
  slug       text NOT NULL REFERENCES articles(slug) ON UPDATE CASCADE ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS article_aliases_slug ON article_aliases (slug);
