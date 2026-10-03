-- Structured facts about a job beyond its citekey (#770, #771): what an init job imports, and the
-- PeriodO definitions an import left out. JSON of `core.JobDetail`; NULL for OCR jobs.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS detail jsonb;
