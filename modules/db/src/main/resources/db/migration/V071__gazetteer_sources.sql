-- V071: the gazetteer's EXTERNAL SOURCES (#1064) — GeoNames, Wikidata and Linked Places Format
-- datasets (the World Historical Gazetteer's exports), and the provenance of every row they write.
--
-- One import per source (`histmersion places load geonames|wikidata|lpf`) reads a pinned, checksummed
-- extract (or an LPF file in the drop folder) and joins each record to the places already here BY
-- SHARED IDENTIFIERS ONLY (`place_links`: Wikidata ↔ GeoNames ↔ Pleiades ↔ TGN, an LPF closeMatch /
-- exactMatch). A record whose ids name an existing place adds its names and links to THAT place; a
-- record that names none becomes a place of its own. So one canonical place keeps every name, each
-- with its language, its time span and the source that gave it.
--
-- Provenance, on every row a source writes:
--   import_source   the source key: 'geonames', 'wikidata', 'lpf:<dataset>' ('' / NULL = the bundled
--                   seed, a person's own names and places, or anything older than this migration)
--   import_ref      the record's id in that source ('geonames:2643743', 'wikidata:Q42', an LPF @id)
--   import_version  the version of the source that wrote it — a re-import of the SAME version changes
--                   nothing; one of a new version moves the rows it keeps and removes the rest.
-- The source itself (title, version, checksum, licence, citation, time span, counts) is a row of
-- `gazetteer_sources`.

CREATE TABLE IF NOT EXISTS gazetteer_sources (
  key          text PRIMARY KEY,                       -- 'geonames' | 'wikidata' | 'lpf:<dataset>'
  kind         text NOT NULL CHECK (kind IN ('geonames', 'wikidata', 'lpf')),
  title        text NOT NULL,
  version      text NOT NULL,                          -- the pinned extract's version; an LPF file's sha256 prefix
  sha256       text NOT NULL,                          -- of the file that was read
  file         text NOT NULL,
  url          text,
  licence      text,
  citation     text,                                   -- the attribution / citation the source asks for
  period       int8range NOT NULL DEFAULT int8range(NULL, NULL),   -- the dataset's time span (LPF)
  places       int NOT NULL DEFAULT 0,                 -- records imported
  names        int NOT NULL DEFAULT 0,
  links        int NOT NULL DEFAULT 0,
  imported_at  timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE places ADD COLUMN IF NOT EXISTS import_source  text;
ALTER TABLE places ADD COLUMN IF NOT EXISTS import_ref     text;
ALTER TABLE places ADD COLUMN IF NOT EXISTS import_version text;
CREATE INDEX IF NOT EXISTS places_import ON places (import_source, import_ref) WHERE import_source IS NOT NULL;

-- A name is now one row PER SOURCE: GeoNames and Wikidata may both call a place "Smyrna" in Greek,
-- with different time spans, and each keeps its own. The bundled seed and a person's names are the
-- source '' (so every existing ON CONFLICT on (place_id, name, lang) keeps its meaning with '' added).
ALTER TABLE place_names ADD COLUMN IF NOT EXISTS import_source  text NOT NULL DEFAULT '';
ALTER TABLE place_names ADD COLUMN IF NOT EXISTS import_ref     text NOT NULL DEFAULT '';
ALTER TABLE place_names ADD COLUMN IF NOT EXISTS import_version text;
ALTER TABLE place_names ADD COLUMN IF NOT EXISTS citation       text;  -- an LPF name's citations
ALTER TABLE place_names DROP CONSTRAINT IF EXISTS place_names_pkey;
ALTER TABLE place_names ADD CONSTRAINT place_names_pkey PRIMARY KEY (place_id, name, lang, import_source, import_ref);
CREATE INDEX IF NOT EXISTS place_names_import ON place_names (import_source, import_ref) WHERE import_source <> '';

-- A link keeps the source that wrote it first (the key stays (place_id, scheme, external_id)).
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS import_source  text NOT NULL DEFAULT '';
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS import_ref     text NOT NULL DEFAULT '';
ALTER TABLE place_links ADD COLUMN IF NOT EXISTS import_version text;
CREATE INDEX IF NOT EXISTS place_links_import ON place_links (import_source, import_ref) WHERE import_source <> '';

-- Place search at gazetteer scale: hundreds of thousands of places and millions of names make the
-- `ILIKE '%q%'` of `GET /api/places?q=` a scan of both tables without trigram indexes.
CREATE INDEX IF NOT EXISTS places_label_trgm      ON places      USING gin (label gin_trgm_ops);
CREATE INDEX IF NOT EXISTS place_names_name_trgm  ON place_names USING gin (name gin_trgm_ops);

-- The seed's own identifiers become links once, here (from then on every seed import writes them for
-- the places it touches, `GazetteerRepo.linkSeedIds`): a Pleiades place by its id, Natural Earth by the
-- Wikidata and GeoNames ids in its properties, a curated place by the Wikidata item its source cites.
-- Without them nothing an external source says could reach a seed place.
INSERT INTO place_links (place_id, scheme, external_id, relation)
SELECT id, scheme, ext, rel FROM (
  SELECT id, 'pleiades' AS scheme, substring(properties->>'pleiades_uri' FROM '/places/([0-9]+)') AS ext,
         'exact_match' AS rel FROM places
  UNION ALL
  SELECT id, 'pleiades', substring(id FROM '^pleiades_([0-9]+)$'), 'exact_match' FROM places
  UNION ALL
  SELECT id, 'wikidata', upper(substring(properties->>'wikidata' FROM '^[Qq][0-9]+$')), 'exact_match' FROM places
  UNION ALL
  SELECT id, 'geonames', substring(properties->>'geonames' FROM '^[0-9]+$'), 'exact_match' FROM places
  UNION ALL
  SELECT id, 'wikidata', substring(source FROM 'Wikidata (Q[0-9]+)'), 'close_match' FROM places
) s WHERE ext IS NOT NULL
ON CONFLICT DO NOTHING;
