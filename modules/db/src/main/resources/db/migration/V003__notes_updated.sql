-- Writes (plan §1.9a): the agent-written article about a source, and the concurrency/seed token.

-- `notes` = free Markdown the agent compiles ABOUT the source (distinct from `ocr_markdown`, which is
-- the paper's own converted body). It is the BODY of `wiki/sources/<citekey>.md`, so an exported note
-- round-trips through Frontmatter.parseNote: frontmatter → columns, body → notes.
ALTER TABLE sources ADD COLUMN IF NOT EXISTS notes text;

-- `updated_at` is the optimistic-concurrency token for patch_source (`ifUpdatedAt`) AND the seed guard
-- (a file older than the row is skipped). Every write path sets it to now(); make sure no row can
-- carry NULL, so "newer in DB" is always decidable.
UPDATE sources SET updated_at = now() WHERE updated_at IS NULL;
ALTER TABLE sources ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE sources ALTER COLUMN updated_at SET NOT NULL;

-- The seed guard reads (citekey, updated_at) for the whole table on every seed.
CREATE INDEX IF NOT EXISTS sources_updated_at ON sources (updated_at);
