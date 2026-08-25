-- hist-db V005 — the GAZETTEER (plan §1.6a): places with geometry, alternate names, a temporally
-- scoped hierarchy, and the materialised finding→place assignment.
--
-- Modelled on the Linked Places Format / World Historical Gazetteer: the SAME name with DIFFERENT
-- borders over time is DIFFERENT `places` rows sharing a `place_names.name`, each carrying its own
-- geometry and its own validity `period`; a `part_of` edge carries its OWN validity, so "Hatti part_of
-- Anatolia" can hold for −1650…−1180 and nothing else. A NULL `period` anywhere means "always valid".
--
-- Overlap is deliberately allowed: a finding may sit inside several places at the same level
-- (contested / overlapping polities), so counts through `finding_places` are NON-ADDITIVE — a
-- parent's count is count(DISTINCT finding) over its subtree, not the sum of its children.

-- ── places: one row per (entity, shape, validity) ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS places (
  id         text PRIMARY KEY,                    -- stable, source-scoped: 'ne_country_tur', 'pleiades_550595'
  label      text NOT NULL,                       -- display name (the modern/most common one)
  kind       text NOT NULL,                       -- region|polity|site|feature
  geom       geometry(Geometry, 4326),            -- polygon where known, point for sites; NULL = hierarchy only
  period     int8range,                           -- when THIS shape/entity is valid; NULL = always
  source     text,                                -- provenance/citation of the shape ("Natural Earth 5.1.1")
  properties jsonb                                -- everything the importer did not model (ccodes, types, …)
);
CREATE INDEX IF NOT EXISTS places_geom_gist   ON places USING gist (geom);
CREATE INDEX IF NOT EXISTS places_period_gist ON places USING gist (period);
CREATE INDEX IF NOT EXISTS places_kind        ON places (kind);

-- ── place_names: alternate / period / language names ("Hatti", "Anatolia", "Türkiye") ─────────
CREATE TABLE IF NOT EXISTS place_names (
  place_id text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  name     text NOT NULL,
  lang     text NOT NULL DEFAULT '',              -- '' = unspecified (part of the PK, so never NULL)
  period   int8range,                             -- when the name was used; NULL = always
  PRIMARY KEY (place_id, name, lang)
);
CREATE INDEX IF NOT EXISTS place_names_name_lower ON place_names (lower(name) text_pattern_ops);
CREATE INDEX IF NOT EXISTS place_names_name_ci    ON place_names (lower(name));

-- ── place_relations: the hierarchy, each edge with its OWN validity ───────────────────────────
CREATE TABLE IF NOT EXISTS place_relations (
  child_id  text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  parent_id text NOT NULL REFERENCES places(id) ON DELETE CASCADE,
  kind      text NOT NULL DEFAULT 'part_of',
  period    int8range,
  PRIMARY KEY (child_id, parent_id, kind)
);
CREATE INDEX IF NOT EXISTS place_relations_parent      ON place_relations (parent_id);
CREATE INDEX IF NOT EXISTS place_relations_period_gist ON place_relations USING gist (period);

-- ── finding_places: the MATERIALISED assignment (histdb places rebuild) ───────────────────────
-- finding ∈ place iff the geometries contain (or a point place is within the configured tolerance)
-- AND the two periods overlap; findings without geometry fall back to region_name = a place name.
CREATE TABLE IF NOT EXISTS finding_places (
  finding_id text NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
  place_id   text NOT NULL REFERENCES places(id)   ON DELETE CASCADE,
  PRIMARY KEY (finding_id, place_id)
);
CREATE INDEX IF NOT EXISTS finding_places_place ON finding_places (place_id);
