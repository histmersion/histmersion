-- V043: `merge apply` (docs/research-merge-import.md §6.1, §6.4, milestone M5).
--
-- `merges` gains what an apply records beside an import: the plan it carried out (`plan_hash`, the
-- hash the user reviewed), the resolutions as given, and the backup taken before anything was
-- written (its file name in `backups.dir`) — so a merge can be traced, and undone by a restore.
--
-- `merge_decisions` remembers every decision an apply carried out, by issue id (git's rerere): an
-- issue id hashes the records it is about and their revs, so the same question about the same,
-- unchanged records is answered the same way next time without asking — and a question about records
-- that changed since gets a new id and is asked again. Only what a person decided (a resolution or a
-- policy) is remembered, never a default.

ALTER TABLE merges ADD COLUMN IF NOT EXISTS plan_hash   text;
ALTER TABLE merges ADD COLUMN IF NOT EXISTS backup      text;
ALTER TABLE merges ADD COLUMN IF NOT EXISTS resolutions jsonb;

CREATE TABLE IF NOT EXISTS merge_decisions (
  issue_id   text        PRIMARY KEY,
  decision   text        NOT NULL,
  merge_id   uuid        REFERENCES merges(id) ON DELETE SET NULL,
  decided_at timestamptz NOT NULL DEFAULT now()
);
