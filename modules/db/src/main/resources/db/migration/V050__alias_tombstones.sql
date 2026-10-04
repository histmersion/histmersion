-- V050: an old citekey DROPPED by renaming back is remembered (#827).
--
-- `rename new → old` where `old` was an alias of the source deletes that alias: the key is the
-- source's citekey again. A bundle from another instance still carries the alias (old → the same
-- source), and without a record of the deletion the next import put it back — an alias equal to the
-- source's own citekey. Aliases have no uid, so the V031 `tombstones` (keyed by uid, typed by a
-- bundle entity kind) cannot hold them; this is their own, keyed by the alias string, which is what
-- a bundle's alias line is matched by. The source is named by uid, so a later rename does not
-- disturb it. The merge planner skips a bundle alias created before its tombstone here.
--
-- Written by the rename path (`SourceRepo.renameIn`), not a trigger: a source's aliases also go by
-- ON DELETE CASCADE with the source itself, and that deletion is the source's tombstone already.
-- An alias that exists again (renamed away once more, or imported newer) clears its tombstone.

CREATE TABLE IF NOT EXISTS alias_tombstones (
  alias      text        PRIMARY KEY,
  source_uid uuid        NOT NULL,
  origin     uuid        NOT NULL DEFAULT histmersion_instance_id(),
  deleted_at timestamptz NOT NULL DEFAULT now()
);
