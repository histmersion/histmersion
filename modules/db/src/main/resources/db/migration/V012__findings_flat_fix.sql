-- `findings_flat.period_end` was off by one year, for the whole life of the view.
--
-- `period` is an int8range and Postgres normalises the inclusive `[a,b]` this codebase writes to the
-- half-open `[a,b+1)`. Every other projection in the repo therefore reads the inclusive upper bound
-- back as `upper(period) - 1` (AGENTS.md says so in as many words); this view alone used bare
-- `upper(f.period)`, so anything reading it — `query`, an ad-hoc report — saw every finding lasting
-- one year longer than it does.
--
-- Redefining a view whose column list is unchanged is safe with CREATE OR REPLACE.

CREATE OR REPLACE VIEW findings_flat AS
SELECT f.id, f.citekey, f.claim, f.region_name,
       ST_X(f.geom) AS lon, ST_Y(f.geom) AS lat,
       lower(f.period) AS period_start, upper(f.period) - 1 AS period_end,
       f.period_name, f.tags, f.confidence
FROM findings f;
