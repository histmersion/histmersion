-- V094: media from open-access collections and pictures that depict events (#1446, third slice).
--
-- 1. New roles of a provider's picture: `depiction` (a Commons file that depicts an event — structured
--    data's P180, or the image of a work the event's item lists as P1299 "depicted by"), `object` (a
--    museum's open-access object found at or made in a place, of its period) and `map` (a public-domain
--    historical map that shows a place — a shape reference, not georeferenced).
-- 2. `media_links.basis`: WHY a collection's record is tied to the place — the record's field that
--    names it and the dates compared (`excavation = Mycenae; 1400–1200 BCE within 1600–1100 BCE`).
--    A match is never made from coordinates or a fuzzy name; the reason stays beside the link so a
--    person can judge it. NULL for the statements of Wikidata (the property and item say why).
-- (V094, not V092: V093 is taken by the gazetteer branch; this one must apply after it.)

ALTER TABLE media_links DROP CONSTRAINT IF EXISTS media_links_role_check;
ALTER TABLE media_links ADD CONSTRAINT media_links_role_check
  CHECK (role IN ('image', 'locator-map', 'plan', 'aerial-view', 'route-map', 'depiction', 'object', 'map',
                  'cover', 'illustration'));

ALTER TABLE media_links ADD COLUMN IF NOT EXISTS basis text;
