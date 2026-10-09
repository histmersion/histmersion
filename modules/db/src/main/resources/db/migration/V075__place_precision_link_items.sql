-- V075: how exact a place's point is, and what a linked record says about the place (2026-10-09).
--
-- 1. `places.location_precision`: 'grid-cell' for a Pleiades place whose representative point is only
--    the CENTRE of a square of the Barrington Atlas grid (5°, 1° or ½° cells) — Pleiades' "rough"
--    location. Thessalia the late Roman province sits at 37.5 N 22.5 E, in the Peloponnese; Viennensis
--    and Tarraconensis share 42.5 N 2.5 W. NULL = the point is as stated. Generated from the row, so
--    every import and rename keeps it right; `core.LocationPrecision` is its Scala twin
--    (`PlaceCleanupSuite` holds them equal). The geocoder ranks such a point below any precise one,
--    and `finding_places` gives it findings only by an explicit match, never by distance.
CREATE OR REPLACE FUNCTION histmersion_grid_centre(x double precision, y double precision) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT coalesce(bool_or(
           mod(mod(round((x * 1000000)::numeric)::bigint, c) + c, c) = c / 2 AND
           mod(mod(round((y * 1000000)::numeric)::bigint, c) + c, c) = c / 2), false)
  FROM unnest(ARRAY[5000000, 1000000, 500000]::bigint[]) AS c
$$;

-- (nested CASEs: ST_X of a polygon is an error, and only a CASE fixes the order of evaluation)
ALTER TABLE places ADD COLUMN IF NOT EXISTS location_precision text GENERATED ALWAYS AS (
  CASE WHEN id LIKE 'pleiades\_%' AND GeometryType(geom) = 'POINT' THEN
    CASE WHEN histmersion_grid_centre(ST_X(geom), ST_Y(geom)) THEN 'grid-cell' END
  END) STORED;

-- 2. What the record behind a link says the item IS: its class (Wikidata's selecting class, e.g.
--    Q3024240 "historical country"), its point and its dates — written by a gazetteer import on the
--    record's OWN identifier link, also when the record joined a seed place (Pleiades' Thessalia joined
--    by Wikidata Q…), so that a place keeps the evidence of the records it absorbed. Read by
--    `lint --rule place-kind` (is this "region" a polity?) and `lint --rule place-grid-point` (a
--    better point than a grid square's centre). NULL = the record said nothing (or predates V075; the
--    next import of the source fills it).
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS item_class  text;
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS item_geom   geometry(Point, 4326);
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS item_period int8range;
