-- Histmersion V024 — a place's 'extent' footprint always mirrors the place.
--
-- V016 seeded one 'extent' footprint per place as a COPY of places.geom/period/source, and nothing
-- kept it current: places imported afterwards had none (13 116 of 29 865), and places re-imported
-- with corrected provenance kept the old text ("traced by hand after …" on the agent-drawn polities).
-- Every writer of `places` — importers, the hierarchy builder, coastline clipping — would have to
-- remember it, so the database does it: a row trigger replaces the extent footprint whenever the
-- place's shape, period, source or properties change. Other roles (core, maximal, frontier, seat,
-- snapshot) are independent dated shapes and are left alone.

CREATE OR REPLACE FUNCTION places_sync_extent_footprint() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM place_footprints WHERE place_id = NEW.id AND role = 'extent';
  IF NEW.geom IS NOT NULL THEN
    INSERT INTO place_footprints (place_id, role, geom, period, certainty, citekey)
    VALUES (NEW.id, 'extent', NEW.geom, coalesce(NEW.period, int8range(NULL, NULL)),
            CASE WHEN coalesce((NEW.properties->>'contested')::boolean, false)
                 THEN 'uncertain' ELSE 'approximate' END,
            NEW.source);
  END IF;
  RETURN NULL;
END
$$;

DROP TRIGGER IF EXISTS places_extent_footprint ON places;
CREATE TRIGGER places_extent_footprint
  AFTER INSERT OR UPDATE OF geom, period, source, properties ON places
  FOR EACH ROW EXECUTE FUNCTION places_sync_extent_footprint();

-- Bring every existing place in line once.
DELETE FROM place_footprints WHERE role = 'extent';
INSERT INTO place_footprints (place_id, role, geom, period, certainty, citekey)
SELECT p.id, 'extent', p.geom, coalesce(p.period, int8range(NULL, NULL)),
       CASE WHEN coalesce((p.properties->>'contested')::boolean, false)
            THEN 'uncertain' ELSE 'approximate' END,
       p.source
FROM places p
WHERE p.geom IS NOT NULL;
