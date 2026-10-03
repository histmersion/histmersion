-- V031: tombstones (docs/research-merge-import.md §4, milestone M0).
--
-- A row deleted here must STAY deleted when a bundle from another instance (which still has it) is
-- imported, and an incremental bundle must carry the deletion to the other side; without a record of
-- it, a deletion at work quietly comes back at home on the next merge. So every delete of a row that
-- has a uid leaves `(uid, kind, origin, deleted_at)` behind. A trigger rather than the delete paths:
-- most of these rows go by ON DELETE CASCADE (a source's findings, assets and attestations; an
-- article's versions), which no repository code ever sees.
--
-- `kind` is the bundle's entity kind (`core.BundleKind`'s wire name), passed as the trigger argument.
-- The importer records the tombstones a bundle carries itself, with the bundle's timestamps, so the
-- trigger stands aside while `histmersion.importing` is on (V030).

CREATE TABLE IF NOT EXISTS tombstones (
  uid        uuid        PRIMARY KEY,
  kind       text        NOT NULL,
  origin     uuid        NOT NULL,
  deleted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS tombstones_deleted_at ON tombstones (deleted_at);

CREATE OR REPLACE FUNCTION histmersion_tombstone() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NOT histmersion_importing() THEN
    INSERT INTO tombstones (uid, kind, origin, deleted_at)
    VALUES (OLD.uid, TG_ARGV[0], OLD.origin, now())
    ON CONFLICT (uid) DO UPDATE SET deleted_at = EXCLUDED.deleted_at;
  END IF;
  RETURN NULL;
END
$$;

DO $$
DECLARE
  pair text[];
BEGIN
  FOREACH pair SLICE 1 IN ARRAY ARRAY[
      ARRAY['sources', 'source'], ARRAY['findings', 'finding'], ARRAY['articles', 'article'],
      ARRAY['article_versions', 'article-version'], ARRAY['assets', 'asset'],
      ARRAY['asset_links', 'asset-link'], ARRAY['attestations', 'attestation'], ARRAY['events', 'event'],
      ARRAY['goals', 'goal'], ARRAY['queue_items', 'queue-item']]
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', pair[1] || '_tombstone', pair[1]);
    EXECUTE format('CREATE TRIGGER %I AFTER DELETE ON %I FOR EACH ROW '
                   'EXECUTE FUNCTION histmersion_tombstone(%L)', pair[1] || '_tombstone', pair[1], pair[2]);
  END LOOP;
END
$$;

-- A uid that exists again (a bundle brought it back, deliberately) is no longer deleted.
CREATE OR REPLACE FUNCTION histmersion_untombstone() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM tombstones WHERE uid = NEW.uid;
  RETURN NULL;
END
$$;

DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['sources', 'findings', 'articles', 'article_versions', 'assets',
                           'asset_links', 'attestations', 'events', 'goals', 'queue_items']
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', t || '_untombstone', t);
    EXECUTE format('CREATE TRIGGER %I AFTER INSERT ON %I FOR EACH ROW '
                   'EXECUTE FUNCTION histmersion_untombstone()', t || '_untombstone', t);
  END LOOP;
END
$$;
