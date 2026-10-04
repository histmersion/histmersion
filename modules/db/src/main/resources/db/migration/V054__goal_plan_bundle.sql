-- V054: a goal's plan, links and schedules travel in a bundle (#881, docs/research-merge-import.md
-- §7.8).
--
-- Two collections can each approve a plan for one goal; "the plan approved later wins" needs to know
-- WHEN a plan was approved: `plan_approved_at`, set with the plan (a plan approved before this
-- migration has none, and a difference between two such plans is a question for a person).
--
-- A goal's links and schedules are part of the goal's bundle line, so changing them must move the
-- goal's change clock (an incremental export then carries the goal again) — as an edge moves its
-- source (V030). A schedule's run-time columns (next/last run, the last note) are this machine's and
-- are NOT carried, so a scheduler tick does not move the goal: only cron, enabled and the cap do.

ALTER TABLE goals ADD COLUMN IF NOT EXISTS plan_approved_at timestamptz;

CREATE OR REPLACE FUNCTION histmersion_touch_goal() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  r record;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  UPDATE goals SET updated_at = now() WHERE slug = r.goal_slug AND updated_at <> now();
  RETURN NULL;
END
$$;

-- INSERT and DELETE only for links, as for edges: a goal rename cascades into `goal_slug`, and
-- touching the goal row from inside that cascade is an error
DROP TRIGGER IF EXISTS goal_links_touch_goal ON goal_links;
CREATE TRIGGER goal_links_touch_goal AFTER INSERT OR DELETE ON goal_links
  FOR EACH ROW EXECUTE FUNCTION histmersion_touch_goal();
DROP TRIGGER IF EXISTS goal_schedules_touch_goal ON goal_schedules;
CREATE TRIGGER goal_schedules_touch_goal AFTER INSERT OR DELETE ON goal_schedules
  FOR EACH ROW EXECUTE FUNCTION histmersion_touch_goal();
DROP TRIGGER IF EXISTS goal_schedules_touch_goal_update ON goal_schedules;
CREATE TRIGGER goal_schedules_touch_goal_update AFTER UPDATE OF cron, enabled, budget_usd ON goal_schedules
  FOR EACH ROW WHEN (OLD.cron IS DISTINCT FROM NEW.cron OR OLD.enabled IS DISTINCT FROM NEW.enabled
                     OR OLD.budget_usd IS DISTINCT FROM NEW.budget_usd)
  EXECUTE FUNCTION histmersion_touch_goal();
