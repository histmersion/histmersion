-- V081: a person's corrected POINT for a place, kept by every re-import (2026-10-10, #1445).
--
-- Two stored points were far from the place they stand for: Parthyene/Parthia (pleiades_952097) sat on
-- Ctesiphon near Baghdad — `lint --rule place-grid-point --fix` had moved it from Pleiades' atlas grid
-- square to its linked Wikidata item's coordinate (P625), which is the Parthian capital in Mesopotamia —
-- and Ancient Egypt (pleiades_981503) sits in Sudan, Pleiades' representative point of a region that
-- reaches up the Nile. `places set` could change a kind and a period (V080), not a point.
--
-- `place_overrides.point` is that point (`histmersion places set <id> --point lat,lon --reason …`,
-- POST /api/places/{id}/override {pointLat, pointLon}, MCP `place_override`). The same trigger as V080
-- puts it over whatever geometry a writer sends — but only over a POINT (or no geometry): a source that
-- later gives the place a real shape keeps its shape, and the override's point waits, unused, for a
-- reset. `source_geom` remembers what the writer sent, so a reset restores it without a re-import.
-- The override makes `lint --rule place-grid-point` leave the place alone (`PlaceReviewRepo.relocationsIn`).
ALTER TABLE place_overrides ADD COLUMN IF NOT EXISTS point       geometry(Point, 4326);
ALTER TABLE place_overrides ADD COLUMN IF NOT EXISTS source_geom geometry(Geometry, 4326);

ALTER TABLE place_overrides DROP CONSTRAINT IF EXISTS place_overrides_check;
ALTER TABLE place_overrides ADD CONSTRAINT place_overrides_check
  CHECK (kind IS NOT NULL OR period IS NOT NULL OR point IS NOT NULL);

CREATE OR REPLACE FUNCTION places_apply_override() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  o place_overrides%ROWTYPE;
  sk text;
  sp int8range;
  su boolean;
  sg geometry;
  changed boolean := false;
BEGIN
  SELECT * INTO o FROM place_overrides WHERE place_id = NEW.id;
  IF NOT FOUND THEN
    RETURN NEW;
  END IF;
  sk := o.source_kind; sp := o.source_period; su := o.source_start_unknown; sg := o.source_geom;
  -- a value other than the override's is what the writer's source says: remember it, apply the override
  IF o.kind IS NULL THEN
    sk := NEW.kind;
  ELSIF NEW.kind IS DISTINCT FROM o.kind THEN
    sk := NEW.kind;
    NEW.kind := o.kind;
    changed := true;
  END IF;
  IF o.period IS NULL THEN
    sp := NEW.period; su := NEW.start_unknown;
  ELSIF NEW.period IS DISTINCT FROM o.period OR NEW.start_unknown THEN
    sp := NEW.period; su := NEW.start_unknown;
    NEW.period := o.period;
    NEW.start_unknown := false;
  END IF;
  -- a point replaces only a point (or nothing): a shape a source gives later is kept
  IF o.point IS NOT NULL AND NEW.geom IS DISTINCT FROM o.point THEN
    sg := NEW.geom;
    IF NEW.geom IS NULL OR GeometryType(NEW.geom) = 'POINT' THEN
      NEW.geom := o.point;
      changed := true;
    END IF;
  END IF;
  -- `places_clip_to_land` ran before this trigger (BEFORE triggers fire by name) on what the writer sent
  IF changed THEN
    NEW.geom_clipped := CASE WHEN NEW.kind = 'polity' THEN clip_to_land(NEW.geom) END;
  END IF;
  IF (sk, sp, su) IS DISTINCT FROM (o.source_kind, o.source_period, o.source_start_unknown)
     OR (o.point IS NOT NULL AND NOT (sg IS NOT DISTINCT FROM o.source_geom)) THEN
    UPDATE place_overrides SET source_kind = sk, source_period = sp, source_start_unknown = su,
                               source_geom = CASE WHEN o.point IS NULL THEN source_geom ELSE sg END
    WHERE place_id = NEW.id;
  END IF;
  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS places_override ON places;
CREATE TRIGGER places_override
  BEFORE INSERT OR UPDATE OF kind, period, start_unknown, geom ON places
  FOR EACH ROW EXECUTE FUNCTION places_apply_override();
