-- V051: the web review screen's draft of a merge (docs/research-merge-import.md §6.5, milestone M6).
--
-- `/merge` decides a plan's issues one by one; the decisions made so far live HERE, not in the
-- browser, so a reload or another browser carries on where the review was, and the CLI and the
-- screen share one format (the resolutions, as `merge plan --resolutions` reads them). One row per
-- uploaded bundle, named by its sha256 as the upload is. A successful `merge apply` deletes the row:
-- its decisions are remembered by issue id from then on (`merge_decisions`, V043).

CREATE TABLE IF NOT EXISTS merge_sessions (
  bundle      text        PRIMARY KEY,           -- the uploaded bundle's sha256
  resolutions jsonb       NOT NULL DEFAULT '{}'::jsonb,
  updated_at  timestamptz NOT NULL DEFAULT now()
);
