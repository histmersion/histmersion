-- V104: the acquisition list's legal re-check. One row per acquisition item that was checked: when, under
-- which fetcher generation (a digest of the legal chain and its fetchers' settings: a new fetcher or key
-- makes the item due again), what it found (closed | fetched | no-copy | failed) and the short reason
-- shown with the item. Removed with the item.
CREATE TABLE acquire_checks (
  item_id    integer PRIMARY KEY REFERENCES queue_items(id) ON DELETE CASCADE,
  checked_at timestamptz NOT NULL,
  generation text NOT NULL DEFAULT '',
  outcome    text NOT NULL,
  reason     text NOT NULL,
  checks     integer NOT NULL DEFAULT 1
);
CREATE INDEX acquire_checks_at ON acquire_checks (checked_at);
