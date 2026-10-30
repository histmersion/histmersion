-- V106: the sites with no known date on the map, when a viewer asks for them ("Also show sites with no
-- known date", `GET /api/tiles/sites/…?undated=true`).
--
-- A site whose source states no date (period open on both sides: most GeoNames and Wikidata sites, ~94 % of
-- a gazetteer import) is never on a time-sliced map. Asked for, it is drawn whatever the span — so a
-- shallow tile would read and count up to every such site of the world (1.7 M) on each request.
--
-- 1. The undated sites in a tile's box, for the deeper tiles and for the few a tile lists one by one. The
--    predicate is word for word what `UndatedSiteRepo` states.
CREATE INDEX places_site_undated ON places USING gist (geom)
  WHERE kind = 'site' AND geom IS NOT NULL AND lower_inf(period) AND upper_inf(period);

-- 2. Their counts by the cells of the shallow tiles, kept: a cell of zoom `z` is the square `gx`/`gy` of a
--    grid of 2^z × `per_side` squares a side over Web Mercator's unit square, the grid a tile groups its
--    items by (`ui.map-tiles.cell-px`). Per site type (`''`: untyped), so the site-type filter reads them
--    too. `sx`/`sy` sum the sites' places on the unit square (their mean is the cell's point), `one` is
--    the least id (the site itself, in a cell of one). Rebuilt whole by the server when the sites it was
--    built from changed (3.) or the grid did.
CREATE TABLE site_undated_cells (
  z         smallint NOT NULL,
  gx        bigint   NOT NULL,
  gy        bigint   NOT NULL,
  site_type text     NOT NULL,
  n         integer  NOT NULL,
  sx        float8   NOT NULL,
  sy        float8   NOT NULL,
  w         float8   NOT NULL,
  s         float8   NOT NULL,
  e         float8   NOT NULL,
  nn        float8   NOT NULL,
  one       text     NOT NULL,
  PRIMARY KEY (z, gx, gy, site_type)
);

-- what the cells were built from: the `undated` group's version (3.) and the grid
CREATE TABLE site_undated_build (
  id        boolean PRIMARY KEY DEFAULT true CHECK (id),
  version   bigint      NOT NULL,
  per_side  integer     NOT NULL,
  max_zoom  integer     NOT NULL,
  built_at  timestamptz NOT NULL DEFAULT now()
);

-- 3. A write that touches an undated site — before or after it — moves the `undated` group of
--    `map_changes` (V105), once per statement; any other write to `places` (a dated site, a region, an
--    agent's new place) leaves it, so the cells are not rebuilt for it. A trigger with transition tables
--    serves one event, hence three, and one for TRUNCATE.
CREATE OR REPLACE FUNCTION histmersion_undated_changed() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  touched boolean := false;
BEGIN
  IF TG_OP = 'TRUNCATE' THEN
    touched := true;
  ELSIF TG_OP = 'INSERT' THEN
    SELECT EXISTS (SELECT 1 FROM new_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period)) INTO touched;
  ELSIF TG_OP = 'DELETE' THEN
    SELECT EXISTS (SELECT 1 FROM old_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period)) INTO touched;
  ELSE
    SELECT EXISTS (SELECT 1 FROM new_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period))
        OR EXISTS (SELECT 1 FROM old_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period))
      INTO touched;
  END IF;
  IF touched THEN
    INSERT INTO map_changes (grp) VALUES ('undated');
  END IF;
  RETURN NULL;
END $$;

CREATE TRIGGER places_undated_insert AFTER INSERT ON places REFERENCING NEW TABLE AS new_rows
  FOR EACH STATEMENT EXECUTE FUNCTION histmersion_undated_changed();
CREATE TRIGGER places_undated_update AFTER UPDATE ON places REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows
  FOR EACH STATEMENT EXECUTE FUNCTION histmersion_undated_changed();
CREATE TRIGGER places_undated_delete AFTER DELETE ON places REFERENCING OLD TABLE AS old_rows
  FOR EACH STATEMENT EXECUTE FUNCTION histmersion_undated_changed();
CREATE TRIGGER places_undated_truncate AFTER TRUNCATE ON places
  FOR EACH STATEMENT EXECUTE FUNCTION histmersion_undated_changed();
