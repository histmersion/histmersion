-- V085: events from CATALOGUES, their links to the corpus, and site types (#1449, 2026-10-14).
--
-- 1. `event_catalogue`: what a catalogue (the Smithsonian's Global Volcanism Program, NOAA NCEI's hazard
--    databases, Wikidata) says about an event — one row per (provider, provider id), pointing at the ONE
--    event it is (records of the same thing from several catalogues point at the same event; an event
--    compiled from sources that a catalogue also lists is matched, never overwritten). The row keeps the
--    catalogue's own dating, point, size (VEI, magnitude, deaths), URL and the record as read.
-- 2. `events.catalogue`: the provider an import CREATED the event from (NULL: compiled from sources, or
--    entered by hand) — a re-import refreshes those events and never touches the others.
--    `events.names`: its other names (a catalogue's other labels, aliases in other languages), which the
--    candidate matcher searches the sources' text for.
-- 3. `event_import_parts`: the parts of a catalogue an import has read (a Wikidata class, a page of a
--    feed), so a stopped import resumes after the last one.
-- 4. `event_link_candidates`: sources that POSSIBLY discuss an event (their findings near it, their
--    text naming it) — proposed links with their evidence, not attestations. Confirming one writes an
--    `attestations` row (from a passage that names it); rejecting one keeps it from being proposed again.
--    `event_link_checks`: when an event's candidates were last computed.
-- 5. The kinds of the events compiled so far, made precise (EventTaxonomy.refine): a `natural_disaster`
--    whose label names an eruption, a drought, a climate event … becomes that sub-kind; `diplomatic_era`,
--    `reign` and `period` become `era`; spellings are normalised.
-- 6. `places.site_type`: what a SITE is (settlement, fortification, sanctuary, burial …, core.SiteTypes),
--    stored only where it was REFINED from outside (a Wikidata item's own classes); the gazetteers' own
--    fields (Pleiades feature types, GeoNames feature codes) are mapped when the map reads them.
--    `site_type_checked_at`: when the refinement asked about the place.

ALTER TABLE events ADD COLUMN IF NOT EXISTS catalogue text;
ALTER TABLE events ADD COLUMN IF NOT EXISTS names text[] NOT NULL DEFAULT '{}';
CREATE INDEX IF NOT EXISTS events_catalogue ON events (catalogue) WHERE catalogue IS NOT NULL;

CREATE TABLE IF NOT EXISTS event_catalogue (
  provider     text        NOT NULL,
  provider_id  text        NOT NULL,
  event_id     text        NOT NULL REFERENCES events (id) ON UPDATE CASCADE ON DELETE CASCADE,
  url          text,
  label        text        NOT NULL,
  kind         text        NOT NULL,
  not_before   bigint,
  begin_by     bigint,
  end_from     bigint,
  not_after    bigint,
  lat          double precision,
  lon          double precision,
  vei          int,
  magnitude    double precision,
  deaths       bigint,
  historicity  text        NOT NULL DEFAULT 'attested',
  record       jsonb       NOT NULL,
  retrieved_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (provider, provider_id)
);
CREATE INDEX IF NOT EXISTS event_catalogue_event ON event_catalogue (event_id);

CREATE TABLE IF NOT EXISTS event_import_parts (
  provider text        NOT NULL,
  part     text        NOT NULL,
  records  int         NOT NULL DEFAULT 0,
  done_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (provider, part)
);

CREATE TABLE IF NOT EXISTS event_link_candidates (
  event_id    text             NOT NULL REFERENCES events (id) ON UPDATE CASCADE ON DELETE CASCADE,
  citekey     text             NOT NULL REFERENCES sources (citekey) ON UPDATE CASCADE ON DELETE CASCADE,
  score       double precision NOT NULL,
  status      text             NOT NULL DEFAULT 'proposed' CHECK (status IN ('proposed', 'confirmed', 'rejected')),
  findings    text[]           NOT NULL DEFAULT '{}',
  passages    jsonb            NOT NULL DEFAULT '[]',
  reasons     text[]           NOT NULL DEFAULT '{}',
  computed_at timestamptz      NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, citekey)
);
CREATE INDEX IF NOT EXISTS event_link_candidates_citekey ON event_link_candidates (citekey);

CREATE TABLE IF NOT EXISTS event_link_checks (
  event_id   text        PRIMARY KEY REFERENCES events (id) ON UPDATE CASCADE ON DELETE CASCADE,
  checked_at timestamptz NOT NULL DEFAULT now()
);

-- the attestations of an event are read for every event on the map (its tier): by target
CREATE INDEX IF NOT EXISTS attestations_event_target ON attestations (target_id) WHERE target_type = 'event';

-- 5. kinds — the same rules as core.EventTaxonomy (canonical, then refine for natural_disaster)
UPDATE events SET kind = lower(regexp_replace(trim(kind), '[\s-]+', '_', 'g'))
  WHERE kind <> lower(regexp_replace(trim(kind), '[\s-]+', '_', 'g'));
UPDATE events SET kind = 'era' WHERE kind IN ('reign', 'period', 'diplomatic_era') OR kind LIKE '%\_era';
UPDATE events SET kind = 'revolt' WHERE kind IN ('rebellion', 'uprising');
UPDATE events SET kind = 'epidemic' WHERE kind IN ('plague', 'pandemic');
UPDATE events SET kind = 'eruption' WHERE kind = 'volcanic_eruption';
UPDATE events SET kind = CASE
    WHEN lower(label) ~ 'erupt|volcan' THEN 'eruption'
    WHEN lower(label) ~ 'earthquake|seism|quake' THEN 'earthquake'
    WHEN lower(label) ~ 'tsunami' THEN 'tsunami'
    WHEN lower(label) ~ 'flood|deluge' THEN 'flood'
    WHEN lower(label) ~ 'drought|aridi' THEN 'drought'
    WHEN lower(label) ~ 'kiloyear|climat|cooling|warming' THEN 'climate_event'
    WHEN lower(label) ~ 'plague|epidemic|pandemic|pestilence' THEN 'epidemic'
    WHEN lower(label) ~ 'famine' THEN 'famine'
    ELSE kind END
  WHERE kind = 'natural_disaster';

-- 6. site types
ALTER TABLE places ADD COLUMN IF NOT EXISTS site_type text;
ALTER TABLE places ADD COLUMN IF NOT EXISTS site_type_checked_at timestamptz;
