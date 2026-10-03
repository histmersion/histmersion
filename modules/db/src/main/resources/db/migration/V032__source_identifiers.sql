-- V032: strong keys of a source (docs/research-merge-import.md §3, milestone M0).
--
-- The evidence that two records are the same work, kept apart from both the label (citekey) and the
-- identity (uid): a DOI (lower-cased, `https://doi.org/` and `doi:` stripped) and the sha256 of the
-- stored original. `isbn` (ISBN-13) and `openalex` (W-id) are admitted schemes for when a writer
-- knows them; nothing records them yet. The merge matcher (M3) looks pairs up here.
--
-- NOT unique on (scheme, value), though the design wished so: two sources sharing a DOI or a file is
-- exactly what `lint duplicate-doi` / `duplicate-sha256` report today, and a constraint would refuse
-- the migration on a corpus that has one. Within ONE source a (scheme, value) is recorded once.
--
-- Derived from `sources.doi` / `sources.sha256` by a trigger (kept even during a bundle import: it is
-- an index, not data), so no writer has to remember it. Keyed by the source's uid, so a rename
-- leaves it alone.

CREATE TABLE IF NOT EXISTS source_identifiers (
  source_uid uuid NOT NULL REFERENCES sources(uid) ON UPDATE CASCADE ON DELETE CASCADE,
  scheme     text NOT NULL CHECK (scheme IN ('doi', 'sha256', 'isbn', 'openalex')),
  value      text NOT NULL,
  PRIMARY KEY (source_uid, scheme, value)
);
CREATE INDEX IF NOT EXISTS source_identifiers_value ON source_identifiers (scheme, value);

-- The DOI as a key: no resolver prefix, no `doi:`, lower case, trimmed. NULL when nothing is left.
CREATE OR REPLACE FUNCTION histmersion_doi_key(doi text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT nullif(regexp_replace(lower(btrim(doi)), '^(https?://(dx\.)?doi\.org/|doi:\s*)', ''), '')
$$;

CREATE OR REPLACE FUNCTION histmersion_sync_source_identifiers() RETURNS trigger
LANGUAGE plpgsql AS $$
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

DROP TRIGGER IF EXISTS sources_identifiers ON sources;
CREATE TRIGGER sources_identifiers AFTER INSERT OR UPDATE OF doi, sha256, uid ON sources
  FOR EACH ROW EXECUTE FUNCTION histmersion_sync_source_identifiers();

INSERT INTO source_identifiers (source_uid, scheme, value)
SELECT uid, 'doi', histmersion_doi_key(doi) FROM sources WHERE histmersion_doi_key(doi) IS NOT NULL
UNION
SELECT uid, 'sha256', lower(btrim(sha256)) FROM sources WHERE nullif(btrim(sha256), '') IS NOT NULL
ON CONFLICT DO NOTHING;
