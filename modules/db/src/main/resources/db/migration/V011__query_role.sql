-- The `query` escape hatch runs as its OWN Postgres role.
--
-- Until now `POST /api/query` executed as the application role. That role owns the schema and, in
-- every deployment we ship, is a SUPERUSER: `SET TRANSACTION READ ONLY` was the only thing standing
-- between a model-written statement and `pg_read_file('/etc/passwd')`, and a `;`-batch could turn
-- that off before the first SELECT. So the hatch gets a role that simply CANNOT write or read the
-- filesystem, and the transaction settings become belt-and-braces rather than the whole belt.
--
-- A ROLE is cluster-global while a migration is per-database, so the creation is guarded — a second
-- database in the same cluster must find the role already there and carry on. The PASSWORD is not
-- here on purpose: the server issues `ALTER ROLE … LOGIN PASSWORD …` at startup from
-- `histmersion.query.password`, so the secret is never committed and rotating it is a restart.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'histmersion_query') THEN
    -- NOINHERIT: it must never pick up privileges from a role it is granted; LOGIN because it is a
    -- separate connection, not a SET ROLE (a SET ROLE is reversible from inside the session).
    CREATE ROLE histmersion_query LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
  END IF;
END
$$;

-- Read-only is a property of the ROLE, so it survives a connection this code did not open.
ALTER ROLE histmersion_query SET default_transaction_read_only = on;
-- Pin the search path: an unqualified table name must resolve to `public`, never to something a
-- statement created in a temp schema.
ALTER ROLE histmersion_query SET search_path = public;

GRANT USAGE ON SCHEMA public TO histmersion_query;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO histmersion_query;
-- Later migrations create more tables/views; the default privilege makes them readable without a
-- follow-up GRANT in every one of them.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO histmersion_query;

-- Explicitly NOT granted: CREATE on the schema, INSERT/UPDATE/DELETE on anything, and any function
-- that is superuser-only (pg_read_file, pg_ls_dir, lo_import) — those stay refused by Postgres.
REVOKE CREATE ON SCHEMA public FROM histmersion_query;
