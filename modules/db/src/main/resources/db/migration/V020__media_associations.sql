-- hist-db V020 — media associations (plan §1.6).
--
-- Extends assets with spatial/temporal/descriptive metadata, and adds asset_links
-- for semantic relationships between assets and entities (events, findings, places).

ALTER TABLE assets ADD COLUMN IF NOT EXISTS kind text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS caption text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS license text;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS geom geometry(Geometry, 4326);
ALTER TABLE assets ADD COLUMN IF NOT EXISTS period int8range;

CREATE INDEX IF NOT EXISTS assets_geom ON assets USING gist (geom) WHERE geom IS NOT NULL;
CREATE INDEX IF NOT EXISTS assets_period ON assets USING gist (period) WHERE period IS NOT NULL;
CREATE INDEX IF NOT EXISTS assets_kind ON assets (kind) WHERE kind IS NOT NULL;

CREATE TABLE IF NOT EXISTS asset_links (
  id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  citekey   text   NOT NULL,
  asset_name text  NOT NULL,
  relation  text   NOT NULL CHECK (relation IN ('depicts','taken_at','illustrates','page_of')),
  target_type text NOT NULL CHECK (target_type IN ('event','finding','place','source','article')),
  target_id  text  NOT NULL,
  xywh      text,
  notes     text,
  FOREIGN KEY (citekey, asset_name) REFERENCES assets(citekey, name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS asset_links_asset ON asset_links (citekey, asset_name);
CREATE INDEX IF NOT EXISTS asset_links_target ON asset_links (target_type, target_id);
