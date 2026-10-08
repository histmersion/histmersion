-- V070: what comparing models per agent role needs (#992).
-- `jobs.dry_run`: a run that does every read and every LLM step but writes nothing to the corpus;
-- each write it would have made is recorded on its step instead. Fixed when the run is created, so a
-- resumed dry run stays one.
-- `agent_steps.actual_model`: the model the provider reported serving the call (claude -p's
-- `modelUsage`, an API response's `model`), beside `model`, the configured ref (an alias such as
-- `claude-code:haiku`).
-- `agent_steps.would_write`: in a dry run, the writes the step skipped — `[{"what", "payload"}]`.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS dry_run boolean NOT NULL DEFAULT false;
ALTER TABLE agent_steps ADD COLUMN IF NOT EXISTS actual_model text;
ALTER TABLE agent_steps ADD COLUMN IF NOT EXISTS would_write jsonb;
