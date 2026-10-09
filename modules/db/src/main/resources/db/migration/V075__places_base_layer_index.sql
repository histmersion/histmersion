-- V075: the map's base layers find their places without reading the imported ones (2026-10-09).
--
-- A base layer (polity, region, site, feature) draws the curated gazetteer — the bundled seed, a
-- person's own places, LPF datasets — and only those GeoNames / Wikidata places a finding is attached
-- to (V074, `PlaceRepo.BaseLayerSql`). As one predicate, `curated OR EXISTS (finding_places …)`, the
-- sites layer at a year read every site alive then — 930 000 rows, nearly all imported — to keep
-- 6 400: 0.4 s a year. `PlaceRepo.geoJsonQuery` now reads the two halves apart, the curated ones
-- through this index (55 000 rows, 1.5 MB).
CREATE INDEX IF NOT EXISTS places_base_layer ON places (kind)
  WHERE import_source IS NULL OR import_source NOT IN ('wikidata', 'geonames');
