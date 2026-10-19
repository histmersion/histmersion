-- V097: a retired provenance value becomes `external-file` (a file the user brought in from
-- elsewhere; core.Provenance). The value is hex-encoded here as in core.Provenance, which keeps
-- accepting it on input (a put, a patch, a seed, a bundle import) and stores the new name.
--
-- Rewritten: the source's own provenance (`sources.source`), and the copies of it that JSON
-- records carry under a "provenance" key — an ingest job's parameters, detail and result, an
-- approval's proposal, an agent step's would-be write, a merge's report. jsonb prints a member as
-- `"key": "value"`, so the text match below is exact. V092 and V095 stay unused.
DO $$
DECLARE
  old  text := convert_from(decode('756e6c6973746564', 'hex'), 'UTF8');
  pat  text := '("provenance": )"' || old || '"';
  repl text := '\1"external-file"';
BEGIN
  UPDATE sources SET source = 'external-file' WHERE lower(btrim(source)) = old;

  UPDATE jobs SET params = regexp_replace(params::text, pat, repl, 'gi')::jsonb
   WHERE params::text ~* pat;
  UPDATE jobs SET detail = regexp_replace(detail::text, pat, repl, 'gi')::jsonb
   WHERE detail IS NOT NULL AND detail::text ~* pat;
  UPDATE jobs SET result = regexp_replace(result::text, pat, repl, 'gi')::jsonb
   WHERE result IS NOT NULL AND result::text ~* pat;
  UPDATE approvals SET proposal = regexp_replace(proposal::text, pat, repl, 'gi')::jsonb
   WHERE proposal::text ~* pat;
  UPDATE agent_steps SET would_write = regexp_replace(would_write::text, pat, repl, 'gi')::jsonb
   WHERE would_write IS NOT NULL AND would_write::text ~* pat;
  UPDATE merges SET report = regexp_replace(report::text, pat, repl, 'gi')::jsonb
   WHERE report::text ~* pat;
END
$$;
