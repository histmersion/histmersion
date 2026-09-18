-- hist-db V018 — ATTESTATIONS (plan §1.3): granular source references linking entities to specific
-- passages in a source. Uses W3C Web Annotation selectors (TextQuoteSelector, TextPositionSelector)
-- plus page labels, IIIF canvas+xywh regions, CTS URNs for classical texts, and source_chunks
-- ordinals. A single table that can anchor any target type (finding, event, place, edge) to the
-- exact passage that supports it.
--
-- The target_type + target_id pair is a discriminated union: no FK enforcement (the target can be
-- in any of several tables), but a CHECK constraint limits target_type to known entity kinds.

CREATE TABLE IF NOT EXISTS attestations (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  citekey     text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
  -- what this attestation anchors to
  target_type text NOT NULL,
  target_id   text NOT NULL,
  -- W3C Web Annotation TextQuoteSelector: exact match with surrounding context
  quote       text,
  prefix      text,
  suffix      text,
  -- W3C Web Annotation TextPositionSelector: character offsets into the OCR body
  char_start  int,
  char_end    int,
  -- source_chunks ordinal (links to the existing FTS chunking)
  chunk_ord   int,
  -- page / folio label (free text: "42", "f.12r", "pl.VII", "§3.2")
  page        text,
  -- IIIF canvas + region (for scanned pages / figures)
  canvas      text,
  xywh        text,
  -- CTS URN (Canonical Text Services, for classical texts)
  cts_urn     text,
  -- metadata
  note        text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT attestations_target_type_check
    CHECK (target_type IN ('finding', 'event', 'event_dating', 'place', 'edge'))
);

CREATE INDEX IF NOT EXISTS attestations_citekey ON attestations (citekey);
CREATE INDEX IF NOT EXISTS attestations_target  ON attestations (target_type, target_id);

COMMENT ON TABLE attestations IS 'W3C Web Annotation–inspired granular references: links any entity (finding, event, place, edge) to a specific passage in a source.';
