-- V033: what was imported from where (docs/research-merge-import.md §3 and §7.1, milestone M2).
--
-- `merges` — one row per bundle import that changed anything: which bundle (its deterministic id
-- and the file's sha256), from which instance, and the import's report.
--
-- `merge_ledger` — for every foreign uid an import has seen: the local uid it maps to (the same uid
-- until the matcher of M3 maps foreign rows onto different local ones), the foreign `rev` merged,
-- the local `rev` it left behind, and what was decided. This is the common ancestor of the next
-- import: a foreign row whose rev equals `foreign_rev` has not changed over there (keep ours); a
-- local row whose rev still equals `local_rev_after` has not changed here (the foreign one may be
-- taken). Without it every difference would be a conflict. One row per foreign uid — the latest.

CREATE TABLE IF NOT EXISTS merges (
  id             uuid        PRIMARY KEY DEFAULT uuidv7(),
  bundle_id      uuid        NOT NULL,
  bundle_sha256  text        NOT NULL,
  from_instance  uuid        NOT NULL,
  from_label     text,
  report         jsonb       NOT NULL DEFAULT '{}'::jsonb,
  applied_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS merges_bundle ON merges (bundle_id);

CREATE TABLE IF NOT EXISTS merge_ledger (
  foreign_uid      uuid        PRIMARY KEY,
  kind             text        NOT NULL,
  merge_id         uuid        NOT NULL REFERENCES merges(id) ON DELETE CASCADE,
  foreign_rev      text        NOT NULL,
  local_uid        uuid        NOT NULL,
  local_rev_after  text,
  decision         text        NOT NULL,
  recorded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS merge_ledger_local ON merge_ledger (local_uid);
