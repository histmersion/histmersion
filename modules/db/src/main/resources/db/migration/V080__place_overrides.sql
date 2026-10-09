-- V080: a person's curated KIND and PERIOD for a place, kept by every re-import; places a person
-- removed (2026-10-09).
--
-- Pleiades files Regio XII (Piscina Publica), one of the 14 Regiones of Augustan Rome, as an "urban"
-- site — so it kept the 10 km point tolerance and took 59 findings about the city — while the other
-- 13 are regions. A raw UPDATE of `places.kind` lasts only until the next `histmersion init`,
-- `places seed` or gazetteer load: every importer upserts the kind and period its source states.
--
-- `place_overrides` holds what a person decided (`histmersion places set <id> --kind … --from … --to …`,
-- POST /api/places/{id}/override, MCP `place_override`): `kind` and/or `period` (NULL = the source's).
-- The DATABASE applies it, as it keeps the extent footprint (V024): a BEFORE INSERT OR UPDATE trigger
-- on `places` puts the override over whatever kind and period a writer sends — the seed upsert, the
-- gazetteer loads, a bundle, the place-kind lint — and records what the writer said as the SOURCE's
-- value (`source_kind`, `source_period`, `source_start_unknown`), so removing the override restores
-- the source without a re-seed. The override follows a rename (ON UPDATE CASCADE) and goes with the
-- place (ON DELETE CASCADE: a merged duplicate's decision is moot).
CREATE TABLE IF NOT EXISTS place_overrides (
  place_id             text PRIMARY KEY REFERENCES places(id) ON UPDATE CASCADE ON DELETE CASCADE,
  kind                 text,
  period               int8range,
  reason               text,
  source_kind          text NOT NULL,
  source_period        int8range NOT NULL,
  source_start_unknown boolean NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  CHECK (kind IS NOT NULL OR period IS NOT NULL)
);

CREATE OR REPLACE FUNCTION places_apply_override() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  o place_overrides%ROWTYPE;
  sk text;
  sp int8range;
  su boolean;
BEGIN
  SELECT * INTO o FROM place_overrides WHERE place_id = NEW.id;
  IF NOT FOUND THEN
    RETURN NEW;
  END IF;
  sk := o.source_kind; sp := o.source_period; su := o.source_start_unknown;
  -- a value other than the override's is what the writer's source says: remember it, apply the override
  IF o.kind IS NULL THEN
    sk := NEW.kind;
  ELSIF NEW.kind IS DISTINCT FROM o.kind THEN
    sk := NEW.kind;
    NEW.kind := o.kind;
  END IF;
  IF o.period IS NULL THEN
    sp := NEW.period; su := NEW.start_unknown;
  ELSIF NEW.period IS DISTINCT FROM o.period OR NEW.start_unknown THEN
    sp := NEW.period; su := NEW.start_unknown;
    NEW.period := o.period;
    NEW.start_unknown := false;
  END IF;
  IF (sk, sp, su) IS DISTINCT FROM (o.source_kind, o.source_period, o.source_start_unknown) THEN
    UPDATE place_overrides SET source_kind = sk, source_period = sp, source_start_unknown = su
    WHERE place_id = NEW.id;
  END IF;
  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS places_override ON places;
CREATE TRIGGER places_override
  BEFORE INSERT OR UPDATE OF kind, period, start_unknown ON places
  FOR EACH ROW EXECUTE FUNCTION places_apply_override();

-- 2. A place a person REMOVED (`histmersion places rm`, only when nothing refers to it): Pleiades keeps
--    18 records it withdrew ("Withdrawn: Untitled") in the seed. A seed import skips an id listed here,
--    and an id that is an old id of another place (`place_aliases`: a merged duplicate, a renamed
--    place) — so neither a removal nor a merge (`histmersion places merge`) comes back with the next
--    `places seed`. Deleting the row lets the seed bring the place back.
CREATE TABLE IF NOT EXISTS place_removals (
  id         text PRIMARY KEY,
  label      text NOT NULL,
  reason     text,
  removed_at timestamptz NOT NULL DEFAULT now()
);
