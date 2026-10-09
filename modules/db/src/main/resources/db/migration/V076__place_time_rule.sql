-- V076: the time rule for place views (2026-10-09) — a Cliopatria concept spans its time slices, and
-- the views at a year read their candidates through small partial indexes.
--
-- (1) A Cliopatria polity CONCEPT spans its time slices.
--
-- The Cliopatria import stores every time slice of a polity as its own dated place
-- (`cliopatria_<slug>_<from>_<to>`) under a grouping node `cliopatria_<slug>` with no shape — and
-- that node was imported with NO period, so all 1 604 of them were "valid at every year" and showed
-- in every time-filtered view (a search at 2500 BCE offered the Free City of Kraków). The importer
-- now gives a concept the span of the dated slices that name it, as polity or as `MemberOf`; this
-- does the same for the stored ones. A concept none of whose slices is dated keeps the open range.
UPDATE places p
SET period = s.span
FROM (
  SELECT r.parent_id, int8range(min(lower(c.period)), max(upper(c.period)), '[)') AS span
  FROM place_relations r JOIN places c ON c.id = r.child_id
  WHERE r.parent_id LIKE 'cliopatria\_%' AND c.id LIKE 'cliopatria\_%'
    AND NOT lower_inf(c.period) AND NOT upper_inf(c.period) AND NOT isempty(c.period)
  GROUP BY r.parent_id
) s
WHERE p.id = s.parent_id
  AND p.geom IS NULL AND lower_inf(p.period) AND upper_inf(p.period)
  AND p.source LIKE 'Cliopatria%';

-- (2) The two halves of a base layer at a year (`PlaceRepo.geoJsonQuery`), each through its own
-- (kind, period) GiST. The period alone cannot narrow them: every undated GeoNames/Wikidata place is
-- open on both sides, so `places_period_gist` matches 1.19 M of the 1.28 M places at any year — and
-- the planner AND'ed it into the sites layer anyway (45 ms of 115 at 500 CE on a live-sized copy).
--   - the curated places (the predicate of `places_base_layer`, V075, word for word): 55 000 rows;
--   - the DATED imports: 77 000 rows, ~600 of them alive in any one year; the layer keeps those a
--     finding is attached to (an undated import is never on a map at a year). Read through
--     `finding_places` instead, every one of the 9 500 imported places with a finding was fetched
--     to keep the few dozen alive then (30 ms a year).
CREATE INDEX IF NOT EXISTS places_base_layer_time ON places USING gist (kind, period)
  WHERE import_source IS NULL OR import_source NOT IN ('wikidata', 'geonames');
CREATE INDEX IF NOT EXISTS places_dated_import ON places USING gist (kind, period)
  WHERE import_source IN ('wikidata', 'geonames') AND NOT (lower_inf(period) AND upper_inf(period));

-- (3) A place search at a year before `places.modern-from` (1500): the time rule never shows a
-- present-day GeoNames/Wikidata place then (1.15 M of the 1.28 M places), so its passes read the
-- other 132 000 — the label trigrams and the ids — apart (`PlaceRepo.ShownBeforeModernSql`, word for
-- word). At 2500 BCE "Tro" checked 8 500 candidates by primary key and searched all 1.28 M labels:
-- 57 ms; 34 ms through these (7.5 MB and 5.3 MB).
CREATE INDEX IF NOT EXISTS places_before_modern ON places (id)
  WHERE NOT (coalesce(import_source, '') IN ('wikidata', 'geonames') AND coalesce((properties->>'modern')::boolean, false));
CREATE INDEX IF NOT EXISTS places_before_modern_label_trgm ON places USING gin (label gin_trgm_ops)
  WHERE NOT (coalesce(import_source, '') IN ('wikidata', 'geonames') AND coalesce((properties->>'modern')::boolean, false));
