-- The acquisition item an ingest job ticks off when it succeeds (an upload started from the item,
-- by the web UI's picker, `histmersion ingest <file> --acquire-item N` or the MCP `ingest` tool).
-- No foreign key: the item may be deleted while the OCR runs, and that must not fail the job; the
-- job simply finds nothing to tick off.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS acquire_item integer;
