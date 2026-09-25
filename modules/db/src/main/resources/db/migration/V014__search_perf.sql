-- Histmersion V014 — search performance: standalone citekey index on source_chunks.
--
-- The two-phase FTS approach (best_chunks CTE + deferred snippets) replaced the per-source LATERAL
-- join, but the snippet query still does `WHERE citekey IN (...)` for the page's sources. The PK
-- (citekey, ord) covers this, but a standalone btree on citekey alone is smaller in memory and
-- avoids carrying ord in every entry — a measurable win for batch lookups.

CREATE INDEX IF NOT EXISTS source_chunks_citekey_idx ON source_chunks (citekey);
