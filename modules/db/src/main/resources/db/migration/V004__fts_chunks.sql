-- Histmersion V002 — full-text search over CHUNKED bodies (plan §1.4).
--
-- Why chunks: a tsvector is capped at ~1 MB per value, and `sources.ocr_markdown` routinely exceeds
-- that (the corpus holds 84 MB of OCR text), so V001 could only index title+summary and fell back to
-- `ILIKE` over the body — a sequential scan of every paper. The body is now split by `com.histmersion.core.
-- Chunker` (pure, ~4 000 chars, cuts on headings then paragraphs) into `source_chunks`, each with its
-- own generated tsvector and a GIN index. A source matches when its title/summary tsvector matches OR
-- any of its chunks does; snippets come from `ts_headline` over the best-matching chunk.
-- Chunks are written by `SourceRepo.upsert` and rebuilt wholesale by `POST /api/reindex`.

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- typo-tolerant chip autocomplete over title/authors (§1.5)

CREATE TABLE IF NOT EXISTS source_chunks (
  citekey text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
  ord     int  NOT NULL,                  -- 0-based, document order
  heading text,                           -- breadcrumb of enclosing Markdown headings ("A > B")
  body    text NOT NULL,
  tsv     tsvector GENERATED ALWAYS AS
            (to_tsvector('english', coalesce(heading, '') || ' ' || body)) STORED,
  PRIMARY KEY (citekey, ord)              -- also the (citekey) lookup index for the per-source join
);
CREATE INDEX IF NOT EXISTS source_chunks_tsv_gin ON source_chunks USING gin (tsv);

-- ── sources: weighted title(A)+summary(B) vector, stored (V001 had it as an expression index only,
--    so it could not be read back for ranking) ────────────────────────────────────────────────────
ALTER TABLE sources ADD COLUMN IF NOT EXISTS tsv tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('english', coalesce(title, '')),   'A') ||
  setweight(to_tsvector('english', coalesce(summary, '')), 'B')) STORED;
DROP INDEX IF EXISTS sources_fts;         -- superseded by the stored column below
CREATE INDEX IF NOT EXISTS sources_tsv_gin ON sources USING gin (tsv);

-- ── trigram indexes for prefix/typo autocomplete ──────────────────────────────────────────────────
-- `array_to_string` is only STABLE (it depends on the element type's output function), so it cannot be
-- indexed directly; for text[] it is in fact immutable, hence this thin wrapper.
CREATE OR REPLACE FUNCTION histmersion_authors_text(text[]) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$ SELECT array_to_string($1, ' ') $$;

CREATE INDEX IF NOT EXISTS sources_title_trgm   ON sources USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS sources_authors_trgm ON sources USING gin (histmersion_authors_text(authors) gin_trgm_ops);
