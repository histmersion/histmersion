-- V055: a place id can be RENAMED, and the old one keeps resolving (#888, docs/research-merge-import.md
-- §7.8) — as a citekey can since V027, and an article slug, an event id and a goal slug since V042/V053.
--
-- A bundle import used to keep a local place's id when the other collection had renamed it (a new id
-- under the same uid): the upsert matches by uid and leaves the id alone, and the bundle's events that
-- name the new id then pointed at nothing. The import now follows the rename (`PlaceRepo.renameIn`),
-- which moves the key with one UPDATE once every foreign key into `places(id)` cascades:
--
--   place_names, place_relations (child and parent), finding_places, place_footprints, place_types,
--   place_links, events.place_id
--
-- The ON DELETE action each key already had is kept: the new definition is the old one, as Postgres
-- prints it, plus the update action (the V027 recipe). Attestations and asset links name a place by
-- id without a key (`target_type = 'place'`); the rename moves those itself.

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.conname, c.conrelid::regclass AS tbl, pg_get_constraintdef(c.oid) AS def
    FROM pg_constraint c
    WHERE c.contype = 'f'
      AND c.confrelid = 'places'::regclass
      AND c.confupdtype <> 'c'                       -- not already ON UPDATE CASCADE
  LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I, ADD CONSTRAINT %I %s ON UPDATE CASCADE',
                   r.tbl, r.conname, r.conname, r.def);
  END LOOP;
END
$$;

-- An alias is an OLD id of a stored place. It follows further renames and goes with the place. An
-- alias is never also an id: the rename refuses an id that is an alias of another place, and renaming
-- back to an old id removes that alias.
CREATE TABLE IF NOT EXISTS place_aliases (
  alias      text PRIMARY KEY,
  place_id   text NOT NULL REFERENCES places(id) ON UPDATE CASCADE ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS place_aliases_place ON place_aliases (place_id);
