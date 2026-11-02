-- The schema the chain of migrations up to V109 built (squashed 2026-11-02): a fresh database gets it
-- whole; an existing one, already at that schema, has its history replaced by a baseline at 109 (`Migrate.squashHistory`)
-- and none of its rows is touched. Generated with `pg_dump --schema-only --no-owner` of the old chain, with its
-- session settings made transaction-local; the role, its settings and the database setting the dump does not carry
-- come first, the rows the chain seeded last.

-- the read-only role of the `query` escape hatch (cluster-global, so guarded; `Migrate.ensureRole` creates it first)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'histmersion_query') THEN
    CREATE ROLE histmersion_query LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
  END IF;
END
$$;
-- its settings, written only when missing: an ALTER ROLE of the same role from two databases at once collides
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole
                 WHERE r.rolname = 'histmersion_query' AND s.setdatabase = 0
                   AND 'default_transaction_read_only=on' = ANY (s.setconfig)
                   AND 'search_path=public' = ANY (s.setconfig)) THEN
    ALTER ROLE histmersion_query SET default_transaction_read_only = on;
    ALTER ROLE histmersion_query SET search_path = public;
  END IF;
END
$$;

-- SSD planner costs
DO $$
BEGIN
  EXECUTE format('ALTER DATABASE %I SET random_page_cost = 1.1', current_database());
END
$$;

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET LOCAL statement_timeout = 0;
SET LOCAL lock_timeout = 0;
SET LOCAL idle_in_transaction_session_timeout = 0;
SET LOCAL transaction_timeout = 0;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', true);
SET LOCAL check_function_bodies = false;
SET LOCAL xmloption = content;
SET LOCAL client_min_messages = warning;
SET LOCAL row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: clip_to_land(public.geometry); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clip_to_land(g public.geometry) RETURNS public.geometry
    LANGUAGE sql STABLE
    AS $$
  SELECT CASE WHEN g IS NULL OR ST_Dimension(g) < 2 THEN NULL ELSE (
    SELECT ST_Multi(ST_CollectionExtract(ST_Union(
             CASE WHEN ST_CoveredBy(l.geom, s.pg) THEN l.geom ELSE ST_Intersection(l.geom, s.pg) END), 3))
    FROM (SELECT ST_Subdivide(ST_MakeValid(g), 256) AS pg) s
    JOIN land l ON l.geom && s.pg AND ST_Intersects(l.geom, s.pg)) END
$$;


--
-- Name: events_follow_corrections(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.events_follow_corrections() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM corrections WHERE target_kind = 'event' AND target_id = OLD.id;
    RETURN OLD;
  ELSIF NEW.id IS DISTINCT FROM OLD.id THEN
    UPDATE corrections SET target_id = NEW.id WHERE target_kind = 'event' AND target_id = OLD.id;
  END IF;
  RETURN NEW;
END $$;


--
-- Name: histmersion_authors_text(text[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_authors_text(text[]) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$ SELECT array_to_string($1, ' ') $_$;


--
-- Name: histmersion_doi_key(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_doi_key(doi text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT nullif(regexp_replace(lower(btrim(doi)), '^(https?://(dx\.)?doi\.org/|doi:\s*)', ''), '')
$$;


--
-- Name: histmersion_grid_centre(double precision, double precision); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_grid_centre(x double precision, y double precision) RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT coalesce(bool_or(
           mod(mod(round((x * 1000000)::numeric)::bigint, c) + c, c) = c / 2 AND
           mod(mod(round((y * 1000000)::numeric)::bigint, c) + c, c) = c / 2), false)
  FROM unnest(ARRAY[5000000, 1000000, 500000]::bigint[]) AS c
$$;


--
-- Name: histmersion_importing(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_importing() RETURNS boolean
    LANGUAGE sql STABLE
    AS $$ SELECT coalesce(current_setting('histmersion.importing', true), '') = 'on' $$;


--
-- Name: histmersion_instance_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_instance_id() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT id FROM instance LIMIT 1 $$;


--
-- Name: histmersion_map_changed(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_map_changed() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  mark text := 'histmersion.map_changed_' || TG_ARGV[0];
BEGIN
  -- once per transaction and group: a batch of a thousand statements records one change
  IF coalesce(current_setting(mark, true), '') <> txid_current()::text THEN
    INSERT INTO map_changes (grp) VALUES (TG_ARGV[0]);
    PERFORM set_config(mark, txid_current()::text, true);
  END IF;
  RETURN NULL;
END $$;


--
-- Name: histmersion_place_key(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_place_key(t text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
SELECT
regexp_replace(btrim(regexp_replace(translate(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(regexp_replace(t, '\([^)]*\)', ' ', 'g'), 'Æ', 'ae'), 'Þ', 'th'), 'ß', 'ss'), 'æ', 'ae'), 'þ', 'th'), 'Ĳ', 'ij'), 'ĳ', 'ij'), 'Œ', 'oe'), 'œ', 'oe'), 'Ǆ', 'dz'), 'ǅ', 'dz'), 'ǆ', 'dz'), 'Ǉ', 'lj'), 'ǈ', 'lj'), 'ǉ', 'lj'), 'Ǌ', 'nj'), 'ǋ', 'nj'), 'ǌ', 'nj'), 'Ǣ', 'ae'), 'ǣ', 'ae'), 'Ǳ', 'dz'), 'ǲ', 'dz'), 'ǳ', 'dz'), 'Ǽ', 'ae'), 'ǽ', 'ae'), 'ẞ', 'ss'), 'Ỻ', 'll'), 'ỻ', 'll'),
      'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïðñòóôõöøùúûüýÿĀāĂăĄąĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžſƀƁƇƈƉƊƑƒƗƘƙƚƝƞƟƠơƤƥƫƬƭƮƯưƲƳƴƵƶǍǎǏǐǑǒǓǔǕǖǗǘǙǚǛǜǝǞǟǠǡǤǥǦǧǨǩǪǫǬǭǰǴǵǸǹǺǻǾǿȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȔȕȖȗȘșȚțȞȟȡȤȥȦȧȨȩȪȫȬȭȮȯȰȱȲȳȴȵȶȷȺȻȼȽȾȿɀɃɄɆɇɈɉɌɍɎɏḀḁḂḃḄḅḆḇḈḉḊḋḌḍḎḏḐḑḒḓḔḕḖḗḘḙḚḛḜḝḞḟḠḡḢḣḤḥḦḧḨḩḪḫḬḭḮḯḰḱḲḳḴḵḶḷḸḹḺḻḼḽḾḿṀṁṂṃṄṅṆṇṈṉṊṋṌṍṎṏṐṑṒṓṔṕṖṗṘṙṚṛṜṝṞṟṠṡṢṣṤṥṦṧṨṩṪṫṬṭṮṯṰṱṲṳṴṵṶṷṸṹṺṻṼṽṾṿẀẁẂẃẄẅẆẇẈẉẊẋẌẍẎẏẐẑẒẓẔẕẖẗẘẙẜẝẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹABCDEFGHIJKLMNOPQRSTUVWXYZ  !"#$%&()*+,-./:;<=>?@[\]^_{|}~‐‑‒–—―“”„«»·…' || chr(9) || chr(10) || chr(13) || '̴̵̶̷̸̡̢̧̨̛̖̗̘̙̜̝̞̟̠̣̤̥̦̩̪̫̬̭̮̯̰̱̲̳̹̺̻̼͇͈͉͍͎̀́̂̃̄̅̆̇̈̉̊̋̌̍̎̏̐̑̒̓̔̽̾̿̀́͂̓̈́͆͊͋͌̕̚ͅ͏͓͔͕͖͙͚͐͑͒͗͛ͣͤͥͦͧͨͩͪͫͬͭͮͯ͘͜͟͢͝͞͠͡''`´‘’ʼʹʿʾ',
      'aaaaaaceeeeiiiidnoooooouuuuyaaaaaaceeeeiiiidnoooooouuuuyyaaaaaaccccccccddddeeeeeeeeeegggggggghhhhiiiiiiiiiijjkkqllllllllllnnnnnnnnnoooooorrrrrrssssssssttttttuuuuuuuuuuuuwwyyyzzzzzzsbbccddffikklnnoooppttttuuvyyzzaaiioouuuuuuuuuueaaaaggggkkoooojggnnaaooaaaaeeeeiiiioooorrrruuuusstthhdzzaaeeooooooooyylntjaccltszbueejjrryyaabbbbbbccddddddddddeeeeeeeeeeffgghhhhhhhhhhiiiikkkkkkllllllllmmmmmmnnnnnnnnoooooooopppprrrrrrrrssssssssssttttttttuuuuuuuuuuvvvvwwwwwwwwwwxxxxyyzzzzzzhtwyssaaaaaaaaaaaaaaaaaaaaaaaaeeeeeeeeeeeeeeeeiiiioooooooooooooooooooooooouuuuuuuuuuuuuuyyyyyyyyabcdefghijklmnopqrstuvwxyz                                             ' || '   '), ' {2,}', ' ', 'g'), ' '), '^the ', '')
$_$;


--
-- Name: histmersion_sync_source_identifiers(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_sync_source_identifiers() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM source_identifiers WHERE source_uid = NEW.uid AND scheme IN ('doi', 'sha256');
  INSERT INTO source_identifiers (source_uid, scheme, value)
  SELECT NEW.uid, s, v FROM (VALUES ('doi', histmersion_doi_key(NEW.doi)),
                                    ('sha256', nullif(lower(btrim(NEW.sha256)), ''))) AS k(s, v)
  WHERE v IS NOT NULL
  ON CONFLICT DO NOTHING;
  RETURN NULL;
END
$$;


--
-- Name: histmersion_tombstone(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_tombstone() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NOT histmersion_importing() THEN
    INSERT INTO tombstones (uid, kind, origin, deleted_at)
    VALUES (OLD.uid, TG_ARGV[0], OLD.origin, now())
    ON CONFLICT (uid) DO UPDATE SET deleted_at = EXCLUDED.deleted_at;
  END IF;
  RETURN NULL;
END
$$;


--
-- Name: histmersion_touch_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_touch_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  r record;
  ev text;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  -- two statements, not a CASE: a CASE names both fields, and a record has only one of them
  IF TG_TABLE_NAME = 'event_edges' THEN ev := r.src_id; ELSE ev := r.event_id; END IF;
  UPDATE events SET updated_at = now() WHERE id = ev AND updated_at <> now();
  RETURN NULL;
END
$$;


--
-- Name: histmersion_touch_goal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_touch_goal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  r record;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  UPDATE goals SET updated_at = now() WHERE slug = r.goal_slug AND updated_at <> now();
  RETURN NULL;
END
$$;


--
-- Name: histmersion_touch_place(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_touch_place() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  r record;
  pid text;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  IF TG_TABLE_NAME = 'place_relations' THEN pid := r.child_id; ELSE pid := r.place_id; END IF;
  UPDATE places SET updated_at = now() WHERE id = pid AND dataset = 'local' AND updated_at <> now();
  RETURN NULL;
END
$$;


--
-- Name: histmersion_touch_source_of_edge(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_touch_source_of_edge() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  r record;
BEGIN
  IF histmersion_importing() THEN RETURN NULL; END IF;
  r := CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  UPDATE sources SET updated_at = now() WHERE citekey = r.src AND updated_at <> now();
  RETURN NULL;
END
$$;


--
-- Name: histmersion_touch_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_touch_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NOT histmersion_importing() AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at := now();
  END IF;
  RETURN NEW;
END
$$;


--
-- Name: histmersion_undated_changed(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_undated_changed() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  touched boolean := false;
BEGIN
  IF TG_OP = 'TRUNCATE' THEN
    touched := true;
  ELSIF TG_OP = 'INSERT' THEN
    SELECT EXISTS (SELECT 1 FROM new_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period)) INTO touched;
  ELSIF TG_OP = 'DELETE' THEN
    SELECT EXISTS (SELECT 1 FROM old_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period)) INTO touched;
  ELSE
    SELECT EXISTS (SELECT 1 FROM new_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period))
        OR EXISTS (SELECT 1 FROM old_rows r WHERE r.kind = 'site' AND lower_inf(r.period) AND upper_inf(r.period))
      INTO touched;
  END IF;
  IF touched THEN
    INSERT INTO map_changes (grp) VALUES ('undated');
  END IF;
  RETURN NULL;
END $$;


--
-- Name: histmersion_untombstone(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.histmersion_untombstone() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM tombstones WHERE uid = NEW.uid;
  RETURN NULL;
END
$$;


--
-- Name: places_apply_override(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.places_apply_override() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  o place_overrides%ROWTYPE;
  sk text;
  sp int8range;
  su boolean;
  sg geometry;
  changed boolean := false;
BEGIN
  SELECT * INTO o FROM place_overrides WHERE place_id = NEW.id;
  IF NOT FOUND THEN
    RETURN NEW;
  END IF;
  sk := o.source_kind; sp := o.source_period; su := o.source_start_unknown; sg := o.source_geom;
  -- a value other than the override's is what the writer's source says: remember it, apply the override
  IF o.kind IS NULL THEN
    sk := NEW.kind;
  ELSIF NEW.kind IS DISTINCT FROM o.kind THEN
    sk := NEW.kind;
    NEW.kind := o.kind;
    changed := true;
  END IF;
  IF o.period IS NULL THEN
    sp := NEW.period; su := NEW.start_unknown;
  ELSIF NEW.period IS DISTINCT FROM o.period OR NEW.start_unknown THEN
    sp := NEW.period; su := NEW.start_unknown;
    NEW.period := o.period;
    NEW.start_unknown := false;
  END IF;
  -- a point replaces only a point (or nothing): a shape a source gives later is kept
  IF o.point IS NOT NULL AND NEW.geom IS DISTINCT FROM o.point THEN
    sg := NEW.geom;
    IF NEW.geom IS NULL OR GeometryType(NEW.geom) = 'POINT' THEN
      NEW.geom := o.point;
      changed := true;
    END IF;
  END IF;
  -- `places_clip_to_land` ran before this trigger (BEFORE triggers fire by name) on what the writer sent
  IF changed THEN
    NEW.geom_clipped := CASE WHEN NEW.kind = 'polity' THEN clip_to_land(NEW.geom) END;
  END IF;
  IF (sk, sp, su) IS DISTINCT FROM (o.source_kind, o.source_period, o.source_start_unknown)
     OR (o.point IS NOT NULL AND NOT (sg IS NOT DISTINCT FROM o.source_geom)) THEN
    UPDATE place_overrides SET source_kind = sk, source_period = sp, source_start_unknown = su,
                               source_geom = CASE WHEN o.point IS NULL THEN source_geom ELSE sg END
    WHERE place_id = NEW.id;
  END IF;
  RETURN NEW;
END
$$;


--
-- Name: places_clip_to_land(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.places_clip_to_land() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.geom_clipped := CASE WHEN NEW.kind = 'polity' THEN clip_to_land(NEW.geom) END;
  RETURN NEW;
END
$$;


--
-- Name: places_sync_extent_footprint(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.places_sync_extent_footprint() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM place_footprints WHERE place_id = NEW.id AND role = 'extent';
  IF NEW.geom IS NOT NULL THEN
    INSERT INTO place_footprints (place_id, role, geom, period, certainty, citekey)
    VALUES (NEW.id, 'extent', NEW.geom, coalesce(NEW.period, int8range(NULL, NULL)),
            CASE WHEN coalesce((NEW.properties->>'contested')::boolean, false)
                 THEN 'uncertain' ELSE 'approximate' END,
            NEW.source);
  END IF;
  RETURN NULL;
END
$$;


SET LOCAL default_tablespace = '';

SET LOCAL default_table_access_method = heap;

--
-- Name: acquire_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acquire_checks (
    item_id integer NOT NULL,
    checked_at timestamp with time zone NOT NULL,
    generation text DEFAULT ''::text NOT NULL,
    outcome text NOT NULL,
    reason text NOT NULL,
    checks integer DEFAULT 1 NOT NULL
);


--
-- Name: agent_allowances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_allowances (
    target text NOT NULL,
    limits jsonb DEFAULT '[]'::jsonb NOT NULL,
    reserve_pct integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT agent_allowances_reserve_pct_check CHECK (((reserve_pct >= 0) AND (reserve_pct <= 99)))
);


--
-- Name: agent_autopilot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_autopilot (
    id boolean DEFAULT true NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    changed_by text DEFAULT ''::text NOT NULL,
    paused text[] DEFAULT '{}'::text[] NOT NULL,
    last_ensure_at timestamp with time zone,
    last_backup_at timestamp with time zone,
    last_lint_at timestamp with time zone,
    max_active_goals integer,
    scheduler_strategy text,
    scheduler_by text,
    scheduler_at timestamp with time zone,
    last_digest_at timestamp with time zone,
    drain_since timestamp with time zone,
    drain_by text,
    drained_at timestamp with time zone,
    CONSTRAINT agent_autopilot_id_check CHECK (id)
);


--
-- Name: agent_leases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_leases (
    name text NOT NULL,
    owner text NOT NULL,
    until timestamp with time zone NOT NULL
);


--
-- Name: agent_managers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_managers (
    id text NOT NULL,
    kind text NOT NULL,
    goal_slug text,
    state text DEFAULT 'active'::text NOT NULL,
    pause_runs boolean DEFAULT false NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    cursor jsonb DEFAULT '{}'::jsonb NOT NULL,
    status_note text,
    cron text,
    next_tick_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_messages (
    job_id text NOT NULL,
    seq integer NOT NULL,
    attempt integer NOT NULL,
    n integer NOT NULL,
    role text NOT NULL,
    content text,
    tool_name text,
    tool_input text,
    tool_result text,
    latency_ms integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_role_chains; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_role_chains (
    role text NOT NULL,
    models text[] NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_role_fallthrough; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_role_fallthrough (
    role text NOT NULL,
    fallthrough boolean NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_state (
    key text NOT NULL,
    value jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE agent_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.agent_state IS 'Key-value JSONB store for agent state that must survive across sessions — replaces local memory files.';


--
-- Name: agent_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_steps (
    job_id text NOT NULL,
    seq integer NOT NULL,
    attempt integer NOT NULL,
    name text NOT NULL,
    kind text NOT NULL,
    status text NOT NULL,
    output jsonb,
    prompt_hash text,
    model text,
    tokens_in bigint DEFAULT 0 NOT NULL,
    tokens_out bigint DEFAULT 0 NOT NULL,
    tokens_cache_read bigint DEFAULT 0 NOT NULL,
    tokens_cache_write bigint DEFAULT 0 NOT NULL,
    cost_usd numeric(12,6) DEFAULT 0 NOT NULL,
    error text,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    finished_at timestamp with time zone DEFAULT now() NOT NULL,
    actual_model text,
    would_write jsonb,
    first_call_tokens bigint,
    turns integer
);


--
-- Name: alias_tombstones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alias_tombstones (
    alias text NOT NULL,
    source_uid uuid NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    deleted_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: approvals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approvals (
    id bigint NOT NULL,
    job_id text,
    kind text NOT NULL,
    citekey text,
    status text,
    current_status text,
    edge_kind text,
    target text,
    proposal jsonb NOT NULL,
    state text DEFAULT 'open'::text NOT NULL,
    note text,
    result text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    decided_at timestamp with time zone,
    subject text,
    seq integer
);


--
-- Name: approvals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.approvals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approvals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.approvals_id_seq OWNED BY public.approvals.id;


--
-- Name: article_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.article_aliases (
    alias text NOT NULL,
    slug text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: article_critique_reasons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.article_critique_reasons (
    slug text NOT NULL,
    src text NOT NULL,
    dst text NOT NULL,
    reason text NOT NULL,
    set_by text DEFAULT 'person'::text NOT NULL,
    set_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT article_critique_reasons_check CHECK ((src <> dst))
);


--
-- Name: article_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.article_links (
    src_slug text NOT NULL,
    target_kind text NOT NULL,
    target text NOT NULL,
    CONSTRAINT article_links_target_kind_check CHECK ((target_kind = ANY (ARRAY['article'::text, 'source'::text])))
);


--
-- Name: article_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.article_versions (
    id bigint NOT NULL,
    slug text NOT NULL,
    body text NOT NULL,
    summary text,
    updated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL
);


--
-- Name: article_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.article_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: article_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.article_versions_id_seq OWNED BY public.article_versions.id;


--
-- Name: articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.articles (
    slug text NOT NULL,
    title text NOT NULL,
    kind text NOT NULL,
    body text NOT NULL,
    summary text,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    period int8range,
    regions text[] DEFAULT '{}'::text[] NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (((setweight(to_tsvector('english'::regconfig, COALESCE(title, ''::text)), 'A'::"char") || setweight(to_tsvector('english'::regconfig, COALESCE(summary, ''::text)), 'B'::"char")) || setweight(to_tsvector('english'::regconfig, "left"(COALESCE(body, ''::text), 900000)), 'C'::"char"))) STORED,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    coverage_filter jsonb,
    coverage_reasons jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: assessments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessments (
    citekey text NOT NULL,
    checked_at timestamp with time zone DEFAULT now() NOT NULL,
    retracted boolean DEFAULT false NOT NULL,
    notices jsonb DEFAULT '[]'::jsonb NOT NULL,
    cited_by integer,
    influential integer,
    verdict text DEFAULT 'unassessed'::text NOT NULL
);


--
-- Name: asset_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asset_links (
    id bigint NOT NULL,
    citekey text NOT NULL,
    asset_name text NOT NULL,
    relation text NOT NULL,
    target_type text NOT NULL,
    target_id text NOT NULL,
    xywh text,
    notes text,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT asset_links_relation_check CHECK ((relation = ANY (ARRAY['depicts'::text, 'taken_at'::text, 'illustrates'::text, 'page_of'::text]))),
    CONSTRAINT asset_links_target_type_check CHECK ((target_type = ANY (ARRAY['event'::text, 'finding'::text, 'place'::text, 'source'::text, 'article'::text])))
);


--
-- Name: asset_links_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.asset_links ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.asset_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assets (
    citekey text,
    name text NOT NULL,
    mime text NOT NULL,
    bytes bytea NOT NULL,
    sha256 text NOT NULL,
    width integer,
    height integer,
    kind text,
    caption text,
    license text,
    geom public.geometry(Geometry,4326),
    period int8range,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    provider text,
    provider_key text,
    source_url text,
    original_url text,
    author text,
    credit text,
    license_url text,
    retrieved_at timestamp with time zone,
    thumb bytea,
    thumb_mime text,
    thumb_width integer,
    thumb_height integer,
    CONSTRAINT assets_one_owner CHECK (((citekey IS NULL) <> (provider IS NULL))),
    CONSTRAINT assets_provider_fields CHECK (((provider IS NULL) OR ((provider_key IS NOT NULL) AND (source_url IS NOT NULL) AND (retrieved_at IS NOT NULL))))
);


--
-- Name: attestations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attestations (
    id bigint NOT NULL,
    citekey text NOT NULL,
    target_type text NOT NULL,
    target_id text NOT NULL,
    quote text,
    prefix text,
    suffix text,
    char_start integer,
    char_end integer,
    chunk_ord integer,
    page text,
    canvas text,
    xywh text,
    cts_urn text,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT attestations_target_type_check CHECK ((target_type = ANY (ARRAY['finding'::text, 'event'::text, 'event_dating'::text, 'place'::text, 'edge'::text])))
);


--
-- Name: TABLE attestations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.attestations IS 'W3C Web Annotation–inspired granular references: links any entity (finding, event, place, edge) to a specific passage in a source.';


--
-- Name: attestations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attestations ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attestations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bookmarks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bookmarks (
    id uuid DEFAULT uuidv7() NOT NULL,
    user_id text DEFAULT 'local'::text NOT NULL,
    target_kind text NOT NULL,
    target_id text,
    target_uid uuid,
    target_label text,
    lat double precision,
    lon double precision,
    zoom double precision,
    period_start bigint,
    period_end bigint,
    view text,
    label text,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT bookmarks_lat_check CHECK (((lat >= ('-90'::integer)::double precision) AND (lat <= (90)::double precision))),
    CONSTRAINT bookmarks_lon_check CHECK (((lon >= ('-180'::integer)::double precision) AND (lon <= (180)::double precision))),
    CONSTRAINT bookmarks_target_kind_check CHECK ((target_kind = ANY (ARRAY['source'::text, 'article'::text, 'event'::text, 'place'::text, 'picture'::text, 'goal'::text, 'view'::text, 'point'::text, 'period'::text])))
);


--
-- Name: candidate_watches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.candidate_watches (
    goal text NOT NULL,
    kind text NOT NULL,
    subject text NOT NULL,
    index_name text NOT NULL,
    watermark date,
    last_run timestamp with time zone,
    last_error text,
    last_found integer DEFAULT 0 NOT NULL,
    CONSTRAINT candidate_watches_kind_check CHECK ((kind = ANY (ARRAY['query'::text, 'citing'::text, 'author'::text])))
);


--
-- Name: corrections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.corrections (
    id bigint NOT NULL,
    target_kind text NOT NULL,
    target_id text NOT NULL,
    field text NOT NULL,
    value jsonb NOT NULL,
    upstream text,
    upstream_value jsonb NOT NULL,
    upstream_now jsonb,
    status text DEFAULT 'active'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    checked_at timestamp with time zone,
    CONSTRAINT corrections_status_check CHECK ((status = ANY (ARRAY['active'::text, 'agreed'::text, 'conflict'::text]))),
    CONSTRAINT corrections_target_kind_check CHECK ((target_kind = ANY (ARRAY['event'::text, 'place'::text])))
);


--
-- Name: corrections_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.corrections_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: corrections_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.corrections_id_seq OWNED BY public.corrections.id;


--
-- Name: edges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edges (
    src text NOT NULL,
    dst text NOT NULL,
    kind text NOT NULL
);


--
-- Name: event_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_aliases (
    alias text NOT NULL,
    event_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: event_catalogue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_catalogue (
    provider text NOT NULL,
    provider_id text NOT NULL,
    event_id text NOT NULL,
    url text,
    label text NOT NULL,
    kind text NOT NULL,
    not_before bigint,
    begin_by bigint,
    end_from bigint,
    not_after bigint,
    lat double precision,
    lon double precision,
    vei integer,
    magnitude double precision,
    deaths bigint,
    historicity text DEFAULT 'attested'::text NOT NULL,
    record jsonb NOT NULL,
    retrieved_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: event_datings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_datings (
    event_id text NOT NULL,
    citekey text NOT NULL,
    not_before bigint,
    begin_by bigint,
    end_from bigint,
    not_after bigint,
    method text,
    note text
);


--
-- Name: event_edges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_edges (
    src_id text NOT NULL,
    dst_id text NOT NULL,
    kind text NOT NULL,
    citekey text NOT NULL,
    note text
);


--
-- Name: event_import_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_import_parts (
    provider text NOT NULL,
    part text NOT NULL,
    records integer DEFAULT 0 NOT NULL,
    done_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: event_link_candidates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_link_candidates (
    event_id text NOT NULL,
    citekey text NOT NULL,
    score double precision NOT NULL,
    status text DEFAULT 'proposed'::text NOT NULL,
    findings text[] DEFAULT '{}'::text[] NOT NULL,
    passages jsonb DEFAULT '[]'::jsonb NOT NULL,
    reasons text[] DEFAULT '{}'::text[] NOT NULL,
    computed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT event_link_candidates_status_check CHECK ((status = ANY (ARRAY['proposed'::text, 'confirmed'::text, 'rejected'::text])))
);


--
-- Name: event_link_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_link_checks (
    event_id text NOT NULL,
    checked_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: event_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_participants (
    event_id text NOT NULL,
    actor text NOT NULL,
    role text DEFAULT 'participant'::text NOT NULL,
    citekey text
);


--
-- Name: events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.events (
    id text NOT NULL,
    label text NOT NULL,
    kind text DEFAULT 'event'::text NOT NULL,
    not_before bigint,
    begin_by bigint,
    end_from bigint,
    not_after bigint,
    inner_span int8range GENERATED ALWAYS AS (
CASE
    WHEN ((begin_by IS NOT NULL) AND (end_from IS NOT NULL)) THEN int8range(begin_by, end_from, '[]'::text)
    ELSE NULL::int8range
END) STORED,
    historicity text DEFAULT 'attested'::text NOT NULL,
    geom public.geometry(Geometry,4326),
    place_id text,
    parent_id text,
    description text,
    citekey text,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    scale text,
    wikidata text,
    catalogue text,
    names text[] DEFAULT '{}'::text[] NOT NULL,
    central bigint,
    plus_minus bigint,
    dating_method text,
    uncertainty bigint GENERATED ALWAYS AS (
CASE
    WHEN (COALESCE(not_before, begin_by, end_from, not_after) IS NULL) THEN NULL::bigint
    ELSE GREATEST(COALESCE(plus_minus, (0)::bigint),
    CASE
        WHEN ((begin_by IS NULL) AND (end_from IS NULL)) THEN (abs(((COALESCE(not_after, end_from, begin_by, not_before) + ((COALESCE(not_after, end_from, begin_by, not_before) < 0))::integer) - (COALESCE(not_before, begin_by, end_from, not_after) + ((COALESCE(not_before, begin_by, end_from, not_after) < 0))::integer))) / 2)
        ELSE (GREATEST(COALESCE(((begin_by + ((begin_by < 0))::integer) - (not_before + ((not_before < 0))::integer)), (0)::bigint), COALESCE(((not_after + ((not_after < 0))::integer) - (end_from + ((end_from < 0))::integer)), (0)::bigint), (0)::bigint) / 2)
    END)
END) STORED,
    outer_span int8range GENERATED ALWAYS AS (int8range(LEAST(COALESCE(not_before, begin_by, end_from, not_after), COALESCE(not_after, end_from, begin_by, not_before)), GREATEST(COALESCE(not_before, begin_by, end_from, not_after), COALESCE(not_after, end_from, begin_by, not_before)), '[]'::text)) STORED,
    CONSTRAINT events_scale_check CHECK ((scale = ANY (ARRAY['local'::text, 'regional'::text]))),
    CONSTRAINT events_wikidata_check CHECK (((wikidata IS NULL) OR (wikidata ~ '^Q[0-9]+$'::text)))
);


--
-- Name: TABLE events; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.events IS 'CIDOC-CRM inspired: E5 Event with EDTF-like four-point timespan, P9 compositional hierarchy (parent_id), P7 location (place_id/geom).';


--
-- Name: finding_places; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finding_places (
    finding_id text NOT NULL,
    place_id text NOT NULL
);


--
-- Name: findings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.findings (
    id text NOT NULL,
    citekey text NOT NULL,
    claim text NOT NULL,
    region_name text,
    geom public.geometry(Point,4326),
    period int8range,
    period_name text,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    confidence text,
    significance text,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT findings_significance_check CHECK (((significance IS NULL) OR (significance = ANY (ARRAY['mention'::text, 'discussion'::text, 'analysis'::text, 'primary_topic'::text]))))
);


--
-- Name: findings_flat; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.findings_flat AS
 SELECT id,
    citekey,
    claim,
    region_name,
    public.st_x(geom) AS lon,
    public.st_y(geom) AS lat,
    lower(period) AS period_start,
    (upper(period) - 1) AS period_end,
    period_name,
    tags,
    confidence
   FROM public.findings f;


--
-- Name: gazetteer_sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gazetteer_sources (
    key text NOT NULL,
    kind text NOT NULL,
    title text NOT NULL,
    version text NOT NULL,
    sha256 text NOT NULL,
    file text NOT NULL,
    url text,
    licence text,
    citation text,
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL,
    places integer DEFAULT 0 NOT NULL,
    names integer DEFAULT 0 NOT NULL,
    links integer DEFAULT 0 NOT NULL,
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT gazetteer_sources_kind_check CHECK ((kind = ANY (ARRAY['geonames'::text, 'wikidata'::text, 'lpf'::text, 'tgn'::text])))
);


--
-- Name: goal_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_aliases (
    alias text NOT NULL,
    goal_slug text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: goal_focus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_focus (
    slug text NOT NULL,
    active boolean DEFAULT false NOT NULL,
    pinned boolean DEFAULT false NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    since timestamp with time zone,
    why text,
    left_at timestamp with time zone,
    left_why text,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: goal_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_links (
    goal_slug text NOT NULL,
    kind text NOT NULL,
    ref text NOT NULL,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: goal_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_schedules (
    goal_slug text NOT NULL,
    kind text NOT NULL,
    cron text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    budget_usd numeric(12,6),
    next_run_at timestamp with time zone,
    last_run_at timestamp with time zone,
    last_job text,
    last_note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: goals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goals (
    slug text NOT NULL,
    title text NOT NULL,
    status text DEFAULT 'planned'::text NOT NULL,
    notes text,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    regions text[] DEFAULT '{}'::text[] NOT NULL,
    period int8range,
    parent_slug text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    plan jsonb,
    budget_usd numeric(12,6),
    plan_approved_at timestamp with time zone
);


--
-- Name: TABLE goals; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goals IS 'Research programs and topic goals — replaces GOAL.md. Status: planned → seeding → compiled → reviewed → done.';


--
-- Name: instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.instance (
    id uuid DEFAULT uuidv7() NOT NULL,
    label text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    singleton boolean DEFAULT true NOT NULL,
    CONSTRAINT instance_singleton_check CHECK (singleton)
);


--
-- Name: jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jobs (
    id text NOT NULL,
    kind text NOT NULL,
    citekey text,
    status text NOT NULL,
    progress text,
    error text,
    sha256 text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    acquire_item integer,
    detail jsonb,
    pipeline text,
    pipeline_version integer,
    target_kind text,
    target_id text,
    model text,
    budget_usd numeric(12,6),
    budget_tokens bigint,
    warnings text[] DEFAULT '{}'::text[] NOT NULL,
    params jsonb DEFAULT '{}'::jsonb NOT NULL,
    goal_slug text,
    parent_job text,
    origin text,
    result jsonb,
    resume_at timestamp with time zone,
    dry_run boolean DEFAULT false NOT NULL,
    manager_id text,
    manager_item bigint,
    heartbeat_at timestamp with time zone
);


--
-- Name: land; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.land (
    id integer NOT NULL,
    geom public.geometry(Geometry,4326) NOT NULL
);


--
-- Name: land_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.land_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: land_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.land_id_seq OWNED BY public.land.id;


--
-- Name: manager_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_events (
    id bigint NOT NULL,
    manager_id text NOT NULL,
    item_id bigint,
    run_id text,
    at timestamp with time zone DEFAULT now() NOT NULL,
    kind text NOT NULL,
    text text NOT NULL
);


--
-- Name: manager_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_events_id_seq OWNED BY public.manager_events.id;


--
-- Name: manager_hint_uses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_hint_uses (
    hint_id bigint NOT NULL,
    run_id text NOT NULL
);


--
-- Name: manager_hints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_hints (
    id bigint NOT NULL,
    manager_id text NOT NULL,
    item_id bigint,
    text text NOT NULL,
    effect jsonb,
    author text NOT NULL,
    standing boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    retired_at timestamp with time zone
);


--
-- Name: manager_hints_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_hints_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_hints_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_hints_id_seq OWNED BY public.manager_hints.id;


--
-- Name: manager_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_items (
    id bigint NOT NULL,
    manager_id text NOT NULL,
    kind text NOT NULL,
    target text NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    failures integer DEFAULT 0 NOT NULL,
    run_id text,
    evaluated_run text,
    not_before timestamp with time zone,
    hold jsonb,
    state jsonb DEFAULT '{}'::jsonb NOT NULL,
    outcome text,
    origin text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: manager_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_items_id_seq OWNED BY public.manager_items.id;


--
-- Name: map_changes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.map_changes (
    id bigint NOT NULL,
    grp text NOT NULL,
    weight bigint DEFAULT 1 NOT NULL
);


--
-- Name: map_changes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.map_changes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: map_changes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.map_changes_id_seq OWNED BY public.map_changes.id;


--
-- Name: media_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_checks (
    provider text NOT NULL,
    target_type text NOT NULL,
    target_id text NOT NULL,
    checked_at timestamp with time zone DEFAULT now() NOT NULL,
    found integer DEFAULT 0 NOT NULL,
    qid text,
    CONSTRAINT media_checks_target_type_check CHECK ((target_type = ANY (ARRAY['place'::text, 'event'::text, 'source'::text])))
);


--
-- Name: media_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_links (
    asset_uid uuid NOT NULL,
    target_type text NOT NULL,
    target_id text NOT NULL,
    role text NOT NULL,
    property text,
    item text,
    rank integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    basis text,
    CONSTRAINT media_links_role_check CHECK ((role = ANY (ARRAY['image'::text, 'locator-map'::text, 'plan'::text, 'aerial-view'::text, 'route-map'::text, 'depiction'::text, 'object'::text, 'map'::text, 'cover'::text, 'illustration'::text]))),
    CONSTRAINT media_links_target_type_check CHECK ((target_type = ANY (ARRAY['place'::text, 'event'::text, 'source'::text])))
);


--
-- Name: merge_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merge_decisions (
    issue_id text NOT NULL,
    decision text NOT NULL,
    merge_id uuid,
    decided_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: merge_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merge_ledger (
    foreign_uid uuid NOT NULL,
    kind text NOT NULL,
    merge_id uuid NOT NULL,
    foreign_rev text NOT NULL,
    local_uid uuid NOT NULL,
    local_rev_after text,
    decision text NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: merge_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merge_sessions (
    bundle text NOT NULL,
    resolutions jsonb DEFAULT '{}'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: merges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merges (
    id uuid DEFAULT uuidv7() NOT NULL,
    bundle_id uuid NOT NULL,
    bundle_sha256 text NOT NULL,
    from_instance uuid NOT NULL,
    from_label text,
    report jsonb DEFAULT '{}'::jsonb NOT NULL,
    applied_at timestamp with time zone DEFAULT now() NOT NULL,
    plan_hash text,
    backup text,
    resolutions jsonb
);


--
-- Name: notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notes (
    id uuid DEFAULT uuidv7() NOT NULL,
    user_id text DEFAULT 'local'::text NOT NULL,
    target_kind text NOT NULL,
    target_id text,
    target_uid uuid,
    target_label text,
    lat double precision,
    lon double precision,
    period_start bigint,
    period_end bigint,
    body text NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT notes_lat_check CHECK (((lat >= ('-90'::integer)::double precision) AND (lat <= (90)::double precision))),
    CONSTRAINT notes_lon_check CHECK (((lon >= ('-180'::integer)::double precision) AND (lon <= (180)::double precision))),
    CONSTRAINT notes_target_kind_check CHECK ((target_kind = ANY (ARRAY['source'::text, 'article'::text, 'event'::text, 'place'::text, 'picture'::text, 'goal'::text, 'point'::text])))
);


--
-- Name: periods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.periods (
    id text NOT NULL,
    label text NOT NULL,
    authority text,
    period int8range NOT NULL,
    region text,
    parent_id text,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    dataset text DEFAULT 'local'::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    preset text,
    CONSTRAINT periods_dataset_check CHECK ((dataset = ANY (ARRAY['seed'::text, 'local'::text])))
);


--
-- Name: place_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_aliases (
    alias text NOT NULL,
    place_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: place_descriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_descriptions (
    place_id text NOT NULL,
    lang text DEFAULT ''::text NOT NULL,
    text text NOT NULL,
    import_source text NOT NULL,
    import_ref text NOT NULL,
    import_version text
);


--
-- Name: place_footprints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_footprints (
    id integer NOT NULL,
    place_id text NOT NULL,
    role text DEFAULT 'extent'::text NOT NULL,
    geom public.geometry(Geometry,4326) NOT NULL,
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL,
    certainty text,
    accuracy_m real,
    citekey text
);


--
-- Name: place_footprints_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.place_footprints_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: place_footprints_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.place_footprints_id_seq OWNED BY public.place_footprints.id;


--
-- Name: place_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_links (
    place_id text NOT NULL,
    scheme text NOT NULL,
    external_id text NOT NULL,
    relation text DEFAULT 'close_match'::text NOT NULL,
    import_source text DEFAULT ''::text NOT NULL,
    import_ref text DEFAULT ''::text NOT NULL,
    import_version text,
    item_class text,
    item_geom public.geometry(Point,4326),
    item_period int8range
);


--
-- Name: place_names; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_names (
    place_id text NOT NULL,
    name text NOT NULL,
    lang text DEFAULT ''::text NOT NULL,
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL,
    manual boolean DEFAULT false NOT NULL,
    import_source text DEFAULT ''::text NOT NULL,
    import_ref text DEFAULT ''::text NOT NULL,
    import_version text,
    citation text
);


--
-- Name: place_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_overrides (
    place_id text NOT NULL,
    kind text,
    period int8range,
    reason text,
    source_kind text NOT NULL,
    source_period int8range NOT NULL,
    source_start_unknown boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    point public.geometry(Point,4326),
    source_geom public.geometry(Geometry,4326),
    CONSTRAINT place_overrides_check CHECK (((kind IS NOT NULL) OR (period IS NOT NULL) OR (point IS NOT NULL)))
);


--
-- Name: place_relations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_relations (
    child_id text NOT NULL,
    parent_id text NOT NULL,
    kind text DEFAULT 'part_of'::text NOT NULL,
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL
);


--
-- Name: COLUMN place_relations.kind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.place_relations.kind IS 'part_of | part_of_admin | part_of_regional | part_of_physical | succeeds | same_as | version_of | capital_of';


--
-- Name: place_removals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_removals (
    id text NOT NULL,
    label text NOT NULL,
    reason text,
    removed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: place_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_types (
    place_id text NOT NULL,
    type_label text NOT NULL,
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL,
    citekey text
);


--
-- Name: places; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.places (
    id text NOT NULL,
    label text NOT NULL,
    kind text NOT NULL,
    geom public.geometry(Geometry,4326),
    period int8range DEFAULT int8range(NULL::bigint, NULL::bigint) NOT NULL,
    source text,
    properties jsonb,
    geom_clipped public.geometry(Geometry,4326),
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    dataset text DEFAULT 'local'::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    import_source text,
    import_ref text,
    import_version text,
    start_unknown boolean DEFAULT false NOT NULL,
    location_precision text GENERATED ALWAYS AS (
CASE
    WHEN ((id ~~ 'pleiades\_%'::text) AND (public.geometrytype(geom) = 'POINT'::text)) THEN
    CASE
        WHEN public.histmersion_grid_centre(public.st_x(geom), public.st_y(geom)) THEN 'grid-cell'::text
        ELSE NULL::text
    END
    ELSE NULL::text
END) STORED,
    site_type text,
    site_type_checked_at timestamp with time zone,
    shape_area double precision GENERATED ALWAYS AS (public.st_area(geom)) STORED,
    shape_box public.box2d GENERATED ALWAYS AS ((geom)::public.box2d) STORED,
    shape_dim smallint GENERATED ALWAYS AS (public.st_dimension(geom)) STORED,
    shape_valid boolean GENERATED ALWAYS AS (public.st_isvalid(geom, 0)) STORED,
    label_norm text GENERATED ALWAYS AS (regexp_replace(regexp_replace(lower(label), '\m(of|the|and)\M'::text, ''::text, 'g'::text), '[^a-z0-9]+'::text, ''::text, 'g'::text)) STORED,
    web_geojson text GENERATED ALWAYS AS (
CASE
    WHEN (public.st_dimension(geom) = 2) THEN public.st_asgeojson(public.st_simplifypreservetopology(COALESCE(geom_clipped, geom), LEAST((0.02)::double precision, (sqrt(public.st_area(geom)) / (100)::double precision))),
    CASE
        WHEN (public.st_area(geom) >= (1)::double precision) THEN 3
        ELSE 4
    END)
    ELSE NULL::text
END) STORED,
    CONSTRAINT places_dataset_check CHECK ((dataset = ANY (ARRAY['seed'::text, 'local'::text])))
);
ALTER TABLE ONLY public.places ALTER COLUMN geom SET STORAGE EXTENDED;
ALTER TABLE ONLY public.places ALTER COLUMN geom_clipped SET STORAGE EXTENDED;


--
-- Name: plan_pools; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_pools (
    pool text NOT NULL,
    held boolean DEFAULT false NOT NULL,
    floors text,
    refusal text,
    refusal_until timestamp with time zone,
    plan_type text,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: plan_quota_readings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_quota_readings (
    pool text NOT NULL,
    limit_id text NOT NULL,
    kind text NOT NULL,
    model text DEFAULT ''::text NOT NULL,
    used_pct double precision NOT NULL,
    resets_at timestamp with time zone,
    rejected boolean DEFAULT false NOT NULL,
    observed_at timestamp with time zone DEFAULT now() NOT NULL,
    source text NOT NULL
);


--
-- Name: plan_quota_refreshes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_quota_refreshes (
    id bigint NOT NULL,
    pool text NOT NULL,
    at timestamp with time zone DEFAULT now() NOT NULL,
    mode text NOT NULL,
    trigger text NOT NULL,
    ok boolean NOT NULL,
    detail text NOT NULL
);


--
-- Name: plan_quota_refreshes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plan_quota_refreshes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plan_quota_refreshes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.plan_quota_refreshes_id_seq OWNED BY public.plan_quota_refreshes.id;


--
-- Name: queue_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.queue_items (
    id integer NOT NULL,
    list text NOT NULL,
    text text NOT NULL,
    topic text,
    status text DEFAULT 'open'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    done_at timestamp with time zone,
    reason text,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    goal_slug text,
    created_job text,
    closed_job text,
    CONSTRAINT queue_items_list_check CHECK ((list = ANY (ARRAY['acquire'::text, 'tasks'::text, 'log'::text])))
);


--
-- Name: queue_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.queue_items_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: queue_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.queue_items_id_seq OWNED BY public.queue_items.id;


--
-- Name: read_marks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.read_marks (
    id uuid DEFAULT uuidv7() NOT NULL,
    user_id text DEFAULT 'local'::text NOT NULL,
    target_kind text NOT NULL,
    target_id text NOT NULL,
    target_uid uuid,
    target_label text,
    read_at timestamp with time zone,
    opened_at timestamp with time zone,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT read_marks_target_kind_check CHECK ((target_kind = ANY (ARRAY['source'::text, 'article'::text])))
);


--
-- Name: retraction_watch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.retraction_watch (
    doi text NOT NULL,
    reason text,
    date date,
    nature text
);


--
-- Name: review_ledgers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.review_ledgers (
    slug text NOT NULL,
    ledger jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: seed_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seed_versions (
    name text NOT NULL,
    version integer NOT NULL,
    applied_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: site_undated_build; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.site_undated_build (
    id boolean DEFAULT true NOT NULL,
    version bigint NOT NULL,
    per_side integer NOT NULL,
    max_zoom integer NOT NULL,
    built_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT site_undated_build_id_check CHECK (id)
);


--
-- Name: site_undated_cells; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.site_undated_cells (
    z smallint NOT NULL,
    gx bigint NOT NULL,
    gy bigint NOT NULL,
    site_type text NOT NULL,
    n integer NOT NULL,
    sx double precision NOT NULL,
    sy double precision NOT NULL,
    w double precision NOT NULL,
    s double precision NOT NULL,
    e double precision NOT NULL,
    nn double precision NOT NULL,
    one text NOT NULL
);


--
-- Name: source_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_aliases (
    alias text NOT NULL,
    citekey text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: source_candidates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_candidates (
    id bigint NOT NULL,
    goal text NOT NULL,
    doi text NOT NULL,
    title text NOT NULL,
    year integer,
    authors text[] DEFAULT '{}'::text[] NOT NULL,
    venue text,
    abstract text,
    is_oa boolean DEFAULT false NOT NULL,
    arxiv_id text,
    found_by text[] DEFAULT '{}'::text[] NOT NULL,
    state text DEFAULT 'new'::text NOT NULL,
    first_seen timestamp with time zone DEFAULT now() NOT NULL,
    last_seen timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT source_candidates_state_check CHECK ((state = ANY (ARRAY['new'::text, 'seen'::text, 'queued'::text, 'dismissed'::text])))
);


--
-- Name: source_candidates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.source_candidates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: source_candidates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.source_candidates_id_seq OWNED BY public.source_candidates.id;


--
-- Name: source_chunks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_chunks (
    citekey text NOT NULL,
    ord integer NOT NULL,
    heading text,
    body text NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('english'::regconfig, ((COALESCE(heading, ''::text) || ' '::text) || body))) STORED
);


--
-- Name: source_identifiers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_identifiers (
    source_uid uuid NOT NULL,
    scheme text NOT NULL,
    value text NOT NULL,
    CONSTRAINT source_identifiers_scheme_check CHECK ((scheme = ANY (ARRAY['doi'::text, 'sha256'::text, 'isbn'::text, 'openalex'::text, 'oclc'::text])))
);


--
-- Name: sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sources (
    citekey text NOT NULL,
    doi text,
    title text NOT NULL,
    authors text[] DEFAULT '{}'::text[] NOT NULL,
    year integer,
    venue text,
    status text DEFAULT 'unassessed'::text NOT NULL,
    topics text[] DEFAULT '{}'::text[] NOT NULL,
    regions text[] DEFAULT '{}'::text[] NOT NULL,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    period int8range,
    cited_by_count integer,
    influential_citations integer,
    summary text,
    ocr_markdown text,
    source text,
    calibre_id text,
    sha256 text,
    added_date date DEFAULT CURRENT_DATE NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    notes text,
    tsv tsvector GENERATED ALWAYS AS ((setweight(to_tsvector('english'::regconfig, COALESCE(title, ''::text)), 'A'::"char") || setweight(to_tsvector('english'::regconfig, COALESCE(summary, ''::text)), 'B'::"char"))) STORED,
    uid uuid DEFAULT uuidv7() NOT NULL,
    origin uuid DEFAULT public.histmersion_instance_id() NOT NULL,
    license text
);


--
-- Name: tombstones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tombstones (
    uid uuid NOT NULL,
    kind text NOT NULL,
    origin uuid NOT NULL,
    deleted_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: ui_seen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ui_seen (
    board text NOT NULL,
    seen_at timestamp with time zone NOT NULL
);


--
-- Name: uid_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uid_aliases (
    alias_uid uuid NOT NULL,
    uid uuid NOT NULL,
    kind text NOT NULL,
    merge_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT uid_aliases_check CHECK ((uid <> alias_uid))
);


--
-- Name: unresolved_edges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unresolved_edges (
    src text NOT NULL,
    dst text NOT NULL,
    kind text NOT NULL
);


--
-- Name: upstream_quota; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upstream_quota (
    upstream text NOT NULL,
    day date NOT NULL,
    lookup_tokens integer DEFAULT 0 NOT NULL,
    search_tokens integer DEFAULT 0 NOT NULL,
    blocked_until timestamp with time zone,
    blocked_why text,
    rate_limit integer,
    rate_remaining integer,
    observed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: approvals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approvals ALTER COLUMN id SET DEFAULT nextval('public.approvals_id_seq'::regclass);


--
-- Name: article_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_versions ALTER COLUMN id SET DEFAULT nextval('public.article_versions_id_seq'::regclass);


--
-- Name: corrections id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corrections ALTER COLUMN id SET DEFAULT nextval('public.corrections_id_seq'::regclass);


--
-- Name: land id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.land ALTER COLUMN id SET DEFAULT nextval('public.land_id_seq'::regclass);


--
-- Name: manager_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_events ALTER COLUMN id SET DEFAULT nextval('public.manager_events_id_seq'::regclass);


--
-- Name: manager_hints id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hints ALTER COLUMN id SET DEFAULT nextval('public.manager_hints_id_seq'::regclass);


--
-- Name: manager_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_items ALTER COLUMN id SET DEFAULT nextval('public.manager_items_id_seq'::regclass);


--
-- Name: map_changes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.map_changes ALTER COLUMN id SET DEFAULT nextval('public.map_changes_id_seq'::regclass);


--
-- Name: place_footprints id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_footprints ALTER COLUMN id SET DEFAULT nextval('public.place_footprints_id_seq'::regclass);


--
-- Name: plan_quota_refreshes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_quota_refreshes ALTER COLUMN id SET DEFAULT nextval('public.plan_quota_refreshes_id_seq'::regclass);


--
-- Name: queue_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_items ALTER COLUMN id SET DEFAULT nextval('public.queue_items_id_seq'::regclass);


--
-- Name: source_candidates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_candidates ALTER COLUMN id SET DEFAULT nextval('public.source_candidates_id_seq'::regclass);


--
-- Name: acquire_checks acquire_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acquire_checks
    ADD CONSTRAINT acquire_checks_pkey PRIMARY KEY (item_id);


--
-- Name: agent_allowances agent_allowances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_allowances
    ADD CONSTRAINT agent_allowances_pkey PRIMARY KEY (target);


--
-- Name: agent_autopilot agent_autopilot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_autopilot
    ADD CONSTRAINT agent_autopilot_pkey PRIMARY KEY (id);


--
-- Name: agent_leases agent_leases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_leases
    ADD CONSTRAINT agent_leases_pkey PRIMARY KEY (name);


--
-- Name: agent_managers agent_managers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_managers
    ADD CONSTRAINT agent_managers_pkey PRIMARY KEY (id);


--
-- Name: agent_messages agent_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_messages
    ADD CONSTRAINT agent_messages_pkey PRIMARY KEY (job_id, seq, attempt, n);


--
-- Name: agent_role_chains agent_role_chains_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_role_chains
    ADD CONSTRAINT agent_role_chains_pkey PRIMARY KEY (role);


--
-- Name: agent_role_fallthrough agent_role_fallthrough_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_role_fallthrough
    ADD CONSTRAINT agent_role_fallthrough_pkey PRIMARY KEY (role);


--
-- Name: agent_state agent_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_state
    ADD CONSTRAINT agent_state_pkey PRIMARY KEY (key);


--
-- Name: agent_steps agent_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_steps
    ADD CONSTRAINT agent_steps_pkey PRIMARY KEY (job_id, seq, attempt);


--
-- Name: alias_tombstones alias_tombstones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alias_tombstones
    ADD CONSTRAINT alias_tombstones_pkey PRIMARY KEY (alias);


--
-- Name: approvals approvals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_pkey PRIMARY KEY (id);


--
-- Name: article_aliases article_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_aliases
    ADD CONSTRAINT article_aliases_pkey PRIMARY KEY (alias);


--
-- Name: article_critique_reasons article_critique_reasons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_critique_reasons
    ADD CONSTRAINT article_critique_reasons_pkey PRIMARY KEY (slug, src, dst);


--
-- Name: article_links article_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_links
    ADD CONSTRAINT article_links_pkey PRIMARY KEY (src_slug, target_kind, target);


--
-- Name: article_versions article_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_versions
    ADD CONSTRAINT article_versions_pkey PRIMARY KEY (id);


--
-- Name: article_versions article_versions_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_versions
    ADD CONSTRAINT article_versions_uid_key UNIQUE (uid);


--
-- Name: articles articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_pkey PRIMARY KEY (slug);


--
-- Name: articles articles_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_uid_key UNIQUE (uid);


--
-- Name: assessments assessments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessments
    ADD CONSTRAINT assessments_pkey PRIMARY KEY (citekey);


--
-- Name: asset_links asset_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset_links
    ADD CONSTRAINT asset_links_pkey PRIMARY KEY (id);


--
-- Name: asset_links asset_links_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset_links
    ADD CONSTRAINT asset_links_uid_key UNIQUE (uid);


--
-- Name: assets assets_citekey_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_citekey_name_key UNIQUE (citekey, name);


--
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (uid);


--
-- Name: assets assets_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_uid_key UNIQUE (uid);


--
-- Name: attestations attestations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attestations
    ADD CONSTRAINT attestations_pkey PRIMARY KEY (id);


--
-- Name: attestations attestations_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attestations
    ADD CONSTRAINT attestations_uid_key UNIQUE (uid);


--
-- Name: bookmarks bookmarks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookmarks
    ADD CONSTRAINT bookmarks_pkey PRIMARY KEY (id);


--
-- Name: candidate_watches candidate_watches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.candidate_watches
    ADD CONSTRAINT candidate_watches_pkey PRIMARY KEY (goal, kind, subject, index_name);


--
-- Name: corrections corrections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corrections
    ADD CONSTRAINT corrections_pkey PRIMARY KEY (id);


--
-- Name: corrections corrections_target_kind_target_id_field_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corrections
    ADD CONSTRAINT corrections_target_kind_target_id_field_key UNIQUE (target_kind, target_id, field);


--
-- Name: edges edges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT edges_pkey PRIMARY KEY (src, dst, kind);


--
-- Name: event_aliases event_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_aliases
    ADD CONSTRAINT event_aliases_pkey PRIMARY KEY (alias);


--
-- Name: event_catalogue event_catalogue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_catalogue
    ADD CONSTRAINT event_catalogue_pkey PRIMARY KEY (provider, provider_id);


--
-- Name: event_datings event_datings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_datings
    ADD CONSTRAINT event_datings_pkey PRIMARY KEY (event_id, citekey);


--
-- Name: event_edges event_edges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_edges
    ADD CONSTRAINT event_edges_pkey PRIMARY KEY (src_id, dst_id, kind);


--
-- Name: event_import_parts event_import_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_import_parts
    ADD CONSTRAINT event_import_parts_pkey PRIMARY KEY (provider, part);


--
-- Name: event_link_candidates event_link_candidates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_link_candidates
    ADD CONSTRAINT event_link_candidates_pkey PRIMARY KEY (event_id, citekey);


--
-- Name: event_link_checks event_link_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_link_checks
    ADD CONSTRAINT event_link_checks_pkey PRIMARY KEY (event_id);


--
-- Name: event_participants event_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_participants
    ADD CONSTRAINT event_participants_pkey PRIMARY KEY (event_id, actor, role);


--
-- Name: events events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_pkey PRIMARY KEY (id);


--
-- Name: events events_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_uid_key UNIQUE (uid);


--
-- Name: finding_places finding_places_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_places
    ADD CONSTRAINT finding_places_pkey PRIMARY KEY (finding_id, place_id);


--
-- Name: findings findings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT findings_pkey PRIMARY KEY (id);


--
-- Name: findings findings_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT findings_uid_key UNIQUE (uid);


--
-- Name: gazetteer_sources gazetteer_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gazetteer_sources
    ADD CONSTRAINT gazetteer_sources_pkey PRIMARY KEY (key);


--
-- Name: goal_aliases goal_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_aliases
    ADD CONSTRAINT goal_aliases_pkey PRIMARY KEY (alias);


--
-- Name: goal_focus goal_focus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_focus
    ADD CONSTRAINT goal_focus_pkey PRIMARY KEY (slug);


--
-- Name: goal_links goal_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_links
    ADD CONSTRAINT goal_links_pkey PRIMARY KEY (goal_slug, kind, ref);


--
-- Name: goal_schedules goal_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_schedules
    ADD CONSTRAINT goal_schedules_pkey PRIMARY KEY (goal_slug, kind);


--
-- Name: goals goals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goals
    ADD CONSTRAINT goals_pkey PRIMARY KEY (slug);


--
-- Name: goals goals_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goals
    ADD CONSTRAINT goals_uid_key UNIQUE (uid);


--
-- Name: instance instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.instance
    ADD CONSTRAINT instance_pkey PRIMARY KEY (id);


--
-- Name: instance instance_singleton_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.instance
    ADD CONSTRAINT instance_singleton_key UNIQUE (singleton);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);


--
-- Name: land land_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.land
    ADD CONSTRAINT land_pkey PRIMARY KEY (id);


--
-- Name: manager_events manager_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_events
    ADD CONSTRAINT manager_events_pkey PRIMARY KEY (id);


--
-- Name: manager_hint_uses manager_hint_uses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hint_uses
    ADD CONSTRAINT manager_hint_uses_pkey PRIMARY KEY (hint_id, run_id);


--
-- Name: manager_hints manager_hints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hints
    ADD CONSTRAINT manager_hints_pkey PRIMARY KEY (id);


--
-- Name: manager_items manager_items_manager_id_kind_target_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_items
    ADD CONSTRAINT manager_items_manager_id_kind_target_key UNIQUE (manager_id, kind, target);


--
-- Name: manager_items manager_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_items
    ADD CONSTRAINT manager_items_pkey PRIMARY KEY (id);


--
-- Name: map_changes map_changes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.map_changes
    ADD CONSTRAINT map_changes_pkey PRIMARY KEY (id);


--
-- Name: media_checks media_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_checks
    ADD CONSTRAINT media_checks_pkey PRIMARY KEY (provider, target_type, target_id);


--
-- Name: media_links media_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_links
    ADD CONSTRAINT media_links_pkey PRIMARY KEY (asset_uid, target_type, target_id, role);


--
-- Name: merge_decisions merge_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merge_decisions
    ADD CONSTRAINT merge_decisions_pkey PRIMARY KEY (issue_id);


--
-- Name: merge_ledger merge_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merge_ledger
    ADD CONSTRAINT merge_ledger_pkey PRIMARY KEY (foreign_uid);


--
-- Name: merge_sessions merge_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merge_sessions
    ADD CONSTRAINT merge_sessions_pkey PRIMARY KEY (bundle);


--
-- Name: merges merges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merges
    ADD CONSTRAINT merges_pkey PRIMARY KEY (id);


--
-- Name: notes notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notes
    ADD CONSTRAINT notes_pkey PRIMARY KEY (id);


--
-- Name: periods periods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periods
    ADD CONSTRAINT periods_pkey PRIMARY KEY (id);


--
-- Name: periods periods_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periods
    ADD CONSTRAINT periods_uid_key UNIQUE (uid);


--
-- Name: place_aliases place_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_aliases
    ADD CONSTRAINT place_aliases_pkey PRIMARY KEY (alias);


--
-- Name: place_descriptions place_descriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_descriptions
    ADD CONSTRAINT place_descriptions_pkey PRIMARY KEY (place_id, lang, import_source, import_ref);


--
-- Name: place_footprints place_footprints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_footprints
    ADD CONSTRAINT place_footprints_pkey PRIMARY KEY (id);


--
-- Name: place_links place_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_links
    ADD CONSTRAINT place_links_pkey PRIMARY KEY (place_id, scheme, external_id);


--
-- Name: place_names place_names_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_names
    ADD CONSTRAINT place_names_pkey PRIMARY KEY (place_id, name, lang, import_source, import_ref);


--
-- Name: place_overrides place_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_overrides
    ADD CONSTRAINT place_overrides_pkey PRIMARY KEY (place_id);


--
-- Name: place_relations place_relations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_relations
    ADD CONSTRAINT place_relations_pkey PRIMARY KEY (child_id, parent_id, kind);


--
-- Name: place_removals place_removals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_removals
    ADD CONSTRAINT place_removals_pkey PRIMARY KEY (id);


--
-- Name: place_types place_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_types
    ADD CONSTRAINT place_types_pkey PRIMARY KEY (place_id, type_label);


--
-- Name: places places_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.places
    ADD CONSTRAINT places_pkey PRIMARY KEY (id);


--
-- Name: places places_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.places
    ADD CONSTRAINT places_uid_key UNIQUE (uid);


--
-- Name: plan_pools plan_pools_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_pools
    ADD CONSTRAINT plan_pools_pkey PRIMARY KEY (pool);


--
-- Name: plan_quota_readings plan_quota_readings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_quota_readings
    ADD CONSTRAINT plan_quota_readings_pkey PRIMARY KEY (pool, limit_id, kind, model);


--
-- Name: plan_quota_refreshes plan_quota_refreshes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_quota_refreshes
    ADD CONSTRAINT plan_quota_refreshes_pkey PRIMARY KEY (id);


--
-- Name: queue_items queue_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_items
    ADD CONSTRAINT queue_items_pkey PRIMARY KEY (id);


--
-- Name: queue_items queue_items_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_items
    ADD CONSTRAINT queue_items_uid_key UNIQUE (uid);


--
-- Name: read_marks read_marks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.read_marks
    ADD CONSTRAINT read_marks_pkey PRIMARY KEY (id);


--
-- Name: read_marks read_marks_user_id_target_kind_target_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.read_marks
    ADD CONSTRAINT read_marks_user_id_target_kind_target_id_key UNIQUE (user_id, target_kind, target_id);


--
-- Name: retraction_watch retraction_watch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.retraction_watch
    ADD CONSTRAINT retraction_watch_pkey PRIMARY KEY (doi);


--
-- Name: review_ledgers review_ledgers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.review_ledgers
    ADD CONSTRAINT review_ledgers_pkey PRIMARY KEY (slug);


--
-- Name: seed_versions seed_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seed_versions
    ADD CONSTRAINT seed_versions_pkey PRIMARY KEY (name);


--
-- Name: site_undated_build site_undated_build_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site_undated_build
    ADD CONSTRAINT site_undated_build_pkey PRIMARY KEY (id);


--
-- Name: site_undated_cells site_undated_cells_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site_undated_cells
    ADD CONSTRAINT site_undated_cells_pkey PRIMARY KEY (z, gx, gy, site_type);


--
-- Name: source_aliases source_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_aliases
    ADD CONSTRAINT source_aliases_pkey PRIMARY KEY (alias);


--
-- Name: source_candidates source_candidates_goal_doi_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_candidates
    ADD CONSTRAINT source_candidates_goal_doi_key UNIQUE (goal, doi);


--
-- Name: source_candidates source_candidates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_candidates
    ADD CONSTRAINT source_candidates_pkey PRIMARY KEY (id);


--
-- Name: source_chunks source_chunks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_chunks
    ADD CONSTRAINT source_chunks_pkey PRIMARY KEY (citekey, ord);


--
-- Name: source_identifiers source_identifiers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_identifiers
    ADD CONSTRAINT source_identifiers_pkey PRIMARY KEY (source_uid, scheme, value);


--
-- Name: sources sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sources
    ADD CONSTRAINT sources_pkey PRIMARY KEY (citekey);


--
-- Name: sources sources_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sources
    ADD CONSTRAINT sources_uid_key UNIQUE (uid);


--
-- Name: tombstones tombstones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tombstones
    ADD CONSTRAINT tombstones_pkey PRIMARY KEY (uid);


--
-- Name: ui_seen ui_seen_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ui_seen
    ADD CONSTRAINT ui_seen_pkey PRIMARY KEY (board);


--
-- Name: uid_aliases uid_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uid_aliases
    ADD CONSTRAINT uid_aliases_pkey PRIMARY KEY (alias_uid);


--
-- Name: unresolved_edges unresolved_edges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unresolved_edges
    ADD CONSTRAINT unresolved_edges_pkey PRIMARY KEY (src, dst, kind);


--
-- Name: upstream_quota upstream_quota_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upstream_quota
    ADD CONSTRAINT upstream_quota_pkey PRIMARY KEY (upstream);


--
-- Name: acquire_checks_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX acquire_checks_at ON public.acquire_checks USING btree (checked_at);


--
-- Name: agent_steps_finished; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_steps_finished ON public.agent_steps USING btree (finished_at);


--
-- Name: agent_steps_model_finished; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_steps_model_finished ON public.agent_steps USING btree (model, finished_at);


--
-- Name: approvals_created_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX approvals_created_page ON public.approvals USING btree (created_at, id);


--
-- Name: approvals_decided_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX approvals_decided_page ON public.approvals USING btree (decided_at, id) WHERE (decided_at IS NOT NULL);


--
-- Name: approvals_open_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX approvals_open_once ON public.approvals USING btree (kind, citekey, COALESCE(status, ''::text), COALESCE(edge_kind, ''::text), COALESCE(target, ''::text)) WHERE (state = 'open'::text);


--
-- Name: approvals_run_step; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX approvals_run_step ON public.approvals USING btree (job_id, seq) WHERE (seq IS NOT NULL);


--
-- Name: approvals_state_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX approvals_state_page ON public.approvals USING btree (state, created_at, id);


--
-- Name: approvals_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX approvals_subject ON public.approvals USING btree (kind, subject) WHERE (subject IS NOT NULL);


--
-- Name: article_aliases_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX article_aliases_slug ON public.article_aliases USING btree (slug);


--
-- Name: article_critique_reasons_dst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX article_critique_reasons_dst ON public.article_critique_reasons USING btree (dst);


--
-- Name: article_critique_reasons_src; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX article_critique_reasons_src ON public.article_critique_reasons USING btree (src);


--
-- Name: article_links_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX article_links_target ON public.article_links USING btree (target_kind, target);


--
-- Name: article_versions_slug_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX article_versions_slug_idx ON public.article_versions USING btree (slug, created_at DESC);


--
-- Name: articles_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_kind ON public.articles USING btree (kind);


--
-- Name: articles_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_period_gist ON public.articles USING gist (period);


--
-- Name: articles_regions_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_regions_gin ON public.articles USING gin (regions);


--
-- Name: articles_tags_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_tags_gin ON public.articles USING gin (tags);


--
-- Name: articles_title_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_title_trgm ON public.articles USING gin (title public.gin_trgm_ops);


--
-- Name: articles_tsv_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX articles_tsv_gin ON public.articles USING gin (tsv);


--
-- Name: assessments_checked_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assessments_checked_at ON public.assessments USING btree (checked_at);


--
-- Name: assessments_verdict; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assessments_verdict ON public.assessments USING btree (verdict);


--
-- Name: asset_links_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX asset_links_asset ON public.asset_links USING btree (citekey, asset_name);


--
-- Name: asset_links_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX asset_links_target ON public.asset_links USING btree (target_type, target_id);


--
-- Name: assets_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assets_geom ON public.assets USING gist (geom) WHERE (geom IS NOT NULL);


--
-- Name: assets_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assets_kind ON public.assets USING btree (kind) WHERE (kind IS NOT NULL);


--
-- Name: assets_license; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assets_license ON public.assets USING btree (license);


--
-- Name: assets_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assets_period ON public.assets USING gist (period) WHERE (period IS NOT NULL);


--
-- Name: assets_provider_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX assets_provider_key ON public.assets USING btree (provider, provider_key) WHERE (provider IS NOT NULL);


--
-- Name: assets_sha256; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX assets_sha256 ON public.assets USING btree (sha256);


--
-- Name: attestations_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX attestations_citekey ON public.attestations USING btree (citekey);


--
-- Name: attestations_event_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX attestations_event_target ON public.attestations USING btree (target_id) WHERE (target_type = 'event'::text);


--
-- Name: attestations_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX attestations_target ON public.attestations USING btree (target_type, target_id);


--
-- Name: bookmarks_item_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX bookmarks_item_once ON public.bookmarks USING btree (user_id, target_kind, target_id) WHERE ((deleted_at IS NULL) AND (target_id IS NOT NULL));


--
-- Name: bookmarks_user_live; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bookmarks_user_live ON public.bookmarks USING btree (user_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: corrections_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX corrections_status ON public.corrections USING btree (status) WHERE (status <> 'agreed'::text);


--
-- Name: edges_dst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX edges_dst ON public.edges USING btree (dst);


--
-- Name: event_aliases_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_aliases_event ON public.event_aliases USING btree (event_id);


--
-- Name: event_catalogue_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_catalogue_event ON public.event_catalogue USING btree (event_id);


--
-- Name: event_edges_dst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_edges_dst ON public.event_edges USING btree (dst_id);


--
-- Name: event_link_candidates_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_link_candidates_citekey ON public.event_link_candidates USING btree (citekey);


--
-- Name: events_catalogue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_catalogue ON public.events USING btree (catalogue) WHERE (catalogue IS NOT NULL);


--
-- Name: events_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_geom ON public.events USING gist (geom) WHERE (geom IS NOT NULL);


--
-- Name: events_historicity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_historicity ON public.events USING btree (historicity);


--
-- Name: events_inner_span; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_inner_span ON public.events USING gist (inner_span) WHERE (inner_span IS NOT NULL);


--
-- Name: events_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_kind ON public.events USING btree (kind);


--
-- Name: events_outer_span; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_outer_span ON public.events USING gist (outer_span);


--
-- Name: events_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_parent ON public.events USING btree (parent_id) WHERE (parent_id IS NOT NULL);


--
-- Name: events_place; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_place ON public.events USING btree (place_id) WHERE (place_id IS NOT NULL);


--
-- Name: events_uncertainty; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX events_uncertainty ON public.events USING btree (uncertainty) WHERE (uncertainty > 500);


--
-- Name: finding_places_place; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finding_places_place ON public.finding_places USING btree (place_id);


--
-- Name: findings_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX findings_citekey ON public.findings USING btree (citekey);


--
-- Name: findings_geom_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX findings_geom_gist ON public.findings USING gist (geom);


--
-- Name: findings_geom_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX findings_geom_period_gist ON public.findings USING gist (geom, period);


--
-- Name: findings_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX findings_period_gist ON public.findings USING gist (period);


--
-- Name: goal_aliases_goal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_aliases_goal ON public.goal_aliases USING btree (goal_slug);


--
-- Name: goal_focus_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_focus_active ON public.goal_focus USING btree (active) WHERE active;


--
-- Name: goal_links_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_links_ref ON public.goal_links USING btree (kind, ref);


--
-- Name: goal_schedules_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_schedules_due ON public.goal_schedules USING btree (next_run_at) WHERE enabled;


--
-- Name: goals_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goals_parent ON public.goals USING btree (parent_slug) WHERE (parent_slug IS NOT NULL);


--
-- Name: goals_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goals_status ON public.goals USING btree (status);


--
-- Name: jobs_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_citekey ON public.jobs USING btree (citekey);


--
-- Name: jobs_created_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_created_page ON public.jobs USING btree (created_at, id);


--
-- Name: jobs_goal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_goal ON public.jobs USING btree (goal_slug, created_at DESC) WHERE (goal_slug IS NOT NULL);


--
-- Name: jobs_kind_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_kind_page ON public.jobs USING btree (kind, created_at, id);


--
-- Name: jobs_kind_status_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_kind_status_page ON public.jobs USING btree (kind, status, created_at, id);


--
-- Name: jobs_resume_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_resume_at ON public.jobs USING btree (resume_at) WHERE (resume_at IS NOT NULL);


--
-- Name: jobs_sha256; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_sha256 ON public.jobs USING btree (sha256);


--
-- Name: jobs_status_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_status_page ON public.jobs USING btree (status, created_at, id);


--
-- Name: land_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX land_geom ON public.land USING gist (geom);


--
-- Name: manager_events_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX manager_events_recent ON public.manager_events USING btree (manager_id, id DESC);


--
-- Name: manager_items_next; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX manager_items_next ON public.manager_items USING btree (manager_id, status, priority DESC, id);


--
-- Name: map_changes_grp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX map_changes_grp ON public.map_changes USING btree (grp);


--
-- Name: media_checks_qid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX media_checks_qid ON public.media_checks USING btree (provider, qid) WHERE (qid IS NOT NULL);


--
-- Name: media_links_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX media_links_item ON public.media_links USING btree (item, rank) WHERE (item IS NOT NULL);


--
-- Name: media_links_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX media_links_target ON public.media_links USING btree (target_type, target_id, rank);


--
-- Name: merge_ledger_local; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX merge_ledger_local ON public.merge_ledger USING btree (local_uid);


--
-- Name: merges_bundle; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX merges_bundle ON public.merges USING btree (bundle_id);


--
-- Name: notes_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notes_target ON public.notes USING btree (user_id, target_kind, target_id) WHERE (deleted_at IS NULL);


--
-- Name: notes_user_live; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notes_user_live ON public.notes USING btree (user_id, updated_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: periods_local; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX periods_local ON public.periods USING btree (id) WHERE (dataset = 'local'::text);


--
-- Name: periods_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX periods_period ON public.periods USING gist (period);


--
-- Name: periods_preset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX periods_preset ON public.periods USING btree (preset) WHERE (preset IS NOT NULL);


--
-- Name: place_aliases_place; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_aliases_place ON public.place_aliases USING btree (place_id);


--
-- Name: place_descriptions_import; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_descriptions_import ON public.place_descriptions USING btree (import_source, import_ref);


--
-- Name: place_footprints_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_footprints_citekey ON public.place_footprints USING btree (citekey) WHERE (citekey IS NOT NULL);


--
-- Name: place_footprints_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_footprints_geom ON public.place_footprints USING gist (geom);


--
-- Name: place_footprints_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_footprints_period ON public.place_footprints USING gist (period);


--
-- Name: place_footprints_place; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_footprints_place ON public.place_footprints USING btree (place_id);


--
-- Name: place_links_external; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_links_external ON public.place_links USING btree (scheme, external_id);


--
-- Name: place_links_import; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_links_import ON public.place_links USING btree (import_source, import_ref) WHERE (import_source <> ''::text);


--
-- Name: place_names_import; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_import ON public.place_names USING btree (import_source, import_ref) WHERE (import_source <> ''::text);


--
-- Name: place_names_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_key ON public.place_names USING btree (public.histmersion_place_key(name));


--
-- Name: place_names_manual; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_manual ON public.place_names USING btree (place_id) WHERE manual;


--
-- Name: place_names_name_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_name_ci ON public.place_names USING btree (lower(name));


--
-- Name: place_names_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_name_lower ON public.place_names USING btree (lower(name) text_pattern_ops);


--
-- Name: place_names_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_names_name_trgm ON public.place_names USING gin (name public.gin_trgm_ops);


--
-- Name: place_relations_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_relations_parent ON public.place_relations USING btree (parent_id);


--
-- Name: place_relations_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX place_relations_period_gist ON public.place_relations USING gist (period);


--
-- Name: places_area_time_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_area_time_geom ON public.places USING gist (period, geom) WHERE (kind <> 'site'::text);


--
-- Name: places_base_layer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_base_layer ON public.places USING btree (kind) WHERE ((import_source IS NULL) OR (import_source <> ALL (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])));


--
-- Name: places_base_layer_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_base_layer_time ON public.places USING gist (kind, period) WHERE ((import_source IS NULL) OR (import_source <> ALL (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])));


--
-- Name: places_before_modern; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_before_modern ON public.places USING btree (id) WHERE (NOT ((COALESCE(import_source, ''::text) = ANY (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])) AND COALESCE(((properties ->> 'modern'::text))::boolean, false)));


--
-- Name: places_before_modern_label_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_before_modern_label_trgm ON public.places USING gin (label public.gin_trgm_ops) WHERE (NOT ((COALESCE(import_source, ''::text) = ANY (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])) AND COALESCE(((properties ->> 'modern'::text))::boolean, false)));


--
-- Name: places_dated_import; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_dated_import ON public.places USING gist (kind, period) WHERE ((import_source = ANY (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])) AND (NOT (lower_inf(period) AND upper_inf(period))));


--
-- Name: places_explicit_point; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_explicit_point ON public.places USING gist (geom) WHERE ((COALESCE(import_source, ''::text) <> ALL (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])) AND COALESCE(((public.st_dimension(geom) = 0) AND ((COALESCE(kind, ''::text) <> ALL (ARRAY['site'::text, 'feature'::text])) OR COALESCE((location_precision = 'grid-cell'::text), false))), false));


--
-- Name: places_geom_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_geom_gist ON public.places USING gist (geom);


--
-- Name: places_import; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_import ON public.places USING btree (import_source, import_ref) WHERE (import_source IS NOT NULL);


--
-- Name: places_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_kind ON public.places USING btree (kind);


--
-- Name: places_label_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_label_key ON public.places USING btree (public.histmersion_place_key(label));


--
-- Name: places_label_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_label_trgm ON public.places USING gin (label public.gin_trgm_ops);


--
-- Name: places_local; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_local ON public.places USING btree (id) WHERE (dataset = 'local'::text);


--
-- Name: places_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_period_gist ON public.places USING gist (period);


--
-- Name: places_rep_point; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_rep_point ON public.places USING gist (public.st_pointonsurface(geom)) WHERE ((public.st_dimension(geom) > 0) AND (kind <> 'site'::text) AND (COALESCE(import_source, ''::text) <> ALL (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])));


--
-- Name: places_site_map; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_site_map ON public.places USING gist (period, geom) WHERE ((kind = 'site'::text) AND (geom IS NOT NULL) AND (NOT ((COALESCE(import_source, ''::text) = ANY (ARRAY['wikidata'::text, 'geonames'::text, 'tgn'::text])) AND lower_inf(period) AND upper_inf(period))));


--
-- Name: places_site_undated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX places_site_undated ON public.places USING gist (geom) WHERE ((kind = 'site'::text) AND (geom IS NOT NULL) AND lower_inf(period) AND upper_inf(period));


--
-- Name: plan_quota_refreshes_pool_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX plan_quota_refreshes_pool_at ON public.plan_quota_refreshes USING btree (pool, at DESC);


--
-- Name: queue_items_closed_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX queue_items_closed_job ON public.queue_items USING btree (closed_job) WHERE (closed_job IS NOT NULL);


--
-- Name: queue_items_created_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX queue_items_created_job ON public.queue_items USING btree (created_job) WHERE (created_job IS NOT NULL);


--
-- Name: queue_items_goal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX queue_items_goal ON public.queue_items USING btree (goal_slug, list, status) WHERE (goal_slug IS NOT NULL);


--
-- Name: queue_items_list; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX queue_items_list ON public.queue_items USING btree (list, status, id);


--
-- Name: queue_items_log_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX queue_items_log_page ON public.queue_items USING btree (created_at, id) WHERE ((list = 'log'::text) AND (status <> 'note'::text));


--
-- Name: read_marks_opened; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX read_marks_opened ON public.read_marks USING btree (user_id, opened_at DESC) WHERE (opened_at IS NOT NULL);


--
-- Name: source_aliases_citekey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX source_aliases_citekey ON public.source_aliases USING btree (citekey);


--
-- Name: source_candidates_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX source_candidates_state ON public.source_candidates USING btree (goal, state, id);


--
-- Name: source_chunks_citekey_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX source_chunks_citekey_idx ON public.source_chunks USING btree (citekey);


--
-- Name: source_chunks_tsv_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX source_chunks_tsv_gin ON public.source_chunks USING gin (tsv);


--
-- Name: source_identifiers_value; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX source_identifiers_value ON public.source_identifiers USING btree (scheme, value);


--
-- Name: sources_authors_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_authors_trgm ON public.sources USING gin (public.histmersion_authors_text(authors) public.gin_trgm_ops);


--
-- Name: sources_period_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_period_gist ON public.sources USING gist (period);


--
-- Name: sources_regions_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_regions_gin ON public.sources USING gin (regions);


--
-- Name: sources_title_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_title_trgm ON public.sources USING gin (title public.gin_trgm_ops);


--
-- Name: sources_topics_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_topics_gin ON public.sources USING gin (topics);


--
-- Name: sources_tsv_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_tsv_gin ON public.sources USING gin (tsv);


--
-- Name: sources_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sources_updated_at ON public.sources USING btree (updated_at);


--
-- Name: tombstones_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tombstones_deleted_at ON public.tombstones USING btree (deleted_at);


--
-- Name: uid_aliases_uid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX uid_aliases_uid ON public.uid_aliases USING btree (uid);


--
-- Name: unresolved_edges_dst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX unresolved_edges_dst ON public.unresolved_edges USING btree (dst);


--
-- Name: article_versions article_versions_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER article_versions_tombstone AFTER DELETE ON public.article_versions FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('article-version');


--
-- Name: article_versions article_versions_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER article_versions_untombstone AFTER INSERT ON public.article_versions FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: articles articles_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER articles_tombstone AFTER DELETE ON public.articles FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('article');


--
-- Name: articles articles_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER articles_untombstone AFTER INSERT ON public.articles FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: asset_links asset_links_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER asset_links_tombstone AFTER DELETE ON public.asset_links FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('asset-link');


--
-- Name: asset_links asset_links_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER asset_links_touch_updated_at BEFORE UPDATE ON public.asset_links FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: asset_links asset_links_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER asset_links_untombstone AFTER INSERT ON public.asset_links FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: assets assets_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER assets_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.assets FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('media');


--
-- Name: assets assets_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER assets_tombstone AFTER DELETE ON public.assets FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('asset');


--
-- Name: assets assets_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER assets_touch_updated_at BEFORE UPDATE ON public.assets FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: assets assets_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER assets_untombstone AFTER INSERT ON public.assets FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: attestations attestations_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER attestations_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.attestations FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('events');


--
-- Name: attestations attestations_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER attestations_tombstone AFTER DELETE ON public.attestations FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('attestation');


--
-- Name: attestations attestations_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER attestations_touch_updated_at BEFORE UPDATE ON public.attestations FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: attestations attestations_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER attestations_untombstone AFTER INSERT ON public.attestations FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: edges edges_touch_source; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER edges_touch_source AFTER INSERT OR DELETE ON public.edges FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_source_of_edge();


--
-- Name: event_catalogue event_catalogue_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER event_catalogue_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.event_catalogue FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('events');


--
-- Name: event_datings event_datings_touch_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER event_datings_touch_event AFTER INSERT OR DELETE OR UPDATE ON public.event_datings FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_event();


--
-- Name: event_edges event_edges_touch_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER event_edges_touch_event AFTER INSERT OR DELETE OR UPDATE ON public.event_edges FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_event();


--
-- Name: event_participants event_participants_touch_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER event_participants_touch_event AFTER INSERT OR DELETE OR UPDATE ON public.event_participants FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_event();


--
-- Name: events events_follow_corrections; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER events_follow_corrections AFTER DELETE OR UPDATE OF id ON public.events FOR EACH ROW EXECUTE FUNCTION public.events_follow_corrections();


--
-- Name: events events_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER events_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.events FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('events');


--
-- Name: events events_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER events_tombstone AFTER DELETE ON public.events FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('event');


--
-- Name: events events_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER events_touch_updated_at BEFORE UPDATE ON public.events FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: events events_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER events_untombstone AFTER INSERT ON public.events FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: finding_places finding_places_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER finding_places_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.finding_places FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('corpus');


--
-- Name: findings findings_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER findings_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.findings FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('corpus');


--
-- Name: findings findings_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER findings_tombstone AFTER DELETE ON public.findings FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('finding');


--
-- Name: findings findings_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER findings_touch_updated_at BEFORE UPDATE ON public.findings FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: findings findings_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER findings_untombstone AFTER INSERT ON public.findings FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: goal_links goal_links_touch_goal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER goal_links_touch_goal AFTER INSERT OR DELETE ON public.goal_links FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_goal();


--
-- Name: goal_schedules goal_schedules_touch_goal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER goal_schedules_touch_goal AFTER INSERT OR DELETE ON public.goal_schedules FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_goal();


--
-- Name: goal_schedules goal_schedules_touch_goal_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER goal_schedules_touch_goal_update AFTER UPDATE OF cron, enabled, budget_usd ON public.goal_schedules FOR EACH ROW WHEN (((old.cron IS DISTINCT FROM new.cron) OR (old.enabled IS DISTINCT FROM new.enabled) OR (old.budget_usd IS DISTINCT FROM new.budget_usd))) EXECUTE FUNCTION public.histmersion_touch_goal();


--
-- Name: goals goals_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER goals_tombstone AFTER DELETE ON public.goals FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('goal');


--
-- Name: goals goals_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER goals_untombstone AFTER INSERT ON public.goals FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: periods periods_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER periods_tombstone AFTER DELETE ON public.periods FOR EACH ROW WHEN ((old.dataset = 'local'::text)) EXECUTE FUNCTION public.histmersion_tombstone('period');


--
-- Name: periods periods_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER periods_touch_updated_at BEFORE UPDATE ON public.periods FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: periods periods_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER periods_untombstone AFTER INSERT ON public.periods FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: place_links place_links_touch_place; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER place_links_touch_place AFTER INSERT OR DELETE OR UPDATE ON public.place_links FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_place();


--
-- Name: place_names place_names_touch_place; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER place_names_touch_place AFTER INSERT OR DELETE OR UPDATE ON public.place_names FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_place();


--
-- Name: place_relations place_relations_touch_place; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER place_relations_touch_place AFTER INSERT OR DELETE OR UPDATE ON public.place_relations FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_place();


--
-- Name: place_types place_types_touch_place; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER place_types_touch_place AFTER INSERT OR DELETE OR UPDATE ON public.place_types FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_place();


--
-- Name: places places_clip_to_land; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_clip_to_land BEFORE INSERT OR UPDATE OF geom, kind ON public.places FOR EACH ROW EXECUTE FUNCTION public.places_clip_to_land();


--
-- Name: places places_extent_footprint; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_extent_footprint AFTER INSERT OR UPDATE OF geom, period, source, properties ON public.places FOR EACH ROW EXECUTE FUNCTION public.places_sync_extent_footprint();


--
-- Name: places places_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.places FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('places');


--
-- Name: places places_override; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_override BEFORE INSERT OR UPDATE OF kind, period, start_unknown, geom ON public.places FOR EACH ROW EXECUTE FUNCTION public.places_apply_override();


--
-- Name: places places_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_tombstone AFTER DELETE ON public.places FOR EACH ROW WHEN ((old.dataset = 'local'::text)) EXECUTE FUNCTION public.histmersion_tombstone('place');


--
-- Name: places places_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_touch_updated_at BEFORE UPDATE ON public.places FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: places places_undated_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_undated_delete AFTER DELETE ON public.places REFERENCING OLD TABLE AS old_rows FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_undated_changed();


--
-- Name: places places_undated_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_undated_insert AFTER INSERT ON public.places REFERENCING NEW TABLE AS new_rows FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_undated_changed();


--
-- Name: places places_undated_truncate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_undated_truncate AFTER TRUNCATE ON public.places FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_undated_changed();


--
-- Name: places places_undated_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_undated_update AFTER UPDATE ON public.places REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_undated_changed();


--
-- Name: places places_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER places_untombstone AFTER INSERT ON public.places FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: queue_items queue_items_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER queue_items_tombstone AFTER DELETE ON public.queue_items FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('queue-item');


--
-- Name: queue_items queue_items_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER queue_items_touch_updated_at BEFORE UPDATE ON public.queue_items FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_updated_at();


--
-- Name: queue_items queue_items_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER queue_items_untombstone AFTER INSERT ON public.queue_items FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: sources sources_identifiers; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sources_identifiers AFTER INSERT OR UPDATE OF doi, sha256, uid ON public.sources FOR EACH ROW EXECUTE FUNCTION public.histmersion_sync_source_identifiers();


--
-- Name: sources sources_map_changed; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sources_map_changed AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.sources FOR EACH STATEMENT EXECUTE FUNCTION public.histmersion_map_changed('corpus');


--
-- Name: sources sources_tombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sources_tombstone AFTER DELETE ON public.sources FOR EACH ROW EXECUTE FUNCTION public.histmersion_tombstone('source');


--
-- Name: sources sources_untombstone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sources_untombstone AFTER INSERT ON public.sources FOR EACH ROW EXECUTE FUNCTION public.histmersion_untombstone();


--
-- Name: unresolved_edges unresolved_edges_touch_source; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER unresolved_edges_touch_source AFTER INSERT OR DELETE ON public.unresolved_edges FOR EACH ROW EXECUTE FUNCTION public.histmersion_touch_source_of_edge();


--
-- Name: acquire_checks acquire_checks_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acquire_checks
    ADD CONSTRAINT acquire_checks_item_id_fkey FOREIGN KEY (item_id) REFERENCES public.queue_items(id) ON DELETE CASCADE;


--
-- Name: agent_managers agent_managers_goal_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_managers
    ADD CONSTRAINT agent_managers_goal_slug_fkey FOREIGN KEY (goal_slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: agent_messages agent_messages_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_messages
    ADD CONSTRAINT agent_messages_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: agent_steps agent_steps_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_steps
    ADD CONSTRAINT agent_steps_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: approvals approvals_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE SET NULL;


--
-- Name: article_aliases article_aliases_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_aliases
    ADD CONSTRAINT article_aliases_slug_fkey FOREIGN KEY (slug) REFERENCES public.articles(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: article_critique_reasons article_critique_reasons_dst_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_critique_reasons
    ADD CONSTRAINT article_critique_reasons_dst_fkey FOREIGN KEY (dst) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: article_critique_reasons article_critique_reasons_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_critique_reasons
    ADD CONSTRAINT article_critique_reasons_slug_fkey FOREIGN KEY (slug) REFERENCES public.articles(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: article_critique_reasons article_critique_reasons_src_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_critique_reasons
    ADD CONSTRAINT article_critique_reasons_src_fkey FOREIGN KEY (src) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: article_links article_links_src_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_links
    ADD CONSTRAINT article_links_src_slug_fkey FOREIGN KEY (src_slug) REFERENCES public.articles(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: article_versions article_versions_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article_versions
    ADD CONSTRAINT article_versions_slug_fkey FOREIGN KEY (slug) REFERENCES public.articles(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: assessments assessments_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessments
    ADD CONSTRAINT assessments_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: asset_links asset_links_citekey_asset_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset_links
    ADD CONSTRAINT asset_links_citekey_asset_name_fkey FOREIGN KEY (citekey, asset_name) REFERENCES public.assets(citekey, name) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: assets assets_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: attestations attestations_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attestations
    ADD CONSTRAINT attestations_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: candidate_watches candidate_watches_goal_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.candidate_watches
    ADD CONSTRAINT candidate_watches_goal_fkey FOREIGN KEY (goal) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: edges edges_dst_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT edges_dst_source FOREIGN KEY (dst) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: edges edges_src_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT edges_src_source FOREIGN KEY (src) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_aliases event_aliases_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_aliases
    ADD CONSTRAINT event_aliases_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_catalogue event_catalogue_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_catalogue
    ADD CONSTRAINT event_catalogue_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_datings event_datings_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_datings
    ADD CONSTRAINT event_datings_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_edges event_edges_dst_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_edges
    ADD CONSTRAINT event_edges_dst_id_fkey FOREIGN KEY (dst_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_edges event_edges_src_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_edges
    ADD CONSTRAINT event_edges_src_id_fkey FOREIGN KEY (src_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_link_candidates event_link_candidates_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_link_candidates
    ADD CONSTRAINT event_link_candidates_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_link_candidates event_link_candidates_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_link_candidates
    ADD CONSTRAINT event_link_candidates_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_link_checks event_link_checks_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_link_checks
    ADD CONSTRAINT event_link_checks_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: event_participants event_participants_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_participants
    ADD CONSTRAINT event_participants_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: events events_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.events(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: events events_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: finding_places finding_places_finding_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_places
    ADD CONSTRAINT finding_places_finding_id_fkey FOREIGN KEY (finding_id) REFERENCES public.findings(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: finding_places finding_places_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_places
    ADD CONSTRAINT finding_places_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: findings findings_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT findings_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: goal_aliases goal_aliases_goal_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_aliases
    ADD CONSTRAINT goal_aliases_goal_slug_fkey FOREIGN KEY (goal_slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: goal_focus goal_focus_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_focus
    ADD CONSTRAINT goal_focus_slug_fkey FOREIGN KEY (slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: goal_links goal_links_goal_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_links
    ADD CONSTRAINT goal_links_goal_slug_fkey FOREIGN KEY (goal_slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: goal_schedules goal_schedules_goal_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_schedules
    ADD CONSTRAINT goal_schedules_goal_slug_fkey FOREIGN KEY (goal_slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: goals goals_parent_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goals
    ADD CONSTRAINT goals_parent_slug_fkey FOREIGN KEY (parent_slug) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: manager_events manager_events_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_events
    ADD CONSTRAINT manager_events_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.agent_managers(id) ON DELETE CASCADE;


--
-- Name: manager_hint_uses manager_hint_uses_hint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hint_uses
    ADD CONSTRAINT manager_hint_uses_hint_id_fkey FOREIGN KEY (hint_id) REFERENCES public.manager_hints(id) ON DELETE CASCADE;


--
-- Name: manager_hint_uses manager_hint_uses_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hint_uses
    ADD CONSTRAINT manager_hint_uses_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: manager_hints manager_hints_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hints
    ADD CONSTRAINT manager_hints_item_id_fkey FOREIGN KEY (item_id) REFERENCES public.manager_items(id) ON DELETE CASCADE;


--
-- Name: manager_hints manager_hints_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_hints
    ADD CONSTRAINT manager_hints_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.agent_managers(id) ON DELETE CASCADE;


--
-- Name: manager_items manager_items_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_items
    ADD CONSTRAINT manager_items_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.agent_managers(id) ON DELETE CASCADE;


--
-- Name: manager_items manager_items_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_items
    ADD CONSTRAINT manager_items_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.jobs(id) ON DELETE SET NULL;


--
-- Name: media_links media_links_asset_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_links
    ADD CONSTRAINT media_links_asset_uid_fkey FOREIGN KEY (asset_uid) REFERENCES public.assets(uid) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: merge_decisions merge_decisions_merge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merge_decisions
    ADD CONSTRAINT merge_decisions_merge_id_fkey FOREIGN KEY (merge_id) REFERENCES public.merges(id) ON DELETE SET NULL;


--
-- Name: merge_ledger merge_ledger_merge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merge_ledger
    ADD CONSTRAINT merge_ledger_merge_id_fkey FOREIGN KEY (merge_id) REFERENCES public.merges(id) ON DELETE CASCADE;


--
-- Name: periods periods_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periods
    ADD CONSTRAINT periods_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.periods(id) ON DELETE SET NULL;


--
-- Name: place_aliases place_aliases_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_aliases
    ADD CONSTRAINT place_aliases_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_descriptions place_descriptions_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_descriptions
    ADD CONSTRAINT place_descriptions_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_footprints place_footprints_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_footprints
    ADD CONSTRAINT place_footprints_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_links place_links_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_links
    ADD CONSTRAINT place_links_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_names place_names_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_names
    ADD CONSTRAINT place_names_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_overrides place_overrides_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_overrides
    ADD CONSTRAINT place_overrides_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_relations place_relations_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_relations
    ADD CONSTRAINT place_relations_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_relations place_relations_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_relations
    ADD CONSTRAINT place_relations_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: place_types place_types_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_types
    ADD CONSTRAINT place_types_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: review_ledgers review_ledgers_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.review_ledgers
    ADD CONSTRAINT review_ledgers_slug_fkey FOREIGN KEY (slug) REFERENCES public.articles(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: source_aliases source_aliases_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_aliases
    ADD CONSTRAINT source_aliases_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: source_candidates source_candidates_goal_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_candidates
    ADD CONSTRAINT source_candidates_goal_fkey FOREIGN KEY (goal) REFERENCES public.goals(slug) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: source_chunks source_chunks_citekey_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_chunks
    ADD CONSTRAINT source_chunks_citekey_fkey FOREIGN KEY (citekey) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: source_identifiers source_identifiers_source_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_identifiers
    ADD CONSTRAINT source_identifiers_source_uid_fkey FOREIGN KEY (source_uid) REFERENCES public.sources(uid) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: uid_aliases uid_aliases_merge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uid_aliases
    ADD CONSTRAINT uid_aliases_merge_id_fkey FOREIGN KEY (merge_id) REFERENCES public.merges(id) ON DELETE SET NULL;


--
-- Name: unresolved_edges unresolved_edges_src_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unresolved_edges
    ADD CONSTRAINT unresolved_edges_src_fkey FOREIGN KEY (src) REFERENCES public.sources(citekey) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA public TO histmersion_query;


--
-- Name: TABLE acquire_checks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.acquire_checks TO histmersion_query;


--
-- Name: TABLE agent_allowances; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_allowances TO histmersion_query;


--
-- Name: TABLE agent_autopilot; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_autopilot TO histmersion_query;


--
-- Name: TABLE agent_leases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_leases TO histmersion_query;


--
-- Name: TABLE agent_managers; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_managers TO histmersion_query;


--
-- Name: TABLE agent_messages; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_messages TO histmersion_query;


--
-- Name: TABLE agent_role_chains; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_role_chains TO histmersion_query;


--
-- Name: TABLE agent_role_fallthrough; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_role_fallthrough TO histmersion_query;


--
-- Name: TABLE agent_state; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_state TO histmersion_query;


--
-- Name: TABLE agent_steps; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.agent_steps TO histmersion_query;


--
-- Name: TABLE alias_tombstones; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.alias_tombstones TO histmersion_query;


--
-- Name: TABLE approvals; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.approvals TO histmersion_query;


--
-- Name: TABLE article_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.article_aliases TO histmersion_query;


--
-- Name: TABLE article_critique_reasons; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.article_critique_reasons TO histmersion_query;


--
-- Name: TABLE article_links; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.article_links TO histmersion_query;


--
-- Name: TABLE article_versions; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.article_versions TO histmersion_query;


--
-- Name: TABLE articles; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.articles TO histmersion_query;


--
-- Name: TABLE assessments; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.assessments TO histmersion_query;


--
-- Name: TABLE asset_links; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.asset_links TO histmersion_query;


--
-- Name: TABLE assets; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.assets TO histmersion_query;


--
-- Name: TABLE attestations; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.attestations TO histmersion_query;


--
-- Name: TABLE bookmarks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.bookmarks TO histmersion_query;


--
-- Name: TABLE candidate_watches; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.candidate_watches TO histmersion_query;


--
-- Name: TABLE corrections; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.corrections TO histmersion_query;


--
-- Name: TABLE edges; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.edges TO histmersion_query;


--
-- Name: TABLE event_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_aliases TO histmersion_query;


--
-- Name: TABLE event_catalogue; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_catalogue TO histmersion_query;


--
-- Name: TABLE event_datings; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_datings TO histmersion_query;


--
-- Name: TABLE event_edges; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_edges TO histmersion_query;


--
-- Name: TABLE event_import_parts; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_import_parts TO histmersion_query;


--
-- Name: TABLE event_link_candidates; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_link_candidates TO histmersion_query;


--
-- Name: TABLE event_link_checks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_link_checks TO histmersion_query;


--
-- Name: TABLE event_participants; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.event_participants TO histmersion_query;


--
-- Name: TABLE events; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.events TO histmersion_query;


--
-- Name: TABLE finding_places; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.finding_places TO histmersion_query;


--
-- Name: TABLE findings; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.findings TO histmersion_query;


--
-- Name: TABLE findings_flat; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.findings_flat TO histmersion_query;


--
-- Name: TABLE gazetteer_sources; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.gazetteer_sources TO histmersion_query;


--
-- Name: TABLE geography_columns; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.geography_columns TO histmersion_query;


--
-- Name: TABLE geometry_columns; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.geometry_columns TO histmersion_query;


--
-- Name: TABLE goal_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.goal_aliases TO histmersion_query;


--
-- Name: TABLE goal_focus; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.goal_focus TO histmersion_query;


--
-- Name: TABLE goal_links; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.goal_links TO histmersion_query;


--
-- Name: TABLE goal_schedules; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.goal_schedules TO histmersion_query;


--
-- Name: TABLE goals; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.goals TO histmersion_query;


--
-- Name: TABLE instance; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.instance TO histmersion_query;


--
-- Name: TABLE jobs; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.jobs TO histmersion_query;


--
-- Name: TABLE land; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.land TO histmersion_query;


--
-- Name: TABLE manager_events; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.manager_events TO histmersion_query;


--
-- Name: TABLE manager_hint_uses; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.manager_hint_uses TO histmersion_query;


--
-- Name: TABLE manager_hints; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.manager_hints TO histmersion_query;


--
-- Name: TABLE manager_items; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.manager_items TO histmersion_query;


--
-- Name: TABLE map_changes; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.map_changes TO histmersion_query;


--
-- Name: TABLE media_checks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.media_checks TO histmersion_query;


--
-- Name: TABLE media_links; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.media_links TO histmersion_query;


--
-- Name: TABLE merge_decisions; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.merge_decisions TO histmersion_query;


--
-- Name: TABLE merge_ledger; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.merge_ledger TO histmersion_query;


--
-- Name: TABLE merge_sessions; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.merge_sessions TO histmersion_query;


--
-- Name: TABLE merges; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.merges TO histmersion_query;


--
-- Name: TABLE notes; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.notes TO histmersion_query;


--
-- Name: TABLE periods; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.periods TO histmersion_query;


--
-- Name: TABLE place_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_aliases TO histmersion_query;


--
-- Name: TABLE place_descriptions; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_descriptions TO histmersion_query;


--
-- Name: TABLE place_footprints; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_footprints TO histmersion_query;


--
-- Name: TABLE place_links; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_links TO histmersion_query;


--
-- Name: TABLE place_names; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_names TO histmersion_query;


--
-- Name: TABLE place_overrides; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_overrides TO histmersion_query;


--
-- Name: TABLE place_relations; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_relations TO histmersion_query;


--
-- Name: TABLE place_removals; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_removals TO histmersion_query;


--
-- Name: TABLE place_types; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.place_types TO histmersion_query;


--
-- Name: TABLE places; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.places TO histmersion_query;


--
-- Name: TABLE plan_pools; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plan_pools TO histmersion_query;


--
-- Name: TABLE plan_quota_readings; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plan_quota_readings TO histmersion_query;


--
-- Name: TABLE plan_quota_refreshes; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plan_quota_refreshes TO histmersion_query;


--
-- Name: TABLE queue_items; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.queue_items TO histmersion_query;


--
-- Name: TABLE read_marks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.read_marks TO histmersion_query;


--
-- Name: TABLE retraction_watch; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.retraction_watch TO histmersion_query;


--
-- Name: TABLE review_ledgers; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.review_ledgers TO histmersion_query;


--
-- Name: TABLE seed_versions; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.seed_versions TO histmersion_query;


--
-- Name: TABLE site_undated_build; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.site_undated_build TO histmersion_query;


--
-- Name: TABLE site_undated_cells; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.site_undated_cells TO histmersion_query;


--
-- Name: TABLE source_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.source_aliases TO histmersion_query;


--
-- Name: TABLE source_candidates; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.source_candidates TO histmersion_query;


--
-- Name: TABLE source_chunks; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.source_chunks TO histmersion_query;


--
-- Name: TABLE source_identifiers; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.source_identifiers TO histmersion_query;


--
-- Name: TABLE sources; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.sources TO histmersion_query;


--
-- Name: TABLE spatial_ref_sys; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.spatial_ref_sys TO histmersion_query;


--
-- Name: TABLE tombstones; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.tombstones TO histmersion_query;


--
-- Name: TABLE ui_seen; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.ui_seen TO histmersion_query;


--
-- Name: TABLE uid_aliases; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.uid_aliases TO histmersion_query;


--
-- Name: TABLE unresolved_edges; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.unresolved_edges TO histmersion_query;


--
-- Name: TABLE upstream_quota; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.upstream_quota TO histmersion_query;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: public; Owner: -
--

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO histmersion_query;


--
-- PostgreSQL database dump complete
--

-- the rows the chain seeded
INSERT INTO public.instance (id) SELECT uuidv7() WHERE NOT EXISTS (SELECT 1 FROM public.instance);
INSERT INTO public.ui_seen (board, seen_at)
SELECT b, now() FROM unnest(ARRAY['goals', 'tasks', 'approvals', 'acquire', 'log']) AS b
ON CONFLICT (board) DO NOTHING;
INSERT INTO public.agent_autopilot (id) VALUES (true);
INSERT INTO public.map_changes (grp) VALUES ('places');
REVOKE CREATE ON SCHEMA public FROM histmersion_query;
