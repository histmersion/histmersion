-- Histmersion V008 — ARTICLES: the compiled wiki itself (plan §5.2 stream 2.7).
--
-- `sources` holds what the corpus SAYS (a paper's converted body) and `sources.notes` holds what the
-- agent compiled about ONE source. An article is the other axis: a piece of prose compiled ACROSS
-- sources — the concept/myth/culture/region/timeline/report pages that used to live as hand-edited
-- Markdown in `wiki/{concepts,myths,…}/*.md`. The database becomes the store of record for them too;
-- `histmersion export` writes them back out as `<kind>/<slug>.md` for Obsidian and git.
--
-- Everything an article points at is written in Obsidian wikilink syntax inside its body:
--   [[other_slug]]  → another article        [[citekey]] → a source
-- `article_links` is that graph, materialised on every write from the body (see `ArticleLinks`), so
-- backlinks and the "sources cited" footer are index lookups instead of a table scan with LIKE.

CREATE TABLE IF NOT EXISTS articles (
  slug       text PRIMARY KEY,                    -- stable id AND filename: wiki/<kind>/<slug>.md
  title      text        NOT NULL,
  kind       text        NOT NULL,                -- concept|myth|culture|region|timeline|report|index|…
  body       text        NOT NULL,                -- the Markdown the agent wrote (wikilinks included)
  summary    text,                                -- one-paragraph blurb (the italic lede, or a report's question)
  tags       text[]      NOT NULL DEFAULT '{}',
  period     int8range,                           -- [period_start, period_end], signed years (BCE negative)
  regions    text[]      NOT NULL DEFAULT '{}',
  updated_at timestamptz NOT NULL DEFAULT now(),  -- also the `ifUpdatedAt` optimistic-concurrency token
  -- Weighted FTS: the title outranks the summary, which outranks the body — same scheme as
  -- `sources.tsv` (V004). Articles are prose written by an agent, not OCR, so a whole one fits in a
  -- single tsvector: no chunking needed. `left(...)` is a guard, not a policy — a tsvector value is
  -- capped at ~1 MB and an article that long is a bug upstream, not something to fail an INSERT on.
  tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')),            'A') ||
        setweight(to_tsvector('english', coalesce(summary, '')),          'B') ||
        setweight(to_tsvector('english', left(coalesce(body, ''), 900000)), 'C')) STORED
);

CREATE INDEX IF NOT EXISTS articles_tsv_gin     ON articles USING gin (tsv);
CREATE INDEX IF NOT EXISTS articles_tags_gin    ON articles USING gin (tags);
CREATE INDEX IF NOT EXISTS articles_regions_gin ON articles USING gin (regions);
CREATE INDEX IF NOT EXISTS articles_period_gist ON articles USING gist (period);
CREATE INDEX IF NOT EXISTS articles_kind        ON articles (kind);
CREATE INDEX IF NOT EXISTS articles_title_trgm  ON articles USING gin (title gin_trgm_ops);

-- ── the wikilink graph ────────────────────────────────────────────────────────────────────────
-- One row per DISTINCT link out of an article. `target_kind` is decided at write time: a `[[x]]` is
-- a SOURCE link when x is citekey-shaped AND a row with that citekey exists, otherwise it is an
-- article slug. Article targets are deliberately NOT foreign keys — a wiki links forward to pages
-- that do not exist yet, and a dangling link is a lint finding (§5.2 stream 2.8), not a write error.
CREATE TABLE IF NOT EXISTS article_links (
  src_slug    text NOT NULL REFERENCES articles(slug) ON DELETE CASCADE,
  target_kind text NOT NULL CHECK (target_kind IN ('article', 'source')),
  target      text NOT NULL,
  PRIMARY KEY (src_slug, target_kind, target)
);
-- backlinks(slug) reads this the other way round, so index the target side too.
CREATE INDEX IF NOT EXISTS article_links_target ON article_links (target_kind, target);
