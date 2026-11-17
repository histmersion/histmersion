-- Problems are stored as their derived entry, {"group", "case", "data", "text"} (api.problem.Answer.Entry), as jsonb:
-- readable in SQL through `text` (`error -> 0 ->> 'text'`), read back by the code as the case it was written from.
-- A column keeps every problem: jobs.error, agent_steps.error, candidate_watches.last_error and plan_pools.refusal each
-- hold a JSON array of entries.
--
-- Each text these columns held becomes a one-element array of its entry. The shapes a master version wrote and the
-- code acts on become their case (api.problem.StoredShapes reads the same shapes, and ProblemsMigrationSuite checks
-- that both agree); any other text becomes Stored.Unrecognised, which keeps the text whole, in `data.text` and in
-- `text`.

CREATE FUNCTION pg_temp.hm_entry(g text, c text, d jsonb, t text) RETURNS jsonb
  LANGUAGE sql IMMUTABLE
  AS $$ SELECT jsonb_build_object('group', g, 'case', c, 'data', d, 'text', t) $$;

-- what a master pause wrote after `<reason>: `: the literals Engine and IngestQueue wrote, else the text as it is
CREATE FUNCTION pg_temp.hm_detail(t text) RETURNS jsonb
  LANGUAGE sql IMMUTABLE
  AS $$
    SELECT CASE t
      WHEN 'interrupted' THEN pg_temp.hm_entry('Run', 'Interrupted', '{}'::jsonb, 'interrupted by a restart')
      WHEN 'paused on request' THEN
        pg_temp.hm_entry('Run', 'Paused', jsonb_build_object('reason', 'Requested'), 'paused on request')
      WHEN 'paused before it started' THEN
        pg_temp.hm_entry('Run', 'Unstarted', '{}'::jsonb, 'paused before it started')
      WHEN 'the upload is no longer held by this process' THEN
        pg_temp.hm_entry('Run', 'UploadUnheld', '{}'::jsonb, 'the upload is no longer held by this process')
      ELSE pg_temp.hm_entry('Stored', 'Unrecognised', jsonb_build_object('text', t), t)
    END
  $$;

CREATE FUNCTION pg_temp.hm_problem(t text) RETURNS jsonb
  LANGUAGE plpgsql IMMUTABLE
  AS $$
    DECLARE
      not_asked constant text := 'not asked this sweep — ';
      words constant text[] := ARRAY['budget', 'interrupted', 'requested', 'provider', 'allowance', 'plan-quota',
                                     'stalled'];
      causes constant text[] := ARRAY['Budget', 'Interrupted', 'Requested', 'Provider', 'Allowance', 'PlanQuota',
                                      'Stalled'];
      why jsonb;
    BEGIN
      IF t = 'interrupted' THEN
        RETURN pg_temp.hm_entry('Run', 'Interrupted', '{}'::jsonb, 'interrupted by a restart');
      END IF;
      IF t = 'stopped while the autopilot drains: it resumes once the drain is lifted' THEN
        RETURN pg_temp.hm_entry('Run', 'Draining', '{}'::jsonb, t);
      END IF;
      IF starts_with(t, not_asked) THEN
        why := pg_temp.hm_detail(substr(t, length(not_asked) + 1));
        RETURN pg_temp.hm_entry('Upstream', 'Unasked', jsonb_build_object('why', why), not_asked || (why ->> 'text'));
      END IF;
      FOR i IN 1 .. array_length(words, 1) LOOP
        IF starts_with(t, words[i] || ': ') THEN
          why := pg_temp.hm_detail(substr(t, length(words[i]) + 3));
          RETURN pg_temp.hm_entry('Run', 'Halted', jsonb_build_object('cause', causes[i], 'why', why),
                                  words[i] || ': ' || (why ->> 'text'));
        END IF;
      END LOOP;
      RETURN pg_temp.hm_entry('Stored', 'Unrecognised', jsonb_build_object('text', t), t);
    END
  $$;

ALTER TABLE public.jobs
  ALTER COLUMN error TYPE jsonb
  USING CASE WHEN error IS NULL THEN NULL ELSE jsonb_build_array(pg_temp.hm_problem(error)) END;

ALTER TABLE public.agent_steps
  ALTER COLUMN error TYPE jsonb
  USING CASE WHEN error IS NULL THEN NULL ELSE jsonb_build_array(pg_temp.hm_problem(error)) END;

ALTER TABLE public.candidate_watches
  ALTER COLUMN last_error TYPE jsonb
  USING CASE WHEN last_error IS NULL THEN NULL ELSE jsonb_build_array(pg_temp.hm_problem(last_error)) END;

ALTER TABLE public.plan_pools
  ALTER COLUMN refusal TYPE jsonb
  USING CASE WHEN refusal IS NULL THEN NULL ELSE jsonb_build_array(pg_temp.hm_problem(refusal)) END;

DROP FUNCTION pg_temp.hm_problem(text);
DROP FUNCTION pg_temp.hm_detail(text);
DROP FUNCTION pg_temp.hm_entry(text, text, jsonb, text);
