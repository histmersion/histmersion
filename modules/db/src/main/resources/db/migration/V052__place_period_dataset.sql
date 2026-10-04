-- V052: which places and named periods are this collection's OWN (docs/research-merge-import.md §7.8).
--
-- The gazetteer and the period presets come from the same bundled seeds on every instance
-- (`histmersion init`, `places seed`, `periods seed`, `places fetch`), so a bundle never carries them;
-- a place or period a person added here (an LPF file of their own, a period they defined) is part of
-- the collection and must travel. `dataset` tells the two apart: 'seed' for what the seed paths
-- import, 'local' for everything else — the column default, so a writer that does not say is local.
-- Rows that exist when this runs cannot be told apart and are marked 'seed' (the conservative
-- choice: nothing that was not carried before starts travelling); re-import a file of your own with
-- `--dataset local` to mark its places.
--
-- A local place or period also gets what a bundle needs of every row it carries: a change clock
-- (`updated_at`, moved by the V030 trigger; a place's names, parents, links and types move it too) and
-- a tombstone when it is deleted (V031) — a seed row needs neither.

ALTER TABLE places  ADD COLUMN IF NOT EXISTS dataset text;
ALTER TABLE periods ADD COLUMN IF NOT EXISTS dataset text;
UPDATE places  SET dataset = 'seed' WHERE dataset IS NULL;
UPDATE periods SET dataset = 'seed' WHERE dataset IS NULL;
ALTER TABLE places  ALTER COLUMN dataset SET DEFAULT 'local', ALTER COLUMN dataset SET NOT NULL;
ALTER TABLE periods ALTER COLUMN dataset SET DEFAULT 'local', ALTER COLUMN dataset SET NOT NULL;
ALTER TABLE places  DROP CONSTRAINT IF EXISTS places_dataset_check;
ALTER TABLE places  ADD CONSTRAINT places_dataset_check CHECK (dataset IN ('seed', 'local'));
ALTER TABLE periods DROP CONSTRAINT IF EXISTS periods_dataset_check;
ALTER TABLE periods ADD CONSTRAINT periods_dataset_check CHECK (dataset IN ('seed', 'local'));
CREATE INDEX IF NOT EXISTS places_local  ON places (id)  WHERE dataset = 'local';
CREATE INDEX IF NOT EXISTS periods_local ON periods (id) WHERE dataset = 'local';

-- the change clock (V030's trigger: an UPDATE moves it unless the statement set it)
ALTER TABLE places  ADD COLUMN IF NOT EXISTS updated_at timestamptz;
ALTER TABLE periods ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE places  SET updated_at = now() WHERE updated_at IS NULL;
UPDATE periods SET updated_at = now() WHERE updated_at IS NULL;
ALTER TABLE places  ALTER COLUMN updated_at SET DEFAULT now(), ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE periods ALTER COLUMN updated_at SET DEFAULT now(), ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS places_touch_updated_at ON places;
CREATE TRIGGER places_touch_updated_at BEFORE UPDATE ON places
  FOR EACH ROW EXECUTE FUNCTION histmersion_touch_updated_at();
DROP TRIGGER IF EXISTS periods_touch_updated_at ON periods;
CREATE TRIGGER periods_touch_updated_at BEFORE UPDATE ON periods
  FOR EACH ROW EXECUTE FUNCTION histmersion_touch_updated_at();

-- what a local place owns moves it (INSERT and DELETE; the sets are replaced, never updated in place
-- by an import). `place_footprints` is not among them: its 'extent' row follows the place (V024).
CREATE OR REPLACE FUNCTION histmersion_touch_place() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  r record;
  pid text;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  IF TG_TABLE_NAME = 'place_relations' THEN pid := r.child_id; ELSE pid := r.place_id; END IF;
  UPDATE places SET updated_at = now() WHERE id = pid AND dataset = 'local' AND updated_at <> now();
  RETURN NULL;
END
$$;
DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['place_names', 'place_relations', 'place_links', 'place_types']
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', t || '_touch_place', t);
    EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW '
                   'EXECUTE FUNCTION histmersion_touch_place()', t || '_touch_place', t);
  END LOOP;
END
$$;

-- a deleted LOCAL place or period leaves a tombstone, so the deletion travels (V031's function)
DROP TRIGGER IF EXISTS places_tombstone ON places;
CREATE TRIGGER places_tombstone AFTER DELETE ON places
  FOR EACH ROW WHEN (OLD.dataset = 'local') EXECUTE FUNCTION histmersion_tombstone('place');
DROP TRIGGER IF EXISTS periods_tombstone ON periods;
CREATE TRIGGER periods_tombstone AFTER DELETE ON periods
  FOR EACH ROW WHEN (OLD.dataset = 'local') EXECUTE FUNCTION histmersion_tombstone('period');
DROP TRIGGER IF EXISTS places_untombstone ON places;
CREATE TRIGGER places_untombstone AFTER INSERT ON places
  FOR EACH ROW EXECUTE FUNCTION histmersion_untombstone();
DROP TRIGGER IF EXISTS periods_untombstone ON periods;
CREATE TRIGGER periods_untombstone AFTER INSERT ON periods
  FOR EACH ROW EXECUTE FUNCTION histmersion_untombstone();
