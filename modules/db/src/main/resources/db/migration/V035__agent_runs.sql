-- Histmersion V035 — embedded agent runs (docs/research-embedded-agents.md §5.1).
--
-- An agent run is a `jobs` row with kind = 'agent', so `histmersion jobs` and /api/jobs list it next to
-- the OCR jobs. Its status adds `paused` (resumable: a budget breach, a server restart, a request)
-- and `cancelled` to queued|running|done|failed. Token and cost totals are NOT kept on the run: they
-- are the sum over its steps, so there is one place they are written.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS pipeline         text;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS pipeline_version int;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS target_kind      text;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS target_id        text;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS model            text;          -- the run's provider:model override
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS budget_usd       numeric(12,6);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS budget_tokens    bigint;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS warnings         text[] NOT NULL DEFAULT '{}';
CREATE INDEX IF NOT EXISTS jobs_kind ON jobs (kind, created_at DESC);

-- One row per step ATTEMPT. A row with status 'done' is the step's CHECKPOINT: a resumed run replays
-- `output` instead of running the step again, so an LLM answer is never paid for twice. A failed
-- attempt (an answer that did not validate) keeps its error and its cost.
CREATE TABLE IF NOT EXISTS agent_steps (
  job_id             text NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  seq                int  NOT NULL,                -- position in the pipeline (deterministic)
  attempt            int  NOT NULL,
  name               text NOT NULL,
  kind               text NOT NULL,                -- code|llm
  status             text NOT NULL,                -- done|failed
  output             jsonb,                        -- the checkpoint (status = done)
  prompt_hash        text,                         -- sha256 of the prompt the step used
  model              text,                         -- provider:model
  tokens_in          bigint NOT NULL DEFAULT 0,
  tokens_out         bigint NOT NULL DEFAULT 0,
  tokens_cache_read  bigint NOT NULL DEFAULT 0,
  tokens_cache_write bigint NOT NULL DEFAULT 0,
  cost_usd           numeric(12,6) NOT NULL DEFAULT 0,
  error              text,
  started_at         timestamptz NOT NULL DEFAULT now(),
  finished_at        timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (job_id, seq, attempt)
);
-- the global daily/monthly caps sum cost over a time window
CREATE INDEX IF NOT EXISTS agent_steps_finished ON agent_steps (finished_at);

-- The trace of an LLM step: the conversation the loop built (prompts, assistant turns, tool calls
-- and their results), bodies truncated to `agents.trace.max-tool-result-chars`. Prunable.
CREATE TABLE IF NOT EXISTS agent_messages (
  job_id      text NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  seq         int  NOT NULL,
  attempt     int  NOT NULL,
  n           int  NOT NULL,
  role        text NOT NULL,                       -- user|assistant|tool
  content     text,
  tool_name   text,
  tool_input  text,
  tool_result text,
  latency_ms  int,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (job_id, seq, attempt, n)
);
