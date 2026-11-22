CREATE TABLE public.acquire_attempts (
    id bigint GENERATED ALWAYS AS IDENTITY,
    item_id integer NOT NULL REFERENCES public.queue_items (id) ON DELETE CASCADE,
    at timestamp with time zone NOT NULL,
    trigger text NOT NULL CHECK (trigger IN ('person-ingest', 'watcher-backlog', 'recheck', 'legacy')),
    generation text NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('closed', 'fetched', 'no-copy', 'failed', 'legacy')),
    citekey text,
    source text,
    answers jsonb DEFAULT '[]'::jsonb NOT NULL,
    failure jsonb,
    oa jsonb,
    legacy_reason text,
    legacy_checks integer,
    CHECK ((trigger = 'legacy') = (outcome = 'legacy')),
    CHECK (outcome = 'legacy' OR (legacy_reason IS NULL AND legacy_checks IS NULL)),
    CHECK ((outcome = 'failed') = (failure IS NOT NULL)),
    CHECK ((outcome = 'closed') = (citekey IS NOT NULL)),
    CHECK ((outcome = 'fetched') = (source IS NOT NULL)),
    PRIMARY KEY (id)
);

CREATE INDEX acquire_attempts_item ON public.acquire_attempts (item_id, at DESC);
CREATE INDEX acquire_attempts_at ON public.acquire_attempts (at);

INSERT INTO public.acquire_attempts (item_id, at, trigger, generation, outcome, legacy_reason, legacy_checks)
SELECT c.item_id, c.checked_at, 'legacy', 'legacy', 'legacy', c.reason, c.checks
  FROM public.acquire_checks c
  JOIN public.queue_items q ON q.id = c.item_id;

DROP TABLE public.acquire_checks;

SELECT public.histmersion_log_writes_on('acquire_attempts');
