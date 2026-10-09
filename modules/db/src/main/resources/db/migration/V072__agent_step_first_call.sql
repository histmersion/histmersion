-- V072: what one LLM step costs before it does anything.
-- `agent_steps.first_call_tokens`: the input of the step's FIRST model call (input + cache read +
-- cache write tokens) — the fixed part every turn re-sends (the system prompt, the tool definitions;
-- for claude -p also what the CLI adds) plus the step's own prompt. NULL when the provider cannot
-- tell (codex, opencode) and on attempts recorded before V072.
-- `agent_steps.turns`: the model turns the step took (claude -p's num_turns, the tool loop's
-- iterations). NULL on attempts that ended without an answer, and before V072.
ALTER TABLE agent_steps ADD COLUMN IF NOT EXISTS first_call_tokens bigint;
ALTER TABLE agent_steps ADD COLUMN IF NOT EXISTS turns int;
