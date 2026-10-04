-- Histmersion V047 — approvals a run WAITS for (docs/research-embedded-agents.md §5.2, §14).
--
-- P2's approvals were about one source (a status, an edge) and never held a run up. P3 adds two kinds
-- a run blocks on until a person decides: `goal-plan` (the sub-goals, queries and candidate DOIs a
-- plan-goal run proposes — nothing is created before approval) and `article-publish` (the article a
-- compile-article run drafted — nothing is written before approval). Such an approval is about a goal
-- or an article (`subject`), not a source, so `citekey` may be empty; `seq` is the run's step that
-- waits, and one step waits on one approval (a resumed run finds the approval it opened).
ALTER TABLE approvals ALTER COLUMN citekey DROP NOT NULL;
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS subject text;     -- goal-plan: the goal slug; article-publish: the slug
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS seq     int;      -- the waiting step of job_id
CREATE UNIQUE INDEX IF NOT EXISTS approvals_run_step ON approvals (job_id, seq) WHERE seq IS NOT NULL;
CREATE INDEX IF NOT EXISTS approvals_subject ON approvals (kind, subject) WHERE subject IS NOT NULL;
