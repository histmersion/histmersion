-- V062: the timeline's period presets are named, not inferred from the tree.
--
-- Until now a preset was any period without a parent, which made the seed nest every regional
-- definition under one authority's Eurasia-wide span just to keep the menu short. A preset is now
-- the row whose `preset` names the REGION HEADING it is listed under (a core `PresetRegion` wire
-- name, e.g. 'aegean'); NULL means "not offered as a preset". The bundled seed sets it
-- (`histmersion.presets` in db/periods/periodo-seed.json).
--
-- `seed_versions` records which version of a bundled seed was last applied, so a server that ships a
-- newer seed re-applies it once on start (Bootstrap.autoStart) instead of only seeding empty tables.
-- Existing rows keep `preset` NULL until then: the first start after this migration re-seeds.
ALTER TABLE periods ADD COLUMN IF NOT EXISTS preset text;
CREATE INDEX IF NOT EXISTS periods_preset ON periods (preset) WHERE preset IS NOT NULL;

CREATE TABLE IF NOT EXISTS seed_versions (
  name       text        PRIMARY KEY,           -- 'periods'
  version    int         NOT NULL,
  applied_at timestamptz NOT NULL DEFAULT now()
);
