-- V093: Getty TGN joins GeoNames and Wikidata as a pinned gazetteer source (#1403).
--
-- (V092 is taken by a concurrent branch.) TGN (the Getty Thesaurus of Geographic Names, ODC-By 1.0)
-- is imported like the other two bulk sources: a pinned, checksummed LPF extract built by
-- db/gazetteer/build.py, joined to the places already here by shared identifiers only (its own
-- `tgn:` id, which 25 753 Wikidata items in the research window already carry as a link). Its places
-- are IMPORTED places like theirs (`Gazetteer.ImportedSources`, `PlaceRepo.importedSql`): no part of
-- the hierarchy, drawn only with a finding, undated = unknown.
--
-- 1. `gazetteer_sources.kind` accepts 'tgn'.
ALTER TABLE gazetteer_sources DROP CONSTRAINT IF EXISTS gazetteer_sources_kind_check;
ALTER TABLE gazetteer_sources ADD CONSTRAINT gazetteer_sources_kind_check
  CHECK (kind IN ('geonames', 'wikidata', 'lpf', 'tgn'));

-- 2. The partial indexes whose predicates name the imported sources (V075, V076, V080) are rebuilt with
--    'tgn' in the list — word for word the predicates `PlaceRepo` writes from `Gazetteer.ImportedSources`,
--    which is what lets the planner use them. No TGN row exists before this migration, so each index
--    holds exactly the rows it held before; the rebuild is the cost (about a minute on a live-sized
--    gazetteer of 1 M places).
DROP INDEX IF EXISTS places_base_layer;
CREATE INDEX places_base_layer ON places (kind)
  WHERE import_source IS NULL OR import_source NOT IN ('wikidata', 'geonames', 'tgn');

DROP INDEX IF EXISTS places_base_layer_time;
CREATE INDEX places_base_layer_time ON places USING gist (kind, period)
  WHERE import_source IS NULL OR import_source NOT IN ('wikidata', 'geonames', 'tgn');

DROP INDEX IF EXISTS places_dated_import;
CREATE INDEX places_dated_import ON places USING gist (kind, period)
  WHERE import_source IN ('wikidata', 'geonames', 'tgn') AND NOT (lower_inf(period) AND upper_inf(period));

DROP INDEX IF EXISTS places_before_modern;
CREATE INDEX places_before_modern ON places (id)
  WHERE NOT (coalesce(import_source, '') IN ('wikidata', 'geonames', 'tgn') AND coalesce((properties->>'modern')::boolean, false));

DROP INDEX IF EXISTS places_before_modern_label_trgm;
CREATE INDEX places_before_modern_label_trgm ON places USING gin (label gin_trgm_ops)
  WHERE NOT (coalesce(import_source, '') IN ('wikidata', 'geonames', 'tgn') AND coalesce((properties->>'modern')::boolean, false));

DROP INDEX IF EXISTS places_rep_point;
CREATE INDEX places_rep_point ON places USING gist (ST_PointOnSurface(geom))
  WHERE ST_Dimension(geom) > 0 AND kind <> 'site' AND coalesce(import_source, '') NOT IN ('wikidata', 'geonames', 'tgn');

DROP INDEX IF EXISTS places_explicit_point;
CREATE INDEX places_explicit_point ON places USING gist (geom)
  WHERE coalesce(import_source, '') NOT IN ('wikidata', 'geonames', 'tgn')
    AND coalesce(ST_Dimension(geom) = 0 AND (coalesce(kind, '') NOT IN ('site', 'feature')
                 OR coalesce(location_precision = 'grid-cell', false)), false);
