-- V061: a run paused because every model of a role's chain is below its allowance knows when the
-- earliest window frees enough; the engine re-queues it then (`PauseReason.Allowance`). Any other
-- state change clears it.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS resume_at timestamptz;
CREATE INDEX IF NOT EXISTS jobs_resume_at ON jobs (resume_at) WHERE resume_at IS NOT NULL;
