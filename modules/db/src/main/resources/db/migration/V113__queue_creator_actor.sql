ALTER TABLE public.queue_items
    ADD COLUMN creator jsonb,
    ADD COLUMN actor text CHECK (actor IN ('person', 'agents'));

ALTER TABLE public.queue_items DISABLE TRIGGER queue_items_touch_updated_at;

UPDATE public.queue_items q
   SET creator = coalesce(
           (SELECT jsonb_build_object('kind', 'automated', 'workflow', j.pipeline)
              FROM public.jobs j
             WHERE j.id = q.created_job AND j.pipeline IS NOT NULL),
           jsonb_strip_nulls(jsonb_build_object('kind', 'unknown', 'job', q.created_job))),
       actor = CASE WHEN q.topic = 'agent review' THEN 'agents' ELSE 'person' END;

ALTER TABLE public.queue_items ENABLE TRIGGER queue_items_touch_updated_at;

ALTER TABLE public.queue_items
    ALTER COLUMN creator SET NOT NULL,
    ADD CONSTRAINT queue_items_task_actor CHECK (list <> 'tasks' OR status <> 'open' OR actor IS NOT NULL);
