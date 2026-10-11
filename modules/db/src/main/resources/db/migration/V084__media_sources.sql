-- V084: pictures of sources, and a place's pictures reached through its equivalents (#1446, second slice).
--
-- 1. `media_links` may now point at a SOURCE: a book's cover (`cover` — from the local Calibre library
--    or the Open Library Covers API) or a paper's illustration (`illustration` — one of the figures
--    the OCR conversion already cut out of it, chosen by size and position; the figure keeps its own
--    `assets` row and its `asset_links`, the link only says "this one illustrates the source").
-- 2. `media_links.item` (the Wikidata item a picture was read from) is how a place reaches a picture
--    stored for ANOTHER place with the same item — Mycenae as `pleiades_570491` and as a Wikidata
--    import, each slice of a polity — so it is indexed for that lookup.
-- 3. `media_checks.qid`: the Wikidata item a check asked about. An import moves on per ITEM, not per
--    place id, so the 5 726 polity slices that share ~500 items are asked about once each; existing
--    checks take the item their place or event names.

ALTER TABLE media_links DROP CONSTRAINT IF EXISTS media_links_target_type_check;
ALTER TABLE media_links ADD CONSTRAINT media_links_target_type_check
  CHECK (target_type IN ('place', 'event', 'source'));
ALTER TABLE media_links DROP CONSTRAINT IF EXISTS media_links_role_check;
ALTER TABLE media_links ADD CONSTRAINT media_links_role_check
  CHECK (role IN ('image', 'locator-map', 'plan', 'aerial-view', 'route-map', 'cover', 'illustration'));

CREATE INDEX IF NOT EXISTS media_links_item ON media_links (item, rank) WHERE item IS NOT NULL;

ALTER TABLE media_checks DROP CONSTRAINT IF EXISTS media_checks_target_type_check;
ALTER TABLE media_checks ADD CONSTRAINT media_checks_target_type_check
  CHECK (target_type IN ('place', 'event', 'source'));
ALTER TABLE media_checks ADD COLUMN IF NOT EXISTS qid text;

UPDATE media_checks c SET qid = e.wikidata
  FROM events e WHERE c.target_type = 'event' AND c.target_id = e.id AND c.qid IS NULL;
UPDATE media_checks c SET qid = coalesce(
    (SELECT pl.external_id FROM place_links pl
      WHERE pl.place_id = c.target_id AND pl.scheme = 'wikidata' AND pl.external_id ~ '^Q[1-9][0-9]*$'
      ORDER BY (pl.relation = 'exact_match') DESC, pl.external_id LIMIT 1),
    CASE WHEN c.target_id ~ '^wikidata_q[1-9][0-9]*$' THEN 'Q' || substr(c.target_id, 11) END)
  WHERE c.target_type = 'place' AND c.qid IS NULL;

CREATE INDEX IF NOT EXISTS media_checks_qid ON media_checks (provider, qid) WHERE qid IS NOT NULL;

-- 4. An OCLC number may be recorded for a source (`source_identifiers`), as an ISBN may: the Open
--    Library Covers API answers by either.
ALTER TABLE source_identifiers DROP CONSTRAINT IF EXISTS source_identifiers_scheme_check;
ALTER TABLE source_identifiers ADD CONSTRAINT source_identifiers_scheme_check
  CHECK (scheme IN ('doi', 'sha256', 'isbn', 'openalex', 'oclc'));
