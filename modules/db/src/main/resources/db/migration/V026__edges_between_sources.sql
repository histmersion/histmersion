-- V026: `edges` connects SOURCES, and nothing else.
--
-- `edges.dst` used to be free text. The seed importer turned every citekey-SHAPED `[[link]]` in a
-- source's notes into a `references` edge, and topic slugs (`[[bronze_age_collapse]]`) are
-- citekey-shaped, so the live corpus carried ~1 055 `references` edges whose target was a topic
-- rather than a source. The graph then drew edges to nodes that do not exist, `neighbors` had to
-- guess which targets were articles, and every reader LEFT JOINed around the mess.
--
-- The split, decided here once:
--   1. `references → <slug without a year>` is a TOPIC, not a citation: the slug moves into
--      `sources.topics` (deduplicated, case-insensitively) and the edge is deleted. "Has a year"
--      is the same rule `lint dangling-edges` uses to tell a citekey from an article link
--      (`Lint.looksLikeCitekey`: four consecutive digits).
--   2. Any other edge whose target is not a stored source — a typed `challenges → smith2019_x`
--      written before smith2019_x was ingested, a short form awaiting `edges/resolve`, a DOI — is a
--      reference to a work that is not in the corpus YET. That is data, not an error, so it is
--      PARKED in `unresolved_edges` rather than dropped, and promoted into `edges` when the target
--      arrives (the source write path and `edges/resolve` both do this).
--   3. `edges` then gets foreign keys on both ends, so the invariant cannot rot again.

CREATE TABLE IF NOT EXISTS unresolved_edges (
  src  text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
  dst  text NOT NULL,                              -- a citekey (or short form / DOI) not in the corpus
  kind text NOT NULL,
  PRIMARY KEY (src, dst, kind)
);
CREATE INDEX IF NOT EXISTS unresolved_edges_dst ON unresolved_edges (dst);

-- an edge out of something that is not a source cannot be reached or rendered from anywhere
DELETE FROM edges e WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.src);

-- (1) topic links → sources.topics, appended after the existing topics, without duplicates
WITH moved AS (
  SELECT e.src, array_agg(DISTINCT e.dst ORDER BY e.dst) AS slugs
  FROM edges e
  WHERE e.kind = 'references'
    AND e.dst !~ '[0-9]{4}'
    AND NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst)
  GROUP BY e.src
)
UPDATE sources s
SET topics = s.topics || ARRAY(
  SELECT g FROM unnest(moved.slugs) AS g
  WHERE NOT EXISTS (SELECT 1 FROM unnest(s.topics) AS t WHERE lower(t) = lower(g)))
FROM moved
WHERE moved.src = s.citekey;

DELETE FROM edges e
WHERE e.kind = 'references'
  AND e.dst !~ '[0-9]{4}'
  AND NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst);

-- (2) everything else that points outside the corpus is parked
INSERT INTO unresolved_edges (src, dst, kind)
SELECT e.src, e.dst, e.kind FROM edges e
WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst)
ON CONFLICT DO NOTHING;

DELETE FROM edges e WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst);

-- (3) the invariant
ALTER TABLE edges
  ADD CONSTRAINT edges_src_source FOREIGN KEY (src) REFERENCES sources(citekey) ON DELETE CASCADE,
  ADD CONSTRAINT edges_dst_source FOREIGN KEY (dst) REFERENCES sources(citekey) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS edges_dst ON edges (dst);
