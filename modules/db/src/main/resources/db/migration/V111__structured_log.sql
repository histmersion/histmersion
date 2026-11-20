-- The structured log: one event per meaningful step (an answered request, a provider call, a problem, a write
-- statement), each with the trace that links the steps of one request or job. The server writes every event twice,
-- to this table and to a rolling JSON-lines file of its own, under the same id, so either can be searched (the file
-- also while the database is down). `event` is the whole event as the server's codec writes it; the other columns
-- are what queries filter on. Backups and bundles leave the rows out.
CREATE TABLE public.log_events (
    id uuid NOT NULL,
    at timestamp with time zone NOT NULL,
    trace uuid NOT NULL,
    label text,
    kind text NOT NULL,
    problem boolean NOT NULL,
    subjects text[] DEFAULT '{}'::text[] NOT NULL,
    event jsonb NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX log_events_at ON public.log_events (at);
CREATE INDEX log_events_trace ON public.log_events (trace, at);
CREATE INDEX log_events_kind ON public.log_events (kind, at);
CREATE INDEX log_events_label ON public.log_events (label, at) WHERE label IS NOT NULL;
CREATE INDEX log_events_subjects ON public.log_events USING gin (subjects);

-- One row per write statement, put here by the triggers below in the writing transaction (so only committed writes
-- are seen) and moved into log_events, and the file, by the server's log writer. Its id is the event's id.
CREATE TABLE public.log_writes (
    seq bigint GENERATED ALWAYS AS IDENTITY,
    id uuid DEFAULT uuidv7() NOT NULL,
    at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    trace uuid NOT NULL,
    label text,
    tbl text NOT NULL,
    op text NOT NULL CHECK (op IN ('insert', 'update', 'delete', 'truncate')),
    rows bigint,
    keys text[] DEFAULT '{}'::text[] NOT NULL,
    citekeys text[] DEFAULT '{}'::text[] NOT NULL,
    PRIMARY KEY (seq)
);

-- The trace of the calling transaction: the one the server set on the connection (histmersion.trace), else one minted
-- now and kept for the rest of the transaction, so the writes of one untraced transaction share one trace.
CREATE FUNCTION public.histmersion_trace() RETURNS uuid
    LANGUAGE plpgsql
    AS $$
DECLARE
  t text := nullif(current_setting('histmersion.trace', true), '');
BEGIN
  IF t IS NULL THEN
    t := uuidv7()::text;
    PERFORM set_config('histmersion.trace', t, true);
  END IF;
  RETURN t::uuid;
END $$;

-- A write statement's row of log_writes: the table, the change, how many rows, the first 20 values of the table's
-- one-column key (TG_ARGV[0], '' when it has none) and of its citekey column (TG_ARGV[1], '' when it has none). A
-- statement that changed no row records nothing.
CREATE FUNCTION public.histmersion_log_write() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  key_col text := nullif(TG_ARGV[0], '');
  cite_col text := nullif(TG_ARGV[1], '');
  n bigint;
  k text[] := '{}';
  c text[] := '{}';
BEGIN
  IF TG_OP = 'TRUNCATE' THEN
    n := NULL;
  ELSIF TG_OP = 'DELETE' THEN
    SELECT count(*) INTO n FROM log_old_rows;
    IF n > 0 AND (key_col IS NOT NULL OR cite_col IS NOT NULL) THEN
      SELECT coalesce(array_agg(j ->> key_col) FILTER (WHERE j ->> key_col IS NOT NULL), '{}'),
             coalesce(array_agg(DISTINCT j ->> cite_col) FILTER (WHERE j ->> cite_col IS NOT NULL), '{}')
        INTO k, c FROM (SELECT to_jsonb(o) AS j FROM log_old_rows o LIMIT 20) s;
    END IF;
  ELSE
    SELECT count(*) INTO n FROM log_new_rows;
    IF n > 0 AND (key_col IS NOT NULL OR cite_col IS NOT NULL) THEN
      SELECT coalesce(array_agg(j ->> key_col) FILTER (WHERE j ->> key_col IS NOT NULL), '{}'),
             coalesce(array_agg(DISTINCT j ->> cite_col) FILTER (WHERE j ->> cite_col IS NOT NULL), '{}')
        INTO k, c FROM (SELECT to_jsonb(r) AS j FROM log_new_rows r LIMIT 20) s;
    END IF;
  END IF;
  IF n IS NULL OR n > 0 THEN
    INSERT INTO log_writes (trace, label, tbl, op, rows, keys, citekeys)
    VALUES (histmersion_trace(), nullif(current_setting('histmersion.trace_label', true), ''), TG_TABLE_NAME,
            lower(TG_OP), n, k, c);
  END IF;
  RETURN NULL;
END $$;

-- The four statement triggers that record `tbl`'s writes. Every table gets them except the log's own, the bulk text
-- chunks and the caches (StructuredLogSchemaSuite holds the list); a new table calls this in its migration.
CREATE FUNCTION public.histmersion_log_writes_on(tbl text) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
  key_col text;
  cite_col text;
  args text;
BEGIN
  SELECT min(a.attname) INTO key_col
    FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
    WHERE i.indrelid = format('public.%I', tbl)::regclass AND i.indisprimary
    HAVING count(*) = 1;
  SELECT 'citekey' INTO cite_col FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = tbl AND column_name = 'citekey';
  args := format('%L, %L', coalesce(key_col, ''), coalesce(cite_col, ''));
  EXECUTE format('CREATE TRIGGER %I AFTER INSERT ON public.%I REFERENCING NEW TABLE AS log_new_rows '
                 'FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_log_write(%s)', tbl || '_log_insert', tbl, args);
  EXECUTE format('CREATE TRIGGER %I AFTER UPDATE ON public.%I REFERENCING NEW TABLE AS log_new_rows '
                 'FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_log_write(%s)', tbl || '_log_update', tbl, args);
  EXECUTE format('CREATE TRIGGER %I AFTER DELETE ON public.%I REFERENCING OLD TABLE AS log_old_rows '
                 'FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_log_write(%s)', tbl || '_log_delete', tbl, args);
  EXECUTE format('CREATE TRIGGER %I AFTER TRUNCATE ON public.%I '
                 'FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_log_write(%s)', tbl || '_log_truncate', tbl, args);
END $$;

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'public' AND c.relkind = 'r'
             AND c.relname NOT IN ('log_events', 'log_writes', 'source_chunks', 'map_changes', 'site_undated_cells',
                                   'site_undated_build', 'flyway_schema_history', 'spatial_ref_sys')
           ORDER BY c.relname
  LOOP
    PERFORM public.histmersion_log_writes_on(r.relname);
  END LOOP;
END $$;

-- The trace a job was created under, so its events can be found from the job. The jobs already there keep none.
ALTER TABLE public.jobs ADD COLUMN trace_id uuid;
ALTER TABLE public.jobs ALTER COLUMN trace_id SET DEFAULT public.histmersion_trace();
