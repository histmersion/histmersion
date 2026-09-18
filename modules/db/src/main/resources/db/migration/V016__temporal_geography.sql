-- hist-db V016 — temporal geography (plan §1.1): footprints, typed relations, periods,
-- place types and external links.
--
-- The gazetteer (V005) puts ONE geometry per place in `places.geom`. This migration adds
-- `place_footprints` — one-to-many dated geometries with roles, certainty and provenance —
-- following the Pleiades Place/Location split. `places.geom` is kept as the representative
-- shape (backward compat), and each existing geometry is seeded as an `extent` footprint.
--
-- Also adds: `periods` (PeriodO-shaped named periods), `place_types` (dated typology),
-- `place_links` (Pleiades/Wikidata/TGN cross-references), and documents the expanded
-- relation vocabulary.

-- ── place_footprints: one-to-many dated geometries per place ─────────────────────────────────
CREATE TABLE IF NOT EXISTS place_footprints (
  id         serial PRIMARY KEY,
  place_id   text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  role       text NOT NULL DEFAULT 'extent',     -- extent|core|maximal|frontier|seat|snapshot
  geom       geometry(Geometry, 4326) NOT NULL,
  period     int8range NOT NULL DEFAULT int8range(NULL, NULL),
  certainty  text,                                -- definite|approximate|uncertain
  accuracy_m real,                                -- spatial accuracy in metres
  citekey    text                                 -- source of this specific shape
);
CREATE INDEX IF NOT EXISTS place_footprints_place    ON place_footprints (place_id);
CREATE INDEX IF NOT EXISTS place_footprints_geom     ON place_footprints USING gist (geom);
CREATE INDEX IF NOT EXISTS place_footprints_period   ON place_footprints USING gist (period);
CREATE INDEX IF NOT EXISTS place_footprints_citekey  ON place_footprints (citekey) WHERE citekey IS NOT NULL;

-- Seed from existing places.geom: one 'extent' footprint per place that has geometry.
INSERT INTO place_footprints (place_id, role, geom, period, certainty, citekey)
SELECT p.id, 'extent', p.geom, p.period,
       CASE WHEN coalesce((p.properties->>'contested')::boolean, false)
            THEN 'uncertain' ELSE 'approximate' END,
       p.source
FROM places p
WHERE p.geom IS NOT NULL;

-- ── periods: named historical periods (PeriodO-shaped) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS periods (
  id         text PRIMARY KEY,                    -- slug: 'lba_eastern_med', 'iron_age_i'
  label      text NOT NULL,
  authority  text,                                -- 'PeriodO', 'hist-db', 'Bryce 2005'
  period     int8range NOT NULL,
  region     text,                                -- geographic scope of the definition
  parent_id  text REFERENCES periods(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS periods_period ON periods USING gist (period);

-- ── place_types: dated typology (a place can change type over time) ──────────────────────────
CREATE TABLE IF NOT EXISTS place_types (
  place_id   text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  type_label text NOT NULL,                       -- settlement|port|capital|fortress|temple|...
  period     int8range NOT NULL DEFAULT int8range(NULL, NULL),
  citekey    text,
  PRIMARY KEY (place_id, type_label)
);

-- ── place_links: external identifiers (Pleiades, Wikidata, TGN, GeoNames, WHG) ─────────────
CREATE TABLE IF NOT EXISTS place_links (
  place_id    text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  scheme      text NOT NULL,                      -- pleiades|wikidata|tgn|geonames|whg
  external_id text NOT NULL,
  relation    text NOT NULL DEFAULT 'close_match', -- close_match|exact_match|broad_match|see_also
  PRIMARY KEY (place_id, scheme, external_id)
);
CREATE INDEX IF NOT EXISTS place_links_external ON place_links (scheme, external_id);

-- Seed place_links from existing Pleiades ids — places whose id starts with 'pleiades_'.
INSERT INTO place_links (place_id, scheme, external_id, relation)
SELECT p.id, 'pleiades', replace(p.id, 'pleiades_', ''), 'exact_match'
FROM places p
WHERE p.id LIKE 'pleiades_%'
ON CONFLICT DO NOTHING;

-- ── document the expanded relation vocabulary ────────────────────────────────────────────────
-- place_relations.kind is free text; the recognised values are now:
--   part_of           — the general containment (existing, the only one in use so far)
--   part_of_admin     — administrative containment (city in province)
--   part_of_regional  — geographic/cultural region membership
--   part_of_physical  — physical geography (river in watershed)
--   succeeds          — temporal succession (Byzantium succeeds Rome at this site)
--   same_as           — co-reference (two ids for the same entity)
--   version_of        — a dated snapshot of a changing entity
--   capital_of        — seat of government/power
-- No schema change needed; the code just recognises more values.
COMMENT ON COLUMN place_relations.kind IS
  'part_of | part_of_admin | part_of_regional | part_of_physical | succeeds | same_as | version_of | capital_of';
