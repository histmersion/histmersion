-- Histmersion V040 — a run's pipeline options (docs/research-embedded-agents.md §12/§13).
--
-- `params` holds what a pipeline was asked to do beyond its target — `review-article`'s `fix` and
-- `rounds` — so a resumed run sees the same options it started with. A run started before this column
-- existed reads as `{}`: every option at its default. (`jobs.acquire_item`, V028, already names the
-- acquisition item an enrich-source run fulfils.)
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS params jsonb NOT NULL DEFAULT '{}'::jsonb;
