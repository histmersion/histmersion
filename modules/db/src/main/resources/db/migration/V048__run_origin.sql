-- Histmersion V048 — where an agent run came from (docs/research-embedded-agents.md §5.1, §14).
--
-- `goal_slug`: the goal the run works for (its schedules, its plan, a compile started from it);
-- `parent_job`: the run that started this one (compile-article chains a review-article run);
-- `origin`: person | scheduler | run — a scheduled run that fails or pauses leaves a backlog item, a
-- person's run does not (they are watching it). Status gains `waiting` (for an approval; status is text).
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS goal_slug  text;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS parent_job text;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS origin     text;
CREATE INDEX IF NOT EXISTS jobs_goal ON jobs (goal_slug, created_at DESC) WHERE goal_slug IS NOT NULL;
