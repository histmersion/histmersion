-- V068: names a PERSON gave a place survive every re-seed (#987).
--
-- An import (`histmersion init`, `places seed`, `places fetch`, `places import`) is the whole truth about
-- the places it mentions, so it replaces their names — which also deleted any name somebody had added
-- by hand to a seed place ("Lake Kinneret" on the Sea of Galilee), at the next re-seed, silently.
-- `manual` marks the names added with `histmersion places name add` (POST /api/places/{id}/names): an
-- import deletes and rewrites only the names that are NOT manual, and a name both an import and a
-- person give stays manual. `histmersion places name list --csv` exports them in the format of
-- `db/places/supplement/names.csv`, so a good one can be promoted into the bundled seed.
ALTER TABLE place_names ADD COLUMN IF NOT EXISTS manual boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS place_names_manual ON place_names (place_id) WHERE manual;
