ALTER TABLE public.sources ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.findings ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.edges ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.unresolved_edges ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.assessments ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.source_candidates ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.candidate_watches ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.articles ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.article_versions ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.review_ledgers ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.assets ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.asset_links ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.events ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.places ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.periods ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb, ADD COLUMN created_at timestamptz;
ALTER TABLE public.attestations ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.corrections ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.place_overrides ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.event_link_candidates ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.goals ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.goal_links ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.goal_schedules ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.approvals ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.jobs ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.agent_managers ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.manager_hints ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.notes ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;
ALTER TABLE public.bookmarks ADD COLUMN creator jsonb NOT NULL DEFAULT '{"kind": "unknown"}'::jsonb;

ALTER TABLE public.findings ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.edges ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.candidate_watches ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.articles ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.review_ledgers ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.assets ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.asset_links ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.events ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.places ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.periods ALTER COLUMN created_at SET DEFAULT now();

ALTER TABLE public.jobs DISABLE TRIGGER USER;
UPDATE public.jobs j
   SET creator = CASE j.origin
       WHEN 'person' THEN '{"kind": "manual"}'::jsonb
       WHEN 'scheduler' THEN '{"kind": "automated", "plugin": "engine", "workflow": "scheduler", "agent": {"kind": "code"}, "surface": "internal"}'::jsonb
       WHEN 'manager' THEN '{"kind": "automated", "plugin": "engine", "workflow": "managers", "agent": {"kind": "code"}, "surface": "internal"}'::jsonb
       ELSE coalesce(
           (SELECT jsonb_build_object('kind', 'automated', 'workflow', p.pipeline, 'surface', 'internal')
              FROM public.jobs p
             WHERE j.origin = 'run' AND p.id = j.parent_job AND p.pipeline ~ '^[a-z][a-z0-9_-]*$'),
           '{"kind": "unknown"}'::jsonb)
   END
 WHERE j.origin IS NOT NULL;
ALTER TABLE public.jobs ENABLE TRIGGER USER;

ALTER TABLE public.approvals DISABLE TRIGGER USER;
UPDATE public.approvals a
   SET creator = jsonb_build_object('kind', 'automated', 'workflow', j.pipeline, 'surface', 'internal')
  FROM public.jobs j
 WHERE j.id = a.job_id AND j.pipeline ~ '^[a-z][a-z0-9_-]*$';
ALTER TABLE public.approvals ENABLE TRIGGER USER;

ALTER TABLE public.events DISABLE TRIGGER USER;
UPDATE public.events
   SET creator = '{"kind": "automated", "workflow": "event-import", "agent": {"kind": "code"}, "surface": "internal"}'::jsonb
 WHERE catalogue IS NOT NULL;
ALTER TABLE public.events ENABLE TRIGGER USER;

ALTER TABLE public.places DISABLE TRIGGER USER;
UPDATE public.places
   SET creator = '{"kind": "automated", "workflow": "gazetteer-import", "agent": {"kind": "code"}, "surface": "internal"}'::jsonb
 WHERE import_source IS NOT NULL;
ALTER TABLE public.places ENABLE TRIGGER USER;

ALTER TABLE public.assets DISABLE TRIGGER USER;
UPDATE public.assets
   SET creator = '{"kind": "automated", "workflow": "media-import", "agent": {"kind": "code"}, "surface": "internal"}'::jsonb
 WHERE provider IS NOT NULL;
ALTER TABLE public.assets ENABLE TRIGGER USER;
