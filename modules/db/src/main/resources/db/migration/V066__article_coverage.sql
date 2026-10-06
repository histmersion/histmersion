-- V066 (#958): an article RECORDS the filter it is compiled from, and why it leaves each of that
-- filter's sources out. Its "Not used" list, the counts above it and the mechanical half of its
-- `_Method_` note are then GENERATED from these two columns against the live corpus every time the
-- article is read (page, get_article, export) — a model only supplies the reasons, never the list.
--
--   coverage_filter   the structured filter (query, topics, tags, status, places, authors, from/to,
--                     extraQueries) as `ArticleFilter` JSON; NULL = none recorded (an article written
--                     before V066 keeps its hand-written sections until someone sets one)
--   coverage_reasons  {citekey: reason} for the filter's sources the body does not cite
ALTER TABLE articles ADD COLUMN IF NOT EXISTS coverage_filter  jsonb;
ALTER TABLE articles ADD COLUMN IF NOT EXISTS coverage_reasons jsonb NOT NULL DEFAULT '{}'::jsonb;
