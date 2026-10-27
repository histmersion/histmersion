-- V105: the map's point layers as vector tiles (`GET /api/tiles/{layer}/{z}/{x}/{y}`).
--
-- 1. What a tile was cut from, versioned. A tile of sites reads `places` (and, for a place dated only
--    by its findings, the findings); one of events reads `events`, their attestations and catalogue
--    rows, their places and — under a filter of sources — the corpus; one of pictures `assets`. The
--    server keeps cut tiles and answers a browser's copy as current (304) while the version of what
--    they read is unchanged. Every write to those tables records itself here, once per transaction
--    and group, so a write is seen by the next tile asked for.
--
--    A group's version is the SUM of its rows' weights, read in the reader's own snapshot. Rows are only
--    ever inserted — never one counter row updated — so writers never wait for each other (a counter
--    row would be locked by every writing transaction until its commit, and two transactions bumping
--    two groups in opposite orders would deadlock); the server folds a group's rows into one now and
--    then (`TileRepo.compact`), which keeps the sum.
CREATE TABLE map_changes (
  id     bigserial PRIMARY KEY,
  grp    text NOT NULL,
  weight bigint NOT NULL DEFAULT 1
);
CREATE INDEX map_changes_grp ON map_changes (grp);

CREATE OR REPLACE FUNCTION histmersion_map_changed() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  mark text := 'histmersion.map_changed_' || TG_ARGV[0];
BEGIN
  -- once per transaction and group: a batch of a thousand statements records one change
  IF coalesce(current_setting(mark, true), '') <> txid_current()::text THEN
    INSERT INTO map_changes (grp) VALUES (TG_ARGV[0]);
    PERFORM set_config(mark, txid_current()::text, true);
  END IF;
  RETURN NULL;
END $$;

DO $$
DECLARE
  t record;
BEGIN
  FOR t IN SELECT * FROM (VALUES
      ('places', 'places'),
      ('events', 'events'), ('attestations', 'events'), ('event_catalogue', 'events'),
      ('sources', 'corpus'), ('findings', 'corpus'), ('finding_places', 'corpus'),
      ('assets', 'media')) AS v(tbl, grp)
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', t.tbl || '_map_changed', t.tbl);
    EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I '
      'FOR EACH STATEMENT EXECUTE FUNCTION histmersion_map_changed(%L)', t.tbl || '_map_changed', t.tbl, t.grp);
  END LOOP;
END $$;

-- 2. The sites a time view can show, by period and point: every site but an UNDATED import (GeoNames,
--    TGN, an undated Wikidata item — never on a time-sliced map). 99 000 of the 1.06 M sites of the
--    2026-10-27 corpus; a tile reads those in its box and span (`TileRepo`). The predicate is word for
--    word what the tile query states, which is what lets the planner use it.
CREATE INDEX places_site_map ON places USING gist (period, geom)
  WHERE kind = 'site' AND geom IS NOT NULL
    AND NOT (coalesce(import_source, '') IN ('wikidata', 'geonames', 'tgn') AND lower_inf(period) AND upper_inf(period));
