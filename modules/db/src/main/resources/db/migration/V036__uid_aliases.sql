-- V036: redirects from merged-away uids (docs/research-merge-import.md §3, milestone M3).
--
-- When two records are folded into one (`merge apply`, M5), the uid that goes away keeps resolving,
-- as OpenAlex redirects a merged work: `alias_uid` → `uid`. The merge planner reads this map (with
-- the ledger's `local_uid`) before anything else, so a bundle line carrying a merged-away uid is the
-- surviving row here — never matched again, never inserted twice. Keyed by the alias: one uid is
-- folded into exactly one other. `kind` is the bundle's entity kind (`core.BundleKind`'s wire name).
-- No foreign key on `uid`: it may name a row of any carried table, and a redirect to a row deleted
-- since is answered by that row's tombstone.

CREATE TABLE IF NOT EXISTS uid_aliases (
  alias_uid  uuid        PRIMARY KEY,
  uid        uuid        NOT NULL CHECK (uid <> alias_uid),
  kind       text        NOT NULL,
  merge_id   uuid        REFERENCES merges(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS uid_aliases_uid ON uid_aliases (uid);
