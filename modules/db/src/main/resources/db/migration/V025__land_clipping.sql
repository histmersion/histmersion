-- Histmersion V025 — polities drawn on land, not over the sea.
--
-- Cliopatria and OpenHistoricalMap outlines are generalised: a coastal state's polygon runs well out
-- to sea, and an island state (Minoan Crete) is a blob. Clipping each polity to real land fixes the
-- picture without touching the source record:
--   * `land` holds Natural Earth land + minor islands (`histmersion places fetch land`), cut into
--     small pieces (ST_Subdivide) so the GiST index narrows a clip to the few pieces that matter;
--   * `places.geom_clipped` is the polity's shape ∩ land, kept by a trigger whenever a polity's
--     shape changes, and recomputed for every polity when the land is (re)imported;
--   * the map draws `coalesce(geom_clipped, geom)`. `geom` stays the shape the source published —
--     areas, overlaps and finding assignment keep using it.
-- With no land imported the column stays NULL and nothing changes.

CREATE TABLE IF NOT EXISTS land (
  id   serial PRIMARY KEY,
  geom geometry(Geometry, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS land_geom ON land USING gist (geom);

ALTER TABLE places ADD COLUMN IF NOT EXISTS geom_clipped geometry(Geometry, 4326);

-- The polygon part of `g` that lies on land; NULL for a non-areal shape, or when no land is loaded
-- or `g` touches none (the caller then falls back to `g`). `g` is cut into pieces too, so every
-- intersection is small piece × small piece — ~18× faster than intersecting the whole polity (a
-- 6 700-vertex British Empire: 22 s → 0.4 s), with the same result. A land piece wholly inside a
-- polity piece is taken as it is; only the boundary pieces pay for an intersection.
CREATE OR REPLACE FUNCTION clip_to_land(g geometry) RETURNS geometry
LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN g IS NULL OR ST_Dimension(g) < 2 THEN NULL ELSE (
    SELECT ST_Multi(ST_CollectionExtract(ST_Union(
             CASE WHEN ST_CoveredBy(l.geom, s.pg) THEN l.geom ELSE ST_Intersection(l.geom, s.pg) END), 3))
    FROM (SELECT ST_Subdivide(ST_MakeValid(g), 256) AS pg) s
    JOIN land l ON l.geom && s.pg AND ST_Intersects(l.geom, s.pg)) END
$$;

CREATE OR REPLACE FUNCTION places_clip_to_land() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.geom_clipped := CASE WHEN NEW.kind = 'polity' THEN clip_to_land(NEW.geom) END;
  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS places_clip_to_land ON places;
CREATE TRIGGER places_clip_to_land
  BEFORE INSERT OR UPDATE OF geom, kind ON places
  FOR EACH ROW EXECUTE FUNCTION places_clip_to_land();
