-- V090: what the map's area layer reads of a shape, stored with the shape (2026-10-16).
--
-- `GET /api/places/geo` over a whole range ("All states in period") reads every state alive in it —
-- 13 000 polities for -3000..2000 — and took 0.9-1.3 s on the live corpus, 85-400 ms for 1000..1500.
-- Where the time went (EXPLAIN ANALYZE on a restore of the 2026-10-16 backup):
--   * ~70 ms per expression that reads the polygon: its area, its bounding box and its dimension were
--     each computed by decompressing the shape again, for every live row;
--   * nearly all the rest reading the SMALL columns: `geom` and `geom_clipped` were `main` storage
--     (PostGIS's default), so the toaster moved every other attribute out of line first to make a
--     row with a large polygon fit — `id` of 9 182 of the 15 102 polities, `properties` of 9 592, the
--     label of 1 103 sat in the TOAST table, and every `id`, `properties->>'method'` and label test
--     of the layer was a TOAST fetch (282 000 buffer reads for one range);
--   * serialising the largest outlines as GeoJSON: land clipping (V025) cuts each polity to Natural
--     Earth's 1:10m coast, 30 M vertices over 13 000 polities where their own outlines have 3.3 M —
--     400 shapes over all time were 22 MB of GeoJSON, 250 ms of writing.
--
-- (1) The polygon columns become `extended`: a large shape is the one moved out of line (compressed),
--     the identifiers, label, period and properties stay in the row. Applies to every row through
--     the rewrite (2) forces.
-- (2) Generated columns, computed from the row on every insert and update (after the BEFORE triggers
--     that clip to land and apply a person's override, V025/V081), so no writer can let them drift:
--       shape_area   ST_Area(geom), the ranking and the "covers half of the smaller" test;
--       shape_box    geom::box2d, the "may overlap" test of the cross-tier collapse;
--       shape_dim    ST_Dimension(geom), "has a boundary" and the undated-polity-point rule;
--       shape_valid  ST_IsValid(geom, 0) (OGC validity, no NOTICE per invalid row): the overlap test
--                    repairs only the 18 invalid areas, as ST_MakeValid returns a valid shape as it is;
--       label_norm   `PlaceRepo.normLabelSql(label)`, word for word — "the same state over time";
--       web_geojson  the outline the map draws, as GeoJSON text: `coalesce(geom_clipped, geom)`
--                    simplified (topology preserved) to a hundredth of the shape's size, at most
--                    0.02° (~2 km: two pixels at zoom 7, the events layer's outline tolerance, and
--                    still finer than Cliopatria's own borders — only the land clip's coast had more),
--                    so a city state keeps its form; 3 decimals (~110 m) for a shape of 1 square
--                    degree or more (tolerance ≥ 0.01°), 4 below. Polygons only; a point or a line is
--                    written from `geom` as before. `geom` and `geom_clipped` stay the published and
--                    clipped shapes for every other use. Over -3000..2000 the 1 744 states drawn are
--                    6.4 MB of outlines this way, 9.4 MB at 0.01°, 28 MB unsimplified.
-- (3) A GiST over (period, geom) of the non-site places (~22 000 rows): the cross-tier collapse over a
--     range finds the shapes that coexist with and may overlap each sketch (tier 3) and each
--     OpenHistoricalMap shape (tier 1) through it, instead of comparing them with all 13 000 alive.
--
-- Cost: one rewrite of `places` with its indexes (1.05 M rows, ~1.2 GB): ~57 s on the restored copy,
-- half of it simplifying 13 000 polities. Flyway runs it before the server binds its port.
ALTER TABLE places
  ALTER COLUMN geom SET STORAGE EXTENDED,
  ALTER COLUMN geom_clipped SET STORAGE EXTENDED,
  ADD COLUMN IF NOT EXISTS shape_area double precision GENERATED ALWAYS AS (ST_Area(geom)) STORED,
  ADD COLUMN IF NOT EXISTS shape_box box2d GENERATED ALWAYS AS (geom::box2d) STORED,
  ADD COLUMN IF NOT EXISTS shape_dim smallint GENERATED ALWAYS AS (ST_Dimension(geom)) STORED,
  ADD COLUMN IF NOT EXISTS shape_valid boolean GENERATED ALWAYS AS (ST_IsValid(geom, 0)) STORED,
  ADD COLUMN IF NOT EXISTS label_norm text GENERATED ALWAYS AS (
    regexp_replace(regexp_replace(lower(label), '\m(of|the|and)\M', '', 'g'), '[^a-z0-9]+', '', 'g')) STORED,
  ADD COLUMN IF NOT EXISTS web_geojson text GENERATED ALWAYS AS (
    CASE WHEN ST_Dimension(geom) = 2 THEN
      ST_AsGeoJSON(ST_SimplifyPreserveTopology(coalesce(geom_clipped, geom), least(0.02, sqrt(ST_Area(geom)) / 100)),
        CASE WHEN ST_Area(geom) >= 1 THEN 3 ELSE 4 END)
    END) STORED;

CREATE INDEX IF NOT EXISTS places_area_time_geom ON places USING gist (period, geom) WHERE kind <> 'site';
