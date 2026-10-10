-- V082: media from outside providers (#1446, first slice: Wikidata → Wikimedia Commons).
--
-- Until now every asset was a figure cut out of an ingested source: `assets(citekey, name)` was the
-- key and the citekey could not be NULL. A picture of a place or an event comes from a PROVIDER
-- instead (Wikimedia Commons first; museum open-access APIs, Europeana and Pleiades later), under a
-- licence that must be recorded and a credit line that must be shown wherever the picture is shown.
--
-- 1. `assets` is keyed by its `uid` (V030; already UNIQUE and NOT NULL). `(citekey, name)` stays
--    UNIQUE, so a source's figures are still found, replaced and linked by it — `asset_links` keeps
--    its foreign key, re-pointed at the unique constraint — and `citekey` may now be NULL: exactly one
--    of `citekey` (a figure of that source) and `provider` (a picture from outside) is set.
-- 2. What a provider's picture carries: the provider's own key for it (`File:Lions-Gate-Mycenae.jpg`),
--    the page it comes from, the original file's URL, the author, the credit line the licence asks
--    for, the licence's URL and when it was retrieved. `license` (V020) holds the SPDX id
--    (`CC-BY-SA-3.0`) or `public-domain`. The stored `bytes` are a rendition capped in size (the
--    provider's own resized copy, not the original); `thumb` is the small rendition the place and
--    event cards show — served from here, so a hover never reaches outside.
-- 3. `media_links`: which place or event a provider's picture is OF, in which role (image, locator
--    map, plan, aerial view, route map), from which statement (`P18` of `Q132564`). The figures of a
--    source keep `asset_links`.
-- 4. `media_checks`: which places and events a provider has been asked about, and when — so an
--    import without `--refresh` moves on to the ones not asked yet, also when the answer was "none".
-- 5. `events.wikidata`: the Wikidata item an event is, so its image (P18) can be read.
-- 6. `sources.license`: the licence a source is published under (SPDX id or `public-domain`), when it
--    is known — a source's figures share it. Nothing records it yet (the corpus held none on
--    2026-10-10); `histmersion media fill-licences` copies it to the source's figures that have none.

ALTER TABLE asset_links DROP CONSTRAINT IF EXISTS asset_links_citekey_asset_name_fkey;
ALTER TABLE assets DROP CONSTRAINT IF EXISTS assets_pkey;
ALTER TABLE assets ADD CONSTRAINT assets_citekey_name_key UNIQUE (citekey, name);
ALTER TABLE assets ADD CONSTRAINT assets_pkey PRIMARY KEY (uid);
ALTER TABLE asset_links ADD CONSTRAINT asset_links_citekey_asset_name_fkey
  FOREIGN KEY (citekey, asset_name) REFERENCES assets (citekey, name) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE assets ALTER COLUMN citekey DROP NOT NULL;

ALTER TABLE assets ADD COLUMN IF NOT EXISTS provider     text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS provider_key text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS source_url   text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS original_url text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS author       text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS credit       text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS license_url  text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS retrieved_at timestamptz;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS thumb        bytea;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS thumb_mime   text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS thumb_width  int;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS thumb_height int;

ALTER TABLE assets ADD CONSTRAINT assets_one_owner CHECK ((citekey IS NULL) <> (provider IS NULL));
ALTER TABLE assets ADD CONSTRAINT assets_provider_fields
  CHECK (provider IS NULL OR (provider_key IS NOT NULL AND source_url IS NOT NULL AND retrieved_at IS NOT NULL));
CREATE UNIQUE INDEX IF NOT EXISTS assets_provider_key ON assets (provider, provider_key) WHERE provider IS NOT NULL;
CREATE INDEX IF NOT EXISTS assets_license ON assets (license);

CREATE TABLE IF NOT EXISTS media_links (
  asset_uid   uuid        NOT NULL REFERENCES assets (uid) ON UPDATE CASCADE ON DELETE CASCADE,
  target_type text        NOT NULL CHECK (target_type IN ('place', 'event')),
  target_id   text        NOT NULL,
  role        text        NOT NULL CHECK (role IN ('image', 'locator-map', 'plan', 'aerial-view', 'route-map')),
  property    text,                       -- the statement it was read from: P18, P242, …
  item        text,                       -- the Wikidata item that statement belongs to
  rank        int         NOT NULL DEFAULT 0,   -- 0 = the provider's preferred one
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (asset_uid, target_type, target_id, role)
);
CREATE INDEX IF NOT EXISTS media_links_target ON media_links (target_type, target_id, rank);

CREATE TABLE IF NOT EXISTS media_checks (
  provider    text        NOT NULL,
  target_type text        NOT NULL CHECK (target_type IN ('place', 'event')),
  target_id   text        NOT NULL,
  checked_at  timestamptz NOT NULL DEFAULT now(),
  found       int         NOT NULL DEFAULT 0,   -- pictures stored for it by that check
  PRIMARY KEY (provider, target_type, target_id)
);

ALTER TABLE events ADD COLUMN IF NOT EXISTS wikidata text;
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_wikidata_check;
ALTER TABLE events ADD CONSTRAINT events_wikidata_check CHECK (wikidata IS NULL OR wikidata ~ '^Q[0-9]+$');

ALTER TABLE sources ADD COLUMN IF NOT EXISTS license text;
