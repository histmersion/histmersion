-- V074: what an IMPORTED place's silence about time means, and what the place IS (2026-10-09).
--
-- 1. An unknown start is not "since forever". The GeoNames / Wikidata import (V071) stored a
--    Wikidata item with a dissolution date and no inception as `(-inf, end]`, so the Kingdom of
--    Patones (dissolved 1769) matched every time filter before 1769 — the map at 4000 BCE among them.
--    Such a place now keeps its known end and gets a WINDOW before it: `period = [end − 300, end]`
--    with `start_unknown = true`. Every time filter reads `period` as before (the map at a year, place
--    search and picking with a time, the geocoder, `finding_places`), so they all see the place only
--    in that window; the API reports the stated facts — end 1769, start unknown (`PlaceDating`).
--    300 is the default of `places.sources.open-start-window-years`; an import of the source applies
--    the configured value (its write is a diff on `period`). Only `wikidata` and `geonames` rows: the
--    bundled seed, a person's own places and LPF datasets state their open ranges on purpose.
--    A place with NO date from those sources keeps the open range (it is findable and placeable by
--    name); it is told apart by its source and never drawn on a time-sliced map.
ALTER TABLE places ADD COLUMN IF NOT EXISTS start_unknown boolean NOT NULL DEFAULT false;

UPDATE places
SET period = int8range(upper(period) - 1 - 300, upper(period), '[)'),
    start_unknown = true
WHERE import_source IN ('wikidata', 'geonames')
  AND lower_inf(period) AND NOT upper_inf(period) AND NOT isempty(period);

-- 2. What a place IS: Wikidata's schema:description (English and the place's own language) or an
--    LPF dataset's `descriptions`, one row per source record and language — the provenance of
--    `place_names` (V071), so a re-import is a diff and a source only ever replaces its own rows. A
--    record that JOINED a seed place describes that place too.
CREATE TABLE IF NOT EXISTS place_descriptions (
  place_id       text NOT NULL REFERENCES places(id) ON DELETE CASCADE ON UPDATE CASCADE,
  lang           text NOT NULL DEFAULT '',
  text           text NOT NULL,
  import_source  text NOT NULL,
  import_ref     text NOT NULL,
  import_version text,
  PRIMARY KEY (place_id, lang, import_source, import_ref)
);
CREATE INDEX IF NOT EXISTS place_descriptions_import ON place_descriptions (import_source, import_ref);
