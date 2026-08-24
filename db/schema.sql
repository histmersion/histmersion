-- hist-db schema — store of record for OCR'd source content + geo/time enrichment.
-- Originals stay in Calibre; this DB holds the OCR markdown + structured metadata and is backed up
-- via pg_dump. Deep time = signed integer years (BCE negative), modeled as int8range.
-- Applied by the server on startup (idempotent) or via `histdb migrate`.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- lets one GiST index combine geometry + range

-- ── sources: one row per paper/book (the OCR body lives here, in the DB) ──────────────────────
CREATE TABLE IF NOT EXISTS sources (
  citekey               text PRIMARY KEY,
  doi                   text,
  title                 text NOT NULL,
  authors               text[]      NOT NULL DEFAULT '{}',
  year                  int,
  venue                 text,
  status                text        NOT NULL DEFAULT 'unassessed',   -- foundational|current|contested|superseded|debunked|retracted|unassessed
  topics                text[]      NOT NULL DEFAULT '{}',
  regions               text[]      NOT NULL DEFAULT '{}',
  tags                  text[]      NOT NULL DEFAULT '{}',
  period                int8range,                                    -- [period_start, period_end], signed years
  cited_by_count        int,
  influential_citations int,
  summary               text,
  ocr_markdown          text,                                         -- full converted body, verbatim (served to LLMs)
  source                text,                                         -- provenance: calibre|archive_org|unpaywall|manual|ocr|…
  calibre_id            text,                                         -- original stays in Calibre; this rehydrates it
  sha256                text,
  added_date            date        NOT NULL DEFAULT current_date,
  updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sources_period_gist ON sources USING gist (period);
CREATE INDEX IF NOT EXISTS sources_topics_gin  ON sources USING gin (topics);
CREATE INDEX IF NOT EXISTS sources_regions_gin ON sources USING gin (regions);
-- full-text over title+summary+body:
CREATE INDEX IF NOT EXISTS sources_fts ON sources
  USING gin (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(summary,'') || ' ' || coalesce(ocr_markdown,'')));

-- ── findings: geo-located, time-scoped claims mined from a source ──────────────────────────────
CREATE TABLE IF NOT EXISTS findings (
  id          text PRIMARY KEY,                    -- e.g. "<citekey>#<n>"
  citekey     text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
  claim       text NOT NULL,
  region_name text,
  geom        geometry(Point, 4326),               -- from lat/lon
  period      int8range,
  period_name text,
  tags        text[] NOT NULL DEFAULT '{}',
  confidence  text                                 -- high|medium|low|contested
);
CREATE INDEX IF NOT EXISTS findings_geom_gist        ON findings USING gist (geom);
CREATE INDEX IF NOT EXISTS findings_period_gist      ON findings USING gist (period);
CREATE INDEX IF NOT EXISTS findings_geom_period_gist ON findings USING gist (geom, period);  -- spatiotemporal
CREATE INDEX IF NOT EXISTS findings_citekey          ON findings (citekey);

-- ── citation / link edges ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS edges (
  src  text NOT NULL,
  dst  text NOT NULL,                              -- citekey or external DOI
  kind text NOT NULL,                              -- cites|wikilink|supersedes
  PRIMARY KEY (src, dst, kind)
);

-- ── Phase 3: gazetteer + periodization (Pleiades / PeriodO aligned) ────────────────────────────
-- CREATE TABLE places  (id text PRIMARY KEY, label text, geom geometry, parent_id text, pleiades_uri text);
-- CREATE TABLE periods (id text PRIMARY KEY, label text, period int8range, region text, periodo_uri text);

-- Handy view: findings as flat lon/lat/start/end for GeoJSON assembly.
CREATE OR REPLACE VIEW findings_flat AS
SELECT f.id, f.citekey, f.claim, f.region_name,
       ST_X(f.geom) AS lon, ST_Y(f.geom) AS lat,
       lower(f.period) AS period_start, upper(f.period) AS period_end,
       f.period_name, f.tags, f.confidence
FROM findings f;
