-- Histmersion V021 — acquisition queue enrichment (plan §1.7).
--
-- Add `reason` to queue items so the frontend acquire page can show why something
-- is on the list and what research direction it serves.

ALTER TABLE queue_items ADD COLUMN IF NOT EXISTS reason text;
