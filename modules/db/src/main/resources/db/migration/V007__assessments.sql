-- Stream 2.6: acquisition & assessment.
--
-- `assessments` is the stored verdict for a source — what `assess.py` used to print and throw away.
-- Keeping it lets `lint` and `update-topic` ask "what has not been checked since X" without hitting
-- the network again, and records WHY a status was set.
--
-- `retraction_watch` is a local mirror of the Retraction Watch CSV Crossref publishes (~60 000 rows,
-- refreshed by `histmersion assess update-retractions`). It is ground truth we own: an assessment must not
-- depend on an upstream being reachable at the moment somebody asks.

CREATE TABLE IF NOT EXISTS assessments (
  citekey     text PRIMARY KEY REFERENCES sources(citekey) ON DELETE CASCADE,
  checked_at  timestamptz NOT NULL DEFAULT now(),
  retracted   boolean     NOT NULL DEFAULT false,
  notices     jsonb       NOT NULL DEFAULT '[]'::jsonb,  -- the RW + Crossref notices, verbatim
  cited_by    int,
  influential int,
  verdict     text        NOT NULL DEFAULT 'unassessed'  -- retracted|corrected|current|low-impact|unassessed
);
CREATE INDEX IF NOT EXISTS assessments_verdict    ON assessments (verdict);
CREATE INDEX IF NOT EXISTS assessments_checked_at ON assessments (checked_at);

CREATE TABLE IF NOT EXISTS retraction_watch (
  doi    text PRIMARY KEY,   -- the ORIGINAL paper's DOI, lower-cased
  reason text,
  date   date,
  nature text                -- Retraction|Correction|Expression of concern|Reinstatement|…
);
